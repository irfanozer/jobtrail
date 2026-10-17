"""CI-only check. Writes a fixture profile to a new, empty localhost instance."""
import json
import os
import subprocess
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"


def request(path, data=None):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(BASE + path, data=body, method="GET" if data is None else "PUT",
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=15) as response:
        return json.load(response)


def wait_for_health():
    for attempt in range(120):
        try:
            if request("/actuator/health")["status"] == "UP":
                return
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(1)
    raise RuntimeError("Application did not become healthy")


if os.environ.get("CI") != "true":
    raise SystemExit("This writes a test fixture and must only run against the disposable CI stack.")
wait_for_health()
with urllib.request.urlopen(BASE, timeout=15) as response:
    assert b"JobTrail" in response.read(), "Packaged frontend is missing"
assert len(request("/api/sources")) > 0
assert request("/api/jobs")["totalElements"] == 0
assert request("/api/profile")["version"] == 0, "Refusing to overwrite an existing profile"
profile = {"displayName": "CI fixture", "preferredLocations": [], "skills": ["Java"],
           "targetRoles": [], "careerFacts": [], "version": 0}
assert request("/api/profile", profile)["version"] == 1
env = {**os.environ, "DATABASE_PASSWORD": "ci-isolated-database-password"}
subprocess.run(["docker", "compose", "restart", "app"], check=True, env=env)
wait_for_health()
saved = request("/api/profile")
assert saved["displayName"] == "CI fixture" and saved["version"] == 1
print("Packaged frontend, migrations, PostgreSQL API, and restart persistence passed.")
