"""Realtime Socket.IO test: chat, typing, WebRTC signaling, notifications.

Requires a running server (see CI workflow / README).

Usage:
    BASE=http://127.0.0.1:5000 python tests/test_realtime.py
"""
import os
import sys
import time

import requests
import socketio

BASE = os.environ.get("BASE", "http://127.0.0.1:5000")

_results = []


def check(name, ok, detail=""):
    _results.append(ok)
    print(f"[{'PASS' if ok else 'FAIL'}] {name} {detail}")


def signup(u):
    r = requests.post(f"{BASE}/api/auth/signup", json={"username": u, "email": f"{u}@x.com", "password": "secret1"})
    r.raise_for_status()
    return r.json()


def login(u):
    r = requests.post(f"{BASE}/api/auth/login", json={"identifier": u, "password": "secret1"})
    r.raise_for_status()
    return r.json()


def login_or_signup(u):
    r = requests.post(f"{BASE}/api/auth/login", json={"identifier": u, "password": "secret1"})
    if r.status_code == 200:
        return r.json()
    return signup(u)


def main():
    a = login_or_signup("rt_alice")
    b = login_or_signup("rt_bob")
    tok_a, id_a = a["token"], a["user"]["id"]
    tok_b, id_b = b["token"], b["user"]["id"]
    ha = {"Authorization": f"Bearer {tok_a}"}

    group_id = requests.post(f"{BASE}/api/chat/groups", headers=ha, json={"member_ids": [id_b]}).json()["id"]

    received = {}
    ca, cb = socketio.Client(), socketio.Client()

    @cb.on("new_message")
    def _new(data):
        received["new_message"] = data

    @ca.on("message_sent")
    def _sent(data):
        received["message_sent"] = data

    @cb.on("user_typing")
    def _typing(data):
        received["typing"] = data

    @cb.on("incoming_call")
    def _incoming(data):
        received["incoming_call"] = data

    @ca.on("call_answered")
    def _answered(data):
        received["call_answered"] = data

    @ca.on("notification")
    def _notif_a(data):
        received.setdefault("notifications_a", []).append(data)

    ca.connect(f"{BASE}?token={tok_a}", transports=["polling"])
    cb.connect(f"{BASE}?token={tok_b}", transports=["polling"])
    time.sleep(1.0)
    check("both connected", ca.connected and cb.connected)

    ca.emit("send_message", {"group_id": group_id, "text": "hi bob"})
    time.sleep(1.2)
    check("B got new_message", received.get("new_message", {}).get("text") == "hi bob")
    check("A got message_sent", "message_sent" in received)

    ca.emit("typing", {"group_id": group_id, "is_typing": True})
    time.sleep(1.0)
    check("B got user_typing", received.get("typing", {}).get("is_typing") is True)

    call = requests.post(f"{BASE}/api/calls/initiate", headers=ha, json={"group_id": group_id, "call_type": "audio"}).json()
    ca.emit("call_offer", {"to_user_id": id_b, "call_id": call["id"], "call_type": "audio",
                           "offer": {"type": "offer", "sdp": "v=0 fake"}})
    time.sleep(1.2)
    check("B got incoming_call", received.get("incoming_call", {}).get("from_user_id") == id_a)

    cb.emit("call_answer", {"to_user_id": id_a, "call_id": call["id"],
                            "answer": {"type": "answer", "sdp": "v=0 fake-answer"}})
    time.sleep(1.2)
    check("A got call_answered", received.get("call_answered", {}).get("from_user_id") == id_b)

    png = bytes.fromhex(
        "89504e470d0a1a0a0000000d494844520000000100000001080600000"
        "01f15c4890000000d49444154789c6360000002000100ffff030000060005"
        "57bfabd40000000049454e44ae426082"
    )
    p = requests.post(f"{BASE}/api/posts", headers=ha,
                      files={"photo": ("x.png", png, "image/png")},
                      data={"caption": "rt post"}).json()
    requests.post(f"{BASE}/api/posts/{p['id']}/like", headers={"Authorization": f"Bearer {tok_b}"})
    time.sleep(1.2)
    notifs = received.get("notifications_a", [])
    check("A got like notification", any(n.get("type") == "like" for n in notifs), [n.get("type") for n in notifs])

    ca.disconnect()
    cb.disconnect()

    passed = sum(1 for ok in _results if ok)
    print(f"\n=== {passed}/{len(_results)} realtime checks passed ===")
    return 0 if passed == len(_results) else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except requests.exceptions.RequestException as exc:
        print(f"Could not reach server at {BASE}: {exc}")
        sys.exit(1)
