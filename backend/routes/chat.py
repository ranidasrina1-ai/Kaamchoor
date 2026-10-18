"""Chat routes: groups, messages (paginated + media), read state."""
from flask import Blueprint, request, jsonify, current_app
from flask_jwt_extended import jwt_required, get_jwt_identity
from sqlalchemy import func, or_, and_

from models import db, ChatGroup, Message, MessageStatus, GroupMember, User
from notifications import notify_new_message
from sockets import emit_to_group_members, emit_to_user
from utils import save_uploaded_file, build_media_url, probe_video_duration

chat_bp = Blueprint("chat", __name__)

VOICE_MAX_SECONDS = 120


def _member_ids(group_id, exclude=None):
    rows = GroupMember.query.filter_by(group_id=group_id).all()
    return [m.user_id for m in rows if m.user_id != exclude]


def _create_statuses(message, recipient_ids):
    for rid in recipient_ids:
        db.session.add(MessageStatus(message_id=message.id, user_id=rid, status="sent"))


@chat_bp.route("/groups", methods=["GET"])
@jwt_required()
def get_groups():
    user_id = int(get_jwt_identity())
    groups = (
        ChatGroup.query
        .filter(ChatGroup.id.in_(
            db.session.query(GroupMember.group_id).filter_by(user_id=user_id)
        ))
        .order_by(ChatGroup.last_activity.desc())
        .all()
    )
    return jsonify([g.to_dict(current_user_id=user_id) for g in groups]), 200


@chat_bp.route("/groups", methods=["POST"])
@jwt_required()
def create_group():
    user_id = int(get_jwt_identity())
    data = request.get_json(silent=True) or {}
    member_ids = [int(m) for m in (data.get("member_ids") or []) if int(m) != user_id]

    if not member_ids:
        return jsonify({"error": "Select at least one member."}), 400

    is_group = len(member_ids) > 1

    # For a 1-on-1, reuse an existing chat with the same pair.
    if not is_group:
        other = member_ids[0]
        mine = db.session.query(GroupMember.group_id).filter_by(user_id=user_id).subquery()
        theirs = db.session.query(GroupMember.group_id).filter_by(user_id=other).subquery()
        existing = (
            ChatGroup.query
            .filter(ChatGroup.is_group.is_(False))
            .filter(ChatGroup.id.in_(db.session.query(mine.c.group_id)))
            .filter(ChatGroup.id.in_(db.session.query(theirs.c.group_id)))
            .first()
        )
        if existing:
            return jsonify(existing.to_dict(current_user_id=user_id)), 200

    group = ChatGroup(
        name=(data.get("name") or "").strip() or None,
        is_group=is_group,
        created_by=user_id,
    )
    db.session.add(group)
    db.session.flush()
    db.session.add(GroupMember(group_id=group.id, user_id=user_id))
    for mid in member_ids:
        db.session.add(GroupMember(group_id=group.id, user_id=mid))
    db.session.commit()
    return jsonify(group.to_dict(current_user_id=user_id)), 201


@chat_bp.route("/groups/<int:group_id>", methods=["GET"])
@jwt_required()
def get_group(group_id):
    user_id = int(get_jwt_identity())
    if not GroupMember.query.filter_by(group_id=group_id, user_id=user_id).first():
        return jsonify({"error": "Not a member"}), 403
    group = ChatGroup.query.get(group_id)
    if not group:
        return jsonify({"error": "Group not found."}), 404
    return jsonify(group.to_dict(current_user_id=user_id)), 200


@chat_bp.route("/groups/<int:group_id>/messages", methods=["GET"])
@jwt_required()
def get_messages(group_id):
    user_id = int(get_jwt_identity())
    if not GroupMember.query.filter_by(group_id=group_id, user_id=user_id).first():
        return jsonify({"error": "Not a member"}), 403

    limit = min(int(request.args.get("limit", 30)), 100)
    cursor = request.args.get("cursor")

    query = Message.query.filter_by(group_id=group_id).order_by(Message.id.desc())
    if cursor:
        try:
            query = query.filter(Message.id < int(cursor))
        except (ValueError, TypeError):
            pass

    rows = query.limit(limit).all()
    has_more = len(rows) == limit
    next_cursor = rows[-1].id if rows else None
    messages = [m.to_dict() for m in reversed(rows)]  # chronological order
    return jsonify({"messages": messages, "next_cursor": next_cursor, "has_more": has_more}), 200


