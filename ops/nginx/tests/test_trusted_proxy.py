"""Real Nginx 1.24 TLS/proxy rehearsal, temporary software keys only; no VPS."""
import hashlib
import http.client
import json
import os
from pathlib import Path
import ssl
import subprocess
import sys
import tempfile
import time
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "ops/pki"))
import internal_tls
import pki


def docker(*args, allow_failure=False):
    result = subprocess.run(["docker", *map(str, args)], capture_output=True, text=True, timeout=90)
    if result.returncode and not allow_failure:
        raise RuntimeError("Isolated Docker/Nginx operation failed: " + " ".join(map(str, args[:2])))
    return result


BACKEND = '''import hashlib,http.server,json,ssl
class Handler(http.server.BaseHTTPRequestHandler):
 def do_GET(self):
  peer=self.connection.getpeercert()
  fingerprint=hashlib.sha256(self.connection.getpeercert(binary_form=True)).hexdigest()
  expected=json.load(open('/fixture/internal/public-metadata.json'))['nginxCertificateSha256']
  if fingerprint != expected or ('URI','urn:imbasac:service:nginx') not in peer.get('subjectAltName',()):
   self.send_error(403);return
  result={'ip':self.headers.get('X-Imbasac-Client-IP'),'verify':self.headers.get('X-Imbasac-Client-Verify'),
   'certificate':self.headers.get('X-Imbasac-Client-Cert'),'requestId':self.headers.get('X-Imbasac-Request-ID'),
   'forwarded':self.headers.get('Forwarded'),'proxyPeerVerified':True,'path':self.path}
  body=json.dumps(result).encode();self.send_response(200);self.send_header('Content-Type','application/json')
  self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def log_message(self,*args):pass
server=http.server.HTTPServer(('127.0.0.1',8091),Handler)
context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
context.load_cert_chain('/fixture/internal/backend.crt.pem','/fixture/internal/backend.key.pem')
context.load_verify_locations('/fixture/internal/root.crt');context.verify_mode=ssl.CERT_REQUIRED
server.socket=context.wrap_socket(server.socket,server_side=True);server.serve_forever()
'''


