"""Auth routes: signup, login, refresh, logout, me, users."""
import re
from datetime import datetime, timedelta

from flask import Blueprint, request, jsonify, current_app
from flask_jwt_extended import (
    create_access_token,
    create_refresh_token,
    decode_token,
    jwt_required,
    get_jwt_identity,
    get_jwt,
)
from werkzeug.security import generate_password_hash, check_password_hash

from models import db, User, RefreshToken
from utils import get_current_user

auth_bp = Blueprint("auth", __name__)

USERNAME_RE = re.compile(r"^[a-zA-Z0-9_]{3,30}$")
EMAIL_RE = re.compile(r"^[^\s@]+@[^\s@]+\.[^\s@]+$")


def _issue_tokens(user):
    access = create_access_token(identity=str(user.id))
    refresh = create_refresh_token(identity=str(user.id))
    jti = decode_token(refresh)["jti"]
    expires = datetime.utcnow() + timedelta(seconds=current_app.config["JWT_REFRESH_TOKEN_EXPIRES"])
    db.session.add(RefreshToken(jti=jti, user_id=user.id, expires_at=expires))
    db.session.commit()
    return {"token": access, "refresh_token": refresh, "user": user.to_dict(include_email=True)}


@auth_bp.route("/signup", methods=["POST"])
def signup():
    data = request.get_json(silent=True) or {}
    username = (data.get("username") or "").strip()
    email = (data.get("email") or "").strip().lower()
    password = data.get("password") or ""
    display_name = (data.get("display_name") or "").strip()

    if not USERNAME_RE.match(username):
        return jsonify({"error": "Username must be 3-30 chars: letters, numbers, underscore only."}), 400
    if not EMAIL_RE.match(email):
        return jsonify({"error": "Invalid email address."}), 400
    if len(password) < 6:
        return jsonify({"error": "Password must be at least 6 characters."}), 400
    if User.query.filter_by(username=username).first():
        return jsonify({"error": "Username already taken."}), 409
    if User.query.filter_by(email=email).first():
        return jsonify({"error": "Email already registered."}), 409

    user = User(
        username=username,
        email=email,
        password_hash=generate_password_hash(password),
        display_name=display_name or username,
        bio="",
    )
    db.session.add(user)
    db.session.commit()
    return jsonify(_issue_tokens(user)), 201


@auth_bp.route("/login", methods=["POST"])
def login():
    data = request.get_json(silent=True) or {}
    identifier = (data.get("identifier") or data.get("username") or data.get("email") or "").strip()
    password = data.get("password") or ""

    if not identifier or not password:
        return jsonify({"error": "Username/email and password are required."}), 400

    if "@" in identifier:
        user = User.query.filter_by(email=identifier.lower()).first()
    else:
        user = User.query.filter_by(username=identifier).first()

    if not user or not check_password_hash(user.password_hash, password):
        return jsonify({"error": "Invalid credentials."}), 401
    return jsonify(_issue_tokens(user)), 200


@auth_bp.route("/refresh", methods=["POST"])
@jwt_required(refresh=True)
def refresh():
    identity = get_jwt_identity()
    jti = get_jwt()["jti"]
    stored = RefreshToken.query.filter_by(jti=jti).first()
    if stored is None or stored.revoked:
        return jsonify({"error": "Refresh token is no longer valid."}), 401

    # rotate: revoke old, issue new
    stored.revoked = True
    db.session.commit()

    user = User.query.get(int(identity))
    if not user:
        return jsonify({"error": "User not found."}), 404
    return jsonify(_issue_tokens(user)), 200


@auth_bp.route("/logout", methods=["POST"])
@jwt_required(refresh=True)
def logout():
    jti = get_jwt()["jti"]
    stored = RefreshToken.query.filter_by(jti=jti).first()
    if stored:
        stored.revoked = True
        db.session.commit()
    return jsonify({"message": "Logged out."}), 200


@auth_bp.route("/me", methods=["GET"])
@jwt_required()
def get_me():
    user = get_current_user()
    if not user:
        return jsonify({"error": "User not found."}), 404
    return jsonify(user.to_dict(include_email=True)), 200


@auth_bp.route("/users", methods=["GET"])
@jwt_required()
def list_users():
    users = User.query.order_by(User.username).all()
    return jsonify([u.to_dict() for u in users]), 200
