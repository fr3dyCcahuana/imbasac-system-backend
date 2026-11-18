import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest
import uuid

import restore_check


@unittest.skipUnless(os.environ.get("IMBASAC_RESTORE_DOCKER_TEST") == "1", "Explicit isolated Docker test not requested")
class RestoreIntegrationTest(unittest.TestCase):
    def test_dump_and_restore_postgres18_without_host_network(self):
        source = "imbasac-restore-source-" + uuid.uuid4().hex
        created = False
        try:
            restore_check.docker("create", "--pull=never", "--name", source, "--network", "none", "--env",
                                 "POSTGRES_HOST_AUTH_METHOD=trust", "postgres:18-alpine")
            created = True
            restore_check.docker("start", source)
            deadline = time.monotonic() + 45
            while time.monotonic() < deadline:
                if restore_check.docker("exec", source, "pg_isready", "-U", "postgres", allow_failure=True).returncode == 0:
                    break
                time.sleep(0.5)
            else:
                self.fail("Isolated source did not become ready")
            restore_check.docker("exec", source, "psql", "-U", "postgres", "-v", "ON_ERROR_STOP=1", "-c",
                                 "CREATE TABLE imbasac_restore_fixture(id int PRIMARY KEY, status text NOT NULL); "
                                 "INSERT INTO imbasac_restore_fixture VALUES (1,'CONFIRMED');")
            with tempfile.TemporaryDirectory(prefix="imbasac-restore-fixture-") as temporary:
                dump = Path(temporary) / "database.dump"
                with dump.open("wb") as output:
                    result = subprocess.run(["docker", "exec", source, "pg_dump", "-U", "postgres", "-d", "postgres",
                                             "--format=custom", "--no-owner", "--no-privileges"], stdout=output, stderr=subprocess.PIPE)
                    self.assertEqual(result.returncode, 0)
                restored = restore_check.verify(dump)
                self.assertTrue(restored["databaseRestoreVerified"])
                self.assertFalse(restored["rpoZeroVerified"])
                self.assertFalse(restored["applicationRestoreVerified"])
                self.assertGreater(restored["tableCount"], 0)
        finally:
            if created:
                restore_check.docker("rm", "--force", "--volumes", source, allow_failure=True)


if __name__ == "__main__":
    unittest.main()
