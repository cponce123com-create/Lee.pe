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
        with self.assertRaisesRegex(RuntimeError, "non-exported"):
            verify_manifest(manifest)

    def test_compose_preview_activity_is_allowed_only_in_debug_and_without_filters(self):
        for name in (
            "androidx.compose.ui.tooling.PreviewActivity",
            "androidx.activity.ComponentActivity",
        ):
            with self.subTest(name=name):
                activity = f'''        <activity android:name="{name}" android:exported="true" />
'''
                manifest = BASE_MANIFEST.replace(
                    "    </application>", activity + "    </application>"
                )
                self.assertEqual("pe.lee.app", verify_manifest(manifest, variant="debug"))
                with self.assertRaisesRegex(RuntimeError, "must not be packaged in release"):
                    verify_manifest(manifest, variant="release")

                broad_activity = activity.replace(
                    'android:exported="true" />',
                    'android:exported="true"><intent-filter><action android:name="android.intent.action.VIEW" /></intent-filter></activity>',
                )
                broad_manifest = BASE_MANIFEST.replace(
                    "    </application>", broad_activity + "    </application>"
                )
                with self.assertRaisesRegex(RuntimeError, "not safely configured"):
                    verify_manifest(broad_manifest, variant="debug")

    def test_allows_only_the_documented_hidden_media3_bluetooth_activity(self):
        activity = """        <activity
            android:name="androidx.media3.session.BluetoothValidationActivity"
            android:exported="true" android:excludeFromRecents="true" android:noHistory="true"
            android:permission="android.permission.BLUETOOTH_PRIVILEGED"
            android:theme="@android:style/Theme.NoDisplay">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="content" />
                <data android:host="media" />
                <data android:pathPrefix="/internal/audio/media/" />
                <data android:mimeType="audio/*" />
            </intent-filter>
        </activity>
"""
        manifest = BASE_MANIFEST.replace(
            "    </application>", activity + "    </application>"
        )
        self.assertEqual("pe.lee.app", verify_manifest(manifest))


    def test_accepts_packaged_manifest_with_combined_data_attributes(self):
        activity = """        <activity
            android:name="androidx.media3.session.BluetoothValidationActivity"
            android:exported="true" android:excludeFromRecents="true" android:noHistory="true"
            android:permission="android.permission.BLUETOOTH_PRIVILEGED"
            android:theme="@android:style/Theme.NoDisplay">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="content" android:host="media"
                    android:pathPrefix="/internal/audio/media/" android:mimeType="audio/*" />
            </intent-filter>
        </activity>
"""
        manifest = BASE_MANIFEST.replace(
            "    </application>", activity + "    </application>"
        )
        self.assertEqual("pe.lee.app", verify_manifest(manifest))


    def test_accepts_packaged_android_theme_reference(self):
        activity = """        <activity
            android:name="androidx.media3.session.BluetoothValidationActivity"
            android:exported="true" android:excludeFromRecents="true" android:noHistory="true"
            android:permission="android.permission.BLUETOOTH_PRIVILEGED"
            android:theme="@ref/0x1030055">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="content" />
                <data android:host="media" />
                <data android:pathPrefix="/internal/audio/media/" />
                <data android:mimeType="audio/*" />
            </intent-filter>
        </activity>
"""
        manifest = BASE_MANIFEST.replace(
            "    </application>", activity + "    </application>"
        )
        self.assertEqual("pe.lee.app", verify_manifest(manifest))

    def test_rejects_validation_activity_without_system_permission(self):
        activity = """        <activity
            android:name="androidx.media3.session.BluetoothValidationActivity"
            android:exported="true" android:excludeFromRecents="true" android:noHistory="true"
            android:theme="@android:style/Theme.NoDisplay">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <data android:scheme="content" android:host="media"
                    android:pathPrefix="/internal/audio/media/" android:mimeType="audio/*" />
            </intent-filter>
        </activity>
"""
        manifest = BASE_MANIFEST.replace(
            "    </application>", activity + "    </application>"
        )
        with self.assertRaisesRegex(RuntimeError, "not safely configured"):
            verify_manifest(manifest)

    def test_rejects_broadened_media3_bluetooth_activity_filter(self):
        activity = """        <activity
            android:name="androidx.media3.session.BluetoothValidationActivity"
            android:exported="true" android:excludeFromRecents="true" android:noHistory="true"
            android:permission="android.permission.BLUETOOTH_PRIVILEGED"
            android:theme="@android:style/Theme.NoDisplay">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:scheme="content" />
                <data android:host="media" />
                <data android:pathPrefix="/" />
                <data android:mimeType="audio/*" />
            </intent-filter>
        </activity>
"""
        manifest = BASE_MANIFEST.replace(
            "    </application>", activity + "    </application>"
        )
        with self.assertRaisesRegex(RuntimeError, "not safely configured"):
            verify_manifest(manifest)


    def test_accepts_profile_installer_only_with_dump_permission_and_fixed_actions(self):
        receiver = """        <receiver
            android:name="androidx.profileinstaller.ProfileInstallReceiver"
            android:directBootAware="false" android:enabled="true"
            android:exported="true" android:permission="android.permission.DUMP">
            <intent-filter><action android:name="androidx.profileinstaller.action.INSTALL_PROFILE" /></intent-filter>
            <intent-filter><action android:name="androidx.profileinstaller.action.SKIP_FILE" /></intent-filter>
            <intent-filter><action android:name="androidx.profileinstaller.action.SAVE_PROFILE" /></intent-filter>
            <intent-filter><action android:name="androidx.profileinstaller.action.BENCHMARK_OPERATION" /></intent-filter>
        </receiver>
"""
        manifest = BASE_MANIFEST.replace(
            "    </application>", receiver + "    </application>"
        )
        self.assertEqual("pe.lee.app", verify_manifest(manifest))

    def test_rejects_profile_installer_without_system_permission(self):
        receiver = """        <receiver
            android:name="androidx.profileinstaller.ProfileInstallReceiver"
            android:exported="true">
            <intent-filter><action android:name="androidx.profileinstaller.action.INSTALL_PROFILE" /></intent-filter>
            <intent-filter><action android:name="androidx.profileinstaller.action.SKIP_FILE" /></intent-filter>
            <intent-filter><action android:name="androidx.profileinstaller.action.SAVE_PROFILE" /></intent-filter>
            <intent-filter><action android:name="androidx.profileinstaller.action.BENCHMARK_OPERATION" /></intent-filter>
        </receiver>
"""
        manifest = BASE_MANIFEST.replace(
            "    </application>", receiver + "    </application>"
        )
        with self.assertRaisesRegex(RuntimeError, "Exported receiver"):
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
