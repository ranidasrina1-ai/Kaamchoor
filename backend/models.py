"""Database models for Kaam Chor v2."""
from datetime import datetime

from flask_sqlalchemy import SQLAlchemy

db = SQLAlchemy()


def _now():
    return datetime.utcnow()


def _iso(dt):
    return dt.isoformat() + "Z" if dt else None


# ── USERS ────────────────────────────────────────────────────────────────────
class User(db.Model):
    __tablename__ = "users"

    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(50), unique=True, nullable=False, index=True)
    email = db.Column(db.String(120), unique=True, nullable=False, index=True)
    password_hash = db.Column(db.String(255), nullable=False)
    display_name = db.Column(db.String(100), nullable=True)
    bio = db.Column(db.Text, nullable=True)
    avatar_url = db.Column(db.String(500), nullable=True)
    created_at = db.Column(db.DateTime, default=_now)
    last_seen = db.Column(db.DateTime, default=_now, onupdate=_now)

    posts = db.relationship("Post", backref="author", lazy="dynamic")
    likes = db.relationship("Like", backref="user", lazy="dynamic")
    comments = db.relationship("Comment", backref="user", lazy="dynamic")
    messages = db.relationship("Message", backref="sender", lazy="dynamic")

    def to_dict(self, include_email=False):
        data = {
            "id": self.id,
            "username": self.username,
            "display_name": self.display_name or self.username,
            "bio": self.bio or "",
            "avatar_url": self.avatar_url or "",
            "created_at": _iso(self.created_at),
        }
        if include_email:
            data["email"] = self.email
        return data


# ── REFRESH TOKENS ───────────────────────────────────────────────────────────
class RefreshToken(db.Model):
    __tablename__ = "refresh_tokens"

    id = db.Column(db.Integer, primary_key=True)
    jti = db.Column(db.String(64), unique=True, nullable=False, index=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, index=True)
    revoked = db.Column(db.Boolean, default=False, nullable=False)
    created_at = db.Column(db.DateTime, default=_now)
    expires_at = db.Column(db.DateTime, nullable=True)


# ── POSTS ────────────────────────────────────────────────────────────────────
class Post(db.Model):
    __tablename__ = "posts"

    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, index=True)
    caption = db.Column(db.Text, nullable=True)
    photo_url = db.Column(db.String(500), nullable=False)
    created_at = db.Column(db.DateTime, default=_now, index=True)

    likes = db.relationship("Like", backref="post", lazy="dynamic", cascade="all, delete-orphan")
    comments = db.relationship("Comment", backref="post", lazy="dynamic", cascade="all, delete-orphan")

    def to_dict(self, viewer_liked=False, like_count=None, comment_count=None):
        return {
            "id": self.id,
            "user_id": self.user_id,
            "caption": self.caption or "",
            "photo_url": self.photo_url,
            "created_at": _iso(self.created_at),
            "author": self.author.to_dict() if self.author else None,
            "like_count": like_count if like_count is not None else self.likes.count(),
            "comment_count": comment_count if comment_count is not None else self.comments.count(),
            "liked_by_me": viewer_liked,
        }


# ── LIKES ────────────────────────────────────────────────────────────────────
class Like(db.Model):
    __tablename__ = "likes"

    id = db.Column(db.Integer, primary_key=True)
    post_id = db.Column(db.Integer, db.ForeignKey("posts.id"), nullable=False, index=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, index=True)
    created_at = db.Column(db.DateTime, default=_now)

    __table_args__ = (db.UniqueConstraint("post_id", "user_id", name="uq_post_user_like"),)


# ── COMMENTS ─────────────────────────────────────────────────────────────────
class Comment(db.Model):
    __tablename__ = "comments"

    id = db.Column(db.Integer, primary_key=True)
    post_id = db.Column(db.Integer, db.ForeignKey("posts.id"), nullable=False, index=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, index=True)
    text = db.Column(db.Text, nullable=False)
    created_at = db.Column(db.DateTime, default=_now)

    def to_dict(self):
        return {
            "id": self.id,
            "post_id": self.post_id,
            "user_id": self.user_id,
            "text": self.text,
            "created_at": _iso(self.created_at),
            "author": self.user.to_dict() if self.user else None,
        }


