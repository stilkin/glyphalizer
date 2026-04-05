package be.pocito.glyphsense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import be.pocito.glyphsense.glyph.GlyphController
import be.pocito.glyphsense.glyph.GlyphController.Companion.LED_COUNT_PHONE_3A
import be.pocito.glyphsense.ui.theme.GlyphSenseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlyphSenseTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SpikeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * Spike UI for Task Group 2: test Glyph SDK lifecycle, per-LED brightness control,
 * and measure the max achievable refresh rate on the actual device.
 */
@Composable
fun SpikeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { GlyphController(context) }

    var sessionOpen by remember { mutableStateOf(false) }
    val logLines = remember { mutableStateOf(listOf<String>()) }
    val scrollState = rememberScrollState()

    fun log(line: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        logLines.value = (listOf("[$ts] $line") + logLines.value).take(200)
    }

    DisposableEffect(Unit) {
        log("Device: ${controller.deviceName()}")
        onDispose {
            controller.release()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Glyph SDK Spike",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "Session: ${if (sessionOpen) "OPEN" else "closed"}",
            style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider()

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !sessionOpen,
            onClick = {
                log("init() + openSession() ...")
                controller.init(
                    onReady = {
                        sessionOpen = true
                        log("Session OPEN")
                    },
                    onError = { err -> log("ERROR: $err") },
                )
            },
        ) { Text("Init + Open Session") }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = sessionOpen,
            onClick = {
                controller.release()
                sessionOpen = false
                log("Released")
            },
        ) { Text("Release Session") }

        HorizontalDivider()
        Text("Tests", style = MaterialTheme.typography.titleMedium)

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = sessionOpen,
            onClick = {
                // Light only A_1 at full brightness
                val arr = IntArray(LED_COUNT_PHONE_3A)
                arr[20] = 4095 // A_1 is index 20 per SDK README
                controller.setFrameColors(arr)
                log("Single LED (A_1, idx 20) @ 4095")
            },
        ) { Text("Single LED on (A_1)") }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = sessionOpen,
            onClick = {
                val arr = IntArray(LED_COUNT_PHONE_3A) { 4095 }
                controller.setFrameColors(arr)
                log("All LEDs @ 4095")
            },
        ) { Text("All LEDs full") }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = sessionOpen,
            onClick = {
                // Linear gradient 0..4095 across 36 LEDs to test brightness range
                val arr = IntArray(LED_COUNT_PHONE_3A) { i ->
                    (i * 4095 / (LED_COUNT_PHONE_3A - 1))
                }
                controller.setFrameColors(arr)
                log("Gradient 0..4095 across 36 LEDs")
            },
        ) { Text("Gradient (brightness test)") }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = sessionOpen,
            onClick = {
                // Try lower brightness values to see the range
                val arr = IntArray(LED_COUNT_PHONE_3A) { 255 }
                controller.setFrameColors(arr)
                log("All LEDs @ 255 (low test)")
            },
        ) { Text("All LEDs @ 255 (low)") }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = sessionOpen,
            onClick = {
                controller.turnOff()
                log("turnOff()")
            },
        ) { Text("Turn Off") }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = sessionOpen,
            onClick = {
                val arr = IntArray(LED_COUNT_PHONE_3A)
                for (i in 0..19) arr[i] = 4095
                controller.setFrameColors(arr)
                log("Zone C only (idx 0..19) @ 4095")
            },
        ) { Text("Only zone C (20 LEDs)") }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = sessionOpen,
            onClick = {
                val arr = IntArray(LED_COUNT_PHONE_3A)
                for (i in 20..30) arr[i] = 4095
                controller.setFrameColors(arr)
                log("Zone A only (idx 20..30) @ 4095")
            },
        ) { Text("Only zone A (11 LEDs)") }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = sessionOpen,
            onClick = {
                val arr = IntArray(LED_COUNT_PHONE_3A)
                for (i in 31..35) arr[i] = 4095
                controller.setFrameColors(arr)
                log("Zone B only (idx 31..35) @ 4095")
            },
        ) { Text("Only zone B (5 LEDs)") }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = sessionOpen,
            onClick = {
                scope.launch {
                    log("Walk: lighting idx 0..35, 400ms each")
                    val arr = IntArray(LED_COUNT_PHONE_3A)
                    for (idx in 0 until LED_COUNT_PHONE_3A) {
                        for (i in arr.indices) arr[i] = 0
                        arr[idx] = 4095
                        controller.setFrameColors(arr)
                        log("  lit idx $idx")
                        kotlinx.coroutines.delay(400)
                    }
                    controller.turnOff()
                    log("Walk complete")
                }
            },
        ) { Text("Walk LEDs (slow chase)") }

        HorizontalDivider()
        Text("Benchmark", style = MaterialTheme.typography.titleMedium)

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = sessionOpen,
            onClick = {
                scope.launch {
                    log("Benchmark: running 3s loop...")
                    val result = withContext(Dispatchers.Default) {
                        benchmarkRefreshRate(controller, durationMs = 3000L)
                    }
                    log(
                        "Bench: ${result.frames} frames in ${result.elapsedMs}ms " +
                            "= ${"%.1f".format(result.fps)} fps"
                    )
                    controller.turnOff()
                }
            },
        ) { Text("Benchmark refresh rate (3s)") }

        HorizontalDivider()
        Text("Log", style = MaterialTheme.typography.titleMedium)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            logLines.value.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

private data class BenchmarkResult(
    val frames: Int,
    val elapsedMs: Long,
    val fps: Double,
)

/**
 * Calls setFrameColors in a tight loop for [durationMs] milliseconds.
 * Animates a single-LED "runner" so we can visually confirm something's happening.
 */
private fun benchmarkRefreshRate(
    controller: GlyphController,
    durationMs: Long,
): BenchmarkResult {
    val arr = IntArray(LED_COUNT_PHONE_3A)
    val start = System.nanoTime()
    val deadline = start + durationMs * 1_000_000L
    var frames = 0
    var now = start
    while (now < deadline) {
        val idx = frames % LED_COUNT_PHONE_3A
        // clear previous, light current
        for (i in arr.indices) arr[i] = 0
        arr[idx] = 4095
        controller.setFrameColors(arr)
        frames++
        now = System.nanoTime()
    }
    val elapsedMs = (now - start) / 1_000_000L
    val fps = if (elapsedMs > 0) frames * 1000.0 / elapsedMs else 0.0
    return BenchmarkResult(frames, elapsedMs, fps)
}