class TrustedProxyRehearsal(unittest.TestCase):
    def test_real_tls_and_forwarded_certificate_boundary(self):
        identifier = uuid.uuid4().hex
        backend = "imbasac-proxy-backend-" + identifier
        nginx = "imbasac-proxy-nginx-" + identifier
        created = []
        with tempfile.TemporaryDirectory(prefix="imbasac-proxy-test-") as temporary:
            base = Path(temporary)
            password = base / "passphrase"
            pki.write_new(password, "synthetic-isolated-test-passphrase\n")
            internal_tls.prepare(base / "internal", password)
            root = pki.initialize(base / "device-root", "root", password, offline=True)
            issuer = pki.initialize(base / "device-issuer", "issuer", password)
            pki.sign_issuer(root, issuer / "issuer.csr.pem", base / "issuer-signed.pem", password, offline=True)
            pki.install_issuer(issuer, base / "issuer-signed.pem", root / "ca.crt.pem")
            pki.run("req", "-new", "-newkey", "rsa:3072", "-nodes", "-sha256", "-keyout", base / "device.key",
                    "-out", base / "device.csr", "-subj", "/CN=untrusted-input")
            pki.issue_device(issuer, base / "device.csr", str(uuid.uuid4()), base / "device.crt", password)
            pki.write_new(base / "device-chain.pem", (base / "device.crt").read_bytes() + (issuer / "ca.crt.pem").read_bytes())
            pki.run("req", "-new", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:prime256v1", "-nodes", "-sha256",
                    "-keyout", base / "device-p256.key", "-out", base / "device-p256.csr", "-subj", "/CN=untrusted-ec-input")
            pki.issue_device(issuer, base / "device-p256.csr", str(uuid.uuid4()), base / "device-p256.crt", password)
            pki.write_new(base / "device-p256-chain.pem", (base / "device-p256.crt").read_bytes() + (issuer / "ca.crt.pem").read_bytes())
            public = base / "public"
            public.mkdir()
            pki.write_new(public / "device-trust.pem", (root / "ca.crt.pem").read_bytes() + (issuer / "ca.crt.pem").read_bytes())
            pki.generate_crl(root, base / "root.crl", password)
            pki.generate_crl(issuer, base / "issuer.crl", password)
            pki.write_new(public / "device-crls.pem", (base / "root.crl").read_bytes() + (base / "issuer.crl").read_bytes())
            location = (ROOT / "ops/nginx/snippets/imbasac-backend-mtls-location.conf").read_text().replace("/etc/imbasac/pki/internal", "/fixture/internal")
            client = (ROOT / "ops/nginx/snippets/imbasac-client-mtls-server.conf").read_text().replace("/etc/imbasac/pki/public", "/fixture/public")
            configuration = '''pid /tmp/nginx.pid;
error_log /dev/stderr warn;
events {}
http { access_log off; server { listen 8443 ssl; server_name localhost;
ssl_certificate /fixture/internal/backend.crt.pem;
ssl_certificate_key /fixture/internal/backend.key.pem;
''' + client + "\nlocation /api/ {\n" + location + "\n}\n}\n}\n"
            pki.write_new(base / "nginx.conf", configuration)
            pki.write_new(base / "backend.py", BACKEND)
            try:
                docker("create", "--pull=never", "--name", backend, "--publish", "127.0.0.1::8443",
                       "--mount", f"type=bind,source={base},target=/fixture,readonly", "python:3", "python", "-B", "/fixture/backend.py")
                created.append(backend)
                docker("start", backend)
                docker("create", "--pull=never", "--name", nginx, "--network", "container:" + backend,
                       "--mount", f"type=bind,source={base},target=/fixture,readonly",
                       "--entrypoint", "/usr/sbin/nginx", "nginx:1.24-alpine", "-c", "/fixture/nginx.conf", "-g", "daemon off;")
                created.append(nginx)
                docker("start", nginx)
                docker("exec", nginx, "nginx", "-t", "-c", "/fixture/nginx.conf")
                ports = json.loads(docker("inspect", "--format", "{{json .NetworkSettings.Ports}}", backend).stdout)
                port = int(ports["8443/tcp"][0]["HostPort"])
                def request(cert=None, key=None, forged=False):
                    context = ssl.create_default_context(cafile=str(base / "internal/root.crt"))
                    if cert:
                        context.load_cert_chain(str(cert), str(key))
                    deadline = time.monotonic() + 12
                    while True:
                        connection = http.client.HTTPSConnection("127.0.0.1", port, context=context, timeout=3)
                        try:
                            headers = {"X-Imbasac-Client-IP": "1.2.3.4", "X-Imbasac-Client-Verify": "SUCCESS",
                                       "X-Imbasac-Client-Cert": "FORGED", "X-Imbasac-Request-ID": "FORGED", "Forwarded": "for=1.2.3.4"} if forged else {}
                            connection.request("GET", "/api/check", headers=headers)
                            response = connection.getresponse()
                            return response.status, response.read()
                        except (OSError, http.client.HTTPException):
                            if time.monotonic() >= deadline:
                                raise
                            time.sleep(0.2)
                        finally:
                            connection.close()
                status, body = request(forged=True)
                self.assertEqual(status, 200)
                observed = json.loads(body)
                self.assertEqual(observed["verify"], "NONE")
                self.assertFalse(observed["certificate"])
                self.assertNotEqual(observed["ip"], "1.2.3.4")
                self.assertNotEqual(observed["requestId"], "FORGED")
                self.assertIsNone(observed["forwarded"])
                self.assertEqual(observed["path"], "/check")
                self.assertTrue(observed["proxyPeerVerified"])
                status, body = request(base / "device-chain.pem", base / "device.key", forged=True)
                self.assertEqual(status, 200)
                observed = json.loads(body)
                self.assertEqual(observed["verify"], "SUCCESS")
                self.assertIn("BEGIN%20CERTIFICATE", observed["certificate"])
                self.assertNotIn("FORGED", observed["certificate"])
                status, body = request(base / "device-p256-chain.pem", base / "device-p256.key")
                self.assertEqual(status, 200)
                self.assertEqual(json.loads(body)["verify"], "SUCCESS")
                status, _ = request(base / "internal/nginx.crt.pem", base / "internal/nginx-client.key")
                self.assertEqual(status, 400)  # A credential from the private proxy CA is not a device credential.
                probe = "import socket,ssl; c=ssl.create_default_context(cafile='/fixture/internal/root.crt'); s=c.wrap_socket(socket.socket(),server_hostname='localhost'); s.connect(('127.0.0.1',8091)); s.sendall(b'GET / HTTP/1.0\\r\\n\\r\\n'); print(s.recv(100))"
                self.assertNotEqual(docker("exec", backend, "python", "-c", probe, allow_failure=True).returncode, 0)
            finally:
                for name in reversed(created):
                    docker("rm", "--force", "--volumes", name, allow_failure=True)


if __name__ == "__main__":
    unittest.main(verbosity=2)