# ── CHAT GROUPS ──────────────────────────────────────────────────────────────
class ChatGroup(db.Model):
    __tablename__ = "chat_groups"

    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(100), nullable=True)  # None for 1-on-1
    is_group = db.Column(db.Boolean, default=False, nullable=False)
    created_by = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False)
    created_at = db.Column(db.DateTime, default=_now)
    last_activity = db.Column(db.DateTime, default=_now, index=True)

    members = db.relationship("GroupMember", backref="group", lazy="dynamic", cascade="all, delete-orphan")
    messages = db.relationship("Message", backref="group", lazy="dynamic", cascade="all, delete-orphan")

    def to_dict(self, current_user_id=None):
        members = [m.user.to_dict() for m in self.members.all() if m.user]
        last_msg = self.messages.order_by(Message.id.desc()).first()
        unread = 0
        if current_user_id:
            unread = (
                MessageStatus.query
                .join(Message, MessageStatus.message_id == Message.id)
                .filter(
                    Message.group_id == self.id,
                    Message.sender_id != current_user_id,
                    MessageStatus.user_id == current_user_id,
                    MessageStatus.status != "read",
                )
                .count()
            )
        return {
            "id": self.id,
            "name": self.name or "",
            "is_group": self.is_group,
            "members": members,
            "created_at": _iso(self.created_at),
            "last_message": last_msg.to_dict() if last_msg else None,
            "unread_count": unread,
        }


class GroupMember(db.Model):
    __tablename__ = "group_members"

    id = db.Column(db.Integer, primary_key=True)
    group_id = db.Column(db.Integer, db.ForeignKey("chat_groups.id"), nullable=False, index=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, index=True)
    joined_at = db.Column(db.DateTime, default=_now)

    user = db.relationship("User", backref="group_memberships")

    __table_args__ = (db.UniqueConstraint("group_id", "user_id", name="uq_group_user_member"),)


# ── MESSAGES ─────────────────────────────────────────────────────────────────
class Message(db.Model):
    __tablename__ = "messages"

    id = db.Column(db.Integer, primary_key=True)
    group_id = db.Column(db.Integer, db.ForeignKey("chat_groups.id"), nullable=False, index=True)
    sender_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, index=True)
    text = db.Column(db.Text, nullable=True)
    media_url = db.Column(db.String(500), nullable=True)
    media_type = db.Column(db.String(20), default="text")  # text | photo | video | voice
    duration = db.Column(db.Float, nullable=True)  # seconds (video / voice)
    created_at = db.Column(db.DateTime, default=_now, index=True)

    statuses = db.relationship("MessageStatus", backref="message", lazy="dynamic", cascade="all, delete-orphan")

    def to_dict(self):
        return {
            "id": self.id,
            "group_id": self.group_id,
            "sender_id": self.sender_id,
            "text": self.text or "",
            "media_url": self.media_url or "",
            "media_type": self.media_type,
            "duration": self.duration,
            "created_at": _iso(self.created_at),
            "sender": self.sender.to_dict() if self.sender else None,
        }


class MessageStatus(db.Model):
    """Per-recipient delivery/read status for messages (group + 1-on-1)."""
    __tablename__ = "message_status"

    id = db.Column(db.Integer, primary_key=True)
    message_id = db.Column(db.Integer, db.ForeignKey("messages.id"), nullable=False, index=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, index=True)
    status = db.Column(db.String(20), default="sent")  # sent | delivered | read
    updated_at = db.Column(db.DateTime, default=_now, onupdate=_now)

    __table_args__ = (db.UniqueConstraint("message_id", "user_id", name="uq_message_user_status"),)


# ── CALL RECORDS ─────────────────────────────────────────────────────────────
class CallRecord(db.Model):
    __tablename__ = "call_records"

    id = db.Column(db.Integer, primary_key=True)
    group_id = db.Column(db.Integer, db.ForeignKey("chat_groups.id"), nullable=True, index=True)
    caller_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, index=True)
    call_type = db.Column(db.String(10), default="audio")  # audio | video
    is_group = db.Column(db.Boolean, default=False, nullable=False)
    status = db.Column(db.String(20), default="initiated")  # initiated | answered | ended | missed | rejected
    started_at = db.Column(db.DateTime, default=_now)
    answered_at = db.Column(db.DateTime, nullable=True)
    ended_at = db.Column(db.DateTime, nullable=True)

    def to_dict(self):
        return {
            "id": self.id,
            "group_id": self.group_id,
            "caller_id": self.caller_id,
            "call_type": self.call_type,
            "is_group": self.is_group,
            "status": self.status,
            "started_at": _iso(self.started_at),
            "answered_at": _iso(self.answered_at),
            "ended_at": _iso(self.ended_at),
        }
