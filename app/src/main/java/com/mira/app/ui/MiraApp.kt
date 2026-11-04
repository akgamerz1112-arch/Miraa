package com.mira.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mira.app.VoiceViewModel
import com.mira.app.rtc.ConnLogic
import com.mira.app.rtc.ConnState

import com.mira.app.net.ApiLogic

private val Bg = Color(0xFF05070A)
private val Panel = Color(0xFF0D1218)
private val Cyan = Color(0xFF22D3EE)
private val Blue = Color(0xFF2563EB)
private val Red = Color(0xFFF87171)
private val Dim = Color(0xFF8A97A6)

@Composable
private fun VoiceTab(vm: VoiceViewModel, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val muted by vm.muted.collectAsStateWithLifecycle()
    val control by vm.control.collectAsStateWithLifecycle()
    val a11y by vm.a11y.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LifecycleResumeEffect(Unit) {
        vm.refreshAccessibility()
        onPauseOrDispose { }
    }

    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Bluetooth/notification permissions are optional; only the mic is required.
        if (granted(Manifest.permission.RECORD_AUDIO)) vm.startSession()
        else vm.reportError("Microphone permission nahi mili. Bina mic ke voice chat nahi chalegi.")
    }

    fun onOrbTap() {
        if (!ConnLogic.active(state)) {
            val missing = mutableListOf<String>()
            if (!granted(Manifest.permission.RECORD_AUDIO)) missing.add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) {
                missing.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
                missing.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (missing.isEmpty()) vm.startSession() else permLauncher.launch(missing.toTypedArray())
        } else {
            vm.stopSession()
        }
    }

    Column(
        modifier = Modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MIRA", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Cyan, letterSpacing = 4.sp)
            TextButton(onClick = onOpenSettings) { Text("Settings", color = Dim) }
        }
        Spacer(Modifier.height(4.dp))
        Orb(state, onClick = ::onOrbTap, modifier = Modifier.size(240.dp))
        val bad = state == ConnState.ERROR || state == ConnState.UPDATE_NEEDED
        Text(ConnLogic.label(state, muted), color = if (bad) Red else Cyan, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        if (!a11y) {
            TextButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Accessibility ON karo (phone control ke liye)", color = Cyan, fontSize = 13.sp) }
        } else {
            FilterChip(selected = control, onClick = { vm.setControl(!control) }, label = { Text("Control do (MIRA tap/type/app khole)") })
            Text(
                if (control) "MIRA ab tumhare kehne pe phone chala sakti hai. Notification mein 'Control band' se turant rok sakte ho."
                else "Control band hai: MIRA phone pe kuch nahi karegi.",
                color = Dim, fontSize = 11.sp, textAlign = TextAlign.Center
            )
        }
        if (action.isNotBlank()) Text(action, color = Cyan, fontSize = 12.sp, textAlign = TextAlign.Center)
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Red, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        if (ConnLogic.active(state)) {
            OutlinedButton(onClick = { vm.toggleMute() }) { Text(if (muted) "Unmute" else "Mute") }
        }
    }
}

@Composable
fun MiraApp(vm: VoiceViewModel = viewModel()) {
    var showSettings by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan, background = Bg, surface = Panel,
            onBackground = Color.White, onSurface = Color.White
        )
    ) {
        Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.systemBarsPadding().imePadding().fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> VoiceTab(vm) { showSettings = true }
                        else -> YaadeinTab(vm)
                    }
                }
                NavigationBar(containerColor = Panel, windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)) {
                    listOf("\uD83C\uDF99" to "Voice", "\uD83E\uDDE0" to "Yaadein")
                        .forEachIndexed { i, (icon, label) ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Text(icon) },
                                label = { Text(label) }
                            )
                        }
                }
            }
        }
        if (showSettings) SettingsDialog(vm) { showSettings = false }
    }
}

