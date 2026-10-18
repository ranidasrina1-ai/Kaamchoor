"""Generic media upload route."""
from flask import Blueprint, request, jsonify, current_app
from flask_jwt_extended import jwt_required

from utils import get_current_user, save_uploaded_file, build_media_url

media_bp = Blueprint("media", __name__)


@media_bp.route("/upload", methods=["POST"])
@jwt_required()
def upload_file():
    user = get_current_user()
    if not user:
        return jsonify({"error": "User not found."}), 404
    if "file" not in request.files:
        return jsonify({"error": "No file provided."}), 400

    file = request.files["file"]
    if not file or not file.filename:
        return jsonify({"error": "Empty file."}), 400

    allowed = (
        current_app.config["ALLOWED_PHOTO_EXTENSIONS"]
        | current_app.config["ALLOWED_VIDEO_EXTENSIONS"]
        | current_app.config["ALLOWED_AUDIO_EXTENSIONS"]
    )
    relative = save_uploaded_file(file, "misc", allowed)
    if not relative:
        return jsonify({"error": "File type not allowed."}), 400
    return jsonify({"url": build_media_url(relative)}), 200
