"""Verify offline permissions, component exposure, app backup policy, and quote cards."""
import os
import argparse
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile


ANDROID = "{http://schemas.android.com/apk/res/android}"
MEDIA_SESSION_ACTION = "androidx.media3.session.MediaSessionService"
PROFILE_INSTALL_RECEIVER = "androidx.profileinstaller.ProfileInstallReceiver"
PROFILE_INSTALL_ACTIONS = {
    "androidx.profileinstaller.action.INSTALL_PROFILE",
    "androidx.profileinstaller.action.SKIP_FILE",
    "androidx.profileinstaller.action.SAVE_PROFILE",
    "androidx.profileinstaller.action.BENCHMARK_OPERATION",
}
DEBUG_ONLY_ACTIVITIES = {
    "androidx.compose.ui.tooling.PreviewActivity",
    "androidx.activity.ComponentActivity",
}
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
        or activity.get(ANDROID + "theme") not in {
            "@android:style/Theme.NoDisplay",
            "@ref/0x1030055",
        }
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




def _is_expected_profile_install_receiver(receiver: ET.Element) -> bool:
    """Allow AndroidX ProfileInstaller only with its system DUMP permission."""
    if (
        receiver.get(ANDROID + "exported") != "true"
        or receiver.get(ANDROID + "permission") != "android.permission.DUMP"
        or receiver.get(ANDROID + "enabled") not in (None, "true")
        or receiver.get(ANDROID + "directBootAware") not in (None, "false")
    ):
        return False
    actions = []
    for intent_filter in receiver.findall("intent-filter"):
        if intent_filter.attrib or intent_filter.findall("category") or intent_filter.findall("data"):
            return False
        filter_actions = [node.get(ANDROID + "name") for node in intent_filter.findall("action")]
        if len(filter_actions) != 1:
            return False
        actions.extend(filter_actions)
    return len(actions) == len(PROFILE_INSTALL_ACTIONS) and set(actions) == PROFILE_INSTALL_ACTIONS


def _is_expected_debug_only_activity(activity: ET.Element) -> bool:
    """Allow the filterless Compose preview/test hosts only in debug APKs."""
    return (
        activity.get(ANDROID + "exported") == "true"
        and activity.get(ANDROID + "permission") is None
        and activity.get(ANDROID + "enabled") in (None, "true")
        and not activity.findall("intent-filter")
    )


def verify_manifest(manifest: str, variant: str = "release") -> str:
    """Validate app security invariants and return the package name."""
    if variant not in {"debug", "release"}:
        raise ValueError("variant must be 'debug' or 'release'")
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
        elif name in DEBUG_ONLY_ACTIVITIES:
            if variant != "debug":
                raise RuntimeError(f"Debug-only activity must not be packaged in release: {name}")
            if not _is_expected_debug_only_activity(activity):
                raise RuntimeError(f"Debug-only activity is not safely configured: {name}")
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
            if name != PROFILE_INSTALL_RECEIVER or not _is_expected_profile_install_receiver(receiver):
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


def verify(apk: Path, variant: str) -> None:
    sdk = Path(os.environ["ANDROID_HOME"])
    analyzer = sdk / "cmdline-tools/latest/bin/apkanalyzer"
    manifest = subprocess.check_output(
        [str(analyzer), "manifest", "print", str(apk)], text=True
    )
    package = verify_manifest(manifest, variant)

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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--variant", choices=("debug", "release"), required=True)
    arguments = parser.parse_args()
    verify(arguments.apk, arguments.variant)
