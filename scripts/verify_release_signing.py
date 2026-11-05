"""Verify a release signature against the configured public certificate fingerprint."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys


def normalized_fingerprint(value):
    result = value.replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", result):
        raise ValueError("Expected a SHA-256 certificate fingerprint")
    return result


def verify_output(output, expected):
    signers = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F:]+)", output)
    if len(signers) != 1 or normalized_fingerprint(signers[0]) != normalized_fingerprint(expected):
        raise ValueError("Release signer does not match the persistent certificate")


def main():
    expected = normalized_fingerprint(os.environ.get("LEE_PE_SIGNING_CERT_SHA256", ""))
    directory = Path(sys.argv[1])
    apks = list(directory.glob("*.apk"))
    if len(apks) != 1:
        raise ValueError("Expected exactly one release APK")
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    candidates = list((sdk / "build-tools").glob("*/apksigner"))
    if not candidates:
        raise ValueError("Android SDK apksigner is not installed")
    signer = max(candidates, key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.parent.name)))
    output = subprocess.check_output([str(signer), "verify", "--verbose", "--print-certs", str(apks[0])], text=True)
    verify_output(output, expected)
    provenance = {
        "apk": apks[0].name,
        "apk_sha256": hashlib.sha256(apks[0].read_bytes()).hexdigest(),
        "certificate_sha256": expected,
        "commit": os.environ.get("GITHUB_SHA"),
    }
    (directory / "signing.json").write_text(json.dumps(provenance, indent=2) + "\n")
    print("Release signature verified against the configured persistent certificate")


if __name__ == "__main__":
    main()
