package com.musicplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musicplayer.app.ui.PlayerViewModel
import com.musicplayer.app.ui.theme.AppTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.NumberPicker
import java.util.concurrent.TimeUnit

@Composable
fun SettingsScreen(
    vm: PlayerViewModel,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val state by vm.state.collectAsState()
    var showTimerDialog by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        // ── Sleep Timer Section ──────────────────────────────────────────────
        Text("Playback", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Alarm, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sleep Timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (state.isSleepTimerActive) {
                        val hours = TimeUnit.MILLISECONDS.toHours(state.sleepTimerMillisLeft)
                        val minutes = TimeUnit.MILLISECONDS.toMinutes(state.sleepTimerMillisLeft) % 60
                        val seconds = TimeUnit.MILLISECONDS.toSeconds(state.sleepTimerMillisLeft) % 60
                        val timeString = if (hours > 0) "${hours}h ${minutes}m ${seconds}s" else "${minutes}m ${seconds}s"
                        Text(
                            "Stopping in $timeString",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text("Music will stop after a set time", style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = { 
                    if (state.isSleepTimerActive) vm.setSleepTimer(0) else showTimerDialog = true 
                }) {
                    Text(if (state.isSleepTimerActive) "Cancel" else "Set")
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        
        Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemeMenu = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("App Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(formatThemeName(currentTheme), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                DropdownMenu(
                    expanded = showThemeMenu,
                    onDismissRequest = { showThemeMenu = false },
                    modifier = Modifier.fillMaxWidth(0.85f).background(MaterialTheme.colorScheme.surface)
                ) {
                    AppTheme.values().forEach { theme ->
                        DropdownMenuItem(
                            text = { Text(formatThemeName(theme)) },
                            onClick = {
                                onThemeChange(theme)
                                showThemeMenu = false
                            },
                            leadingIcon = {
                                val (primary, bg) = getThemeColors(theme)
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(bg)
                                        .border(1.dp, primary, CircleShape)
                                )
                            }
                        )
                    }
                }
            }
        }
        
        if (showTimerDialog) {
            SleepTimerDialog(
                onDismiss = { showTimerDialog = false },
                onConfirm = { millis ->
                    vm.setSleepTimer(millis)
                    showTimerDialog = false
                }
            )
        }
    }
}

@Composable
fun SleepTimerDialog(onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Sleep Timer") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select duration:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hr", style = MaterialTheme.typography.labelMedium)
                        AndroidView(
                            factory = { context ->
                                NumberPicker(context).apply {
                                    minValue = 0
                                    maxValue = 23
                                    value = hours
                                    wrapSelectorWheel = true
                                    setOnValueChangedListener { _, _, newVal -> hours = newVal }
                                    
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        textColor = android.graphics.Color.WHITE
                                    }
                                    for (i in 0 until childCount) {
                                        val child = getChildAt(i)
                                        if (child is android.widget.EditText) child.setTextColor(android.graphics.Color.WHITE)
                                    }
                                }
                            }
                        )
                    }
                    Text(":", style = MaterialTheme.typography.headlineMedium)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Min", style = MaterialTheme.typography.labelMedium)
                        AndroidView(
                            factory = { context ->
                                NumberPicker(context).apply {
                                    minValue = 0
                                    maxValue = 59
                                    value = minutes
                                    wrapSelectorWheel = true
                                    setOnValueChangedListener { _, _, newVal -> minutes = newVal }
                                    
                                    // Force text color to white for visibility
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        textColor = android.graphics.Color.WHITE
                                    }
                                    for (i in 0 until childCount) {
                                        val child = getChildAt(i)
                                        if (child is android.widget.EditText) child.setTextColor(android.graphics.Color.WHITE)
                                    }
                                }
                            }
                        )
                    }
                    Text(":", style = MaterialTheme.typography.headlineMedium)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Sec", style = MaterialTheme.typography.labelMedium)
                        AndroidView(
                            factory = { context ->
                                NumberPicker(context).apply {
                                    minValue = 0
                                    maxValue = 59
                                    value = seconds
                                    wrapSelectorWheel = true
                                    setOnValueChangedListener { _, _, newVal -> seconds = newVal }
                                    
                                    // Force text color to white for visibility
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        textColor = android.graphics.Color.WHITE
                                    }
                                    for (i in 0 until childCount) {
                                        val child = getChildAt(i)
                                        if (child is android.widget.EditText) child.setTextColor(android.graphics.Color.WHITE)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                val totalMillis = (hours * 3600 + minutes * 60 + seconds) * 1000L
                if (totalMillis > 0) onConfirm(totalMillis)
            }) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}




fun formatThemeName(theme: AppTheme): String {
    return theme.name
        .split("_")
        .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
}

fun getThemeColors(theme: AppTheme): Pair<Color, Color> {
    return when(theme) {
        AppTheme.NAVY -> Color(0xFF5BC0BE) to Color(0xFF0B132B)
        AppTheme.EARTHY -> Color(0xFF2E8B57) to Color(0xFFC89B6D)
        AppTheme.GOLDEN -> Color(0xFFD4A055) to Color(0xFF2E1F0F)
        AppTheme.COTTON -> Color(0xFFB298E7) to Color(0xFFB8E3E9)
        AppTheme.ORIGINAL -> Color(0xFFFFB599) to Color(0xFF120E0D)
    }
}

@Composable
fun ThemeOptionRow(theme: AppTheme, isSelected: Boolean, onClick: () -> Unit) {
    val themeName = formatThemeName(theme)
    val (primaryColor, backgroundColor) = getThemeColors(theme)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .border(2.dp, primaryColor, CircleShape)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = themeName,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}