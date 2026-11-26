package com.nukvah.photon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nukvah.photon.core.benchmarkEncode
import com.nukvah.photon.core.coreInfo
import com.nukvah.photon.core.ping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Developer screen for Stages 0 and 1: proves the app can load the Rust core and
 * measures the jpegli encoder on this phone. The real screens arrive in Stage 3.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val status = loadCore()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    DevScreen(status, Modifier.padding(padding))
                }
            }
        }
    }

    private fun loadCore(): CoreStatus = try {
        val info = coreInfo()
        CoreStatus.Loaded(info.version, info.targetArch, info.targetOs, ping("hello from Kotlin"))
    } catch (e: Throwable) {
        CoreStatus.Failed(e.message ?: e.javaClass.simpleName)
    }
}

@Composable
private fun DevScreen(status: CoreStatus, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(
        modifier = modifier.padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Photon", style = MaterialTheme.typography.headlineMedium)
        Text(status.summary(), style = MaterialTheme.typography.bodyLarge)
        if (status is CoreStatus.Loaded) {
            Text(status.ping, style = MaterialTheme.typography.bodyMedium)
            Button(
                enabled = !running,
                onClick = {
                    running = true
                    scope.launch {
                        results = withContext(Dispatchers.Default) { runBenchmarks() }
                        running = false
                    }
                },
            ) {
                Text(if (running) "Encoding…" else "Run jpegli benchmark")
            }
            results.forEach { line ->
                Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Encodes the synthetic test image at each preset size; one line per run. */
private fun runBenchmarks(): List<String> = BenchmarkCases.map { case ->
    try {
        val b = benchmarkEncode(case.width, case.height, case.distance, 5u)
        formatBenchmark(case.name, b.width, b.height, b.distance, b.bytes, b.encodeMs, b.fingerprint)
    } catch (e: Throwable) {
        "${case.name}: failed (${e.message})"
    }
}
