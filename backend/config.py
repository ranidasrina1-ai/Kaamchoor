"""Application configuration for Kaam Chor v2."""
import os

BASE_DIR = os.path.abspath(os.path.dirname(__file__))


def _bool(name, default=False):
    return os.environ.get(name, str(default)).lower() in ("1", "true", "yes", "on")


class Config:
    SECRET_KEY = os.environ.get("FLASK_SECRET_KEY", "dev-secret-change-me")

    # ── Database (MariaDB) ───────────────────────────────────────────────
    SQLALCHEMY_DATABASE_URI = os.environ.get(
        "DATABASE_URL",
        "mysql+pymysql://kaamchor:kaamchor@localhost:3306/kaamchor",
    )
    SQLALCHEMY_TRACK_MODIFICATIONS = False
    SQLALCHEMY_ENGINE_OPTIONS = {
        "pool_pre_ping": True,
        "pool_recycle": 280,
    }

    # ── JWT ──────────────────────────────────────────────────────────────
    JWT_SECRET_KEY = os.environ.get("JWT_SECRET_KEY", "jwt-dev-secret-change-me")
    JWT_ACCESS_TOKEN_EXPIRES = int(os.environ.get("JWT_ACCESS_MINUTES", "30")) * 60
    JWT_REFRESH_TOKEN_EXPIRES = int(os.environ.get("JWT_REFRESH_DAYS", "30")) * 86400
    JWT_TOKEN_LOCATION = ["headers"]

    # ── Uploads ──────────────────────────────────────────────────────────
    UPLOAD_FOLDER = os.environ.get("UPLOAD_FOLDER", os.path.join(BASE_DIR, "uploads"))
    MAX_CONTENT_LENGTH = int(os.environ.get("MAX_CONTENT_LENGTH", 52_428_800))  # 50 MB
    ALLOWED_PHOTO_EXTENSIONS = {"png", "jpg", "jpeg", "webp", "gif"}
    ALLOWED_VIDEO_EXTENSIONS = {"mp4", "webm", "3gp", "mov", "mkv"}
    ALLOWED_AUDIO_EXTENSIONS = {"m4a", "aac", "mp3", "ogg", "opus", "wav"}
    ALLOWED_AVATAR_EXTENSIONS = {"png", "jpg", "jpeg", "webp"}
    VIDEO_MAX_SECONDS = float(os.environ.get("VIDEO_MAX_SECONDS", "15"))
    AVATAR_MAX_BYTES = int(os.environ.get("AVATAR_MAX_BYTES", str(5 * 1024 * 1024)))

    # ── Notifications ────────────────────────────────────────────────────
    NTFY_SERVER_URL = os.environ.get("NTFY_SERVER_URL", "http://localhost:8090")
    NTFY_ENABLED = _bool("NTFY_ENABLED", False)

    # ── Server URL ───────────────────────────────────────────────────────
    SERVER_BASE_URL = os.environ.get("SERVER_BASE_URL", "http://localhost:5000").rstrip("/")

    # ── Socket.IO ────────────────────────────────────────────────────────
    CORS_ORIGINS = os.environ.get("CORS_ORIGINS", "*")

    # ── WebRTC ICE (STUN now, coturn/TURN placeholder for cross-network) ──
    ICE_SERVERS = [
        {"urls": "stun:stun.l.google.com:19302"},
        {"urls": "stun:stun1.l.google.com:19302"},
        # Uncomment & configure when self-hosting coturn for 4G<->WiFi calls:
        # {
        #     "urls": "turn:YOUR_PUBLIC_IP:3478?transport=udp",
        #     "username": "kaamchor",
        #     "credential": "change-me",
        # },
    ]
