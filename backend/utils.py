"""Utility helpers for Kaam Chor v2."""
import os
import uuid
import subprocess
from datetime import datetime

from flask import current_app
from flask_jwt_extended import get_jwt_identity, get_jwt

from models import User


def allowed_file(filename, allowed_set):
    if not filename or "." not in filename:
        return False
    ext = filename.rsplit(".", 1)[1].lower()
    return ext in allowed_set


def _extension(filename):
    return filename.rsplit(".", 1)[1].lower()


def save_uploaded_file(file_obj, subfolder, allowed_set, max_bytes=None):
    """Save an uploaded file. Returns relative path or None if invalid/too big."""
    if file_obj is None:
        return None
    original = getattr(file_obj, "filename", "") or ""
    if not allowed_file(original, allowed_set):
        return None

    ext = _extension(original)
    unique_name = f"{uuid.uuid4().hex}.{ext}"

    base = current_app.config["UPLOAD_FOLDER"]
    dest_dir = os.path.join(base, subfolder)
    os.makedirs(dest_dir, exist_ok=True)
    dest_path = os.path.join(dest_dir, unique_name)

    file_obj.save(dest_path)

    if max_bytes is not None:
        try:
            if os.path.getsize(dest_path) > max_bytes:
                os.remove(dest_path)
                return None
        except OSError:
            pass

    return f"{subfolder}/{unique_name}"


def build_media_url(relative_path):
    if not relative_path:
        return ""
    if relative_path.startswith("http://") or relative_path.startswith("https://"):
        return relative_path
    base = current_app.config["SERVER_BASE_URL"]
    return f"{base}/uploads/{relative_path}"


def probe_video_duration(path):
    """Return duration in seconds using ffprobe if available, else None."""
    try:
        out = subprocess.run(
            [
                "ffprobe", "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", path,
            ],
            capture_output=True, text=True, timeout=15,
        )
        if out.returncode == 0 and out.stdout.strip():
            return float(out.stdout.strip())
    except (OSError, ValueError, subprocess.SubprocessError):
        pass
    return None


def get_current_user():
    user_id = get_jwt_identity()
    if user_id is None:
        return None
    return db_get_user(int(user_id))


def db_get_user(user_id):
    return User.query.get(user_id)


def current_jti():
    try:
        return get_jwt().get("jti")
    except Exception:
        return None


def utcnow():
    return datetime.utcnow()
