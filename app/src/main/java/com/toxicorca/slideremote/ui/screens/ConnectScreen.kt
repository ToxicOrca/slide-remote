package com.toxicorca.slideremote.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.toxicorca.slideremote.app
import com.toxicorca.slideremote.hid.HidState
import com.toxicorca.slideremote.hid.HostDevice
import com.toxicorca.slideremote.ui.components.SectionCard
import com.toxicorca.slideremote.ui.theme.Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val hid = context.app.hid
    val status by hid.status.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }

    val runtimePermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hid.start()
        refresh++
    }
    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hid.start()
    }
    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh++
    }

    fun hasAdvertise(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) ==
            PackageManager.PERMISSION_GRANTED

    val paired = remember(status, refresh) { hid.pairedDevices() }
    val phoneName = remember(status, refresh) { hid.phoneName() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Connect to a computer") },
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
            StatusHeader(status.state, status.hostName, status.message)

            when (status.state) {
                HidState.NO_PERMISSION -> SectionCard {
                    Text("Bluetooth permission needed", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "The app needs the Nearby devices permission to act as a Bluetooth keyboard.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { permissionLauncher.launch(runtimePermissions) }) { Text("Allow") }
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) { Text("App settings") }
                    }
                }

                HidState.OFF -> Unit

                HidState.BLUETOOTH_OFF -> SectionCard {
                    Text("Bluetooth is off", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = {
                        runCatching { enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                    }) { Text("Turn on Bluetooth") }
                }

                HidState.UNSUPPORTED -> SectionCard {
                    Text("This phone can't act as a keyboard", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Some manufacturers disable the Bluetooth HID device profile. There's no workaround in the app; " +
                            "a different phone is needed.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { hid.enable() }) { Text("Try again") }
                }

                else -> {
                    SectionCard {
                        Text("First time? Pair from the computer", style = MaterialTheme.typography.titleMedium)
                        Step(1, "Make this phone visible for 2 minutes.")
                        Button(
                            onClick = {
                                if (!hasAdvertise()) {
                                    permissionLauncher.launch(runtimePermissions)
                                } else {
                                    runCatching {
                                        discoverable.launch(
                                            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.padding(start = 34.dp),
                        ) { Text("Make visible") }
                        Step(
                            2,
                            "On Windows: Settings → Bluetooth & devices → Add device → Bluetooth. " +
                                "On a Mac: System Settings → Bluetooth."
                        )
                        Step(3, "Pick \"${phoneName ?: "this phone"}\" and accept the pairing code on both sides.")
                        Step(4, "It shows up on the PC as a keyboard. The status above turns green.")
                    }

                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Paired devices", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { refresh++ }) { Text("Refresh") }
                        }
                        if (paired.isEmpty()) {
                            Text(
                                "Nothing paired yet. Follow the steps above.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        paired.forEachIndexed { i, device ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            DeviceRow(
                                device = device,
                                connected = status.state == HidState.CONNECTED && status.hostAddress == device.address,
                                connecting = status.state == HidState.CONNECTING && status.hostAddress == device.address,
                                enabled = status.state == HidState.READY || status.state == HidState.CONNECTED,
                                onConnect = { hid.connect(device.address) },
                                onDisconnect = { hid.disconnect() },
                            )
                        }
                    }
                }
            }

            SectionCard {
                Text("Tips", style = MaterialTheme.typography.titleMedium)
                Tip("Start the slideshow before tapping slides. Outside the show, numbers get typed onto your slide.")
                Tip("Only one keyboard app can use Bluetooth at a time. Close other keyboard apps first.")
                Tip("If the PC keeps the old pairing but won't connect, remove the phone from the PC's Bluetooth list and pair again.")
            }

            if (status.state == HidState.READY || status.state == HidState.CONNECTED || status.state == HidState.CONNECTING) {
                TextButton(onClick = { hid.shutdown() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Turn off keyboard mode", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (status.state == HidState.IDLE || status.state == HidState.OFF) {
                OutlinedButton(onClick = { hid.enable() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Start keyboard mode")
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun StatusHeader(state: HidState, hostName: String?, message: String?) {
    val (icon, color, title, subtitle) = when (state) {
        HidState.CONNECTED -> Quad(Icons.Filled.BluetoothConnected, Palette.Connected, "Connected", hostName ?: "Computer")
        HidState.CONNECTING -> Quad(Icons.Filled.Bluetooth, Palette.Amber, "Connecting…", hostName ?: "")
        HidState.READY -> Quad(Icons.Filled.Bluetooth, Palette.Accent, "Ready", "Waiting for a computer to connect")
        HidState.IDLE -> Quad(Icons.Filled.Bluetooth, Palette.TextDim, "Starting…", "Setting up the keyboard profile")
        HidState.OFF -> Quad(Icons.Filled.BluetoothDisabled, Palette.TextDim, "Keyboard mode off", "Other keyboard apps can use Bluetooth now")
        HidState.BLUETOOTH_OFF -> Quad(Icons.Filled.BluetoothDisabled, Palette.Danger, "Bluetooth off", "")
        HidState.NO_PERMISSION -> Quad(Icons.Filled.BluetoothDisabled, Palette.Amber, "Permission needed", "")
        HidState.UNSUPPORTED -> Quad(Icons.Filled.BluetoothDisabled, Palette.Danger, "Not supported", "")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (message != null) {
                Text(message, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private data class Quad(val icon: ImageVector, val color: Color, val title: String, val subtitle: String)

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                n.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Tip(text: String) {
    Row {
        Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeviceRow(
    device: HostDevice,
    connected: Boolean,
    connecting: Boolean,
    enabled: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (device.isComputer) Icons.Filled.Computer else Icons.Filled.Devices,
            contentDescription = null,
            tint = if (connected) Palette.Connected else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    connected -> "Connected"
                    connecting -> "Connecting…"
                    device.isComputer -> "Computer"
                    else -> "Other device"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (connected) {
            OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
        } else {
            Button(onClick = onConnect, enabled = enabled && !connecting) { Text("Connect") }
        }
    }
}
