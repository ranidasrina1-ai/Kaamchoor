"""Socket-based notifications for Kaam Chor v2.

Notifications are delivered to a user's connected Socket.IO clients as a
`notification` event. The Android foreground service listens for these and
posts a system notification. No third-party push provider is used.
"""
import time
import threading


def _notif(user_id, title, message, notif_type, extra=None):
    payload = {
        "id": f"{user_id}_{int(time.time() * 1000)}_{threading.get_ident()}",
        "title": title,
        "message": message,
        "type": notif_type,           # message | like | comment | call
        "timestamp": time.time(),
    }
    if extra:
        payload.update(extra)
    return payload


def notify_user(user_id, title, message, notif_type="message", extra=None):
    """Emit a notification to all of a user's live socket connections."""
    payload = _notif(user_id, title, message, notif_type, extra)
    try:
        # Local import to avoid a circular dependency at module load time.
        from sockets import emit_to_user
        emit_to_user(int(user_id), "notification", payload)
    except Exception as exc:  # pragma: no cover - best effort
        print(f"[NOTIF] emit failed for user {user_id}: {exc}")
    return payload


def notify_new_message(group_id, sender, message, recipient_ids):
    preview = message.text if message.media_type == "text" else f"Sent a {message.media_type}"
    for rid in recipient_ids:
        notify_user(
            rid,
            title=sender.display_name or sender.username,
            message=preview,
            notif_type="message",
            extra={"group_id": group_id, "sender_id": sender.id},
        )


def notify_like(post, actor):
    notify_user(
        post.user_id,
        title="New like",
        message=f"{actor.display_name or actor.username} liked your post",
        notif_type="like",
        extra={"post_id": post.id},
    )


def notify_comment(post, actor, text):
    notify_user(
        post.user_id,
        title="New comment",
        message=f"{actor.display_name or actor.username}: {text[:60]}",
        notif_type="comment",
        extra={"post_id": post.id},
    )


def notify_call(user_id, caller, call_type, call_id, group_id=None, is_group=False):
    notify_user(
        user_id,
        title="Incoming call",
        message=f"{caller.display_name or caller.username} is calling",
        notif_type="call",
        extra={
            "call_id": call_id,
            "call_type": call_type,
            "group_id": group_id,
            "is_group": is_group,
            "caller_id": caller.id,
        },
    )
