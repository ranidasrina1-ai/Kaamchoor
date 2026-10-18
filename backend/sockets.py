"""Socket.IO handlers: realtime chat + WebRTC signaling + notifications.

IMPORTANT (v1 bug fixed): `register_socket_handlers(sio)` MUST be called from
the app factory. In v1 it was never called, so no realtime features worked.
"""
from flask import request
from flask_socketio import join_room
from flask_jwt_extended import decode_token
from jwt.exceptions import InvalidTokenError
from sqlalchemy import func

from models import (
    db,
    User,
    ChatGroup,
    GroupMember,
    Message,
    MessageStatus,
)
from notifications import notify_call, notify_new_message

# user_id -> set of socket ids (in-memory; single process local setup)
user_sockets: dict[int, set[str]] = {}
socket_to_user: dict[str, int] = {}
_sio = None


def _authenticate(sid):
    token = None
    try:
        token = request.args.get("token")
        if not token:
            auth = request.headers.get("Authorization", "")
            if auth.startswith("Bearer "):
                token = auth[7:]
        if not token and getattr(request, "auth", None):
            token = request.auth.get("token") if isinstance(request.auth, dict) else None
        if not token:
            return None
        decoded = decode_token(token)
        return int(decoded["sub"])
    except (InvalidTokenError, KeyError, ValueError, TypeError):
        return None


def emit_to_user(user_id, event, data):
    """Send an event to all of a user's connected sockets."""
    if _sio is None:
        return
    for sid in list(user_sockets.get(int(user_id), set())):
        _sio.emit(event, data, room=sid)


def emit_to_group_members(group_id, event, data, exclude_user_id=None):
    members = GroupMember.query.filter_by(group_id=group_id).all()
    for m in members:
        if exclude_user_id and m.user_id == exclude_user_id:
            continue
        emit_to_user(m.user_id, event, data)


def _is_member(group_id, user_id):
    return GroupMember.query.filter_by(group_id=group_id, user_id=user_id).first() is not None


def _group_member_ids(group_id, exclude_user_id=None):
    rows = GroupMember.query.filter_by(group_id=group_id).all()
    return [m.user_id for m in rows if m.user_id != exclude_user_id]