@chat_bp.route("/groups/<int:group_id>/messages", methods=["POST"])
@jwt_required()
def send_message(group_id):
    user_id = int(get_jwt_identity())
    if not GroupMember.query.filter_by(group_id=group_id, user_id=user_id).first():
        return jsonify({"error": "Not a member"}), 403

    is_multipart = bool(request.content_type and "multipart" in request.content_type)
    media_url = None
    media_type = "text"
    duration = None

    if is_multipart:
        media_type = (request.form.get("media_type") or "photo").lower()
        text = (request.form.get("text") or "").strip() or None
        duration_raw = request.form.get("duration")

        file = request.files.get("file")
        if not file or not file.filename:
            return jsonify({"error": "File is required."}), 400

        if media_type == "video":
            allowed = current_app.config["ALLOWED_VIDEO_EXTENSIONS"]
        elif media_type == "voice":
            allowed = current_app.config["ALLOWED_AUDIO_EXTENSIONS"]
        else:
            media_type = "photo"
            allowed = current_app.config["ALLOWED_PHOTO_EXTENSIONS"]

        relative = save_uploaded_file(file, "chat", allowed)
        if not relative:
            return jsonify({"error": "File type not allowed."}), 400
        media_url = build_media_url(relative)

        if duration_raw:
            try:
                duration = float(duration_raw)
            except (ValueError, TypeError):
                duration = None
        if duration is None:
            import os
            duration = probe_video_duration(
                os.path.join(current_app.config["UPLOAD_FOLDER"], relative)
            )

        if media_type == "video":
            if duration is not None and duration > current_app.config["VIDEO_MAX_SECONDS"]:
                return jsonify({
                    "error": f"Video too long. Max {int(current_app.config['VIDEO_MAX_SECONDS'])}s."
                }), 400
        elif media_type == "voice":
            if duration is not None and duration > VOICE_MAX_SECONDS:
                return jsonify({"error": f"Voice note too long. Max {VOICE_MAX_SECONDS}s."}), 400
    else:
        data = request.get_json(silent=True) or {}
        text = (data.get("text") or data.get("content") or "").strip() or None
        if not text:
            return jsonify({"error": "Message text is required."}), 400

    message = Message(
        group_id=group_id,
        sender_id=user_id,
        text=text,
        media_url=media_url,
        media_type=media_type,
        duration=duration,
    )
    db.session.add(message)
    db.session.flush()

    recipients = _member_ids(group_id, exclude=user_id)
    _create_statuses(message, recipients)

    group = ChatGroup.query.get(group_id)
    if group:
        group.last_activity = func.now()
    db.session.commit()

    payload = message.to_dict()
    emit_to_group_members(group_id, "new_message", payload, exclude_user_id=user_id)
    emit_to_user(user_id, "message_sent", payload)

    sender = User.query.get(user_id)
    if sender:
        notify_new_message(group_id, sender, message, recipients)
    return jsonify(payload), 201


@chat_bp.route("/groups/<int:group_id>/read-all", methods=["POST"])
@jwt_required()
def mark_all_read(group_id):
    user_id = int(get_jwt_identity())
    if not GroupMember.query.filter_by(group_id=group_id, user_id=user_id).first():
        return jsonify({"error": "Not a member"}), 403

    pending = (
        MessageStatus.query
        .join(Message, MessageStatus.message_id == Message.id)
        .filter(
            Message.group_id == group_id,
            Message.sender_id != user_id,
            MessageStatus.user_id == user_id,
            MessageStatus.status != "read",
        )
        .all()
    )
    for ms in pending:
        ms.status = "read"
    db.session.commit()
    return jsonify({"updated": len(pending)}), 200


@chat_bp.route("/messages/<int:message_id>/read", methods=["POST"])
@jwt_required()
def mark_message_read(message_id):
    user_id = int(get_jwt_identity())
    ms = MessageStatus.query.filter_by(message_id=message_id, user_id=user_id).first()
    if ms is None:
        ms = MessageStatus(message_id=message_id, user_id=user_id, status="read")
        db.session.add(ms)
    else:
        ms.status = "read"
    db.session.commit()
    return jsonify({"status": "read"}), 200


@chat_bp.route("/groups/<int:group_id>/members", methods=["GET"])
@jwt_required()
def get_members(group_id):
    rows = GroupMember.query.filter_by(group_id=group_id).all()
    result = []
    for m in rows:
        u = User.query.get(m.user_id)
        if u:
            result.append(u.to_dict())
    return jsonify(result), 200
