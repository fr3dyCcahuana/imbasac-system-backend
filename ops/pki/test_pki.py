import json
import os
from pathlib import Path
import tempfile
import unittest
import uuid
import shutil

import pki
import internal_tls
import publish_crl


class PkiIntegrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="imbasac-pki-test-")
        cls.base = Path(cls.temp.name)
        cls.password = cls.base / "password"
        pki.write_new(cls.password, "isolated-test-only-password\n")
        cls.root = pki.initialize(cls.base / "root", "root", cls.password, offline=True)
        cls.issuer = pki.initialize(cls.base / "issuer", "issuer", cls.password)
        signed = cls.base / "issuer.crt.pem"
        pki.sign_issuer(cls.root, cls.issuer / "issuer.csr.pem", signed, cls.password, offline=True)
        pki.install_issuer(cls.issuer, signed, cls.root / "ca.crt.pem")
        cls.csr = cls.base / "device.csr.pem"
        cls.key = cls.base / "device.key.pem"
        # Software key is deliberately test-only; no claim of physical device binding.
        pki.run("req", "-new", "-newkey", "rsa:3072", "-nodes", "-sha256", "-keyout", cls.key,
                "-out", cls.csr, "-subj", "/CN=UNTRUSTED CLIENT", "-addext", "basicConstraints=critical,CA:TRUE",
                "-addext", "subjectAltName=DNS:evil.example", "-addext", "extendedKeyUsage=serverAuth")

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_leaf_has_server_controlled_identity_and_profile(self):
        device = str(uuid.uuid4())
        cert = self.base / "leaf-profile.pem"
        metadata = pki.issue_device(self.issuer, self.csr, device, cert, self.password)
        content = pki.run("x509", "-in", cert, "-text", "-noout").decode()
        self.assertIn("CA:FALSE", content)
        self.assertIn("TLS Web Client Authentication", content)
        self.assertNotIn("TLS Web Server Authentication", content)
        self.assertIn("URI:urn:imbasac:device:" + device, content)
        self.assertNotIn("evil.example", content)
        self.assertNotIn("UNTRUSTED CLIENT", content)
        self.assertFalse(metadata["hardwareAttestationVerified"])
        self.assertEqual(len(metadata["fingerprintSha256"]), 64)

    def test_revoke_and_crl_validation(self):
        cert = self.base / "revoked.pem"
        pki.issue_device(self.issuer, self.csr, str(uuid.uuid4()), cert, self.password)
        pki.revoke(self.issuer, cert, self.password)
        crl = self.base / "issuer.crl.pem"
        pki.generate_crl(self.issuer, crl, self.password)
        with self.assertRaises(ValueError):
            pki.run("verify", "-CAfile", self.root / "ca.crt.pem", "-untrusted", self.issuer / "ca.crt.pem",
                    "-CRLfile", crl, "-crl_check", cert)

    def test_outputs_not_overwritten(self):
        cert = self.base / "existing.pem"
        pki.write_new(cert, "retained")
        with self.assertRaises(ValueError):
            pki.issue_device(self.issuer, self.csr, str(uuid.uuid4()), cert, self.password)
        self.assertEqual(cert.read_text(), "retained")
        with self.assertRaises(FileExistsError):
            pki.initialize(self.root, "root", self.password, offline=True)

    def test_rejects_repository_and_invalid_uuid(self):
        with self.assertRaises(ValueError):
            pki.external(pki.REPOSITORY / "generated-pki")
        with self.assertRaises(ValueError):
            pki.issue_device(self.issuer, self.csr, "../../invalid", self.base / "invalid.pem", self.password)

    def test_ca_serialization_refuses_concurrent_operator(self):
        with pki.locked(self.issuer):
            with self.assertRaises(FileExistsError):
                pki.issue_device(self.issuer, self.csr, str(uuid.uuid4()), self.base / "locked.pem", self.password)

    def test_rejects_weak_key(self):
        weak = self.base / "weak.csr.pem"
        pki.run("req", "-new", "-newkey", "rsa:2048", "-nodes", "-sha256", "-keyout", self.base / "weak.key",
                "-out", weak, "-subj", "/CN=weak")
        with self.assertRaises(ValueError):
            pki.validate_csr(weak)

    def test_ec_p256_csr_can_issue_standard_client_certificate(self):
        csr = self.base / "p256.csr.pem"
        cert = self.base / "p256.crt.pem"
        pki.run("req", "-new", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:prime256v1", "-nodes", "-sha256",
                "-keyout", self.base / "p256.key.pem", "-out", csr, "-subj", "/CN=UNTRUSTED EC INPUT",
                "-addext", "basicConstraints=critical,CA:TRUE", "-addext", "extendedKeyUsage=serverAuth")
        pki.validate_csr(csr)
        device = str(uuid.uuid4())
        metadata = pki.issue_device(self.issuer, csr, device, cert, self.password)
        text = pki.run("x509", "-in", cert, "-text", "-noout").decode()
        self.assertIn("ASN1 OID: prime256v1", text)
        self.assertIn("TLS Web Client Authentication", text)
        self.assertIn("URI:urn:imbasac:device:" + device, text)
        self.assertIn("CA:FALSE", text)
        self.assertNotIn("TLS Web Server Authentication", text)
        self.assertFalse(metadata["hardwareAttestationVerified"])

    def test_rejects_ec_p384_even_with_valid_csr_signature(self):
        csr = self.base / "p384.csr.pem"
        pki.run("req", "-new", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:secp384r1", "-nodes", "-sha256",
                "-keyout", self.base / "p384.key.pem", "-out", csr, "-subj", "/CN=unsupported curve")
        pki.run("req", "-in", csr, "-verify", "-noout")
        with self.assertRaises(ValueError):
            pki.validate_csr(csr)

    def test_rejects_invalid_signature_and_oversized_csr(self):
        broken = self.base / "broken.csr.pem"
        pki.write_new(broken, "not a CSR")
        with self.assertRaises(ValueError):
            pki.validate_csr(broken)
        oversized = self.base / "large.csr.pem"
        pki.write_new(oversized, "X" * 16385)
        with self.assertRaises(ValueError):
            pki.validate_csr(oversized)

    def test_requires_offline_confirmation(self):
        with self.assertRaises(ValueError):
            pki.initialize(self.base / "forbidden-root", "root", self.password)
        with self.assertRaises(ValueError):
            pki.sign_issuer(self.root, self.issuer / "issuer.csr.pem", self.base / "forbidden.pem", self.password)

    def test_rejects_overlong_lifetime(self):
        with self.assertRaises(ValueError):
            pki.issue_device(self.issuer, self.csr, str(uuid.uuid4()), self.base / "long.pem", self.password, 366)

    def test_root_crl(self):
        crl = self.base / "root.crl.pem"
        pki.generate_crl(self.root, crl, self.password)
        pki.run("crl", "-in", crl, "-verify", "-CAfile", self.root / "ca.crt.pem", "-noout")

    def test_internal_channel_has_separate_ca_and_pinned_client_identity(self):
        directory = self.base / "internal"
        fingerprint = internal_tls.prepare(directory, self.password)
        self.assertEqual(len(fingerprint), 64)
        content = pki.run("x509", "-in", directory / "nginx.crt.pem", "-text", "-noout").decode()
        self.assertIn("URI:urn:imbasac:service:nginx", content)
        self.assertIn("TLS Web Client Authentication", content)
        pki.run("verify", "-CAfile", directory / "internal-ca.crt.pem", "-verify_hostname", "localhost", directory / "backend.crt.pem")
        self.assertTrue((directory / "nginx-client.key").exists())
        self.assertEqual((directory / "nginx-client-chain.pem").read_bytes().count(b"BEGIN CERTIFICATE"), 2)
        if shutil.which(os.environ.get("KEYTOOL", "keytool")):
            internal_tls.prepare_java_stores(directory, self.password)
            pki.run("pkcs12", "-in", directory / "backend.p12", "-passin", pki.secret(self.password), "-info", "-noout")
            self.assertTrue((directory / "proxy-trust.p12").is_file())
        with self.assertRaises(ValueError):
            pki.run("verify", "-CAfile", self.root / "ca.crt.pem", directory / "nginx.crt.pem")

    def test_atomic_crl_bundle_validates_both_signatures(self):
        root_crl = self.base / "publish-root.crl.pem"
        issuer_crl = self.base / "publish-issuer.crl.pem"
        target = self.base / "published-crls.pem"
        pki.generate_crl(self.root, root_crl, self.password)
        pki.generate_crl(self.issuer, issuer_crl, self.password)
        publish_crl.publish(root_crl, self.root / "ca.crt.pem", issuer_crl, self.issuer / "ca.crt.pem", target)
        retained = target.read_bytes()
        self.assertEqual(retained.count(b"BEGIN X509 CRL"), 2)
        with self.assertRaises(ValueError):
            publish_crl.publish(root_crl, self.issuer / "ca.crt.pem", issuer_crl, self.issuer / "ca.crt.pem", target)
        self.assertEqual(target.read_bytes(), retained)


if __name__ == "__main__":
    unittest.main()
