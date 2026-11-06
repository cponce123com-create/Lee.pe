package pe.lee.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "pe.lee.app"
private const val WAIT_MS = 30_000L
private const val ITERATIONS = 5
private const val SWIPE_PERCENT = .65f

@LargeTest
@RunWith(AndroidJUnit4::class)
class PageTurnBenchmark {
    @get:Rule
    val benchmark = MacrobenchmarkRule()

    @Test
    fun curl() = measurePageTurn("CURL")

    @Test
    fun cut() = measurePageTurn("NONE")

    private fun measurePageTurn(style: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val scale = device.executeShellCommand("settings get global animator_duration_scale").trim()
        check(scale == "null" || (scale.toFloatOrNull() ?: 0f) > 0f) {
            "Enable Android animator duration scale before comparing curl and cut."
        }
        instrumentation.context.startActivity(Intent().apply {
            component = ComponentName(TARGET_PACKAGE, "$TARGET_PACKAGE.BenchmarkFixtureActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("pageTurnStyle", style)
        })
        check(device.wait(Until.hasObject(By.text("Benchmark fixture ready")), WAIT_MS))
        device.pressBack()

        benchmark.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = ITERATIONS,
            startupMode = StartupMode.WARM,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            val book = checkNotNull(device.wait(Until.findObject(By.desc("Benchmark 00")), WAIT_MS))
            book.click()
            val page = checkNotNull(device.wait(Until.findObject(By.clazz("android.webkit.WebView")), WAIT_MS))
            device.waitForIdle()
            page.swipe(Direction.LEFT, SWIPE_PERCENT)
            device.waitForIdle()
        }
    }
}
