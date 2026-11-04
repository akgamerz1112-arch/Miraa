package com.zenro.app.ui

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
import com.zenro.app.VoiceViewModel
import com.zenro.app.audio.AudioLogic
import com.zenro.app.audio.RoutePref
import com.zenro.app.voice.Gender
import com.zenro.app.voice.GeminiLiveProtocol
import com.zenro.app.voice.Role
import com.zenro.app.voice.SessionState
import com.zenro.app.voice.Style
import com.zenro.app.voice.UserGender
import com.zenro.app.voice.VadPreset
import com.zenro.app.voice.VoiceCatalog

private val Bg = Color(0xFF05070A)
private val Panel = Color(0xFF0D1218)
private val Cyan = Color(0xFF22D3EE)
private val Blue = Color(0xFF2563EB)
private val Red = Color(0xFFF87171)
private val Dim = Color(0xFF8A97A6)

@Composable
private fun VoiceTab(vm: VoiceViewModel, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val muted by vm.muted.collectAsStateWithLifecycle()
    val voice by vm.voice.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    val route by vm.route.collectAsStateWithLifecycle()
    val share by vm.screenShare.collectAsStateWithLifecycle()
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
        if (state == SessionState.IDLE || state == SessionState.ERROR) {
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

    val listState = rememberLazyListState()
    LaunchedEffect(lines) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ZENRO", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Cyan, letterSpacing = 4.sp)
            TextButton(onClick = onOpenSettings) { Text("Settings", color = Dim) }
        }
        Spacer(Modifier.height(4.dp))
        Orb(state, level, ::onOrbTap, Modifier.size(240.dp))
        Text(statusLabel(state, muted, hasKey), color = if (state == SessionState.ERROR) Red else Cyan, fontSize = 15.sp)
        if (route.isNotBlank()) Text(route, color = Dim, fontSize = 12.sp)
        if (!a11y) {
            TextButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Accessibility ON karo (screen + control ke liye)", color = Cyan, fontSize = 13.sp) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = share, onClick = { vm.setScreenShare(!share) }, label = { Text("Screen dikhao") })
                FilterChip(selected = control, enabled = share, onClick = { vm.setControl(!control) }, label = { Text("Control do") })
            }
            if (share) {
                Text(
                    "Screen ki tasveer lagbhag har second Google ke model ko jaa rahi hai. Notification mein 'Screen band' se rok sakte ho.",
                    color = Dim, fontSize = 11.sp, textAlign = TextAlign.Center
                )
            }
        }
        if (action.isNotBlank()) Text(action, color = Cyan, fontSize = 12.sp, textAlign = TextAlign.Center)
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Red, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lines) { line ->
                val mine = line.role == Role.USER
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    Text(if (mine) "You" else "ZENRO", color = Dim, fontSize = 11.sp)
                    Text(line.text, color = if (mine) Color.White else Cyan, fontSize = 15.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Gender.values().forEach { g ->
                FilterChip(
                    selected = voice.gender == g,
                    onClick = { vm.setGender(g) },
                    label = { Text(if (g == Gender.MALE) "Male voice" else "Female voice") }
                )
            }
            OutlinedButton(onClick = { vm.toggleMute() }) { Text(if (muted) "Unmute" else "Mute") }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoiceCatalog.forGender(voice.gender).forEach { v ->
                FilterChip(selected = v.apiName == voice.apiName, onClick = { vm.setVoice(v) }, label = { Text(v.apiName) })
            }
        }
    }
}

