package com.k155aravin.bluetrace

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.pow

private const val MAX_TIMESTAMPS_PER_DEVICE = 80
private const val MAX_VISIBLE_DEVICES = 25
private const val UI_REFRESH_MS = 1000L
private const val SCAN_DURATION_MS = 30000L
private const val TOTAL_LOCATIONS = 3
private const val SWEEP_WINDOW_MS = 15 * 60 * 1000L
private const val SCREEN_SCAN = "scan"
private const val SCREEN_TRUSTED = "trusted"
private const val MODE_SWEEP = "sweep"
private const val MODE_TRUST = "trust"
private const val MODE_BASELINE = "baseline"

data class BleDevice(
    val mac: String,
    val name: String,
    val rssi: Int,
    val txPower: Int?,
    val manufacturer: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val timestamps: List<Long>,
    val scanCount: Int,
    val locationsMatched: Int,
    val locations: Set<String>,
    val trusted: Boolean,
    val baselineMatch: Boolean,
)

data class SweepLocation(
    val name: String,
    val deviceCount: Int,
    val timestamp: Long,
)

fun BleDevice.heartbeatMs(): Double? {
    if (timestamps.size < 4) return null
    val intervals = timestamps.zipWithNext { a, b -> b - a }
        .filter { it in 50..10000 }
    if (intervals.size < 3) return null
    return intervals.average()
}

fun BleDevice.distanceMeters(): Double? {
    val tx = txPower ?: -59
    if (rssi == 0 || tx == 0) return null
    return try {
        val ratio = rssi.toDouble() / tx.toDouble()
        val distance = if (ratio < 1.0) ratio.pow(10.0)
        else 0.89976 * ratio.pow(7.7095) + 0.111
        if (distance.isNaN() || distance.isInfinite() || distance < 0.0) null else distance
    } catch (_: Exception) {
        null
    }
}

fun BleDevice.confidenceScore(): Int {
    var score = 0
    score += when {
        locationsMatched >= 3 -> 60
        locationsMatched == 2 -> 30
        else -> 0
    }
    if (heartbeatMs() != null) score += 25
    score += when {
        scanCount > 20 -> 10
        scanCount > 10 -> 5
        else -> 0
    }
    if (txPower != null) score += 8
    if (manufacturer != "Unknown") score += 10
    return score.coerceAtMost(100)
}

fun BleDevice.isConfirmedFollow(): Boolean = locationsMatched >= TOTAL_LOCATIONS

fun BleDevice.isWatchMatch(): Boolean = locationsMatched == 2

fun BleDevice.isAlertMatch(): Boolean = !trusted && !baselineMatch && isConfirmedFollow()

fun BleDevice.isWatchAlert(): Boolean = !trusted && !baselineMatch && isWatchMatch()

fun BleDevice.matchesBaseline(baseline: List<BaselineDevice>): Boolean {
    val heartbeat = heartbeatMs()
    return baseline.any { reference ->
        if (reference.mac == mac) return@any true
        val sameKind = reference.manufacturer != "Unknown" &&
            reference.manufacturer == manufacturer &&
            reference.deviceType == deviceType()
        val closeHeartbeat = heartbeat != null &&
            reference.heartbeatMs != null &&
            kotlin.math.abs(reference.heartbeatMs - heartbeat) <= 120.0
        sameKind && closeHeartbeat
    }
}

fun BleDevice.deviceType(): String {
    val hb = heartbeatMs()?.toInt() ?: return "Unknown device"
    return when (hb) {
        in 50..200 -> "Fast BLE beacon"
        in 450..550 -> "Tile-style tracker"
        in 900..1100 -> "Earbuds / audio"
        in 1200..1400 -> "SmartTag-style tracker"
        in 1800..2200 -> "AirTag-style tracker"
        in 2300..2700 -> "Wearable"
        else -> "Unknown BLE device"
    }
}

private val DarkBg = Color(0xFF0D0D0F)
private val DarkCard = Color(0xFF111114)
private val DarkBorder = Color(0xFF1E1E24)
private val GreenColor = Color(0xFF4ADE80)
private val RedColor = Color(0xFFEF4444)
private val YellowColor = Color(0xFFFACC15)
private val BlueColor = Color(0xFF60A5FA)
private val TextPrimary = Color(0xFFE0E0E0)
private val TextMuted = Color(0xFF8A8A92)

