package com.toxicorca.slideremote.hid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.toxicorca.slideremote.BuildConfig
import com.toxicorca.slideremote.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

enum class HidState {
    /** This phone doesn't expose the Bluetooth HID Device profile. */
    UNSUPPORTED,
    BLUETOOTH_OFF,
    NO_PERMISSION,
    /** The user turned keyboard mode off. */
    OFF,
    /** Starting up / not registered yet. */
    IDLE,
    /** Registered as a keyboard, no computer connected. */
    READY,
    CONNECTING,
    CONNECTED,
}

data class HidStatus(
    val state: HidState,
    val hostName: String? = null,
    val hostAddress: String? = null,
    val message: String? = null,
) {
    val isConnected: Boolean get() = state == HidState.CONNECTED
}

data class HostDevice(
    val name: String,
    val address: String,
    val isComputer: Boolean,
)

/**
 * Makes the phone act as a Bluetooth keyboard using [BluetoothHidDevice] (Android 9+).
 *
 * Flow: get the HID_DEVICE profile proxy -> registerApp() with a keyboard SDP record ->
 * the PC pairs with / connects to the phone -> we send 8-byte keyboard reports.
 *
 * Only one app on the phone can be registered as an HID device at a time
 * (other keyboard apps must be closed while this is in use).
 */
