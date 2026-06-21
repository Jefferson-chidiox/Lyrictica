package com.oss.euphoriae.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.oss.euphoriae.data.preferences.DarkModeOption
import com.oss.euphoriae.data.preferences.ThemeColorOption
import com.oss.euphoriae.ui.theme.*

data class ThemeColorItem(
    val option: ThemeColorOption,
    val color: Color,
    val isDynamic: Boolean = false
)

private val themeColors = listOf(
    ThemeColorItem(ThemeColorOption.DYNAMIC, Color.Transparent, isDynamic = true),
    ThemeColorItem(ThemeColorOption.PURPLE, SeedPurple),
    ThemeColorItem(ThemeColorOption.BLUE, SeedBlue),
    ThemeColorItem(ThemeColorOption.GREEN, SeedGreen),
    ThemeColorItem(ThemeColorOption.ORANGE, SeedOrange),
    ThemeColorItem(ThemeColorOption.PINK, SeedPink),
    ThemeColorItem(ThemeColorOption.RED, SeedRed)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onContributorsClick: () -> Unit,
    currentThemeColor: ThemeColorOption,
    onThemeColorChange: (ThemeColorOption) -> Unit,
    currentDarkMode: DarkModeOption,
    onDarkModeChange: (DarkModeOption) -> Unit,
    currentOnlineTracksEnabled: Boolean,
    onOnlineTracksEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val packageInfo = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
    val versionName = packageInfo?.versionName ?: "Unknown"
    val appearanceEnabled = false
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier.alpha(if (appearanceEnabled) 1f else 0.45f)
            ) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                // Dark Mode Selector
                ListItem(
                    headlineContent = { Text("Dark Mode") },
                    supportingContent = { Text(currentDarkMode.displayName) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    DarkModeOption.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = currentDarkMode == option,
                            onClick = { onDarkModeChange(option) },
                            enabled = appearanceEnabled,
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = DarkModeOption.entries.size
                            )
                        ) {
                            Text(option.displayName)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Theme Color Selector
                ListItem(
                    headlineContent = { Text("Theme Color") },
                    supportingContent = { Text(currentThemeColor.displayName) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    themeColors.forEach { themeColorItem ->
                        ColorOptionItem(
                            themeColorItem = themeColorItem,
                            isSelected = currentThemeColor == themeColorItem.option,
                            enabled = appearanceEnabled,
                            onClick = { onThemeColorChange(themeColorItem.option) }
                        )
                    }
                }
            }
            
            /*
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            Text(
                text = "Online",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            ListItem(
                headlineContent = { Text("Online Tracks") },
                supportingContent = {
                    Text(
                        if (currentOnlineTracksEnabled) {
                            "Show Spinamp and NCS tracks across Home, Explore, and lyrics-source previews."
                        } else {
                            "Hide all provider-based online tracks across Home, Explore, and lyrics-source previews."
                        }
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Switch(
                        checked = currentOnlineTracksEnabled,
                        onCheckedChange = onOnlineTracksEnabledChange
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            */
            
            Text(
                text = "About",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            ListItem(
                headlineContent = { Text("Contributors") },
                supportingContent = { Text("People who made this app") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable(onClick = onContributorsClick)
            )
            
            ListItem(
                headlineContent = { Text("App Version") },
                supportingContent = { Text(versionName) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun ColorOptionItem(
    themeColorItem: ThemeColorItem,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chipForeground = when {
        themeColorItem.isDynamic -> Color.Black.copy(alpha = 0.82f)
        themeColorItem.color.luminance() > 0.52f -> Color.Black
        else -> Color.White
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (themeColorItem.isDynamic) {
                    Modifier.background(
                        brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFFFF6B6B),
                                Color(0xFFFFE66D),
                                Color(0xFF4ECDC4),
                                Color(0xFF45B7D1),
                                Color(0xFF96CEB4),
                                Color(0xFFFF6B6B)
                            )
                        )
                    )
                } else {
                    Modifier.background(themeColorItem.color)
                }
            )
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = chipForeground,
                modifier = Modifier.size(20.dp)
            )
        } else if (themeColorItem.isDynamic) {
            Icon(
                imageVector = Icons.Default.Wallpaper,
                contentDescription = "Dynamic",
                tint = chipForeground,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
