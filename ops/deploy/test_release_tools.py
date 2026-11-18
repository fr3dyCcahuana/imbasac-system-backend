import json
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import unittest
from unittest import mock

import backup_release
import restore_check
import preflight


class SelectionTest(unittest.TestCase):
    def test_clock_requires_synchronized_value_not_just_command_success(self):
        with mock.patch("subprocess.run", return_value=mock.Mock(returncode=0, stdout="no\n")):
            self.assertFalse(preflight.clock_synchronized())
        with mock.patch("subprocess.run", return_value=mock.Mock(returncode=0, stdout="yes\n")):
            self.assertTrue(preflight.clock_synchronized())
    def test_selected_files_must_be_specific_and_exist(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            selected = base / "selected.json"
            selected.write_text(json.dumps([str(base / "missing")]))
            with self.assertRaises(ValueError):
                backup_release.validate_selection(selected)
            existing = base / "config"
            existing.write_text("test-only")
            selected.write_text(json.dumps([str(existing)]))
            self.assertEqual(backup_release.validate_selection(selected), [existing])

    def test_repository_backup_target_is_forbidden(self):
        with self.assertRaises(ValueError):
            backup_release.private_external(backup_release.REPOSITORY / "backup")

    def test_restore_does_not_accept_arbitrary_image(self):
        with tempfile.NamedTemporaryFile() as source:
            with self.assertRaises(ValueError), mock.patch.object(restore_check, "docker") as command:
                restore_check.verify(source.name, "untrusted/image")
            command.assert_not_called()

    def test_restore_cleanup_is_only_its_new_container(self):
        with tempfile.NamedTemporaryFile() as source:
            calls = []
            def fake(*args, **kwargs):
                calls.append(args)
                if args[0] == "cp":
                    raise ValueError("test restore failure")
                return mock.Mock(returncode=0, stdout="")
            with mock.patch.object(restore_check, "docker", side_effect=fake), self.assertRaises(ValueError):
                restore_check.verify(source.name)
            self.assertEqual(calls[0][0], "create")
            self.assertIn("none", calls[0])
            created_name = calls[0][calls[0].index("--name") + 1]
            self.assertTrue(created_name.startswith("imbasac-restore-check-"))
            self.assertEqual(calls[-1], ("rm", "--force", "--volumes", created_name))

    @unittest.skipUnless(os.name == "posix" and shutil.which("gpg"), "POSIX GPG test")
    def test_encrypted_package_retains_files_and_leaves_no_plaintext(self):
        with tempfile.TemporaryDirectory(prefix="imbasac-backup-test-") as temporary:
            base = Path(temporary)
            config = base / "config"
            config.write_text("SYNTHETIC-SECRET-ONLY")
            selection = base / "selection.json"
            selection.write_text(json.dumps([str(config)]))
            password = base / "password"
            password.write_text("isolated-test-password")
            password.chmod(0o600)
            destination = base / "backup"
            real_execute = backup_release.execute
            def execute(command, stdout=None):
                if command[0] == "runuser":
                    stdout.write(b"SYNTHETIC-DUMP-ONLY")
                elif command[0] != "pg_restore":
                    real_execute(command, stdout)
            with mock.patch.object(backup_release, "execute", side_effect=execute), mock.patch("os.geteuid", return_value=0):
                encrypted = backup_release.backup("test_only", 5432, selection, destination, password, True)
            self.assertEqual({entry.name for entry in destination.iterdir()}, {"release-backup.tar.gpg", "SHA256SUMS"})
            self.assertEqual(encrypted.stat().st_mode & 0o777, 0o600)
            self.assertNotIn(b"SYNTHETIC-SECRET-ONLY", encrypted.read_bytes())
            decrypted = base / "decrypted.tar"
            subprocess.run(["gpg", "--batch", "--no-options", "--homedir", str(base), "--pinentry-mode", "loopback",
                            "--passphrase-file", str(password), "--output", str(decrypted), "--decrypt", str(encrypted)],
                           check=True, capture_output=True)
            with tarfile.open(decrypted) as package:
                self.assertEqual(set(package.getnames()), {"database.dump", "selected-files.tar", "manifest.json"})
                manifest = json.load(package.extractfile("manifest.json"))
                self.assertFalse(manifest["restoreVerified"])
                self.assertFalse(manifest["offVpsCopyVerified"])
                self.assertEqual(package.extractfile("database.dump").read(), b"SYNTHETIC-DUMP-ONLY")

    def test_backup_rejects_no_writer_ack_before_running_commands(self):
        with mock.patch("os.name", "posix"), mock.patch("os.geteuid", return_value=0, create=True), mock.patch.object(backup_release, "execute") as command:
            with self.assertRaises(ValueError):
                backup_release.backup("test_only", 5432, "ignored", "ignored", "ignored", False)
            command.assert_not_called()


if __name__ == "__main__":
    unittest.main()
