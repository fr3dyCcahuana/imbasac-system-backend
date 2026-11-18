import unittest
from unittest.mock import patch
import subprocess
import inventory

class ReadinessTests(unittest.TestCase):
    def sample(self):
        return dict(fsync="on", full_page_writes="on", wal_level="replica", synchronous_commit="on",
                    synchronous_standby_names="FIRST 1 (standby)", in_recovery=False,
                    replicas=[dict(state="streaming", sync_state="sync", has_flush_lsn=True)])
    def test_local_commit_without_standby_is_not_candidate(self):
        snapshot = self.sample(); snapshot["synchronous_standby_names"] = ""
        self.assertFalse(inventory.assess_postgres(snapshot)["configuration_candidate"])
    def test_async_remote_write_local_or_missing_flush_is_not_candidate(self):
        for mode in ("off", "local", "remote_write"):
            snapshot = self.sample(); snapshot["synchronous_commit"] = mode
            self.assertFalse(inventory.assess_postgres(snapshot)["configuration_candidate"])
        for field, value in (("sync_state", "async"), ("has_flush_lsn", False)):
            snapshot = self.sample(); snapshot["replicas"][0][field] = value
            self.assertFalse(inventory.assess_postgres(snapshot)["configuration_candidate"])
    def test_even_synchronous_snapshot_never_certifies_rpo_rto(self):
        result = inventory.assess_postgres(self.sample())
        self.assertTrue(result["configuration_candidate"])
        self.assertFalse(result["rpo_zero_verified"])
        self.assertFalse(result["rto_one_hour_verified"])
        self.assertFalse(result["independent_destination_verified"])
    def test_primary_and_safety_settings_are_required(self):
        for field, value in (("fsync", "off"), ("full_page_writes", "off"), ("in_recovery", True)):
            snapshot = self.sample(); snapshot[field] = value
            self.assertFalse(inventory.assess_postgres(snapshot)["configuration_candidate"])
    def test_command_error_or_timeout_does_not_leak_output(self):
        with patch("inventory.subprocess.run", return_value=subprocess.CompletedProcess([],1,"secret","password")):
            self.assertEqual(inventory.run_command(["test"]), {"status":"unavailable", "exit_code":1})
        with patch("inventory.subprocess.run", side_effect=subprocess.TimeoutExpired("test",12,output="secret")):
            self.assertEqual(inventory.run_command(["test"]), {"status":"unavailable"})
    def test_pg_diagnostic_clears_ambient_credentials_and_forces_read_only(self):
        with patch.dict("inventory.os.environ", {"PGPASSWORD":"secret", "PGSERVICE":"external", "PGHOST":"remote"}):
            with patch("inventory.run_command", return_value={"status":"ok", "output":"{}"}) as command:
                result = inventory.read_postgres("postgres", 5432)
                args, kwargs = command.call_args
                self.assertNotIn("PGPASSWORD",kwargs["env"])
                self.assertNotIn("PGHOST",kwargs["env"])
                self.assertIn("default_transaction_read_only=on",kwargs["env"]["PGOPTIONS"])
                self.assertIn("--host=/var/run/postgresql",args[0])
                self.assertFalse(result["assessment"]["rpo_zero_verified"])

if __name__ == "__main__": unittest.main()