@Composable
fun ZenroApp(vm: VoiceViewModel = viewModel()) {
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
                        1 -> ChatTab(vm) { showSettings = true }
                        else -> YaadeinTab(vm)
                    }
                }
                NavigationBar(containerColor = Panel, windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)) {
                    listOf("\uD83C\uDF99" to "Voice", "\uD83D\uDCAC" to "Chat", "\uD83E\uDDE0" to "Yaadein")
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
private fun ChatTab(vm: VoiceViewModel, onOpenSettings: () -> Unit) {
    val lines by vm.chat.collectAsStateWithLifecycle()
    val busy by vm.chatBusy.collectAsStateWithLifecycle()
    val err by vm.chatError.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size, busy) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }
    Column(Modifier.padding(16.dp).fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Chat", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Cyan)
            Row {
                TextButton(onClick = { vm.clearChat() }) { Text("Clear", color = Dim) }
                TextButton(onClick = onOpenSettings) { Text("Settings", color = Dim) }
            }
        }
        if (lines.isEmpty()) {
            Text("Type karo, ZENRO text mein jawab degi. Voice ki zaroorat nahi.", color = Dim, fontSize = 13.sp)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lines) { line ->
                val mine = line.role == Role.USER
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    Text(if (mine) "You" else "ZENRO", color = Dim, fontSize = 11.sp)
                    Surface(
                        color = if (mine) Blue.copy(alpha = 0.35f) else Panel,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(line.text, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(10.dp))
                    }
                }
            }
        }
        if (busy) Text("Typing...", color = Dim, fontSize = 12.sp)
        err?.let { Text(it, color = Red, fontSize = 13.sp) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("ZENRO se kuch bhi poochho...") },
                maxLines = 4
            )
            TextButton(
                onClick = { vm.sendChat(input); input = "" },
                enabled = !busy && input.isNotBlank()
            ) { Text("Bhejo") }
        }
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
            "ZENRO baat karte waqt kaam ki baatein (naam, pasand, plans) yahan save karti hai. " +
                "Ye phone mein encrypted rehti hain; baat shuru hone pe ye list Google ke model ko prompt ke saath jaati hai. " +
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

private fun statusLabel(state: SessionState, muted: Boolean, hasKey: Boolean): String = when (state) {
    SessionState.IDLE -> if (hasKey) "Tap the orb to talk" else "Settings mein API key daalo, phir orb tap karo"
    SessionState.CONNECTING -> "Connecting..."
    SessionState.RECONNECTING -> "Reconnecting..."
    SessionState.LISTENING -> if (muted) "Muted" else "Listening"
    SessionState.SPEAKING -> "Speaking (bol ke beech mein rok sakte ho)"
    SessionState.ERROR -> "Error - orb tap karke dobara try karo"
}

