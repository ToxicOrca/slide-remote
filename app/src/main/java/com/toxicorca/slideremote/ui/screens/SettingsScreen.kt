package com.toxicorca.slideremote.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.toxicorca.slideremote.app
import com.toxicorca.slideremote.data.StepMode
import com.toxicorca.slideremote.ui.components.SectionCard
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val appSettings = LocalContext.current.app.settings
    val s by appSettings.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard {
                Text("Next / Previous buttons", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(
                    selected = s.stepMode == StepMode.JUMP,
                    title = "Jump by slide number (recommended)",
                    subtitle = "Always lands on the exact slide. Skips build animations.",
                ) { appSettings.update { it.copy(stepMode = StepMode.JUMP) } }
                ChoiceRow(
                    selected = s.stepMode == StepMode.ARROW,
                    title = "Arrow keys",
                    subtitle = "Plays animations step by step, but slide tracking can drift on animated slides.",
                ) { appSettings.update { it.copy(stepMode = StepMode.ARROW) } }
            }

            SectionCard {
                Text("Presenter", style = MaterialTheme.typography.titleMedium)
                SwitchRow(
                    title = "Volume buttons change slides",
                    subtitle = "Volume down = next, volume up = previous (presenter screen only).",
                    checked = s.volumeKeys,
                ) { v -> appSettings.update { it.copy(volumeKeys = v) } }
                SwitchRow(
                    title = "Vibrate on send",
                    subtitle = "A short tap each time a command goes out.",
                    checked = s.haptics,
                ) { v -> appSettings.update { it.copy(haptics = v) } }
                SwitchRow(
                    title = "Confirm before sending when the show isn't started",
                    subtitle = "Outside the slideshow, slide keys type into PowerPoint and Enter adds blank slides.",
                    checked = s.confirmBeforeShow,
                ) { v -> appSettings.update { it.copy(confirmBeforeShow = v) } }
            }

            SectionCard {
                Text("Keyboard", style = MaterialTheme.typography.titleMedium)
                SwitchRow(
                    title = "Use numpad digits",
                    subtitle = "Off sends the top-row number keys, which work regardless of NumLock.",
                    checked = s.numpadDigits,
                ) { v -> appSettings.update { it.copy(numpadDigits = v) } }
                SwitchRow(
                    title = "Reconnect automatically",
                    subtitle = "Reconnect to the last computer when the app starts.",
                    checked = s.autoReconnect,
                ) { v -> appSettings.update { it.copy(autoReconnect = v) } }

                var keyDelay by remember(s.keyDelayMs) { mutableFloatStateOf(s.keyDelayMs.toFloat()) }
                SliderRow(
                    title = "Key press length",
                    valueLabel = "${keyDelay.roundToInt()} ms",
                    subtitle = "Raise this if the PC misses digits.",
                    value = keyDelay,
                    range = 6f..80f,
                    onChange = { keyDelay = it },
                    onDone = { appSettings.update { it.copy(keyDelayMs = keyDelay.roundToInt()) } },
                )

                var openDelay by remember(s.showOpenDelayMs) { mutableFloatStateOf(s.showOpenDelayMs.toFloat()) }
                SliderRow(
                    title = "Slideshow open delay",
                    valueLabel = "${openDelay.roundToInt()} ms",
                    subtitle = "Used by \"From here\": how long to wait after F5 before jumping.",
                    value = openDelay,
                    range = 300f..3000f,
                    onChange = { openDelay = it },
                    onDone = { appSettings.update { it.copy(showOpenDelayMs = openDelay.roundToInt()) } },
                )
            }

            SectionCard {
                Text("How it works", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Your phone pairs with the PC as a regular Bluetooth keyboard, so nothing needs installing on the PC. " +
                        "During a PowerPoint slideshow, typing a slide number and pressing Enter jumps straight to that slide. " +
                        "That's what each tile sends.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "The app tracks the slide it last sent. If you move with the mouse or a clicker, long-press the right tile " +
                        "and choose \"Mark as current\".",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = onDone, valueRange = range)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
