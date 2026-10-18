"""HTTP integration test for the Kaam Chor v2 API.

Runs against a real database (MariaDB in CI, or any SQLAlchemy URL).
Destructive: drops and recreates all tables.

Usage:
    DATABASE_URL=mysql+pymysql://kaamchor:kaamchor123@127.0.0.1:3306/kaamchor \
        python tests/test_integration.py
"""
import io
import os
import sys

os.environ.setdefault("JWT_SECRET_KEY", "ci-test-secret-key-please-change-1234567890")
os.environ.setdefault("SERVER_BASE_URL", "http://127.0.0.1:5000")

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from app import create_app  # noqa: E402
from models import db  # noqa: E402

PNG = bytes.fromhex(
    "89504e470d0a1a0a0000000d494844520000000100000001080600000"
    "01f15c4890000000d49444154789c6360000002000100ffff030000060005"
    "57bfabd40000000049454e44ae426082"
)

_results = []


def check(name, ok, detail=""):
    _results.append(ok)
    print(f"[{'PASS' if ok else 'FAIL'}] {name} {detail}")


def main():
    app = create_app()
    with app.app_context():
        db.drop_all()
        db.create_all()

    c = app.test_client()

    r = c.post("/api/auth/signup", json={"username": "alice", "email": "a@x.com", "password": "secret1", "display_name": "Alice"})
    check("signup A", r.status_code == 201, r.status_code)
    tok_a, ref_a, id_a = r.get_json()["token"], r.get_json()["refresh_token"], r.get_json()["user"]["id"]

    r = c.post("/api/auth/signup", json={"username": "bob", "email": "b@x.com", "password": "secret1"})
    check("signup B", r.status_code == 201, r.status_code)
    tok_b, id_b = r.get_json()["token"], r.get_json()["user"]["id"]

    ha = {"Authorization": f"Bearer {tok_a}"}
    hb = {"Authorization": f"Bearer {tok_b}"}

    r = c.post("/api/auth/refresh", headers={"Authorization": f"Bearer {ref_a}"})
    check("refresh rotates", r.status_code == 200 and r.get_json()["token"], r.status_code)
    r = c.post("/api/auth/refresh", headers={"Authorization": f"Bearer {ref_a}"})
    check("old refresh rejected", r.status_code == 401, r.status_code)

    check("login", c.post("/api/auth/login", json={"identifier": "alice", "password": "secret1"}).status_code == 200)
    check("me", c.get("/api/auth/me", headers=ha).get_json()["username"] == "alice")
    check("users list", len(c.get("/api/auth/users", headers=ha).get_json()) == 2)

    r = c.post(
        "/api/posts",
        headers=ha,
        data={"caption": "first post", "photo": (io.BytesIO(PNG), "pic.png")},
        content_type="multipart/form-data",
    )
    check("create post", r.status_code == 201, r.status_code)
    post_id = r.get_json()["id"]

    r = c.get("/api/posts", headers=hb)
    feed = r.get_json()
    check("feed envelope", r.status_code == 200 and "posts" in feed and feed["posts"][0]["like_count"] == 0)

    r = c.post(f"/api/posts/{post_id}/like", headers=hb)
    check("like", r.get_json()["liked"] is True)
    r = c.post(f"/api/posts/{post_id}/like", headers=hb)
    check("unlike", r.get_json()["liked"] is False)

    check("add comment", c.post(f"/api/posts/{post_id}/comments", headers=hb, json={"text": "nice!"}).status_code == 201)
    check("list comments", len(c.get(f"/api/posts/{post_id}/comments", headers=hb).get_json()) == 1)

    r = c.post("/api/chat/groups", headers=ha, json={"member_ids": [id_b]})
    check("create 1-1 group", r.status_code in (200, 201) and r.get_json()["is_group"] is False, r.status_code)
    group_id = r.get_json()["id"]
    check("reuse 1-1 group", c.post("/api/chat/groups", headers=ha, json={"member_ids": [id_b]}).get_json()["id"] == group_id)

    check("send message", c.post(f"/api/chat/groups/{group_id}/messages", headers=ha, json={"text": "hello bob"}).status_code == 201)

    r = c.post(
        f"/api/chat/groups/{group_id}/messages",
        headers=ha,
        data={"media_type": "voice", "duration": "3.5", "file": (io.BytesIO(b"\x00\x01\x02\x03" * 100), "v.m4a")},
        content_type="multipart/form-data",
    )
    check("send voice note", r.status_code == 201 and r.get_json()["media_type"] == "voice", r.status_code)

    body = c.get(f"/api/chat/groups/{group_id}/messages", headers=hb).get_json()
    check("messages envelope", set(body.keys()) == {"messages", "next_cursor", "has_more"}, list(body.keys()))
    check("messages count", len(body["messages"]) == 2, len(body["messages"]))

    check("read-all", c.post(f"/api/chat/groups/{group_id}/read-all", headers=hb).status_code == 200)
    check("groups list", len(c.get("/api/chat/groups", headers=hb).get_json()) == 1)

    r = c.get("/api/ice-servers", headers=ha)
    check("ice-servers", r.status_code == 200 and len(r.get_json()["iceServers"]) >= 1)

    r = c.post("/api/calls/initiate", headers=ha, json={"group_id": group_id, "call_type": "video"})
    check("initiate call", r.status_code == 201 and r.get_json()["call_type"] == "video", r.status_code)

    r = c.put("/api/profile/edit", headers=ha, json={"display_name": "Alice A", "bio": "hi"})
    check("edit profile", r.status_code == 200 and r.get_json()["display_name"] == "Alice A", r.status_code)

    r = c.post("/api/media/upload", headers=ha, data={"file": (io.BytesIO(PNG), "x.png")}, content_type="multipart/form-data")
    check("media upload", r.status_code == 200 and "/uploads/" in r.get_json()["url"], r.status_code)

    check("auth required", c.get("/api/posts").status_code == 401)

    passed = sum(1 for ok in _results if ok)
    print(f"\n=== {passed}/{len(_results)} HTTP checks passed ===")
    return 0 if passed == len(_results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
