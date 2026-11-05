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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "pe.lee.app"
private const val TIMEOUT = 30_000L

/** Stable fixtures make scrolling and opening exercise actual books instead of an empty catalog. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LibraryBenchmarks {
    @get:Rule
    val benchmark = MacrobenchmarkRule()

    @Before
    fun seedLibrary() {
        val context = InstrumentationRegistry.getInstrumentation().context
        context.startActivity(Intent().apply {
            component = ComponentName(PACKAGE, "$PACKAGE.BenchmarkFixtureActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        check(device.wait(Until.hasObject(By.text("Benchmark fixture ready")), TIMEOUT))
        device.pressHome()
    }

    @Test
    fun startupCold() = benchmark.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        check(device.wait(Until.hasObject(By.res("library_catalog")), TIMEOUT))
    }

    @Test
    fun libraryScroll() = benchmark.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = 5,
        setupBlock = {
            killProcess()
            startActivityAndWait()
            check(device.wait(Until.hasObject(By.desc("Benchmark 00")), TIMEOUT))
        },
    ) {
        val catalog = device.findObject(By.res("library_catalog"))
        checkNotNull(catalog)
        catalog.setGestureMargin(device.displayWidth / 5)
        repeat(3) { catalog.fling(Direction.DOWN) }
        repeat(3) { catalog.fling(Direction.UP) }
    }

    @Test
    fun epubOpen() = benchmark.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = 5,
        setupBlock = {
            killProcess()
            startActivityAndWait()
            check(device.wait(Until.hasObject(By.desc("Benchmark 00")), TIMEOUT))
        },
    ) {
        device.findObject(By.desc("Benchmark 00")).click()
        check(device.wait(Until.hasObject(By.clazz("android.webkit.WebView")), TIMEOUT))
        device.waitForIdle()
    }
}
