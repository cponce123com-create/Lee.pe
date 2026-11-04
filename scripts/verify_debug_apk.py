"""Verify offline permissions, component exposure, app backup policy, and quote cards."""
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile


ANDROID = "{http://schemas.android.com/apk/res/android}"
MEDIA_SESSION_ACTION = "androidx.media3.session.MediaSessionService"
FORBIDDEN_PERMISSIONS = {
    "android.permission.INTERNET",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_MEDIA_VIDEO",
    "android.permission.READ_MEDIA_AUDIO",
    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
    "android.permission.CAMERA",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.READ_CALL_LOG",
    "android.permission.WRITE_CALL_LOG",
    "android.permission.RECORD_AUDIO",
}


def _is_expected_media3_bluetooth_validation_activity(activity: ET.Element) -> bool:
    """Allow Media3's hidden Android 16/17 Bluetooth validation entry point only."""
    if (
        activity.get(ANDROID + "exported") != "true"
        or activity.get(ANDROID + "excludeFromRecents") != "true"
        or activity.get(ANDROID + "noHistory") != "true"
        or activity.get(ANDROID + "theme") != "@android:style/Theme.NoDisplay"
        or activity.get(ANDROID + "permission") != "android.permission.BLUETOOTH_PRIVILEGED"
    ):
        return False
    filters = activity.findall("intent-filter")
    if len(filters) != 1:
        return False
    intent_filter = filters[0]
    actions = {node.get(ANDROID + "name") for node in intent_filter.findall("action")}
    if actions != {"android.intent.action.VIEW"} or intent_filter.findall("category"):
        return False
    data_attributes = [
        attribute
        for node in intent_filter.findall("data")
        for attribute in node.attrib.items()
    ]
    expected_data = {
        ANDROID + "scheme": "content",
        ANDROID + "host": "media",
        ANDROID + "pathPrefix": "/internal/audio/media/",
        ANDROID + "mimeType": "audio/*",
    }
    # The APK manifest printer may merge the four source <data> elements into
    # one element. Compare the complete attribute set while rejecting extras.
    return len(data_attributes) == len(expected_data) and dict(data_attributes) == expected_data


def verify_manifest(manifest: str) -> str:
    """Validate app security invariants and return the package name."""
    root = ET.fromstring(manifest)
    package = root.attrib.get("package")
    if not package:
        raise RuntimeError("APK manifest has no package name")
    application = root.find("application")
    if application is None or application.get(ANDROID + "allowBackup") != "false":
        raise RuntimeError('Application must set android:allowBackup="false"')

    permissions = {
        node.get(ANDROID + "name")
        for tag in ("uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-33")
        for node in root.findall(tag)
    }
    unexpected = permissions & FORBIDDEN_PERMISSIONS
    if unexpected:
        raise RuntimeError(f"Unexpected permissions: {sorted(unexpected)}")

    app_components = {
        tag: application.findall(tag)
        for tag in ("activity", "activity-alias", "service", "receiver", "provider")
    }
    main_activity = package + ".MainActivity"
    activities = app_components["activity"] + app_components["activity-alias"]
    launchers = [
        node for node in activities
        if any(
            action.get(ANDROID + "name") == "android.intent.action.MAIN"
            for intent in node.findall("intent-filter")
            for action in intent.findall("action")
        )
    ]
    if len(launchers) != 1 or launchers[0].get(ANDROID + "name") != main_activity:
        raise RuntimeError("The only launcher activity must be the app MainActivity")
    for activity in activities:
        name = activity.get(ANDROID + "name", "")
        exported = activity.get(ANDROID + "exported")
        if name == main_activity:
            if exported != "true":
                raise RuntimeError("MainActivity must be exported for the launcher")
        elif name == "androidx.media3.session.BluetoothValidationActivity":
            if not _is_expected_media3_bluetooth_validation_activity(activity):
                raise RuntimeError("Media3 Bluetooth validation activity is not safely configured")
        elif name.startswith("pe.lee.") and exported != "false":
            raise RuntimeError(f"App activity must be explicitly non-exported: {name}")
        elif exported == "true":
            raise RuntimeError(f"Unexpected exported activity: {name}")

    services = app_components["service"]
    exported_services = {
        node.get(ANDROID + "name", ""): node
        for node in services
        if node.get(ANDROID + "exported") == "true"
    }
    expected_names = {package + "." + suffix for suffix in (
        "ui.audio.AudioPlaybackService",
        "ui.audio.TtsPlaybackService",
    )}
    if set(exported_services) != expected_names:
        raise RuntimeError(
            "Only the two Media3 session services may be exported; "
            f"found {sorted(exported_services)}"
        )
    for service in services:
        name = service.get(ANDROID + "name", "")
        if name.startswith("pe.lee.") and service.get(ANDROID + "exported") not in ("true", "false"):
            raise RuntimeError(f"App service must declare android:exported: {name}")
        if name in expected_names:
            actions = {
                action.get(ANDROID + "name")
                for intent in service.findall("intent-filter")
                for action in intent.findall("action")
            }
            if actions != {MEDIA_SESSION_ACTION} or len(service.findall("intent-filter")) != 1:
                raise RuntimeError(f"Unexpected Media3 service actions or filters: {name}")
        elif service.get(ANDROID + "exported") == "true":
            raise RuntimeError(f"Unexpected exported service: {name}")

    for receiver in app_components["receiver"]:
        name = receiver.get(ANDROID + "name", "")
        if receiver.get(ANDROID + "exported") == "true":
            raise RuntimeError(f"Exported receiver requires an explicit security review: {name}")

    for provider in app_components["provider"]:
        name = provider.get(ANDROID + "name", "")
        if provider.get(ANDROID + "exported") == "true":
            raise RuntimeError(f"Content providers must not be exported: {name}")
        if name.endswith("FileProvider") or name.endswith("BundledFileProvider"):
            if provider.get(ANDROID + "exported") != "false":
                raise RuntimeError(f"FileProvider must explicitly be non-exported: {name}")
            if provider.get(ANDROID + "grantUriPermissions") != "true":
                raise RuntimeError(f"FileProvider must grant scoped URI access: {name}")

    required_authority = package + ".fileprovider"
    if not any(
        provider.get(ANDROID + "authorities") == required_authority
        and provider.get(ANDROID + "exported") == "false"
        and provider.get(ANDROID + "grantUriPermissions") == "true"
        for provider in app_components["provider"]
    ):
        raise RuntimeError("Quote-card FileProvider missing or unsafe")
    return package


def verify(apk: Path) -> None:
    sdk = Path(os.environ["ANDROID_HOME"])
    analyzer = sdk / "cmdline-tools/latest/bin/apkanalyzer"
    manifest = subprocess.check_output(
        [str(analyzer), "manifest", "print", str(apk)], text=True
    )
    package = verify_manifest(manifest)

    with zipfile.ZipFile(apk) as archive:
        marker = b"Lpe/lee/feature/sharecard/navigation/ShareCardActivity;"
        if not any(
            marker in archive.read(name)
            for name in archive.namelist()
            if name.startswith("classes") and name.endswith(".dex")
        ):
            raise RuntimeError("Quote-card class missing from DEX")
    print(
        f"APK verified: {apk.name}; package={package}; quote cards present; "
        "no prohibited permissions; backup disabled; app components restricted"
    )


if __name__ == "__main__":
    verify(Path(sys.argv[1]))
