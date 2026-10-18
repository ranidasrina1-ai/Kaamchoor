"""Profile routes: view + edit."""
from flask import Blueprint, request, jsonify, current_app
from flask_jwt_extended import jwt_required

from models import db, User
from utils import get_current_user, save_uploaded_file, build_media_url

profile_bp = Blueprint("profile", __name__)


@profile_bp.route("/<int:user_id>", methods=["GET"])
@jwt_required()
def get_profile(user_id):
    user = User.query.get(user_id)
    if not user:
        return jsonify({"error": "User not found."}), 404
    return jsonify(user.to_dict()), 200


@profile_bp.route("/me", methods=["GET"])
@jwt_required()
def get_my_profile():
    user = get_current_user()
    if not user:
        return jsonify({"error": "User not found."}), 404
    return jsonify(user.to_dict(include_email=True)), 200


@profile_bp.route("/edit", methods=["PUT"])
@jwt_required()
def edit_profile():
    user = get_current_user()
    if not user:
        return jsonify({"error": "User not found."}), 404

    if request.content_type and "multipart" in request.content_type:
        display_name = (request.form.get("display_name") or "").strip()
        bio = request.form.get("bio")
        if "avatar" in request.files:
            file = request.files["avatar"]
            if file and file.filename:
                relative = save_uploaded_file(
                    file, "avatars", current_app.config["ALLOWED_AVATAR_EXTENSIONS"],
                    max_bytes=current_app.config["AVATAR_MAX_BYTES"],
                )
                if not relative:
                    return jsonify({"error": "Invalid avatar (PNG/JPG/WEBP, max 5MB)."}), 400
                user.avatar_url = build_media_url(relative)
    else:
        data = request.get_json(silent=True) or {}
        display_name = (data.get("display_name") or "").strip()
        bio = data.get("bio")

    if display_name:
        user.display_name = display_name
    if bio is not None:
        user.bio = bio

    db.session.commit()
    return jsonify(user.to_dict(include_email=True)), 200
