from __future__ import annotations

import json
import os
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from wildlife_sync import RequestBudgetExceeded, UpstreamError, WildlifeRepository


ROOT = Path(__file__).resolve().parent
DATABASE_PATH = Path(os.environ.get("WILDLIFE_DB_PATH", ROOT / "wildlife_sync.db"))
HOST = os.environ.get("WILDLIFE_HOST", "127.0.0.1")
PORT = int(os.environ.get("WILDLIFE_PORT", "8765"))
repository = WildlifeRepository(DATABASE_PATH)


class Handler(BaseHTTPRequestHandler):
    server_version = "WildlifeSync/0.1"

    def do_GET(self) -> None:
        if self.path == "/health":
            self.respond(200, {"status": "ok"})
            return
        match = re.fullmatch(r"/v1/users/(\d+)/observations", self.path)
        if match:
            self.respond(200, repository.snapshot(int(match.group(1))))
            return
        self.respond(404, {"error": "Not found"})

    def do_POST(self) -> None:
        sync_match = re.fullmatch(r"/v1/users/(\d+)/sync", self.path)
        confirm_match = re.fullmatch(r"/v1/users/(\d+)/observations/([^/]+)/confirm", self.path)
        try:
            if sync_match:
                user_id = int(sync_match.group(1))
                body = self.read_json()
                login = str(body.get("login") or "").strip()
                if not login:
                    self.respond(400, {"error": "login is required"})
                    return
                self.respond(200, repository.sync(user_id, login))
                return
            if confirm_match:
                result = repository.confirm(int(confirm_match.group(1)), confirm_match.group(2))
                self.respond(200, {
                    "xp_awarded": result.xp_awarded,
                    "total_xp": result.total_xp,
                    "confirmed_count": result.confirmed_count,
                })
                return
            self.respond(404, {"error": "Not found"})
        except LookupError as error:
            self.respond(404, {"error": str(error)})
        except RequestBudgetExceeded as error:
            self.respond(429, {"error": str(error)})
        except UpstreamError as error:
            self.respond(502, {"error": str(error)})
        except (ValueError, json.JSONDecodeError) as error:
            self.respond(400, {"error": str(error)})
        except Exception as error:
            self.respond(500, {"error": f"Internal sync error: {error}"})

    def read_json(self) -> dict:
        length = int(self.headers.get("Content-Length", "0"))
        if length == 0:
            return {}
        return json.loads(self.rfile.read(length).decode("utf-8"))

    def respond(self, status: int, body: dict) -> None:
        payload = json.dumps(body, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format: str, *args) -> None:
        print(f"{self.address_string()} - {format % args}")


if __name__ == "__main__":
    print(f"Wildlife sync backend listening on http://{HOST}:{PORT}")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
