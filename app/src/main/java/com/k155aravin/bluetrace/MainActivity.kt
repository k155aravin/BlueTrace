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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.pow

private const val MAX_TIMESTAMPS_PER_DEVICE = 80
private const val MAX_VISIBLE_DEVICES = 25
private const val UI_REFRESH_MS = 1000L
private const val SCAN_DURATION_MS = 30000L

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
        locationsMatched >= 3 -> 40
        locationsMatched == 2 -> 20
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
    fun snapshot(): BleDevice = BleDevice(
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
    )
}

class MainActivity : ComponentActivity() {
    private val rawDevices = mutableMapOf<String, MutableBleDevice>()
    private val visibleDevices = mutableStateListOf<BleDevice>()
    private val isScanning = mutableStateOf(false)
    private val statusText = mutableStateOf("Ready - tap Scan to start")
    private val locationName = mutableStateOf("")
    private val handler = Handler(Looper.getMainLooper())
    private var scanCount = 0
    private var currentScanLocation = "Location 1"
    private var lastUiRefreshAt = 0L

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) startBleScan()
        else statusText.value = "Permissions denied - enable Bluetooth and Location in Settings"
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BlueTraceApp(
                devices = visibleDevices,
                isScanning = isScanning.value,
                statusText = statusText.value,
                locationName = locationName.value,
                onLocationChange = { locationName.value = it },
                onScanToggle = { if (isScanning.value) stopBleScan() else checkAndScan() },
                onClear = {
                    rawDevices.clear()
                    visibleDevices.clear()
                    statusText.value = "Cleared - ready for a new sweep"
                }
            )
        }
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
        if (allGranted) startBleScan() else permissionLauncher.launch(permissions)
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = btManager.adapter?.bluetoothLeScanner ?: run {
            statusText.value = "Bluetooth scanner not available"
            return
        }

        scanCount += 1
        currentScanLocation = locationName.value.ifBlank { "Location $scanCount" }
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
    private fun stopBleScan() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        try {
            btManager.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
            // Scanner can already be stopped by Android. Keep the UI stable.
        }

        isScanning.value = false
        refreshVisibleDevices()
        val suspicious = visibleDevices.count { it.confidenceScore() >= 75 }
        statusText.value = if (suspicious > 0) {
            "ALERT - $suspicious suspicious device(s) found"
        } else {
            "Scan complete - ${rawDevices.size} devices found"
        }
    }

    @SuppressLint("MissingPermission")
    private fun recordScanResult(result: ScanResult) {
        val now = System.currentTimeMillis()
        val mac = try { result.device.address ?: return } catch (_: Exception) { return }
        val name = try { result.device.name ?: "Unknown device" } catch (_: Exception) { "Unknown device" }
        val rssi = result.rssi
        val txPower = result.txPower.takeIf { it != Int.MIN_VALUE }
        val manufacturer = manufacturerName(result)

        synchronized(rawDevices) {
            val device = rawDevices.getOrPut(mac) {
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
            device.locations.add(currentScanLocation)
            device.timestamps.addLast(now)
            while (device.timestamps.size > MAX_TIMESTAMPS_PER_DEVICE) {
                device.timestamps.removeFirst()
            }
        }

        if (now - lastUiRefreshAt >= UI_REFRESH_MS) {
            lastUiRefreshAt = now
            handler.post { refreshVisibleDevices() }
        }
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
            rawDevices.values
                .map { it.snapshot() }
                .sortedWith(
                    compareByDescending<BleDevice> { it.confidenceScore() }
                        .thenByDescending { it.scanCount }
                        .thenBy { it.mac }
                )
                .take(MAX_VISIBLE_DEVICES)
        }
        visibleDevices.clear()
        visibleDevices.addAll(snapshot)
    }
}

@Composable
fun BlueTraceApp(
    devices: List<BleDevice>,
    isScanning: Boolean,
    statusText: String,
    locationName: String,
    onLocationChange: (String) -> Unit,
    onScanToggle: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("BlueTrace", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                if (isScanning) "scanning" else "ready",
                color = if (isScanning) GreenColor else TextMuted,
                fontSize = 13.sp
            )
        }

        Text(
            statusText,
            color = if (statusText.startsWith("ALERT")) RedColor else TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = locationName,
            onValueChange = onLocationChange,
            placeholder = { Text("Location name, e.g. coffee shop", color = TextMuted, fontSize = 13.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(bottom = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isScanning) Color(0xFF422006) else Color(0xFF166534)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (isScanning) "Stop scanning" else "Scan this location",
                color = if (isScanning) YellowColor else GreenColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("Shown", devices.size.toString(), Modifier.weight(1f))
            StatCard(
                "Suspicious",
                devices.count { it.confidenceScore() >= 75 }.toString(),
                Modifier.weight(1f),
                RedColor
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(devices, key = { it.mac }) { device ->
                DeviceCard(device)
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
fun DeviceCard(device: BleDevice) {
    val score = device.confidenceScore()
    val isSuspicious = score >= 75
    val bgColor = if (isSuspicious) Color(0xFF1A0808) else DarkCard
    val heartbeat = device.heartbeatMs()
    val distance = device.distanceMeters()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                device.name,
                color = if (isSuspicious) Color(0xFFFECACA) else TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$score%",
                color = when {
                    score >= 90 -> RedColor
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
            Chip(device.deviceType(), BlueColor)
            Chip(device.manufacturer, TextMuted)
            if (device.locationsMatched >= 2) Chip("${device.locationsMatched} locations", RedColor)
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (heartbeat != null) Text("HB ${String.format("%.0f", heartbeat)}ms", color = Color(0xFF38BDF8), fontSize = 12.sp)
            if (distance != null) Text("~${String.format("%.1f", distance)}m", color = TextMuted, fontSize = 12.sp)
            Text("${device.scanCount} signals", color = TextMuted, fontSize = 12.sp)
        }

        if (isSuspicious) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Seen at ${device.locationsMatched} location(s) - confidence $score%",
                color = Color(0xFFFCA5A5),
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
