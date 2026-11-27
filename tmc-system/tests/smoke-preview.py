"""Task 8: local preview smoke; does not print secrets."""
import json
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError
root = Path(__file__).resolve().parents[1]
settings = dict(line.split("=", 1) for line in (root / ".env.preview").read_text().splitlines() if line and not line.startswith("#") and "=" in line)
def call(path, expected, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token: headers["Authorization"] = "Bearer " + token
    req = Request("http://127.0.0.1:5173" + path, data=json.dumps(body).encode() if body is not None else None, headers=headers)
    try:
        with urlopen(req, timeout=20) as res: status, payload = res.status, res.read()
    except HTTPError as error: status, payload = error.code, error.read()
    assert status == expected, f"{path}: expected {expected}, received {status}"
    print(f"PASS {path}: {status}")
    return json.loads(payload) if payload and payload.startswith(b"{") else None
call("/", 200)
call("/api/v1/employees", 401)
session = call("/api/v1/auth/login", 200, {"username": "preview.admin", "password": settings["TMC_PREVIEW_PASSWORD"]})
token = session["accessToken"]
try:
    people = call("/api/v1/employees", 200, token=token)
    assert people["items"], "Expected persisted preview employee"
    qr = call("/api/v1/me/qr-token", 200, token=token)
    assert len(qr["token"].split(".")) == 3
    call("/api/v1/qr/keys/refresh", 403, token=token)
finally:
    call("/api/v1/auth/logout", 204, {}, token)
call("/api/v1/employees", 401, token=token)
