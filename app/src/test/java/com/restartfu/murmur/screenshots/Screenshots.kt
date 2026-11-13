package com.restartfu.murmur.screenshots

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.restartfu.murmur.R
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.takahirom.roborazzi.captureRoboImage
import com.restartfu.murmur.model.ModelManager
import com.restartfu.murmur.overlay.MicButtonView
import com.restartfu.murmur.overlay.OverlayController
import com.restartfu.murmur.ui.MurmurTheme
import com.restartfu.murmur.ui.SetupActions
import com.restartfu.murmur.ui.SetupContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the setup screen and the overlay to docs/screenshots. Run with
 * `./gradlew testDebugUnitTest -Pscreenshots`.
 *
 * The overlay shots use the real [MicButtonView] on top of a drawn stand-in for a chat
 * app and a keyboard, since other apps' windows can't be rendered on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h892dp-xxhdpi")
class Screenshots {
    @get:Rule val compose = createComposeRule()

    private val out = File(System.getProperty("user.dir"), "../docs/screenshots").canonicalFile

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { MurmurTheme(content) }
        compose.onRoot().captureRoboImage(File(out, "$name.png").path)
    }

    private fun setup(name: String, mic: Boolean, service: Boolean, model: ModelManager.State) =
        capture(name) { SetupContent(mic, service, model, SetupActions()) }

    @Test fun setupFresh() = setup("setup-fresh", false, false, ModelManager.State.NotInstalled)

    @Test @Config(qualifiers = "+night")
    fun setupFreshDark() = setup("setup-fresh-dark", false, false, ModelManager.State.NotInstalled)

    @Test fun setupDownloading() = setup(
        "setup-downloading", true, false,
        ModelManager.State.Downloading(downloaded = 212_000_000, total = ModelManager.ARCHIVE_SIZE),
    )

    @Test fun setupReady() = setup("setup-ready", true, true, ModelManager.State.Installed)

    @Test fun brand() = capture("brand") { Brand() }

    @Test fun overlayIdle() = overlay("overlay-idle", MicButtonView.State.IDLE)

    @Test fun overlayRecording() = overlay("overlay-recording", MicButtonView.State.RECORDING)

    @Test fun overlayTranscribing() = overlay("overlay-transcribing", MicButtonView.State.TRANSCRIBING)

    @Test @Config(qualifiers = "+night")
    fun overlayRecordingDark() = overlay("overlay-recording-dark", MicButtonView.State.RECORDING)

    private fun overlay(name: String, state: MicButtonView.State) = capture(name) {
        val dark = isSystemInDarkTheme()
        val density = LocalDensity.current
        val keyboardHeight = 270.dp
        var field by remember { mutableStateOf<Rect?>(null) }
        var screen by remember { mutableStateOf(IntSize.Zero) }
        Box(Modifier.fillMaxSize().onSizeChanged { screen = it }) {
            ChatApp(dark, keyboardHeight, onFieldPlaced = { field = it })
            Keyboard(dark, keyboardHeight, Modifier.align(Alignment.BottomCenter))
            // Placed with the same maths OverlayController uses on a device.
            val size = with(density) { 64.dp.roundToPx() }
            val keyboardTop = screen.height - with(density) { keyboardHeight.roundToPx() }
            val y = OverlayController.dockY(
                keyboardTop = keyboardTop,
                fieldTop = field?.top?.toInt(),
                fieldBottom = field?.bottom?.toInt(),
                buttonSize = size,
                gap = with(density) { 4.dp.roundToPx() },
                density = density.density,
            )
            val x = screen.width - size - with(density) { 8.dp.roundToPx() }
            AndroidView(
                factory = { ctx ->
                    MicButtonView(ctx).apply {
                        this.state = state
                        if (state == MicButtonView.State.RECORDING) repeat(12) { setLevel(0.05f) }
                    }
                },
                modifier = Modifier.offset { IntOffset(x, y) },
            )
        }
    }
}