@Composable
private fun Orb(state: SessionState, level: Float, onClick: () -> Unit, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val target = when (state) {
        SessionState.IDLE, SessionState.ERROR -> 0f
        SessionState.CONNECTING, SessionState.RECONNECTING -> 0.25f
        SessionState.LISTENING -> level * 0.5f
        SessionState.SPEAKING -> 0.2f + level * 0.6f
    }
    val smooth by animateFloatAsState(target, tween(120), label = "level")
    val color = if (state == SessionState.ERROR) Red else Cyan

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
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    val routePref by vm.routePref.collectAsStateWithLifecycle()
    val gain by vm.gain.collectAsStateWithLifecycle()
    val vad by vm.vad.collectAsStateWithLifecycle()
    val style by vm.style.collectAsStateWithLifecycle()
    val userGender by vm.userGender.collectAsStateWithLifecycle()
    val keyTest by vm.keyTest.collectAsStateWithLifecycle()
    val micGain by vm.micGain.collectAsStateWithLifecycle()
    val emotion by vm.emotion.collectAsStateWithLifecycle()
    var cm by remember { mutableStateOf(vm.chatModel.value) }
    var key by remember { mutableStateOf("") }
    var m by remember { mutableStateOf(vm.model.value) }
    var name by remember { mutableStateOf(vm.userName.value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (hasKey) "API key saved (hidden). Naya daalo to replace ho jayegi."
                    else "Gemini API key abhi set nahi hai.",
                    fontSize = 13.sp, color = Dim
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text("Gemini API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedButton(onClick = { vm.testKey(key.trim()) }) { Text("Key test karo") }
                if (keyTest.isNotBlank()) Text(keyTest, fontSize = 12.sp, color = Dim)

                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Aapka naam") }, singleLine = true
                )
                Text("Aap ho", fontSize = 12.sp, color = Dim)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = userGender == UserGender.MALE, onClick = { vm.setUserGender(UserGender.MALE) }, label = { Text("Ladka") })
                    FilterChip(selected = userGender == UserGender.FEMALE, onClick = { vm.setUserGender(UserGender.FEMALE) }, label = { Text("Ladki") })
                }

                Text("Awaaz kahan se aaye", fontSize = 12.sp, color = Dim)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        RoutePref.AUTO to "Auto", RoutePref.HEADPHONES to "Headphones",
                        RoutePref.SPEAKER to "Speaker", RoutePref.EAR to "Ear"
                    ).forEach { (p, label) ->
                        FilterChip(selected = routePref == p, onClick = { vm.setRoutePref(p) }, label = { Text(label) })
                    }
                }
                Text("Volume boost: ${"%.1f".format(gain)}x", fontSize = 12.sp, color = Dim)
                Slider(
                    value = gain, onValueChange = { vm.setGainLive(it) },
                    onValueChangeFinished = { vm.saveGain() }, valueRange = 1f..3f
                )
                Text(
                    "Headphones mein phone ke volume buttons (media) se awaaz badhao. Speaker mode mein call volume bhi badh jaati hai aur band hone pe wapas ho jaati hai.",
                    fontSize = 11.sp, color = Dim
                )

                val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                TextButton(onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                    Text("Contacts permission do (WhatsApp naam se kholne ke liye)", color = Cyan, fontSize = 12.sp)
                }
                Text("Mic sensitivity (dheere bolne ke liye): ${"%.1f".format(micGain)}x", fontSize = 12.sp, color = Dim)
                Slider(
                    value = micGain, onValueChange = { vm.setMicGainLive(it) },
                    onValueChangeFinished = { vm.saveMicGain() },
                    valueRange = AudioLogic.MIC_MIN_GAIN..AudioLogic.MIC_MAX_GAIN
                )
                Text("Dheere bolne pe sunai na de to badhao. Zyada badhane pe aas-paas ka shor bhi badhta hai.", fontSize = 11.sp, color = Dim)

                Text("Jawab kitni jaldi shuru ho", fontSize = 12.sp, color = Dim)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(VadPreset.QUICK to "Jaldi", VadPreset.NORMAL to "Normal", VadPreset.SLOW to "Aaram se").forEach { (v, label) ->
                        FilterChip(selected = vad == v, onClick = { vm.setVad(v) }, label = { Text(label) })
                    }
                }

                Text("Baat karne ka style", fontSize = 12.sp, color = Dim)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Style.DOST to "Dost", Style.PYAARI to "Pyaari dost",
                        Style.FOCUS to "Focus", Style.PADHAI to "Padhai"
                    ).forEach { (st, label) ->
                        FilterChip(selected = style == st, onClick = { vm.setStyle(st) }, label = { Text(label) })
                    }
                }

                OutlinedTextField(
                    value = m, onValueChange = { m = it },
                    label = { Text("Live model name") }, singleLine = true
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        GeminiLiveProtocol.DEFAULT_MODEL,
                        GeminiLiveProtocol.EXTENDED_MODEL,
                        GeminiLiveProtocol.LEGACY_MODEL,
                        GeminiLiveProtocol.NATIVE_AUDIO_MODEL
                    ).forEach { opt ->
                        FilterChip(selected = m == opt, onClick = { m = opt }, label = { Text(opt, fontSize = 11.sp) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Emotion + proactive mode", fontSize = 13.sp, color = Color.White)
                    Switch(checked = emotion, onCheckedChange = { vm.setEmotionMode(it) })
                }
                Text(
                    "Awaaz tumhare mood ke hisaab se badalti hai aur ZENRO kabhi-kabhi chup bhi reh sakti hai jab baat usse nahi hoti. " +
                        "Google ke docs ke mutabik sirf 2.5 native-audio model pe chalta hai (upar wala 2.5-native-audio chip chuno). " +
                        "Server mana kare to voice bina emotion ke chalu rahegi.",
                    fontSize = 11.sp, color = Dim
                )
                OutlinedTextField(
                    value = cm, onValueChange = { cm = it },
                    label = { Text("Chat model name (text chat ke liye)") }, singleLine = true
                )
                Text("Key is phone mein encrypted store hoti hai, kahin upload nahi hoti (sirf Google ko voice ke saath jaati hai). Style/speed badalne pe session naya shuru hota hai.", fontSize = 12.sp, color = Dim)
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.saveSettings(key.trim(), m.trim(), name.trim(), cm.trim()); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
