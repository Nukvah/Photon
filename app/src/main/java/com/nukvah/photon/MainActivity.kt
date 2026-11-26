package com.nukvah.photon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nukvah.photon.core.coreInfo
import com.nukvah.photon.core.ping

/**
 * Stage 0 placeholder screen: proves the app can load and call the Rust core.
 * The real Idle, Processing and Done screens arrive in Stage 3.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val status = loadCore()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    CoreStatusScreen(status, Modifier.padding(padding))
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
private fun CoreStatusScreen(status: CoreStatus, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Photon", style = MaterialTheme.typography.headlineMedium)
        Text(status.summary(), style = MaterialTheme.typography.bodyLarge)
        if (status is CoreStatus.Loaded) {
            Text(status.ping, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
