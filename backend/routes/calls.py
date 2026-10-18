"""Call record routes."""
from datetime import datetime

from flask import Blueprint, request, jsonify
from flask_jwt_extended import jwt_required

from models import db, CallRecord, ChatGroup, GroupMember
from utils import get_current_user

calls_bp = Blueprint("calls", __name__)


@calls_bp.route("/initiate", methods=["POST"])
@jwt_required()
def initiate_call():
    user = get_current_user()
    if not user:
        return jsonify({"error": "User not found."}), 404

    data = request.get_json(silent=True) or {}
    group_id = data.get("group_id")
    call_type = data.get("call_type", "audio")
    if call_type not in ("audio", "video"):
        call_type = "audio"

    is_group = False
    if group_id:
        group = ChatGroup.query.get(group_id)
        if not group:
            return jsonify({"error": "Group not found."}), 404
        if not GroupMember.query.filter_by(group_id=group_id, user_id=user.id).first():
            return jsonify({"error": "Not a member of this group."}), 403
        is_group = bool(group.is_group)

    call = CallRecord(
        group_id=group_id,
        caller_id=user.id,
        call_type=call_type,
        is_group=is_group,
        status="initiated",
    )
    db.session.add(call)
    db.session.commit()
    return jsonify(call.to_dict()), 201


@calls_bp.route("/<int:call_id>/answer", methods=["POST"])
@jwt_required()
def answer_call(call_id):
    call = CallRecord.query.get(call_id)
    if not call:
        return jsonify({"error": "Call not found."}), 404
    call.status = "answered"
    call.answered_at = datetime.utcnow()
    db.session.commit()
    return jsonify(call.to_dict()), 200


@calls_bp.route("/<int:call_id>/end", methods=["POST"])
@jwt_required()
def end_call(call_id):
    call = CallRecord.query.get(call_id)
    if not call:
        return jsonify({"error": "Call not found."}), 404
    if call.status == "initiated":
        call.status = "missed"
    else:
        call.status = "ended"
    call.ended_at = datetime.utcnow()
    db.session.commit()
    return jsonify(call.to_dict()), 200


@calls_bp.route("/<int:call_id>/reject", methods=["POST"])
@jwt_required()
def reject_call(call_id):
    call = CallRecord.query.get(call_id)
    if not call:
        return jsonify({"error": "Call not found."}), 404
    call.status = "rejected"
    call.ended_at = datetime.utcnow()
    db.session.commit()
    return jsonify(call.to_dict()), 200


@calls_bp.route("/history", methods=["GET"])
@jwt_required()
def call_history():
    user = get_current_user()
    if not user:
        return jsonify({"error": "User not found."}), 404
    calls = (
        CallRecord.query.filter_by(caller_id=user.id)
        .order_by(CallRecord.id.desc())
        .limit(50)
        .all()
    )
    return jsonify([c.to_dict() for c in calls]), 200
