package com.musicplayer.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Column(modifier = Modifier.selectableGroup()) {
            ThemeOption(
                label = "System Default",
                icon = { Icon(Icons.Default.SettingsBrightness, null) },
                selected = currentTheme == ThemeMode.SYSTEM,
                onClick = { onThemeChange(ThemeMode.SYSTEM) }
            )
            ThemeOption(
                label = "Light",
                icon = { Icon(Icons.Default.LightMode, null) },
                selected = currentTheme == ThemeMode.LIGHT,
                onClick = { onThemeChange(ThemeMode.LIGHT) }
            )
            ThemeOption(
                label = "Dark",
                icon = { Icon(Icons.Default.DarkMode, null) },
                selected = currentTheme == ThemeMode.DARK,
                onClick = { onThemeChange(ThemeMode.DARK) }
            )
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        RadioButton(selected = selected, onClick = null)
    }
    HorizontalDivider(thickness = 0.5.dp)
}