private data class MutableBleDevice(
    val mac: String,
    var name: String,
    var rssi: Int,
    var txPower: Int?,
    var manufacturer: String,
    val firstSeen: Long,
    var lastSeen: Long,
    val timestamps: ArrayDeque<Long> = ArrayDeque(),
    var scanCount: Int = 0,
    val locations: MutableSet<String> = mutableSetOf(),
) {
    fun snapshot(trustedDevices: Set<String>): BleDevice = BleDevice(
        mac = mac,
        name = name,
        rssi = rssi,
        txPower = txPower,
        manufacturer = manufacturer,
        firstSeen = firstSeen,
        lastSeen = lastSeen,
        timestamps = timestamps.toList(),
        scanCount = scanCount,
        locationsMatched = locations.size.coerceAtLeast(1),
        locations = locations.toSet(),
        trusted = mac in trustedDevices,
        baselineMatch = false,
    )
}

class MainActivity : ComponentActivity() {
    private val rawDevices = mutableMapOf<String, MutableBleDevice>()
    private val visibleDevices = mutableStateListOf<BleDevice>()
    private val trustScanRawDevices = mutableMapOf<String, MutableBleDevice>()
    private val trustScanDevices = mutableStateListOf<BleDevice>()
    private val trustedDevices = mutableStateListOf<TrustedDevice>()
    private val baselineRawDevices = mutableMapOf<String, MutableBleDevice>()
    private val baselineDevices = mutableStateListOf<BaselineDevice>()
    private val totalDeviceCount = mutableStateOf(0)
    private val activeScreen = mutableStateOf(SCREEN_SCAN)
    private val isScanning = mutableStateOf(false)
    private val isTrustScanning = mutableStateOf(false)
    private val isBaselineScanning = mutableStateOf(false)
    private val statusText = mutableStateOf("Ready - start your sweep")
    private val locationName = mutableStateOf("")
    private val handler = Handler(Looper.getMainLooper())
    private var scanCount = 0
    private var currentScanLocation = "Location 1"
    private var lastUiRefreshAt = 0L
    private var pendingScanMode = MODE_SWEEP
    private val currentLocationDevices = mutableSetOf<String>()

