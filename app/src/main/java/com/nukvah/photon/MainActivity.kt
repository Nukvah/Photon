package com.nukvah.photon

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nukvah.photon.core.benchmarkEncode
import com.nukvah.photon.core.coreInfo
import com.nukvah.photon.core.ping
import com.nukvah.photon.pipeline.RustPipeline
import com.nukvah.photon.system.AndroidPhotoReader
import com.nukvah.photon.system.AndroidThumbnails
import com.nukvah.photon.system.EmailApps
import com.nukvah.photon.system.FileBatchStore
import com.nukvah.photon.system.Sharing
import com.nukvah.photon.system.describe
import com.nukvah.photon.system.saveToTree
import com.nukvah.photon.system.sharedImageUris
import com.nukvah.photon.ui.AppChoice
import com.nukvah.photon.ui.EmailAppSheet
import com.nukvah.photon.ui.LocalThumbnails
import com.nukvah.photon.ui.PhotonActions
import com.nukvah.photon.ui.PhotonApp
import com.nukvah.photon.ui.PhotonColors
import com.nukvah.photon.ui.PhotonTheme
import com.nukvah.photon.ui.PhotonViewModel
import com.nukvah.photon.ui.SettingsScreen
import com.nukvah.photon.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hosts the three Photon screens. Photos arrive from the share sheet (straight to
 * Processing) or the photo picker; results leave through FileProvider share intents
 * or a folder the person picks. The gear opens Settings (email app, developer tools).
 */
class MainActivity : ComponentActivity() {

    private val vm: PhotonViewModel by viewModels {
        viewModelFactory {
            initializer {
                val store = FileBatchStore(applicationContext)
                val reader = AndroidPhotoReader(applicationContext.contentResolver)
                PhotonViewModel(RustPipeline(reader, store::newBatchDir), store)
            }
        }
    }

    private val thumbnails by lazy { AndroidThumbnails(applicationContext.contentResolver) }
    private val sharing by lazy { Sharing(this) }
    private val emailApps by lazy { EmailApps(this) }

    /** Open email app picker: the apps, and the batch to send once one is picked (null from Settings). */
    private var emailPicker by mutableStateOf<Pair<List<AppChoice>, UiState.Done?>?>(null)
    private var emailAppLabel by mutableStateOf<String?>(null)

    private val pickPhotos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        startWith(uris)
    }

    /** The batch waiting for the folder picker to answer; lost if Android ends the app meanwhile. */
    private var pendingSave: UiState.Done? = null

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val batch = pendingSave
        pendingSave = null
        if (tree != null && batch != null) saveTo(tree, batch)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A share starts processing at once; a recreated activity (rotation) must not start it again.
        if (savedInstanceState == null) startWith(intent.sharedImageUris())
        addOnNewIntentListener { startWith(it.sharedImageUris()) }

        setContent {
            PhotonTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                var showSettings by rememberSaveable { mutableStateOf(false) }
                val screen = Modifier.fillMaxSize().background(PhotonColors.Background).safeDrawingPadding()

                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        emailApp = emailAppLabel,
                        onChangeEmailApp = { emailPicker = emailApps.installed() to null },
                        onBack = { showSettings = false },
                        modifier = screen,
                    ) { DeveloperSection(state.lastFinishedBatch()) }
                } else {
                    val actions = remember { Actions { showSettings = true } }
                    CompositionLocalProvider(LocalThumbnails provides thumbnails) {
                        PhotonApp(state, vm, actions, screen)
                    }
                }
                emailPicker?.let { (apps, batch) ->
                    EmailAppSheet(
                        apps = apps,
                        current = emailApps.chosen,
                        onPick = { app ->
                            emailPicker = null
                            emailApps.chosen = app.id
                            emailAppLabel = app.label
                            batch?.let { sendEmail(it, app.id) }
                        },
                        onDismiss = { emailPicker = null },
                    )
                }
            }
        }
    }

    /** Names and sizes come from quick provider queries, so the first frame already shows Processing. */
    private fun startWith(uris: List<Uri>) {
        if (uris.isEmpty()) return
        vm.start(uris.map { contentResolver.describe(it) })
    }

    /**
     * Share via email: the remembered app, the only one, or Photon's own picker.
     * Android's share sheet is not used, because phone makers change it: some open
     * their own email app, some hide the others behind a sideways scroll.
     */
    private fun emailBatch(batch: UiState.Done) {
        val apps = emailApps.installed()
        val chosen = emailApps.chosen?.takeIf { id -> apps.any { it.id == id } }
        when {
            chosen != null -> sendEmail(batch, chosen)
            apps.isEmpty() -> sharing.otherApps(batch.results)
            apps.size == 1 -> sendEmail(batch, apps[0].id)
            else -> emailPicker = apps to batch
        }
    }

    private fun sendEmail(batch: UiState.Done, emailApp: String) {
        try {
            sharing.email(batch.results, emailApp)
        } catch (e: ActivityNotFoundException) {
            // Uninstalled or disabled since it was picked: ask again next time.
            emailApps.chosen = null
            sharing.otherApps(batch.results)
        }
    }

    private fun currentEmailAppLabel(): String? {
        val id = emailApps.chosen ?: return null
        return runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(id, 0)).toString() }.getOrNull()
    }

    private fun saveTo(tree: Uri, batch: UiState.Done) {
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { contentResolver.saveToTree(tree, batch.results) }
            val message = if (saved == batch.results.size) {
                resources.getQuantityString(R.plurals.saved_to_folder, saved, saved)
            } else {
                getString(R.string.save_failed, saved, batch.results.size)
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private inner class Actions(val onSettings: () -> Unit) : PhotonActions {
        override fun selectPhotos() =
            pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

        override fun shareEmail(batch: UiState.Done) = whenAny(batch) { emailBatch(batch) }
        override fun otherApps(batch: UiState.Done) = whenAny(batch) { sharing.otherApps(batch.results) }
        override fun shareOne(batch: UiState.Done, index: Int) = sharing.otherApps(listOf(batch.results[index]))
        override fun saveToFolder(batch: UiState.Done) = whenAny(batch) {
            pendingSave = batch
            pickFolder.launch(null)
        }

        override fun openSettings() {
            emailAppLabel = currentEmailAppLabel()
            onSettings()
        }

        private fun whenAny(batch: UiState.Done, block: () -> Unit) {
            if (batch.results.isNotEmpty()) block()
        }
    }
}

@Composable
private fun DeveloperSection(lastBatch: UiState.Done?) {
    val status = remember { loadCore() }
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(
        modifier = Modifier.padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.developer), style = MaterialTheme.typography.titleMedium)
        if (lastBatch != null && lastBatch.elapsedMillis > 0) {
            val count = lastBatch.results.size + lastBatch.failedCount
            val seconds = lastBatch.elapsedMillis / 1000.0
            Text(
                stringResource(R.string.last_batch_timing, count, seconds, seconds / count.coerceAtLeast(1)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
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

private fun UiState.lastFinishedBatch(): UiState.Done? = when (this) {
    is UiState.Done -> this
    is UiState.Idle -> lastBatch
    is UiState.Processing -> null
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
