package pe.lee.app

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
        }
    }
}
