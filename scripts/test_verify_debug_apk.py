"""Unit tests for the APK manifest policy."""
import unittest

from verify_debug_apk import verify_manifest


BASE_MANIFEST = """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="pe.lee.app">
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <application android:allowBackup="false">
        <activity android:name="pe.lee.app.MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <activity android:name="pe.lee.feature.sharecard.navigation.ShareCardActivity"
            android:exported="false" />
        <service android:name="pe.lee.app.ui.audio.AudioPlaybackService" android:exported="true">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService" />
            </intent-filter>
        </service>
        <service android:name="pe.lee.app.ui.audio.TtsPlaybackService" android:exported="true">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService" />
            </intent-filter>
        </service>
        <provider android:name="androidx.core.content.FileProvider"
            android:authorities="pe.lee.app.fileprovider" android:exported="false"
            android:grantUriPermissions="true" />
        <provider android:name="pe.lee.app.BundledFileProvider"
            android:authorities="pe.lee.app.bundled" android:exported="false"
            android:grantUriPermissions="true" />
    </application>
</manifest>"""


class VerifyApkManifestTest(unittest.TestCase):
    def test_accepts_expected_launcher_media_services_and_scoped_providers(self):
        self.assertEqual("pe.lee.app", verify_manifest(BASE_MANIFEST))

    def test_rejects_exported_reader_activity(self):
        manifest = BASE_MANIFEST.replace(
            'android:name="pe.lee.feature.sharecard.navigation.ShareCardActivity"\n            android:exported="false"',
            'android:name="pe.lee.feature.sharecard.navigation.ShareCardActivity"\n            android:exported="true"',
        )
        with self.assertRaisesRegex(RuntimeError, "exported activity"):
            verify_manifest(manifest)

    def test_rejects_exported_receiver(self):
        manifest = BASE_MANIFEST.replace(
            "    </application>",
            """        <receiver android:name="pe.lee.app.UnexpectedReceiver" android:exported="true" />
    </application>""",
        )
        with self.assertRaisesRegex(RuntimeError, "Exported receiver"):
            verify_manifest(manifest)

    def test_rejects_unscoped_file_provider(self):
        manifest = BASE_MANIFEST.replace(
            'android:grantUriPermissions="true"',
            'android:grantUriPermissions="false"',
            1,
        )
        with self.assertRaisesRegex(RuntimeError, "grant scoped URI access"):
            verify_manifest(manifest)

    def test_rejects_internet_and_general_storage_permissions(self):
        for permission in (
            "android.permission.INTERNET",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
        ):
            with self.subTest(permission=permission):
                manifest = BASE_MANIFEST.replace(
                    "    <application",
                    f'    <uses-permission android:name="{permission}" />\n    <application',
                )
                with self.assertRaisesRegex(RuntimeError, "Unexpected permissions"):
                    verify_manifest(manifest)


if __name__ == "__main__":
    unittest.main()
