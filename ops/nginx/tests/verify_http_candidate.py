"""Check the HTTP candidate in disposable Nginx 1.24; never contact the VPS."""
from pathlib import Path
import http.client
import json
import re
import subprocess
import tempfile
import time
import uuid


def docker(*args):
    result = subprocess.run(
        ["docker", *args], text=True, capture_output=True, check=False, timeout=180
    )
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or "Docker command failed")
    return result.stdout.strip()


def main():
    candidate = Path(__file__).resolve().parents[1] / "candidates" / "pos-deny-unknown-hosts.conf"
    identifier = None
    with tempfile.TemporaryDirectory(prefix="imbasac-nginx-test-") as directory:
        fixture = Path(directory) / "nginx.conf"
        # These synthetic named vhosts are NOT copies of production configuration.
        fixture.write_text("""pid /tmp/nginx.pid;
error_log /dev/stderr notice;
events {}
http {
  access_log off;
  include /candidate.conf;
  server {
    listen 80;
    listen [::]:80;
    server_name imbasac.cloud www.imbasac.cloud;
    return 301 https://imbasac.cloud$request_uri;
  }
  server {
    listen 80;
    listen [::]:80;
    server_name localhost 127.0.0.1;
    return 404;
  }
}
""", encoding="utf-8")
        try:
            identifier = docker(
                "run", "--detach", "--name", "imbasac-nginx-http-test-" + uuid.uuid4().hex,
                "--read-only", "--tmpfs", "/tmp:rw,nosuid,nodev",
                "--tmpfs", "/var/cache/nginx:rw,nosuid,nodev", "--publish", "127.0.0.1::80",
                "--mount", f"type=bind,src={candidate},dst=/candidate.conf,readonly",
                "--mount", f"type=bind,src={fixture},dst=/fixture.conf,readonly",
                "--entrypoint", "/usr/sbin/nginx", "nginx:1.24-alpine",
                "-c", "/fixture.conf", "-g", "daemon off;"
            )
            if not re.fullmatch(r"[a-f0-9]{64}", identifier):
                raise RuntimeError("Unexpected disposable container identity")
            docker("exec", identifier, "nginx", "-t", "-c", "/fixture.conf")
            ports = json.loads(docker("inspect", "--format", "{{json .NetworkSettings.Ports}}", identifier))
            port = int(ports["80/tcp"][0]["HostPort"])
            cases = [
                ("imbasac.cloud", "/", 301),
                ("www.imbasac.cloud", "/api/test", 301),
                ("_", "/", 403),
                ("_", "/auth/login", 403),
                ("_", "/API_SUNAT/", 403),
                ("76.13.81.60", "/", 403),
                ("unrecognized.invalid", "/api/test", 403),
                ("_", "/images/products/example.png", 403),
                ("localhost", "/", 404),
                ("127.0.0.1", "/", 404),
            ]
            for host, path, expected in cases:
                deadline = time.monotonic() + 8
                while True:
                    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=2)
                    try:
                        connection.request("GET", path, headers={"Host": host})
                        response = connection.getresponse()
                        if response.status != expected:
                            raise AssertionError(f"{host} {path}: expected {expected}, got {response.status}")
                        if expected == 301 and response.getheader("Location") != "https://imbasac.cloud" + path:
                            raise AssertionError("Unexpected redirect in synthetic vhost")
                        response.read()
                        break
                    except OSError:
                        if time.monotonic() >= deadline:
                            raise
                        time.sleep(0.2)
                    finally:
                        connection.close()
            print(f"Nginx 1.24: syntax OK; {len(cases)} local HTTP routing checks passed.")
        except Exception as error:
            if identifier and re.fullmatch(r"[a-f0-9]{64}", identifier):
                logs = subprocess.run(
                    ["docker", "logs", identifier], text=True, capture_output=True, timeout=15
                )
                raise RuntimeError(f"{error}\nSynthetic Nginx logs:\n{logs.stdout}{logs.stderr}") from error
            raise
        finally:
            if identifier and re.fullmatch(r"[a-f0-9]{64}", identifier):
                docker("rm", "--force", identifier)


if __name__ == "__main__":
    main()