@Composable
private fun YaadeinTab(vm: VoiceViewModel) {
    val items by vm.memories.collectAsStateWithLifecycle()
    val on by vm.memoryOn.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Yaadein", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Cyan)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Khud yaad rakho", color = Color.White, fontSize = 14.sp)
            Switch(checked = on, onCheckedChange = { vm.setMemoryOn(it) })
        }
        Text(
            "MIRA baat karte waqt kaam ki baatein (naam, pasand, plans) yahan save karti hai. " +
                "Ye phone mein encrypted rehti hain; abhi ye list sirf is phone mein hai (server ki yaadasht alag hai). " +
                "Password, OTP aur card/ID number save nahi hote.",
            color = Dim, fontSize = 12.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Kuch yaad karwao...") }
            )
            TextButton(onClick = {
                if (vm.addMemoryManual(input)) { input = ""; msg = "" }
                else msg = "Ye save nahi hua (bahut chhota hai ya number/password jaisa lag raha hai)."
            }) { Text("Add") }
        }
        if (msg.isNotBlank()) Text(msg, color = Red, fontSize = 12.sp)
        if (items.isEmpty()) Text("Abhi kuch save nahi hai.", color = Dim, fontSize = 13.sp)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(items) { i, m ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(m.text, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.deleteMemory(i) }) { Text("Hatao", color = Dim) }
                }
            }
        }
        if (items.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Sab mitao", color = Red) }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Sab yaadein mitaoge?") },
            text = { Text("Ye wapas nahi aayengi.") },
            confirmButton = { TextButton(onClick = { vm.clearMemories(); confirmClear = false }) { Text("Haan, mitao") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Nahi") } }
        )
    }
}

@Composable
private fun Orb(state: ConnState, onClick: () -> Unit, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val target = when (state) {
        ConnState.IDLE, ConnState.ERROR, ConnState.UPDATE_NEEDED -> 0f
        ConnState.CHECKING, ConnState.CONNECTING, ConnState.RECONNECTING, ConnState.WAITING_AGENT -> 0.25f
        ConnState.AGENT_JOINED -> 0.4f
    }
    val smooth by animateFloatAsState(target, tween(120), label = "level")
    val color = if (state == ConnState.ERROR || state == ConnState.UPDATE_NEEDED) Red else Cyan

    Canvas(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        val c = center
        val base = size.minDimension / 2f * 0.55f
        val r = base * (1f + 0.06f * pulse + smooth * 0.5f)
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), center = c, radius = r * 1.6f),
            radius = r * 1.6f, center = c
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), color, Blue), center = c, radius = r),
            radius = r, center = c
        )
        drawCircle(color = color.copy(alpha = 0.5f), radius = r * 1.2f, center = c, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun SettingsDialog(vm: VoiceViewModel, onDismiss: () -> Unit) {
    val savedUrl by vm.serverUrl.collectAsStateWithLifecycle()
    val savedId by vm.deviceId.collectAsStateWithLifecycle()
    val hasKey by vm.hasDeviceKey.collectAsStateWithLifecycle()
    val info by vm.configInfo.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf(savedUrl) }
    var id by remember { mutableStateOf(savedId) }
    var key by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "MIRA ka dimaag tumhare PC pe chalta hai. Yahan PC ke API ka address aur is phone ki device id/key daalo.",
                    fontSize = 12.sp, color = Dim
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Server URL (https://...)") }, singleLine = true
                )
                if (url.isNotBlank()) {
                    ApiLogic.urlProblem(url)?.let { Text(it, fontSize = 12.sp, color = Red) }
                }
                OutlinedTextField(
                    value = id, onValueChange = { id = it },
                    label = { Text("Device id (jaise my-phone)") }, singleLine = true
                )
                if (id.isNotBlank() && !ApiLogic.isValidDeviceId(id)) {
                    Text("Sirf chhote letters, numbers aur -, 2 se 32 characters.", fontSize = 12.sp, color = Red)
                }
                Text(
                    if (hasKey) "Device key saved (chhupi hui). Naya daalo to replace ho jayegi."
                    else "Device key abhi set nahi hai.",
                    fontSize = 13.sp, color = Dim
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text("Device key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Text(info, fontSize = 12.sp, color = Dim)

                val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                TextButton(onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                    Text("Contacts permission do (WhatsApp naam se kholne ke liye)", color = Cyan, fontSize = 12.sp)
                }
                Text(
                    "Key is phone ke encrypted store mein rehti hai, sirf tumhare server ko Authorization header mein jaati hai. " +
                        "Awaaz, model aur persona ab PC ke server se set hote hain (app badalne ki zaroorat nahi).",
                    fontSize = 11.sp, color = Dim
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.saveConnection(url, id, key.trim()); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
