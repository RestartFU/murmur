package com.restartfu.murmur.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.restartfu.murmur.R
import com.restartfu.murmur.model.ModelManager
import com.restartfu.murmur.service.MurmurAccessibilityService

@Composable
fun SetupScreen() {
    val context = LocalContext.current
    var micGranted by remember { mutableStateOf(false) }
    var serviceEnabled by remember { mutableStateOf(false) }
    val model by ModelManager.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        micGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        serviceEnabled = MurmurAccessibilityService.isEnabled(context)
        ModelManager.refresh(context)
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (!granted) {
            // After a second denial Android stops showing the dialog; send the user to App info.
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
            )
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Murmur", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Talk instead of typing, in any app. A mic button appears above your keyboard; " +
                    "tap it, speak, tap again. Speech is recognised on your phone in 25 languages.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))

            Step(1, "Microphone", done = micGranted) {
                Text("Murmur only listens while the mic button is red.")
                if (!micGranted) {
                    Button(onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("Allow microphone")
                    }
                }
            }

            Step(2, "Speech model", done = model is ModelManager.State.Installed) {
                ModelStep(model)
            }

            Step(3, "Show the mic above your keyboard", done = serviceEnabled) {
                Text(
                    "Murmur uses Android's accessibility service to see when a keyboard is open, " +
                        "place the mic button above it, and type your words into the focused text field. " +
                        "It does not read, store or send your screen contents or what you type, and " +
                        "nothing leaves your phone.",
                )
                if (!serviceEnabled) {
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text("Open accessibility settings") }
                    Text(
                        "Find Murmur under Downloaded apps and switch it on. If the switch is greyed out, " +
                            "go to App info → ⋮ → Allow restricted settings, then try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Step(4, "Try it", done = false, showDone = false) {
                var text by rememberSaveable { mutableStateOf("") }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Tap here, then tap the mic above the keyboard") },
                    minLines = 3,
                )
            }

            Text(
                "Speech recognition: Parakeet Ultra by Moondream, post-trained from NVIDIA Parakeet TDT 0.6B v3 " +
                    "(CC BY 4.0), running on sherpa-onnx (Apache 2.0).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ModelStep(model: ModelManager.State) {
    val context = LocalContext.current
    when (model) {
        ModelManager.State.NotInstalled -> {
            Text("${ModelManager.DISPLAY_NAME}. A one-time ${mb(ModelManager.ARCHIVE_SIZE)} download; Wi-Fi recommended.")
            Button(onClick = { ModelManager.download(context) }) { Text("Download") }
        }
        is ModelManager.State.Downloading -> {
            val fraction = if (model.total > 0) model.downloaded.toFloat() / model.total else 0f
            Text("Downloading… ${mb(model.downloaded)} of ${mb(model.total)}")
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { ModelManager.cancel(context) }) { Text("Cancel") }
        }
        is ModelManager.State.Installing -> {
            Text("Verifying and unpacking…")
            LinearProgressIndicator(progress = { model.progress }, modifier = Modifier.fillMaxWidth())
        }
        ModelManager.State.Installed -> {
            Text("${ModelManager.DISPLAY_NAME} is ready.")
            TextButton(onClick = { ModelManager.delete(context) }) { Text("Delete model") }
        }
        is ModelManager.State.Failed -> {
            Text(model.message, color = MaterialTheme.colorScheme.error)
            Button(onClick = { ModelManager.download(context) }) { Text("Try again") }
        }
    }
}

@Composable
private fun Step(
    number: Int,
    title: String,
    done: Boolean,
    showDone: Boolean = true,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val badge = if (done && showDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                val onBadge = if (done && showDone) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                Surface(shape = CircleShape, color = badge, modifier = Modifier.size(28.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (done && showDone) "✓" else "$number", color = onBadge, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                if (number == 4) {
                    Icon(painterResource(R.drawable.ic_mic), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (!(done && showDone) || number == 2) content()
        }
    }
}

private fun mb(bytes: Long) = "${bytes / 1_000_000} MB"