    // 3-location sweep state
    private val currentStep = mutableStateOf(0) // 0,1,2 = locations; 3 = results
    private val sweepLocations = mutableStateListOf<SweepLocation>()
    private val sessionStart = mutableStateOf(0L)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            when (pendingScanMode) {
                MODE_TRUST -> startTrustScan()
                MODE_BASELINE -> startBaselineScan()
                else -> startBleScan()
            }
        } else {
            statusText.value = "Permissions denied - enable Bluetooth and Location in Settings"
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            recordScanResult(result)
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { recordScanResult(it) }
        }
        override fun onScanFailed(errorCode: Int) {
            handler.post {
                isScanning.value = false
                statusText.value = "Scan failed - error $errorCode"
            }
        }
    }

    private val trustScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            recordTrustScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { recordTrustScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            handler.post { isTrustScanning.value = false }
        }
    }

    private val baselineScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            recordBaselineScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { recordBaselineScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            handler.post { isBaselineScanning.value = false }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadTrustedDevices()
        loadBaselineDevices()
        setContent {
            BlueTraceApp(
                devices = visibleDevices,
                isScanning = isScanning.value,
                isTrustScanning = isTrustScanning.value,
                isBaselineScanning = isBaselineScanning.value,
                statusText = statusText.value,
                locationName = locationName.value,
                totalDeviceCount = totalDeviceCount.value,
                currentStep = currentStep.value,
                sweepLocations = sweepLocations,
                sessionStart = sessionStart.value,
                trustedDevices = trustedDevices,
                baselineDevices = baselineDevices,
                trustScanDevices = trustScanDevices,
                activeScreen = activeScreen.value,
                onLocationChange = { locationName.value = it },
                onScanToggle = { if (isScanning.value) stopBleScan() else checkAndScan() },
                onReset = { resetSweep() },
                onTrustDevice = { trustDevice(it) },
                onTrustScanToggle = { if (isTrustScanning.value) stopTrustScan() else checkAndTrustScan() },
                onBaselineScanToggle = { if (isBaselineScanning.value) stopBaselineScan(saveResults = true) else checkAndBaselineScan() },
                onScreenChange = { activeScreen.value = it },
                onTrustedEnabledChange = { mac, enabled -> setTrustedEnabled(mac, enabled) },
                onClearTrusted = { clearTrustedDevices() },
                onClearBaseline = { clearBaselineDevices() },
            )
        }
    }

    private fun loadTrustedDevices() {
        val saved = getPreferences(Context.MODE_PRIVATE)
            .getString("trusted_devices_json", "[]")
            .orEmpty()
        trustedDevices.clear()
        try {
            val array = JSONArray(saved)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                trustedDevices.add(
                    TrustedDevice(
                        mac = item.getString("mac"),
                        name = item.optString("name", "Trusted device"),
                        deviceType = item.optString("deviceType", "Unknown device"),
                        addedAt = item.optLong("addedAt", System.currentTimeMillis()),
                        enabled = item.optBoolean("enabled", true),
                    )
                )
            }
        } catch (_: Exception) {
            // Legacy fallback from the first whitelist version.
            val legacy = getPreferences(Context.MODE_PRIVATE)
                .getStringSet("trusted_devices", emptySet())
                .orEmpty()
            trustedDevices.addAll(
                legacy.map {
                    TrustedDevice(
                        mac = it,
                        name = "Trusted device",
                        deviceType = "Unknown device",
                        addedAt = System.currentTimeMillis(),
                        enabled = true,
                    )
                }
            )
            saveTrustedDevices()
        }
    }

    private fun saveTrustedDevices() {
        val array = JSONArray()
        trustedDevices.forEach {
            array.put(
                JSONObject()
                    .put("mac", it.mac)
                    .put("name", it.name)
                    .put("deviceType", it.deviceType)
                    .put("addedAt", it.addedAt)
                    .put("enabled", it.enabled)
            )
        }
        getPreferences(Context.MODE_PRIVATE)
            .edit()
            .putString("trusted_devices_json", array.toString())
            .apply()
    }

    private fun loadBaselineDevices() {
        val saved = getPreferences(Context.MODE_PRIVATE)
            .getString("baseline_devices_json", "[]")
            .orEmpty()
        baselineDevices.clear()
        try {
            val array = JSONArray(saved)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                baselineDevices.add(
                    BaselineDevice(
                        mac = item.getString("mac"),
                        name = item.optString("name", "Reference device"),
                        manufacturer = item.optString("manufacturer", "Unknown"),
                        deviceType = item.optString("deviceType", "Unknown device"),
                        heartbeatMs = if (item.isNull("heartbeatMs")) null else item.optDouble("heartbeatMs"),
                        addedAt = item.optLong("addedAt", System.currentTimeMillis()),
                    )
                )
            }
        } catch (_: Exception) {
            baselineDevices.clear()
            saveBaselineDevices()
        }
    }

    private fun saveBaselineDevices() {
        val array = JSONArray()
        baselineDevices.forEach {
            array.put(
                JSONObject()
                    .put("mac", it.mac)
                    .put("name", it.name)
                    .put("manufacturer", it.manufacturer)
                    .put("deviceType", it.deviceType)
                    .put("heartbeatMs", it.heartbeatMs ?: JSONObject.NULL)
                    .put("addedAt", it.addedAt)
            )
        }
        getPreferences(Context.MODE_PRIVATE)
            .edit()
            .putString("baseline_devices_json", array.toString())
            .apply()
    }

    private fun clearBaselineDevices() {
        baselineDevices.clear()
        synchronized(baselineRawDevices) { baselineRawDevices.clear() }
        saveBaselineDevices()
        refreshVisibleDevices()
    }

    private fun trustDevice(device: BleDevice) {
        val existingIndex = trustedDevices.indexOfFirst { it.mac == device.mac }
        val trusted = TrustedDevice(
            mac = device.mac,
            name = device.name,
            deviceType = device.deviceType(),
            addedAt = System.currentTimeMillis(),
            enabled = true,
        )
        if (existingIndex >= 0) trustedDevices[existingIndex] = trusted
        else trustedDevices.add(trusted)
        saveTrustedDevices()
        synchronized(rawDevices) { rawDevices.remove(device.mac) }
        synchronized(trustScanRawDevices) { trustScanRawDevices.remove(device.mac) }
        refreshVisibleDevices()
        refreshTrustScanDevices()
    }

    private fun setTrustedEnabled(mac: String, enabled: Boolean) {
        val index = trustedDevices.indexOfFirst { it.mac == mac }
        if (index < 0) return
        trustedDevices[index] = trustedDevices[index].copy(enabled = enabled)
        saveTrustedDevices()
        refreshVisibleDevices()
    }

    private fun clearTrustedDevices() {
        trustedDevices.clear()
        saveTrustedDevices()
        refreshVisibleDevices()
    }

    private fun resetSweep() {
        rawDevices.clear()
        visibleDevices.clear()
        totalDeviceCount.value = 0
        currentLocationDevices.clear()
        sweepLocations.clear()
        currentStep.value = 0
        scanCount = 0
        sessionStart.value = 0L
        locationName.value = ""
        statusText.value = "Ready - start your sweep"
    }

    private fun checkAndScan() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) startBleScan() else {
            pendingScanMode = MODE_SWEEP
            permissionLauncher.launch(permissions)
        }
    }

    private fun checkAndTrustScan() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) startTrustScan() else {
            pendingScanMode = MODE_TRUST
            permissionLauncher.launch(permissions)
        }
    }

    private fun checkAndBaselineScan() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) startBaselineScan() else {
            pendingScanMode = MODE_BASELINE
            permissionLauncher.launch(permissions)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (currentStep.value >= TOTAL_LOCATIONS) return
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = btManager.adapter?.bluetoothLeScanner ?: run {
            statusText.value = "Bluetooth scanner not available"
            return
        }
        scanCount += 1
        if (sessionStart.value == 0L) sessionStart.value = System.currentTimeMillis()
        currentScanLocation = locationName.value.ifBlank { "Location ${currentStep.value + 1}" }
        currentLocationDevices.clear()
        lastUiRefreshAt = 0L
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setReportDelay(500L)
            .build()
        scanner.startScan(null, settings, scanCallback)
        isScanning.value = true
        statusText.value = "Scanning at $currentScanLocation..."
        handler.postDelayed({ stopBleScan() }, SCAN_DURATION_MS)
    }

    @SuppressLint("MissingPermission")
    private fun startTrustScan() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = btManager.adapter?.bluetoothLeScanner ?: return
        synchronized(trustScanRawDevices) { trustScanRawDevices.clear() }
        trustScanDevices.clear()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()
        scanner.startScan(null, settings, trustScanCallback)
        isTrustScanning.value = true
        handler.postDelayed({ stopTrustScan() }, 15000L)
    }

    @SuppressLint("MissingPermission")
    private fun startBaselineScan() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = btManager.adapter?.bluetoothLeScanner ?: return
        synchronized(baselineRawDevices) { baselineRawDevices.clear() }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setReportDelay(500L)
            .build()
        scanner.startScan(null, settings, baselineScanCallback)
        isBaselineScanning.value = true
        handler.postDelayed({ stopBaselineScan(saveResults = true) }, 30000L)
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (!isScanning.value) return

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        try {
            btManager.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}

        isScanning.value = false
        refreshVisibleDevices()

        val locName = locationName.value.ifBlank { "Location ${currentStep.value + 1}" }
        val locationDeviceCount = synchronized(rawDevices) { currentLocationDevices.size }
        sweepLocations.add(SweepLocation(
            name = locName,
            deviceCount = locationDeviceCount,
            timestamp = System.currentTimeMillis()
        ))

        currentStep.value += 1
        locationName.value = ""

        if (currentStep.value >= TOTAL_LOCATIONS) {
            val confirmedMatches = visibleDevices.count { it.isAlertMatch() }
            val overWindow = sessionStart.value > 0L &&
                System.currentTimeMillis() - sessionStart.value > SWEEP_WINDOW_MS
            statusText.value = if (confirmedMatches > 0 && overWindow)
                "ALERT - $confirmedMatches match(es), but sweep passed 15 min"
            else if (confirmedMatches > 0)
                "ALERT - $confirmedMatches device(s) followed all 3 locations"
            else
                "Sweep complete - no suspicious devices found"
        } else {
            statusText.value = "Location ${currentStep.value} done - move and scan again"
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopTrustScan() {
        if (!isTrustScanning.value) return
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        try {
            btManager.adapter?.bluetoothLeScanner?.stopScan(trustScanCallback)
        } catch (_: Exception) {}
        isTrustScanning.value = false
        refreshTrustScanDevices()
    }

    @SuppressLint("MissingPermission")
    private fun stopBaselineScan(saveResults: Boolean) {
        if (!isBaselineScanning.value) return
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        try {
            btManager.adapter?.bluetoothLeScanner?.stopScan(baselineScanCallback)
        } catch (_: Exception) {}
        isBaselineScanning.value = false
        if (saveResults) saveBaselineFromScan()
    }

    @SuppressLint("MissingPermission")
    private fun recordScanResult(result: ScanResult) {
        val now = System.currentTimeMillis()
        val mac = try { result.device.address ?: return } catch (_: Exception) { return }
        if (trustedDevices.any { it.enabled && it.mac == mac }) return
        val name = try { result.device.name ?: "Unknown device" } catch (_: Exception) { "Unknown device" }
        val rssi = result.rssi
        val txPower = result.txPower.takeIf { it != Int.MIN_VALUE }
        val manufacturer = manufacturerName(result)

        synchronized(rawDevices) {
            val device = rawDevices.getOrPut(mac) {
                MutableBleDevice(mac = mac, name = name, rssi = rssi, txPower = txPower,
                    manufacturer = manufacturer, firstSeen = now, lastSeen = now)
            }
            device.name = if (name == "Unknown device") device.name else name
            device.rssi = rssi
            device.txPower = txPower ?: device.txPower
            device.manufacturer = if (manufacturer == "Unknown") device.manufacturer else manufacturer
            device.lastSeen = now
            device.scanCount += 1
            device.locations.add(currentScanLocation)
            currentLocationDevices.add(mac)
            device.timestamps.addLast(now)
            while (device.timestamps.size > MAX_TIMESTAMPS_PER_DEVICE) device.timestamps.removeFirst()
        }

        if (now - lastUiRefreshAt >= UI_REFRESH_MS) {
            lastUiRefreshAt = now
            handler.post { refreshVisibleDevices() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun recordTrustScanResult(result: ScanResult) {
        val now = System.currentTimeMillis()
        val mac = try { result.device.address ?: return } catch (_: Exception) { return }
        if (trustedDevices.any { it.enabled && it.mac == mac }) return
        val name = try { result.device.name ?: "Unknown device" } catch (_: Exception) { "Unknown device" }
        val rssi = result.rssi
        val txPower = result.txPower.takeIf { it != Int.MIN_VALUE }
        val manufacturer = manufacturerName(result)

        synchronized(trustScanRawDevices) {
            val device = trustScanRawDevices.getOrPut(mac) {
                MutableBleDevice(
                    mac = mac,
                    name = name,
                    rssi = rssi,
                    txPower = txPower,
                    manufacturer = manufacturer,
                    firstSeen = now,
                    lastSeen = now
                )
            }
            device.name = if (name == "Unknown device") device.name else name
            device.rssi = rssi
            device.txPower = txPower ?: device.txPower
            device.manufacturer = if (manufacturer == "Unknown") device.manufacturer else manufacturer
            device.lastSeen = now
            device.scanCount += 1
            device.locations.add("Trust setup")
            device.timestamps.addLast(now)
            while (device.timestamps.size > MAX_TIMESTAMPS_PER_DEVICE) device.timestamps.removeFirst()
        }

        handler.post { refreshTrustScanDevices() }
    }

    @SuppressLint("MissingPermission")
    private fun recordBaselineScanResult(result: ScanResult) {
        val now = System.currentTimeMillis()
        val mac = try { result.device.address ?: return } catch (_: Exception) { return }
        val name = try { result.device.name ?: "Unknown device" } catch (_: Exception) { "Unknown device" }
        val rssi = result.rssi
        val txPower = result.txPower.takeIf { it != Int.MIN_VALUE }
        val manufacturer = manufacturerName(result)

        synchronized(baselineRawDevices) {
            val device = baselineRawDevices.getOrPut(mac) {
                MutableBleDevice(
                    mac = mac,
                    name = name,
                    rssi = rssi,
                    txPower = txPower,
                    manufacturer = manufacturer,
                    firstSeen = now,
                    lastSeen = now
                )
            }
            device.name = if (name == "Unknown device") device.name else name
            device.rssi = rssi
            device.txPower = txPower ?: device.txPower
            device.manufacturer = if (manufacturer == "Unknown") device.manufacturer else manufacturer
            device.lastSeen = now
            device.scanCount += 1
            device.locations.add("Baseline")
            device.timestamps.addLast(now)
            while (device.timestamps.size > MAX_TIMESTAMPS_PER_DEVICE) device.timestamps.removeFirst()
        }
    }

    private fun saveBaselineFromScan() {
        val trusted = trustedDevices.filter { it.enabled }.map { it.mac }.toSet()
        val baseline = synchronized(baselineRawDevices) {
            baselineRawDevices.values
                .filter { it.mac !in trusted }
                .map { it.snapshot(trusted) }
                .map {
                    BaselineDevice(
                        mac = it.mac,
                        name = it.name,
                        manufacturer = it.manufacturer,
                        deviceType = it.deviceType(),
                        heartbeatMs = it.heartbeatMs(),
                        addedAt = System.currentTimeMillis(),
                    )
                }
        }
        baselineDevices.clear()
        baselineDevices.addAll(baseline)
        saveBaselineDevices()
        refreshVisibleDevices()
    }

    private fun manufacturerName(result: ScanResult): String {
        val mfrMap = result.scanRecord?.manufacturerSpecificData ?: return "Unknown"
        return when {
            mfrMap.indexOfKey(76) >= 0 -> "Apple"
            mfrMap.indexOfKey(6) >= 0 -> "Microsoft"
            mfrMap.indexOfKey(117) >= 0 -> "Samsung"
            mfrMap.indexOfKey(343) >= 0 -> "Tile"
            else -> "Unknown"
        }
    }

    private fun refreshVisibleDevices() {
        val snapshot = synchronized(rawDevices) {
            val trusted = trustedDevices.filter { it.enabled }.map { it.mac }.toSet()
            rawDevices.values
                .map { it.snapshot(trusted) }
                .map { it.copy(baselineMatch = it.matchesBaseline(baselineDevices)) }
                .sortedWith(compareBy<BleDevice> { it.trusted }
                    .thenBy { it.baselineMatch }
                    .thenByDescending { it.locationsMatched }
                    .thenByDescending { it.confidenceScore() }
                    .thenByDescending { it.scanCount }
                    .thenBy { it.mac })
                .take(MAX_VISIBLE_DEVICES)
        }
        totalDeviceCount.value = synchronized(rawDevices) { rawDevices.size }
        visibleDevices.clear()
        visibleDevices.addAll(snapshot)
    }

    private fun refreshTrustScanDevices() {
        val snapshot = synchronized(trustScanRawDevices) {
            val trusted = trustedDevices.filter { it.enabled }.map { it.mac }.toSet()
            trustScanRawDevices.values
                .map { it.snapshot(trusted) }
                .sortedWith(compareByDescending<BleDevice> { it.scanCount }
                    .thenBy { it.mac })
                .take(20)
        }
        trustScanDevices.clear()
        trustScanDevices.addAll(snapshot)
    }
}

// ─── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun BlueTraceApp(
    devices: List<BleDevice>,
    isScanning: Boolean,
    isTrustScanning: Boolean,
    isBaselineScanning: Boolean,
    statusText: String,
    locationName: String,
    totalDeviceCount: Int,
    currentStep: Int,
    sweepLocations: List<SweepLocation>,
    sessionStart: Long,
    trustedDevices: List<TrustedDevice>,
    baselineDevices: List<BaselineDevice>,
    trustScanDevices: List<BleDevice>,
    activeScreen: String,
    onLocationChange: (String) -> Unit,
    onScanToggle: () -> Unit,
    onReset: () -> Unit,
    onTrustDevice: (BleDevice) -> Unit,
    onTrustScanToggle: () -> Unit,
    onBaselineScanToggle: () -> Unit,
    onScreenChange: (String) -> Unit,
    onTrustedEnabledChange: (String, Boolean) -> Unit,
    onClearTrusted: () -> Unit,
    onClearBaseline: () -> Unit,
) {
    val sweepComplete = currentStep >= TOTAL_LOCATIONS
    val confirmedMatches = devices.filter { it.isAlertMatch() }
    val watchMatches = devices.filter { it.isWatchAlert() }
    val sweepElapsed = rememberSweepElapsed(sessionStart, sweepComplete)
    val sweepOverWindow = sessionStart > 0L && sweepElapsed > SWEEP_WINDOW_MS

    if (activeScreen == SCREEN_TRUSTED) {
        Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
            TrustedDevicesScreen(
                trustedDevices = trustedDevices,
                baselineDevices = baselineDevices,
                trustScanDevices = trustScanDevices,
                isTrustScanning = isTrustScanning,
                isBaselineScanning = isBaselineScanning,
                onBack = { onScreenChange(SCREEN_SCAN) },
                onTrustScanToggle = onTrustScanToggle,
                onBaselineScanToggle = onBaselineScanToggle,
                onTrustDevice = onTrustDevice,
                onEnabledChange = onTrustedEnabledChange,
                onClearAll = onClearTrusted,
                onClearBaseline = onClearBaseline,
                modifier = Modifier.weight(1f)
            )
            BottomTabs(
                activeScreen = activeScreen,
                onScreenChange = onScreenChange,
                trustedCount = trustedDevices.count { it.enabled }
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ── Header ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("BlueTrace", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                if (isScanning) "● scanning" else if (sweepComplete) "● done" else "● ready",
                color = if (isScanning) GreenColor else if (sweepComplete) YellowColor else TextMuted,
                fontSize = 13.sp
            )
        }

        // ── Progress dots ──
        SweepProgressBar(currentStep = currentStep, sweepComplete = sweepComplete)

        Spacer(modifier = Modifier.height(10.dp))

        SweepTimer(
            sessionStart = sessionStart,
            elapsedMs = sweepElapsed,
            sweepComplete = sweepComplete
        )

        Spacer(modifier = Modifier.height(10.dp))

        // ── Alert banner (show when sweep done and suspicious found) ──
        if (sweepComplete && confirmedMatches.isNotEmpty()) {
            AlertBanner(
                count = confirmedMatches.size,
                overWindow = sweepOverWindow
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Status ──
        Text(
            statusText,
            color = if (statusText.startsWith("ALERT")) RedColor else TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // ── Completed locations ──
        if (sweepLocations.isNotEmpty()) {
            LocationHistory(sweepLocations)
            Spacer(modifier = Modifier.height(10.dp))
        }

        // ── Scan input + button (only if sweep not complete) ──
        if (!sweepComplete) {
            OutlinedTextField(
                value = locationName,
                onValueChange = onLocationChange,
                placeholder = {
                    Text(
                        "Name this location (e.g. coffee shop)",
                        color = TextMuted, fontSize = 13.sp
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GreenColor,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = GreenColor,
                ),
                singleLine = true
            )

            Button(
                onClick = onScanToggle,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) Color(0xFF422006) else Color(0xFF166534)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (isScanning) "Stop scanning"
                    else "Scan location ${currentStep + 1} of $TOTAL_LOCATIONS",
                    color = if (isScanning) YellowColor else GreenColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Stats row ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("Devices", totalDeviceCount.toString(), Modifier.weight(1f))
            StatCard(
                "Alerts",
                confirmedMatches.size.toString(),
                Modifier.weight(1f),
                if (confirmedMatches.isNotEmpty()) RedColor else GreenColor
            )
        }

        if (!sweepComplete && watchMatches.isNotEmpty()) {
            Text(
                "${watchMatches.size} watch item(s): seen at 2 locations",
                color = YellowColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // ── Device list ──
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(devices, key = { it.mac }) { device ->
                DeviceCard(device, onTrustDevice = onTrustDevice)
            }
        }

        // ── Reset button ──
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("New sweep", color = TextMuted, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))
        BottomTabs(
            activeScreen = activeScreen,
            onScreenChange = onScreenChange,
            trustedCount = trustedDevices.count { it.enabled }
        )
    }
}

@Composable
fun BottomTabs(
    activeScreen: String,
    onScreenChange: (String) -> Unit,
    trustedCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TabButton(
            label = "Scan",
            active = activeScreen == SCREEN_SCAN,
            modifier = Modifier.weight(1f),
            onClick = { onScreenChange(SCREEN_SCAN) }
        )
        TabButton(
            label = "Trusted ($trustedCount)",
            active = activeScreen == SCREEN_TRUSTED,
            modifier = Modifier.weight(1f),
            onClick = { onScreenChange(SCREEN_TRUSTED) }
        )
    }
}

@Composable
fun TabButton(
    label: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFF14532D) else Color.Transparent
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(
            label,
            color = if (active) GreenColor else TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun rememberSweepElapsed(sessionStart: Long, sweepComplete: Boolean): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sessionStart, sweepComplete) {
        while (sessionStart > 0L && !sweepComplete) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    return if (sessionStart == 0L) 0L else now - sessionStart
}

@Composable
fun SweepProgressBar(currentStep: Int, sweepComplete: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(TOTAL_LOCATIONS) { index ->
            val isDone = index < currentStep
            val isActive = index == currentStep && !sweepComplete
            val color = when {
                isDone -> GreenColor
                isActive -> YellowColor
                else -> DarkBorder
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
        Text(
            "${minOf(currentStep, TOTAL_LOCATIONS)}/$TOTAL_LOCATIONS",
            color = TextMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
fun SweepTimer(sessionStart: Long, elapsedMs: Long, sweepComplete: Boolean) {
    val remainingMs = (SWEEP_WINDOW_MS - elapsedMs).coerceAtLeast(0L)
    val minutes = remainingMs / 60000L
    val seconds = (remainingMs % 60000L) / 1000L
    val color = when {
        sessionStart == 0L -> TextMuted
        elapsedMs > SWEEP_WINDOW_MS -> RedColor
        remainingMs <= 5 * 60 * 1000L -> YellowColor
        else -> GreenColor
    }
    val text = when {
        sessionStart == 0L -> "15 min clean window starts on scan 1"
        elapsedMs > SWEEP_WINDOW_MS -> "15 min window passed - phone IDs may rotate"
        sweepComplete -> "Sweep completed within ${formatElapsed(elapsedMs)}"
        else -> "Clean sweep window: ${minutes}:${seconds.toString().padStart(2, '0')} left"
    }

    Text(
        text,
        color = color,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    )
}

fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}

@Composable
fun AlertBanner(count: Int, overWindow: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A0808), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF7F1D1D), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(RedColor, CircleShape)
        )
        Column {
            Text(
                "$count device${if (count > 1) "s" else ""} matched all 3 locations",
                color = Color(0xFFFCA5A5),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (overWindow) "Past 15 min window - treat as possible, not clean proof"
                else "Under 15 min window - strong follow pattern",
                color = if (overWindow) YellowColor else Color(0xFF7F1D1D),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun LocationHistory(locations: List<SweepLocation>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        locations.forEachIndexed { index, loc ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color(0xFF052E16), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${index + 1}",
                        color = GreenColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                Text(loc.name, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${loc.deviceCount} devices", color = GreenColor, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier, valueColor: Color = GreenColor) {
    Column(
        modifier = modifier
            .background(DarkCard, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(value, color = valueColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DeviceCard(device: BleDevice, onTrustDevice: (BleDevice) -> Unit) {
    val score = device.confidenceScore()
    val isConfirmed = device.isConfirmedFollow()
    val isWatch = device.isWatchMatch()
    val bgColor = when {
        device.trusted -> Color(0xFF06140D)
        device.baselineMatch -> Color(0xFF06111F)
        isConfirmed -> Color(0xFF1A0808)
        isWatch -> Color(0xFF171302)
        else -> DarkCard
    }
    val heartbeat = device.heartbeatMs()
    val distance = device.distanceMeters()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .then(
                if (device.trusted) Modifier.border(1.dp, Color(0xFF14532D), RoundedCornerShape(12.dp))
                else if (device.baselineMatch) Modifier.border(1.dp, Color(0xFF1E3A8A), RoundedCornerShape(12.dp))
                else if (isConfirmed) Modifier.border(1.dp, Color(0xFF7F1D1D), RoundedCornerShape(12.dp))
                else if (isWatch) Modifier.border(1.dp, Color(0xFF854D0E), RoundedCornerShape(12.dp))
                else Modifier
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                device.name,
                color = if (isConfirmed) Color(0xFFFECACA) else TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$score%",
                color = when {
                    isConfirmed -> RedColor
                    isWatch -> YellowColor
                    score >= 75 -> YellowColor
                    else -> GreenColor
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(device.mac, color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

        Spacer(modifier = Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (device.trusted) Chip("Trusted", GreenColor)
            if (device.baselineMatch) Chip("Baseline match", BlueColor)
            Chip(device.deviceType(), BlueColor)
            Chip(device.manufacturer, TextMuted)
            if (device.locationsMatched >= 2) {
                Chip(
                    "${device.locationsMatched} locations",
                    if (device.trusted) GreenColor else RedColor
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (heartbeat != null) Text("HB ${String.format("%.0f", heartbeat)}ms", color = Color(0xFF38BDF8), fontSize = 12.sp)
            if (distance != null) Text("~${String.format("%.1f", distance)}m", color = TextMuted, fontSize = 12.sp)
            Text("${device.scanCount} signals", color = TextMuted, fontSize = 12.sp)
        }

        if (!device.trusted && (isConfirmed || isWatch)) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isConfirmed) "⚠ Alert: seen at all 3 locations - confidence $score%"
                else "Watch: seen at 2 locations - keep scanning",
                color = if (isConfirmed) Color(0xFFFCA5A5) else YellowColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = { onTrustDevice(device) },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
        ) {
            Text(
                "Trust this device",
                color = GreenColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun Chip(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
