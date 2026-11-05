import unittest
from verify_release_signing import normalized_fingerprint, verify_output


class ReleaseSigningTest(unittest.TestCase):
    def test_accepts_only_expected_certificate(self):
        expected = "ab" * 32
        verify_output("Signer #1 certificate SHA-256 digest: " + expected, expected)
        with self.assertRaises(ValueError):
            verify_output("Signer #1 certificate SHA-256 digest: " + "cd" * 32, expected)

    def test_rejects_unsigned_or_multiple_signers(self):
        expected = "ab" * 32
        for output in ["", "Signer #1 certificate SHA-256 digest: " + expected + "\nSigner #2 certificate SHA-256 digest: " + expected]:
            with self.assertRaises(ValueError):
                verify_output(output, expected)

    def test_accepts_keytool_colon_format(self):
        self.assertEqual("ab" * 32, normalized_fingerprint(":".join(["AB"] * 32)))

    def test_rejects_missing_or_malformed_fingerprint(self):
        for value in ["", "ab", "g" * 64]:
            with self.assertRaises(ValueError):
                normalized_fingerprint(value)


if __name__ == "__main__":
    unittest.main()
