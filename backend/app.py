"""Kaam Chor v2 — Flask app factory."""
import os

from dotenv import load_dotenv

load_dotenv()

from flask import Flask, jsonify, send_from_directory
from flask_socketio import SocketIO
from flask_cors import CORS
from flask_jwt_extended import JWTManager, jwt_required

from config import Config
from models import db, RefreshToken
from notifications import notify_user

socketio = SocketIO()


def create_app():
    app = Flask(__name__)
    app.config.from_object(Config)

    jwt = JWTManager(app)
    CORS(app, resources={r"/*": {"origins": app.config["CORS_ORIGINS"]}})

    db.init_app(app)
    socketio.init_app(
        app,
        async_mode="threading",
        cors_allowed_origins=app.config["CORS_ORIGINS"],
    )

    # ── Blueprints (v1 bug fixed: ALL blueprints registered) ─────────────
    from routes.auth import auth_bp
    from routes.posts import posts_bp
    from routes.likes_comments import likes_comments_bp
    from routes.profile import profile_bp
    from routes.chat import chat_bp
    from routes.media import media_bp
    from routes.calls import calls_bp

    app.register_blueprint(auth_bp, url_prefix="/api/auth")
    app.register_blueprint(posts_bp, url_prefix="/api/posts")
    app.register_blueprint(likes_comments_bp, url_prefix="/api")
    app.register_blueprint(profile_bp, url_prefix="/api/profile")
    app.register_blueprint(chat_bp, url_prefix="/api/chat")
    app.register_blueprint(media_bp, url_prefix="/api/media")
    app.register_blueprint(calls_bp, url_prefix="/api/calls")

    # ── Socket.IO handlers (v1 bug fixed: this call was missing) ─────────
    import sockets
    sockets.register_socket_handlers(socketio)

    # ── Misc endpoints ───────────────────────────────────────────────────
    @app.route("/api/health")
    def health():
        return {"status": "ok", "service": "Kaam Chor v2"}

    @app.route("/api/ice-servers")
    @jwt_required()
    def ice_servers():
        return jsonify({"iceServers": app.config["ICE_SERVERS"]}), 200

    @app.route("/api/notifications/test", methods=["POST"])
    @jwt_required()
    def test_notification():
        from flask_jwt_extended import get_jwt_identity
        uid = int(get_jwt_identity())
        notify_user(uid, "Test notification", "Notifications are working.", "test")
        return {"success": True}, 200

    @app.route("/uploads/<path:filename>")
    def uploaded_file(filename):
        return send_from_directory(app.config["UPLOAD_FOLDER"], filename)

    @app.route("/")
    def index():
        return jsonify({"status": "ok", "message": "Kaam Chor v2 API running"})

    return app


app = create_app()


if __name__ == "__main__":
    with app.app_context():
        os.makedirs(app.config["UPLOAD_FOLDER"], exist_ok=True)
        db.create_all()
    print("Starting Kaam Chor v2 server on 0.0.0.0:5000")
    socketio.run(app, host="0.0.0.0", port=5000, allow_unsafe_werkzeug=True)
