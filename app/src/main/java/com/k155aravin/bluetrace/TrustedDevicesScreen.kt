package com.k155aravin.bluetrace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TrustedDevice(
    val mac: String,
    val name: String,
    val deviceType: String,
    val addedAt: Long,
    val enabled: Boolean = true,
)

data class BaselineDevice(
    val mac: String,
    val name: String,
    val manufacturer: String,
    val deviceType: String,
    val heartbeatMs: Double?,
    val addedAt: Long,
)

private val TrustedDarkBg = Color(0xFF0D0D0F)
private val TrustedDarkCard = Color(0xFF111114)
private val TrustedDarkBorder = Color(0xFF1E1E24)
private val TrustedGreen = Color(0xFF4ADE80)
private val TrustedRed = Color(0xFFEF4444)
private val TrustedYellow = Color(0xFFFACC15)
private val TrustedBlue = Color(0xFF60A5FA)
private val TrustedText = Color(0xFFE0E0E0)
private val TrustedMuted = Color(0xFF8A8A92)
private const val BASELINE_SCAN_SECONDS = 30L

@Composable
fun TrustedDevicesScreen(
    trustedDevices: List<TrustedDevice>,
    baselineDevices: List<BaselineDevice>,
    trustScanDevices: List<BleDevice>,
    trustStatusText: String,
    isTrustScanning: Boolean,
    isBaselineScanning: Boolean,
    baselineScanDeviceCount: Int,
    baselineScanStartedAt: Long,
    onBack: () -> Unit,
    onTrustScanToggle: () -> Unit,
    onBaselineScanToggle: () -> Unit,
    onTrustDevice: (BleDevice) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onClearAll: () -> Unit,
    onClearBaseline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val autoDetect = remember { mutableStateOf(false) }
    val trustTeam = remember { mutableStateOf(false) }
    val hasRunTrustScan = remember { mutableStateOf(false) }
    val candidates = trustScanDevices.filter { scanned ->
        trustedDevices.none { it.mac == scanned.mac }
    }

    LazyColumn(
        modifier = modifier
            .background(TrustedDarkBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Text("<", color = TrustedGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Trusted devices", color = TrustedText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${trustedDevices.count { it.enabled }} active of ${trustedDevices.size} saved",
                        color = TrustedMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            InfoCard()
        }

        item {
            TrustSetupCard()
        }

        item {
            BaselineProfileCard(
                baselineCount = baselineDevices.size,
                isBaselineScanning = isBaselineScanning,
                baselineScanDeviceCount = baselineScanDeviceCount,
                baselineScanStartedAt = baselineScanStartedAt,
                statusText = trustStatusText,
                onBaselineScanToggle = onBaselineScanToggle,
                onClearBaseline = onClearBaseline
            )
        }

        item {
            Button(
                onClick = {
                    hasRunTrustScan.value = true
                    onTrustScanToggle()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTrustScanning) Color(0xFF422006) else Color(0xFF14532D)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (isTrustScanning) "Stop trust scan" else "Scan nearby devices to trust",
                    color = if (isTrustScanning) TrustedYellow else TrustedGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            TrustScanSummaryCard(
                isScanning = isTrustScanning,
                hasRunScan = hasRunTrustScan.value,
                candidateCount = candidates.size,
                savedCount = trustedDevices.size,
                statusText = trustStatusText
            )
        }

        if (candidates.isNotEmpty()) {
            item {
                Text(
                    "Nearby devices (${candidates.size})",
                    color = TrustedMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            items(candidates, key = { it.mac }) { device ->
                TrustCandidateRow(device, onTrustDevice)
            }
        }

        item {
            Text("Whitelist", color = TrustedMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        if (trustedDevices.isEmpty()) {
            item {
                EmptyTrustedCard()
            }
        } else {
            items(trustedDevices, key = { it.mac }) { device ->
                TrustedDeviceRow(device, onEnabledChange)
            }
        }

        item {
            SettingsCard(
                autoDetect = autoDetect.value,
                trustTeam = trustTeam.value,
                onAutoDetect = { autoDetect.value = it },
                onTrustTeam = { trustTeam.value = it }
            )
        }

        item {
            DangerZone(onClearAll)
        }
    }
}

@Composable
private fun TrustSetupCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrustedDarkCard, RoundedCornerShape(12.dp))
            .border(1.dp, TrustedDarkBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("Best way to use this", color = TrustedGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Do this at home, in your car, or in a quiet place.", color = TrustedText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            "Keep your headphones, watch, laptop, second phone, and team devices close. Scan, then tap Trust on anything you own.",
            color = TrustedMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun TrustScanSummaryCard(
    isScanning: Boolean,
    hasRunScan: Boolean,
    candidateCount: Int,
    savedCount: Int,
    statusText: String,
) {
    val title = when {
        statusText.isNotBlank() -> statusText
        isScanning -> "Scanning nearby devices..."
        hasRunScan && candidateCount > 0 -> "Scan complete - $candidateCount new ${deviceWord(candidateCount)} found"
        hasRunScan -> "Scan complete - no new devices found"
        else -> "Ready to scan trusted devices"
    }
    val body = when {
        statusText.contains("Bluetooth", ignoreCase = true) -> "Turn Bluetooth on from your phone quick settings, then run the trust scan again."
        statusText.startsWith("Trusted:", ignoreCase = true) -> "Saved. This device moved into your whitelist and will be ignored during security sweeps."
        statusText.isNotBlank() -> "Follow the message above, then try the scan again."
        isScanning -> "Keep your own device nearby. New candidates will appear below while the scan runs."
        hasRunScan && candidateCount > 0 -> "Tap Trust beside your own devices. Trusted devices will be ignored during security sweeps."
        hasRunScan -> "Try moving closer, waking the device, or turning Bluetooth off and on."
        else -> "$savedCount trusted ${deviceWord(savedCount)} saved. Use the green button when you want to add more."
    }
    val border = when {
        statusText.contains("Bluetooth", ignoreCase = true) -> TrustedRed
        statusText.startsWith("Trusted:", ignoreCase = true) -> TrustedGreen
        isScanning -> TrustedYellow
        hasRunScan && candidateCount > 0 -> TrustedGreen
        else -> TrustedDarkBorder
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrustedDarkCard, RoundedCornerShape(12.dp))
            .border(1.dp, border.copy(alpha = .55f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            title,
            color = when {
                statusText.contains("Bluetooth", ignoreCase = true) -> TrustedRed
                statusText.startsWith("Trusted:", ignoreCase = true) -> TrustedGreen
                isScanning -> TrustedYellow
                else -> TrustedText
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(body, color = TrustedMuted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun BaselineProfileCard(
    baselineCount: Int,
    isBaselineScanning: Boolean,
    baselineScanDeviceCount: Int,
    baselineScanStartedAt: Long,
    statusText: String,
    onBaselineScanToggle: () -> Unit,
    onClearBaseline: () -> Unit,
) {
    val elapsedSeconds = rememberBaselineElapsedSeconds(baselineScanStartedAt, isBaselineScanning)
    val pulse = (elapsedSeconds % 3).toInt()
    val dots = ".".repeat(pulse + 1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrustedDarkCard, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF1E3A8A), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Baseline profile", color = TrustedBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (isBaselineScanning) {
            BaselineLiveHeader(
                seconds = elapsedSeconds,
                deviceCount = baselineScanDeviceCount,
                dots = dots
            )
        }
        Text("Teach BlueTrace what is normal around you.", color = TrustedText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            if (isBaselineScanning) "Keep the phone still for this 30 second baseline scan."
            else if (baselineCount == 0) "Use this in a quiet place with only your own/team devices nearby."
            else "$baselineCount reference device(s) saved.",
            color = TrustedMuted,
            fontSize = 12.sp
        )
        Button(
            onClick = onBaselineScanToggle,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isBaselineScanning) Color(0xFF422006) else Color(0xFF082F49)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                if (isBaselineScanning) "Scanning baseline - ${elapsedSeconds}s - $baselineScanDeviceCount heard"
                else "Create quiet-place baseline",
                color = if (isBaselineScanning) TrustedYellow else TrustedBlue,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isBaselineScanning) {
            BaselineScanningStatusCard(
                seconds = elapsedSeconds,
                deviceCount = baselineScanDeviceCount,
                dots = dots
            )
        } else if (statusText.startsWith("Baseline saved", ignoreCase = true)) {
            Text(
                statusText,
                color = TrustedGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        } else if (statusText.contains("Baseline scan failed", ignoreCase = true) ||
            statusText.contains("Bluetooth", ignoreCase = true)
        ) {
            Text(
                statusText,
                color = TrustedRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (baselineCount > 0) {
            TextButton(
                onClick = onClearBaseline,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    "Clear baseline profile",
                    color = TrustedYellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BaselineLiveHeader(
    seconds: Long,
    deviceCount: Int,
    dots: String,
) {
    val remaining = (BASELINE_SCAN_SECONDS - seconds).coerceAtLeast(0L)
    val progress = (seconds.toFloat() / BASELINE_SCAN_SECONDS.toFloat()).coerceIn(0f, 1f)
    val phaseText = when {
        seconds < 5L -> "Starting Bluetooth baseline$dots"
        seconds < 15L -> "Listening for nearby devices$dots"
        seconds < 25L -> "Collecting signal patterns$dots"
        else -> "Finishing baseline profile$dots"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF082F49), RoundedCornerShape(12.dp))
            .border(1.dp, TrustedBlue, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(TrustedBlue, CircleShape)
            )
            Text(
                "LIVE BASELINE SCAN$dots",
                color = TrustedBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${remaining}s left",
                color = TrustedText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        BaselineProgressBar(progress)
        Text(
            phaseText,
            color = TrustedText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "$deviceCount nearby ${deviceWord(deviceCount)} heard so far",
            color = TrustedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Do not close the app. BlueTrace will save the baseline automatically when the timer reaches zero.",
            color = TrustedMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun BaselineProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF06111F))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(8.dp)
                .background(TrustedBlue)
        )
    }
}

@Composable
private fun rememberBaselineElapsedSeconds(startedAt: Long, isScanning: Boolean): Long {
    val seconds = remember { mutableStateOf(0L) }
    LaunchedEffect(startedAt, isScanning) {
        seconds.value = 0L
        while (isScanning && startedAt > 0L) {
            seconds.value = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
            delay(1000L)
        }
    }
    return seconds.value
}

@Composable
private fun BaselineScanningStatusCard(
    seconds: Long,
    deviceCount: Int,
    dots: String,
) {
    val remaining = (BASELINE_SCAN_SECONDS - seconds).coerceAtLeast(0L)
    val tip = when {
        deviceCount == 0 -> "No devices yet. This can happen in a quiet room."
        deviceCount < 4 -> "A few signals found. Keep the phone still."
        else -> "Signals are coming in. BlueTrace is building the baseline."
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF06111F), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF1E3A8A), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(TrustedBlue, CircleShape)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Scanning quiet place$dots",
                color = TrustedBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "$tip $remaining seconds left.",
                color = TrustedMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        Text(
            "${seconds}s",
            color = TrustedText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TrustCandidateRow(device: BleDevice, onTrustDevice: (BleDevice) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrustedDarkCard, RoundedCornerShape(12.dp))
            .border(1.dp, TrustedDarkBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(Color(0xFF082F49), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = TrustedBlue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                device.name,
                color = TrustedText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(device.mac, color = TrustedMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(device.deviceType(), color = TrustedBlue, fontSize = 11.sp)
        }
        Button(
            onClick = { onTrustDevice(device) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14532D)),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text("Trust", color = TrustedGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF06140D), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF14532D), RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(Color(0xFF052E16), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("S", color = TrustedGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Column {
            Text("Whitelisting keeps your own devices quiet.", color = TrustedText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Trusted devices are ignored during scans and never trigger Watch or Alert.", color = TrustedMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptyTrustedCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrustedDarkCard, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("No trusted devices yet", color = TrustedText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Scan first, then trust your headphones, watch, car, or laptop.", color = TrustedMuted, fontSize = 12.sp)
    }
}

@Composable
private fun TrustedDeviceRow(
    device: TrustedDevice,
    onEnabledChange: (String, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrustedDarkCard, RoundedCornerShape(12.dp))
            .border(1.dp, if (device.enabled) Color(0xFF14532D) else TrustedDarkBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(Color(0xFF052E16), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(device.name.take(1).uppercase(), color = TrustedGreen, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                device.name,
                color = TrustedText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(device.mac, color = TrustedMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(
                "${device.deviceType} - added ${formatTrustedDate(device.addedAt)}",
                color = TrustedBlue,
                fontSize = 11.sp
            )
        }
        Switch(
            checked = device.enabled,
            onCheckedChange = { onEnabledChange(device.mac, it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = TrustedGreen,
                checkedTrackColor = Color(0xFF14532D),
                uncheckedThumbColor = TrustedMuted,
                uncheckedTrackColor = Color(0xFF1E1E24)
            )
        )
    }
}

@Composable
private fun SettingsCard(
    autoDetect: Boolean,
    trustTeam: Boolean,
    onAutoDetect: (Boolean) -> Unit,
    onTrustTeam: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrustedDarkCard, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Auto-trust settings", color = TrustedMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        SettingToggle("Auto-detect my devices", "Future: learn devices you carry often", autoDetect, onAutoDetect)
        SettingToggle("Trust team devices", "Future: ignore devices from people you work with", trustTeam, onTrustTeam)
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TrustedText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TrustedMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = TrustedGreen, checkedTrackColor = Color(0xFF14532D))
        )
    }
}

@Composable
private fun DangerZone(onClearAll: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A0808), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF7F1D1D), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Danger zone", color = TrustedRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Clear trusted devices if you want every device to be scanned again.", color = TrustedMuted, fontSize = 12.sp)
        Button(
            onClick = onClearAll,
            modifier = Modifier.fillMaxWidth().height(42.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Clear all trusted devices", color = Color(0xFFFECACA), fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatTrustedDate(time: Long): String {
    return SimpleDateFormat("MMM d", Locale.US).format(Date(time))
}

private fun deviceWord(count: Int): String = if (count == 1) "device" else "devices"