@Composable
private fun ChatApp(dark: Boolean, keyboardHeight: Dp, onFieldPlaced: (Rect) -> Unit) {
    val bg = if (dark) Color(0xFF111318) else Color(0xFFF7F8FC)
    val onBg = if (dark) Color(0xFFE3E3E8) else Color(0xFF1A1C20)
    val theirs = if (dark) Color(0xFF2B2E36) else Color(0xFFE6E8F0)
    val mine = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
            .padding(bottom = keyboardHeight),
    ) {
        Spacer(Modifier.height(40.dp))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).background(Color(0xFF7E6BC4), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("S", color = Color.White, fontWeight = FontWeight.SemiBold) }
            Text("Sam", color = onBg, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
            Bubble("Are we still on for dinner tonight?", theirs, onBg, Alignment.Start)
            Bubble("Yes! Booked a table for 7:30", mine, MaterialTheme.colorScheme.onPrimary, Alignment.End)
            Bubble("Perfect. Where is it?", theirs, onBg, Alignment.Start)
            Spacer(Modifier.height(4.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .onGloballyPositioned { onFieldPlaced(it.boundsInRoot()) }
                    .background(theirs, RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 13.dp),
            ) { Text("The little Italian place on|", color = onBg, fontSize = 16.sp) }
            Box(
                Modifier.padding(start = 8.dp).size(46.dp).background(mine, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("➤", color = MaterialTheme.colorScheme.onPrimary, fontSize = 18.sp) }
        }
    }
}

@Composable
private fun Bubble(text: String, color: Color, textColor: Color, align: Alignment.Horizontal) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (align == Alignment.End) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text,
            color = textColor,
            fontSize = 15.sp,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(color, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun Keyboard(dark: Boolean, height: Dp, modifier: Modifier) {
    val bg = if (dark) Color(0xFF1F2024) else Color(0xFFE4E6EB)
    val key = if (dark) Color(0xFF3A3B40) else Color.White
    val special = if (dark) Color(0xFF2C2D31) else Color(0xFFCDD0D8)
    val label = if (dark) Color(0xFFE8E8EA) else Color(0xFF202124)
    val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    Column(
        modifier.fillMaxWidth().height(height).background(bg).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            listOf("the", "Main", "corner").forEach { Text(it, color = label, fontSize = 15.sp) }
        }
        rows.forEachIndexed { i, row ->
            Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = if (i == 1) 18.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (i == 2) Key("⇧", special, label, 1.5f)
                row.forEach { Key(it.toString(), key, label, 1f) }
                if (i == 2) Key("⌫", special, label, 1.5f)
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Key("?123", special, label, 1.5f)
            Key(",", key, label, 1f)
            Key("", key, label, 5f)
            Key(".", key, label, 1f)
            Key("↵", special, label, 1.5f)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Key(text: String, color: Color, label: Color, weight: Float) {
    Box(
        Modifier.weight(weight).fillMaxSize().background(color, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = label, fontSize = 18.sp) }
}

/** The launcher icon under a few launcher masks, and the button in each state, enlarged. */
@Composable
private fun Brand() {
    val bg = Color(0xFFF2F0F7)
    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Launcher icon", fontSize = 15.sp, color = Color(0xFF49454F))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            LauncherIcon(CircleShape, 112.dp)
            LauncherIcon(RoundedCornerShape(30.dp), 112.dp)
            // Themed (monochrome) icon on Android 13+
            Box(
                Modifier.size(112.dp).clip(CircleShape).background(Color(0xFFDCE1FF)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(R.drawable.ic_launcher_foreground), null,
                    colorFilter = ColorFilter.tint(Color(0xFF1F3269)),
                    modifier = Modifier.requiredSize(112.dp * 108 / 72),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf(48.dp, 40.dp, 32.dp).forEach { LauncherIcon(CircleShape, it) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Dictation button: idle, recording, transcribing", fontSize = 15.sp, color = Color(0xFF49454F))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(MicButtonView.State.IDLE, MicButtonView.State.RECORDING, MicButtonView.State.TRANSCRIBING).forEach { st ->
                Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { ctx ->
                            MicButtonView(ctx).apply {
                                state = st
                                if (st == MicButtonView.State.RECORDING) repeat(12) { setLevel(0.04f) }
                            }
                        },
                        modifier = Modifier.scale(1.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LauncherIcon(shape: androidx.compose.ui.graphics.Shape, size: Dp) {
    // Adaptive icons are 108dp with the visible area being the middle 72dp.
    Box(Modifier.size(size).clip(shape).background(Color(0xFF17141F)), contentAlignment = Alignment.Center) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground), null,
            modifier = Modifier.requiredSize(size * 108 / 72),
        )
    }
}
