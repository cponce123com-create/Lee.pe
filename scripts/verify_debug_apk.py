"""Verify offline permissions, app backup policy, audio-service exposure, and quote cards."""
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile


def verify(apk: Path) -> None:
    sdk = Path(os.environ['ANDROID_HOME'])
    analyzer = sdk / 'cmdline-tools/latest/bin/apkanalyzer'
    manifest = subprocess.check_output([str(analyzer), 'manifest', 'print', str(apk)], text=True)
    root = ET.fromstring(manifest)
    android = '{http://schemas.android.com/apk/res/android}'
    package = root.attrib['package']
    application = root.find('application')
    if application is None or application.get(android + 'allowBackup') != 'false':
        raise RuntimeError('Application must set android:allowBackup="false"')
    forbidden = {'android.permission.INTERNET', 'android.permission.MANAGE_EXTERNAL_STORAGE'}
    permissions = {node.get(android + 'name') for node in root.findall('uses-permission')}
    if permissions & forbidden:
        raise RuntimeError(f'Unexpected permissions: {permissions & forbidden}')
    activity_name = 'pe.lee.feature.sharecard.navigation.ShareCardActivity'
    activities = root.findall('application/activity')
    if not any(a.get(android + 'name') == activity_name and a.get(android + 'exported') == 'false' for a in activities):
        raise RuntimeError('Quote-card activity missing or exported')
    providers = root.findall('application/provider')
    if not any(p.get(android + 'authorities') == package + '.fileprovider'
               and p.get(android + 'exported') == 'false'
               and p.get(android + 'grantUriPermissions') == 'true' for p in providers):
        raise RuntimeError('Quote-card FileProvider missing or unsafe')
    expected_service_action = 'androidx.media3.session.MediaSessionService'
    for suffix in ('.ui.audio.AudioPlaybackService', '.ui.audio.TtsPlaybackService'):
        service = next((s for s in root.findall('application/service')
                        if s.get(android + 'name', '').endswith(suffix)), None)
        if service is None or service.get(android + 'exported') != 'true':
            raise RuntimeError(f'Media session service is missing or incorrectly exported: {suffix}')
        actions = {action.get(android + 'name') for action in service.findall('intent-filter/action')}
        if actions != {expected_service_action}:
            raise RuntimeError(f'Media session service has unexpected actions: {suffix}: {actions}')
        if len(service.findall('intent-filter')) != 1:
            raise RuntimeError(f'Media session service has unexpected intent filters: {suffix}')
    with zipfile.ZipFile(apk) as archive:
        marker = b'Lpe/lee/feature/sharecard/navigation/ShareCardActivity;'
        if not any(marker in archive.read(name) for name in archive.namelist()
                   if name.startswith('classes') and name.endswith('.dex')):
            raise RuntimeError('Quote-card class missing from DEX')
    print(f'APK verified: {apk.name}; package={package}; quote cards present; no INTERNET; backup disabled; media services limited to Media3 session action')


if __name__ == '__main__':
    verify(Path(sys.argv[1]))
