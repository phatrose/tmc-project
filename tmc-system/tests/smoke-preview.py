"""Task 8: parameterized local smoke test (supports isolated stack URL/env without printing secrets)."""
import argparse
import json
import os
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

root = Path(__file__).resolve().parents[1]

parser = argparse.ArgumentParser(description="TMC HTTP smoke verification")
parser.add_argument("--base-url", default=os.environ.get("TMC_BASE_URL", "http://127.0.0.1:5173"))
parser.add_argument("--env-file", default=os.environ.get("TMC_ENV_FILE", str(root / ".env.preview")))
parser.add_argument("--username", default=os.environ.get("TMC_SMOKE_USER", "preview.admin"))
args = parser.parse_args()

settings = {}
env_path = Path(args.env_file)
if env_path.is_file():
    for line in env_path.read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            settings[k.strip()] = v.strip()

password = os.environ.get("TMC_PREVIEW_PASSWORD") or settings.get("TMC_PREVIEW_PASSWORD")
if not password:
    raise SystemExit("Missing TMC_PREVIEW_PASSWORD (supply via env var or --env-file)")

base_url = args.base_url.rstrip("/")

def call(path, expected, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = Request(
        base_url + path,
        data=json.dumps(body).encode("utf-8") if body is not None else None,
        headers=headers,
    )
    try:
        with urlopen(req, timeout=20) as res:
            status, payload = res.status, res.read()
    except HTTPError as error:
        status, payload = error.code, error.read()
    assert status == expected, f"{path}: expected {expected}, received {status}"
    print(f"PASS {path}: {status}")
    return json.loads(payload) if payload and payload.startswith(b"{") else None

call("/", 200)
call("/api/v1/employees", 401)
session = call("/api/v1/auth/login", 200, {"username": args.username, "password": password})
token = session["accessToken"]
try:
    people = call("/api/v1/employees", 200, token=token)
    assert people["items"], "Expected persisted employee"
    qr = call("/api/v1/me/qr-token", 200, token=token)
    assert len(qr["token"].split(".")) == 3
    gates = call("/api/v1/gates?page=0&size=20", 200, token=token)
    assert "items" in gates, "Expected paged gates response"
    call("/api/v1/qr/keys/refresh", 403, token=token)
finally:
    call("/api/v1/auth/logout", 204, {}, token)
call("/api/v1/employees", 401, token=token)
print("ALL SMOKE CHECKS PASSED")
