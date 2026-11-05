package pe.lee.app

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Guards the real launcher path, including the library's first composition. */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
    @Test
    fun launcherActivityRemainsVisibleAfterStartupEffects() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertFalse("MainActivity cerró durante el arranque", activity.isFinishing)
                assertTrue("La ventana principal no llegó a mostrarse", activity.window.decorView.isShown)
            }
            if (Build.VERSION.SDK_INT >= 29) {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                instrumentation.uiAutomation.waitForIdle(500, 10_000)
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "library.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LeepeUiPreview")
                }
                val resolver = instrumentation.targetContext.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }
}