def register_socket_handlers(sio):
    global _sio
    _sio = sio

    @sio.on("connect")
    def handle_connect(auth=None):
        user_id = _authenticate(request.sid)
        if user_id is None:
            return False  # reject unauthenticated connections

        socket_to_user[request.sid] = user_id
        user_sockets.setdefault(user_id, set()).add(request.sid)

        for m in GroupMember.query.filter_by(user_id=user_id).all():
            join_room(f"group_{m.group_id}")

        user = User.query.get(user_id)
        if user:
            user.last_seen = func.now()
            db.session.commit()

        emit_to_user(user_id, "connected", {"user_id": user_id})

    @sio.on("disconnect")
    def handle_disconnect():
        user_id = socket_to_user.pop(request.sid, None)
        if user_id is None:
            return
        sids = user_sockets.get(user_id)
        if sids:
            sids.discard(request.sid)
            if not sids:
                del user_sockets[user_id]
                sio.emit("user_offline", {"user_id": user_id})

    # ── CHAT: send message via socket ────────────────────────────────────
    @sio.on("send_message")
    def handle_send_message(data):
        user_id = socket_to_user.get(request.sid)
        if not user_id:
            return {"error": "Not authenticated"}
        data = data or {}
        group_id = data.get("group_id")
        if not group_id or not _is_member(group_id, user_id):
            return {"error": "Not a member"}

        msg = Message(
            group_id=group_id,
            sender_id=user_id,
            text=data.get("text"),
            media_url=data.get("media_url"),
            media_type=data.get("media_type", "text"),
            duration=data.get("duration"),
        )
        db.session.add(msg)
        db.session.flush()

        recipients = _group_member_ids(group_id, exclude_user_id=user_id)
        for rid in recipients:
            db.session.add(MessageStatus(message_id=msg.id, user_id=rid, status="sent"))

        group = ChatGroup.query.get(group_id)
        if group:
            group.last_activity = func.now()

        db.session.commit()

        payload = msg.to_dict()
        emit_to_group_members(group_id, "new_message", payload, exclude_user_id=user_id)
        emit_to_user(user_id, "message_sent", payload)

        sender = User.query.get(user_id)
        if sender:
            notify_new_message(group_id, sender, msg, recipients)
        return payload

    @sio.on("typing")
    def handle_typing(data):
        user_id = socket_to_user.get(request.sid)
        if not user_id:
            return
        data = data or {}
        group_id = data.get("group_id")
        if not group_id or not _is_member(group_id, user_id):
            return
        user = User.query.get(user_id)
        name = (user.display_name or user.username) if user else "Someone"
        emit_to_group_members(
            group_id,
            "user_typing",
            {
                "group_id": group_id,
                "user_id": user_id,
                "username": name,
                "is_typing": bool(data.get("is_typing", False)),
            },
            exclude_user_id=user_id,
        )

    @sio.on("mark_delivered")
    def handle_mark_delivered(data):
        user_id = socket_to_user.get(request.sid)
        if not user_id:
            return
        data = data or {}
        group_id = data.get("group_id")
        if not group_id or not _is_member(group_id, user_id):
            return

        pending = (
            MessageStatus.query
            .join(Message, MessageStatus.message_id == Message.id)
            .filter(
                Message.group_id == group_id,
                Message.sender_id != user_id,
                MessageStatus.user_id == user_id,
                MessageStatus.status == "sent",
            )
            .all()
        )
        for ms in pending:
            ms.status = "delivered"
        db.session.commit()

        msg_ids = [ms.message_id for ms in pending]
        if msg_ids:
            for sender_id in _senders_of(msg_ids):
                emit_to_user(sender_id, "messages_delivered", {
                    "group_id": group_id,
                    "message_ids": msg_ids,
                })

    @sio.on("mark_read")
    def handle_mark_read(data):
        user_id = socket_to_user.get(request.sid)
        if not user_id:
            return
        data = data or {}
        group_id = data.get("group_id")
        message_ids = data.get("message_ids", []) or []
        if not group_id or not _is_member(group_id, user_id):
            return

        updated = []
        for mid in message_ids:
            ms = MessageStatus.query.filter_by(message_id=mid, user_id=user_id).first()
            if ms is None:
                ms = MessageStatus(message_id=mid, user_id=user_id, status="read")
                db.session.add(ms)
                updated.append(mid)
            elif ms.status != "read":
                ms.status = "read"
                updated.append(mid)
        db.session.commit()

        if updated:
            for sender_id in _senders_of(updated):
                if sender_id == user_id:
                    continue
                emit_to_user(sender_id, "messages_read", {
                    "group_id": group_id,
                    "message_ids": updated,
                    "read_by": user_id,
                })

    # ── WebRTC signaling (mesh) ──────────────────────────────────────────
    @sio.on("call_offer")
    def handle_call_offer(data):
        user_id = socket_to_user.get(request.sid)
        if not user_id:
            return {"error": "Not authenticated"}
        data = data or {}
        caller = User.query.get(user_id)
        payload = {
            "from_user_id": user_id,
            "from_username": (caller.display_name or caller.username) if caller else "Unknown",
            "offer": data.get("offer"),
            "call_id": data.get("call_id"),
            "call_type": data.get("call_type", "audio"),
            "group_id": data.get("group_id"),
            "video": data.get("call_type") == "video",
        }
        to_user_id = data.get("to_user_id")
        group_id = data.get("group_id")
        if to_user_id:
            emit_to_user(int(to_user_id), "incoming_call", payload)
            if caller:
                notify_call(int(to_user_id), caller, payload["call_type"], data.get("call_id"),
                            group_id=group_id, is_group=bool(group_id))
        elif group_id and _is_member(group_id, user_id):
            emit_to_group_members(group_id, "incoming_call", payload, exclude_user_id=user_id)

    @sio.on("call_answer")
    def handle_call_answer(data):
        user_id = socket_to_user.get(request.sid)
        if not user_id:
            return {"error": "Not authenticated"}
        data = data or {}
        to_user_id = data.get("to_user_id")
        if to_user_id:
            emit_to_user(int(to_user_id), "call_answered", {
                "from_user_id": user_id,
                "answer": data.get("answer"),
                "call_id": data.get("call_id"),
            })

    @sio.on("call_reject")
    def handle_call_reject(data):
        user_id = socket_to_user.get(request.sid)
        if not user_id:
            return {"error": "Not authenticated"}
        data = data or {}
        to_user_id = data.get("to_user_id")
        if to_user_id:
            emit_to_user(int(to_user_id), "call_rejected", {
                "from_user_id": user_id,
                "call_id": data.get("call_id"),
            })

    @sio.on("ice_candidate")
    def handle_ice_candidate(data):
        user_id = socket_to_user.get(request.sid)
        if not user_id:
            return
        data = data or {}
        to_user_id = data.get("to_user_id")
        if to_user_id:
            emit_to_user(int(to_user_id), "ice_candidate", {
                "from_user_id": user_id,
                "candidate": data.get("candidate"),
            })

    @sio.on("call_end")
    def handle_call_end(data):
        user_id = socket_to_user.get(request.sid)
        if not user_id:
            return
        data = data or {}
        payload = {"from_user_id": user_id, "call_id": data.get("call_id")}
        to_user_id = data.get("to_user_id")
        group_id = data.get("group_id")
        if to_user_id:
            emit_to_user(int(to_user_id), "call_ended", payload)
        elif group_id:
            emit_to_group_members(group_id, "call_ended", payload, exclude_user_id=user_id)


def _senders_of(message_ids):
    rows = Message.query.filter(Message.id.in_(message_ids)).all()
    return {m.sender_id for m in rows}
