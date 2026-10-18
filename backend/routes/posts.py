"""Post routes: create, feed (cursor), single, user posts."""
from flask import Blueprint, request, jsonify, current_app
from flask_jwt_extended import jwt_required
from sqlalchemy import func
from sqlalchemy.orm import joinedload

from models import db, Post, Like, Comment
from utils import get_current_user, save_uploaded_file, build_media_url

posts_bp = Blueprint("posts", __name__)


def _serialize(posts, viewer_id):
    """Serialize a batch of posts without N+1 like/comment queries."""
    if not posts:
        return []
    ids = [p.id for p in posts]

    like_rows = (
        db.session.query(Like.post_id, func.count(Like.id))
        .filter(Like.post_id.in_(ids))
        .group_by(Like.post_id)
        .all()
    )
    like_map = {pid: count for pid, count in like_rows}

    comment_rows = (
        db.session.query(Comment.post_id, func.count(Comment.id))
        .filter(Comment.post_id.in_(ids))
        .group_by(Comment.post_id)
        .all()
    )
    comment_map = {pid: count for pid, count in comment_rows}

    liked_ids = set()
    if viewer_id:
        liked = Like.query.filter(Like.post_id.in_(ids), Like.user_id == viewer_id).all()
        liked_ids = {l.post_id for l in liked}

    return [
        p.to_dict(
            viewer_liked=p.id in liked_ids,
            like_count=like_map.get(p.id, 0),
            comment_count=comment_map.get(p.id, 0),
        )
        for p in posts
    ]


@posts_bp.route("", methods=["POST"])
@jwt_required()
def create_post():
    user = get_current_user()
    if not user:
        return jsonify({"error": "User not found."}), 404
    if "photo" not in request.files:
        return jsonify({"error": "Photo is required."}), 400

    file = request.files["photo"]
    if not file or not file.filename:
        return jsonify({"error": "Empty photo file."}), 400

    caption = (request.form.get("caption") or "").strip()
    relative = save_uploaded_file(file, "posts", current_app.config["ALLOWED_PHOTO_EXTENSIONS"])
    if not relative:
        return jsonify({"error": "Invalid photo. Use PNG, JPG, JPEG, WEBP or GIF."}), 400

    post = Post(user_id=user.id, caption=caption, photo_url=build_media_url(relative))
    db.session.add(post)
    db.session.commit()
    return jsonify(post.to_dict(viewer_liked=False, like_count=0, comment_count=0)), 201


@posts_bp.route("", methods=["GET"])
@jwt_required()
def get_feed():
    user = get_current_user()
    if not user:
        return jsonify({"error": "User not found."}), 404

    limit = min(int(request.args.get("limit", 20)), 50)
    cursor = request.args.get("cursor")

    query = Post.query.options(joinedload(Post.author)).order_by(Post.id.desc())
    if cursor:
        try:
            query = query.filter(Post.id < int(cursor))
        except (ValueError, TypeError):
            pass

    posts = query.limit(limit).all()
    result = _serialize(posts, user.id)
    has_more = len(result) == limit
    next_cursor = result[-1]["id"] if result and has_more else None
    return jsonify({"posts": result, "next_cursor": next_cursor, "has_more": has_more}), 200


@posts_bp.route("/<int:post_id>", methods=["GET"])
@jwt_required()
def get_post(post_id):
    user = get_current_user()
    post = Post.query.options(joinedload(Post.author)).get(post_id)
    if not post:
        return jsonify({"error": "Post not found."}), 404
    return jsonify(_serialize([post], user.id if user else None)[0]), 200


@posts_bp.route("/user/<int:user_id>", methods=["GET"])
@jwt_required()
def get_user_posts(user_id):
    viewer = get_current_user()
    limit = min(int(request.args.get("limit", 30)), 50)
    cursor = request.args.get("cursor")

    query = (
        Post.query.options(joinedload(Post.author))
        .filter_by(user_id=user_id)
        .order_by(Post.id.desc())
    )
    if cursor:
        try:
            query = query.filter(Post.id < int(cursor))
        except (ValueError, TypeError):
            pass

    posts = query.limit(limit).all()
    result = _serialize(posts, viewer.id if viewer else None)
    has_more = len(result) == limit
    next_cursor = result[-1]["id"] if result and has_more else None
    return jsonify({"posts": result, "next_cursor": next_cursor, "has_more": has_more}), 200
