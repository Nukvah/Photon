package com.nukvah.photon

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nukvah.photon.core.benchmarkEncode
import com.nukvah.photon.core.coreInfo
import com.nukvah.photon.core.ping
import com.nukvah.photon.pipeline.FakePipeline
import com.nukvah.photon.ui.PhotonActions
import com.nukvah.photon.ui.PhotonApp
import com.nukvah.photon.ui.PhotonColors
import com.nukvah.photon.ui.PhotonTheme
import com.nukvah.photon.ui.PhotonViewModel
import com.nukvah.photon.ui.UiState
import com.nukvah.photon.ui.startSampleBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hosts the three Photon screens. Until Stage 4 the pipeline is [FakePipeline] and
 * sharing shows a placeholder message. The gear button opens the developer screen
 * (Rust core status and the jpegli benchmark).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotonTheme {
                val vm: PhotonViewModel = viewModel { PhotonViewModel(FakePipeline()) }
                val state by vm.state.collectAsStateWithLifecycle()
                var showDeveloper by rememberSaveable { mutableStateOf(false) }
                val screen = Modifier.fillMaxSize().background(PhotonColors.Background).safeDrawingPadding()

                if (showDeveloper) {
                    BackHandler { showDeveloper = false }
                    DevScreen(onBack = { showDeveloper = false }, modifier = screen)
                } else {
                    val actions = remember(vm) { Actions(vm) { showDeveloper = true } }
                    PhotonApp(state, vm, actions, screen)
                }
            }
        }
    }

    private inner class Actions(val vm: PhotonViewModel, val onSettings: () -> Unit) : PhotonActions {
        override fun selectPhotos() = vm.startSampleBatch()
        override fun shareEmail(batch: UiState.Done) = comingSoon()
        override fun saveToFolder(batch: UiState.Done) = comingSoon()
        override fun otherApps(batch: UiState.Done) = comingSoon()
        override fun shareOne(batch: UiState.Done, index: Int) = comingSoon()
        override fun openSettings() = onSettings()

        private fun comingSoon() {
            Toast.makeText(this@MainActivity, R.string.coming_soon, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun DevScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val status = remember { loadCore() }
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(
        modifier = modifier.padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.developer), style = MaterialTheme.typography.headlineMedium)
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

private fun loadCore(): CoreStatus = try {
    val info = coreInfo()
    CoreStatus.Loaded(info.version, info.targetArch, info.targetOs, ping("hello from Kotlin"))
} catch (e: Throwable) {
    CoreStatus.Failed(e.message ?: e.javaClass.simpleName)
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