@SuppressLint("MissingPermission") // every entry point checks hasPermission() first
class HidKeyboard(
    private val context: Context,
    private val settings: AppSettings,
) {
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    @Volatile private var hid: BluetoothHidDevice? = null
    @Volatile private var host: BluetoothDevice? = null
    /** Address passed to connect(), until it connects or fails. */
    @Volatile private var connectingAddress: String? = null
    @Volatile private var registered = false
    @Volatile private var proxyRequested = false
    /** registerApp() called, waiting for onAppStatusChanged. */
    @Volatile private var registering = false
    /** User turned keyboard mode off; start() does nothing until enable(). */
    @Volatile private var userDisabled = false
    private var proxyTimeout: Job? = null
    private var receiverRegistered = false

    /** Debug builds only: a fake connected PC for screenshots (see src/debug). Bluetooth is left alone. */
    @Volatile private var demoHost: String? = null

    private val _status = MutableStateFlow(HidStatus(HidState.IDLE))
    val status: StateFlow<HidStatus> = _status.asStateFlow()

    private val queue = Channel<List<HidAction>>(Channel.UNLIMITED)

    init {
        scope.launch { runQueue() }
    }

    // ---------------------------------------------------------------- lifecycle

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /** Turn keyboard mode back on after [shutdown]. */
    fun enable() {
        userDisabled = false
        start()
    }

    /** Safe to call repeatedly (after permission grant, Bluetooth on, app start...). */
    @Synchronized
    fun start() {
        if (demoHost != null) return
        registerStateReceiver()
        if (userDisabled) {
            setStatus(HidState.OFF)
            return
        }
        val a = adapter
        if (a == null) {
            setStatus(HidState.UNSUPPORTED, message = "This device has no Bluetooth adapter.")
            return
        }
        when {
            !hasPermission() -> {
                setStatus(HidState.NO_PERMISSION)
                return
            }
            !a.isEnabled -> {
                setStatus(HidState.BLUETOOTH_OFF)
                return
            }
        }

        val proxy = hid
        if (proxy != null) {
            if (!registered && !registering) registerApp(proxy)
            return
        }
        if (proxyRequested) return

        setStatus(HidState.IDLE)
        proxyRequested = true
        val ok = try {
            a.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
        } catch (t: Throwable) {
            false
        }
        if (!ok) {
            proxyRequested = false
            setStatus(
                HidState.UNSUPPORTED,
                message = "This phone doesn't support acting as a Bluetooth keyboard."
            )
            return
        }
        // Some OEM builds accept the request but never deliver the profile.
        proxyTimeout?.cancel()
        proxyTimeout = scope.launch {
            delay(6_000)
            if (hid == null && proxyRequested) {
                proxyRequested = false
                setStatus(
                    HidState.UNSUPPORTED,
                    message = "The Bluetooth keyboard profile didn't respond. Your phone may have it disabled."
                )
            }
        }
    }

    /** Debug builds only: report [hostName] as connected and accept every send without Bluetooth. */
    fun startDemo(hostName: String) {
        if (!BuildConfig.DEBUG) return
        demoHost = hostName
        _status.value = HidStatus(HidState.CONNECTED, hostName, DEMO_ADDRESS)
    }

    /** Fully release the HID registration (e.g. so another keyboard app can use it). */
    @Synchronized
    fun shutdown() {
        userDisabled = true
        val proxy = hid
        try {
            host?.let { proxy?.disconnect(it) }
            if (registered) proxy?.unregisterApp()
            if (proxy != null) adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy)
        } catch (_: Throwable) {
        }
        hid = null
        host = null
        connectingAddress = null
        registered = false
        registering = false
        proxyRequested = false
        // Status first: HidService decides whether to stop from the live status.
        setStatus(HidState.OFF)
        HidService.stop(context)
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            proxyTimeout?.cancel()
            val h = proxy as BluetoothHidDevice
            val previous = hid
            if (previous != null && previous !== h) {
                // A stale proxy from before Bluetooth was toggled; drop the newcomer.
                try {
                    adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, h)
                } catch (_: Throwable) {
                }
                return
            }
            hid = h
            proxyRequested = false
            if (userDisabled) return
            if (!registered && !registering) registerApp(h)
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hid = null
            host = null
            connectingAddress = null
            registered = false
            registering = false
            proxyRequested = false
            when {
                userDisabled -> setStatus(HidState.OFF)
                adapter?.isEnabled == true -> setStatus(HidState.IDLE)
                else -> setStatus(HidState.BLUETOOTH_OFF)
            }
            HidService.stop(context)
        }
    }

    private fun registerApp(h: BluetoothHidDevice) {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Slide Remote Keyboard",
            "Phone presentation remote",
            "ToxicOrca",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            Descriptors.KEYBOARD
        )
        registering = true
        val ok = try {
            h.registerApp(sdp, null, null, callbackExecutor, hidCallback)
        } catch (t: Throwable) {
            false
        }
        if (!ok) {
            registering = false
            setStatus(
                HidState.IDLE,
                message = "Couldn't register as a keyboard. Close other keyboard apps and try again."
            )
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@HidKeyboard.registered = registered
            registering = false
            if (!registered) {
                host = null
                connectingAddress = null
                setStatus(if (userDisabled) HidState.OFF else HidState.IDLE)
                HidService.stop(context)
                return
            }
            // Already connected (e.g. after process restart)?
            val already = try {
                hid?.connectedDevices?.firstOrNull()
            } catch (_: Throwable) {
                null
            }
            if (already != null) {
                onConnected(already)
                return
            }
            setStatus(HidState.READY)
            if (settings.value.autoReconnect) {
                val target = pluggedDevice ?: settings.value.lastDeviceAddress?.let { addr ->
                    runCatching { adapter?.getRemoteDevice(addr) }.getOrNull()
                }
                if (target != null && target.bondState == BluetoothDevice.BOND_BONDED) {
                    try {
                        hid?.connect(target)
                    } catch (_: Throwable) {
                    }
                }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> onConnected(device)
                BluetoothProfile.STATE_CONNECTING -> setStatus(
                    HidState.CONNECTING, device.safeName(), device.address
                )
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val pending = connectingAddress
                    // The old host dropping while we connect to another must not clobber CONNECTING.
                    if (pending != null && pending != device.address) return
                    if (pending == device.address) connectingAddress = null
                    if (host == null || host?.address == device.address) {
                        host = null
                        setStatus(if (registered) HidState.READY else HidState.IDLE)
                        HidService.stop(context)
                    }
                }
                else -> Unit
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            try {
                if (type == BluetoothHidDevice.REPORT_TYPE_INPUT && id.toInt() == Descriptors.KEYBOARD_REPORT_ID) {
                    hid?.replyReport(device, type, id, ByteArray(8))
                } else {
                    hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
                }
            } catch (_: Throwable) {
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            // LED state (NumLock etc). Acknowledge and ignore.
            try {
                hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
            } catch (_: Throwable) {
            }
        }
    }

    private fun onConnected(device: BluetoothDevice) {
        host = device
        connectingAddress = null
        settings.update { it.copy(lastDeviceAddress = device.address) }
        setStatus(HidState.CONNECTED, device.safeName(), device.address)
        HidService.start(context)
    }

    // ---------------------------------------------------------------- hosts

    fun pairedDevices(): List<HostDevice> {
        demoHost?.let { return listOf(HostDevice(it, DEMO_ADDRESS, isComputer = true)) }
        val a = adapter ?: return emptyList()
        if (!hasPermission()) return emptyList()
        return try {
            a.bondedDevices.orEmpty().map { d ->
                HostDevice(
                    name = d.safeName(),
                    address = d.address,
                    isComputer = d.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER
                )
            }.sortedWith(compareByDescending<HostDevice> { it.isComputer }.thenBy { it.name.lowercase() })
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun connect(address: String): Boolean {
        val h = hid ?: return false
        if (!hasPermission()) return false
        return try {
            val device = adapter?.getRemoteDevice(address) ?: return false
            // Set before disconnecting A so A's DISCONNECTED callback is recognised as stale.
            connectingAddress = address
            val current = host
            if (current != null && current.address != address) {
                host = null
                h.disconnect(current)
            }
            setStatus(HidState.CONNECTING, device.safeName(), address)
            val ok = h.connect(device)
            if (!ok) {
                connectingAddress = null
                setStatus(HidState.READY, message = "Couldn't start the connection. Make sure the PC is on and in range.")
            }
            ok
        } catch (_: Throwable) {
            connectingAddress = null
            false
        }
    }

    fun disconnect() {
        val d = host ?: return
        try {
            hid?.disconnect(d)
        } catch (_: Throwable) {
        }
    }

    /** The name a PC will see when adding this phone as a Bluetooth device. */
    fun phoneName(): String? = try {
        if (hasPermission()) adapter?.name else null
    } catch (_: Throwable) {
        null
    }

    // ---------------------------------------------------------------- sending

    /** Queue a keystroke sequence. Returns false (and sends nothing) when no PC is connected. */
    fun send(actions: List<HidAction>): Boolean {
        if (demoHost != null) return true
        if (host == null || hid == null) return false
        return queue.trySend(actions).isSuccess
    }

    private suspend fun runQueue() {
        for (batch in queue) {
            for (action in batch) {
                when (action) {
                    is HidAction.Pause -> delay(action.ms)
                    is HidAction.Key -> {
                        val gap = settings.value.keyDelayMs.toLong().coerceIn(4, 200)
                        if (!report(action.modifiers, action.code)) break
                        delay(gap)
                        report(0, 0)
                        delay(gap)
                    }
                }
            }
        }
    }

    private fun report(modifiers: Int, key: Int): Boolean {
        val h = hid ?: return false
        val d = host ?: return false
        val bytes = byteArrayOf(modifiers.toByte(), 0, key.toByte(), 0, 0, 0, 0, 0)
        return try {
            h.sendReport(d, Descriptors.KEYBOARD_REPORT_ID, bytes)
        } catch (_: Throwable) {
            false
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun BluetoothDevice.safeName(): String =
        try {
            name?.takeIf { it.isNotBlank() } ?: address
        } catch (_: Throwable) {
            address
        }

    private fun setStatus(
        state: HidState,
        hostName: String? = null,
        hostAddress: String? = null,
        message: String? = null,
    ) {
        if (demoHost != null) return
        _status.update { HidStatus(state, hostName, hostAddress, message) }
    }

    private fun registerStateReceiver() {
        if (receiverRegistered) return
        receiverRegistered = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_ON -> start()
                    BluetoothAdapter.STATE_OFF -> {
                        val stale = hid
                        if (stale != null) {
                            try {
                                adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, stale)
                            } catch (_: Throwable) {
                            }
                        }
                        hid = null
                        registering = false
                        host = null
                        connectingAddress = null
                        registered = false
                        proxyRequested = false
                        setStatus(HidState.BLUETOOTH_OFF)
                        HidService.stop(context)
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            // ACTION_STATE_CHANGED is a protected system broadcast, so exporting is safe.
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private companion object {
        const val DEMO_ADDRESS = "00:00:00:00:00:00"
    }
}
