"""Like + comment routes."""
from flask import Blueprint, request, jsonify
from flask_jwt_extended import jwt_required

from models import db, Post, Like, Comment
from utils import get_current_user
from notifications import notify_like, notify_comment

likes_comments_bp = Blueprint("likes_comments", __name__)


@likes_comments_bp.route("/posts/<int:post_id>/like", methods=["POST"])
@jwt_required()
def toggle_like(post_id):
    user = get_current_user()
    if not user:
        return jsonify({"error": "User not found."}), 404

    post = Post.query.get(post_id)
    if not post:
        return jsonify({"error": "Post not found."}), 404

    existing = Like.query.filter_by(post_id=post_id, user_id=user.id).first()
    if existing:
        db.session.delete(existing)
        db.session.commit()
        return jsonify({"liked": False, "like_count": post.likes.count()}), 200

    db.session.add(Like(post_id=post_id, user_id=user.id))
    db.session.commit()
    if post.user_id != user.id:
        notify_like(post, user)
    return jsonify({"liked": True, "like_count": post.likes.count()}), 200


@likes_comments_bp.route("/posts/<int:post_id>/comments", methods=["GET"])
@jwt_required()
def get_comments(post_id):
    if not Post.query.get(post_id):
        return jsonify({"error": "Post not found."}), 404
    comments = (
        Comment.query.filter_by(post_id=post_id)
        .order_by(Comment.id.asc())
        .all()
    )
    return jsonify([c.to_dict() for c in comments]), 200


@likes_comments_bp.route("/posts/<int:post_id>/comments", methods=["POST"])
@jwt_required()
def add_comment(post_id):
    user = get_current_user()
    if not user:
        return jsonify({"error": "User not found."}), 404

    post = Post.query.get(post_id)
    if not post:
        return jsonify({"error": "Post not found."}), 404

    data = request.get_json(silent=True) or {}
    text = (data.get("text") or "").strip()
    if not text:
        return jsonify({"error": "Comment text cannot be empty."}), 400
    if len(text) > 500:
        return jsonify({"error": "Comment too long (max 500 chars)."}), 400

    comment = Comment(post_id=post_id, user_id=user.id, text=text)
    db.session.add(comment)
    db.session.commit()
    if post.user_id != user.id:
        notify_comment(post, user, text)
    return jsonify(comment.to_dict()), 201
