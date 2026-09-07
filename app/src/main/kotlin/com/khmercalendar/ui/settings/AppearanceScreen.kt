package com.khmercalendar.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.prefs.CalendarDensity
import com.khmercalendar.data.prefs.DashboardCard
import com.khmercalendar.data.prefs.SettingsStore
import com.khmercalendar.data.prefs.ThemeMode
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.AccentPalette
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/**
 * Appearance and customization.
 *
 * Everything here is a preference read by the theme or the calendar grid; nothing needs a
 * restart, because the whole tree recomposes off one [AppSettings] snapshot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    settings: AppSettings,
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pickBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            // Without a persisted grant the wallpaper disappears the next time the app starts.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            scope.launch { settingsStore.setBackgroundImage(uri.toString()) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("រូបរាង") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "ត្រឡប់")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup("ពន្លឺ") {
                ThemeMode.entries.forEach { mode ->
                    ChoiceRow(
                        title = when (mode) {
                            ThemeMode.SYSTEM -> "តាមប្រព័ន្ធ"
                            ThemeMode.LIGHT -> "ភ្លឺ"
                            ThemeMode.DARK -> "ងងឹត"
                        },
                        selected = settings.themeMode == mode,
                        onClick = { scope.launch { settingsStore.setThemeMode(mode) } },
                    )
                }
            }

            HorizontalDivider()

            SettingsGroup("ពណ៌សំខាន់") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow(
                        title = "ប្រើពណ៌ពីផ្ទាំងរូបភាព",
                        subtitle = "Material You",
                        checked = settings.useDynamicColor,
                        onChange = { scope.launch { settingsStore.setDynamicColor(it) } },
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AccentPalette.forEach { (name, argb) ->
                        val selected = settings.accentArgb == argb && !settings.useDynamicColor
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    scope.launch {
                                        settingsStore.setDynamicColor(false)
                                        settingsStore.setAccent(argb)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = name,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            SettingsGroup("អក្សរ និងទំហំ") {
                SliderRow(
                    label = "ទំហំអក្សរ",
                    value = settings.fontScale,
                    range = 0.8f..1.6f,
                    steps = 7,
                    display = "${localeNumber((settings.fontScale * 100).toInt())}%",
                    onChange = { scope.launch { settingsStore.setFontScale(it) } },
                )
                Text(
                    "ដង់ស៊ីតេប្រតិទិន",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
                CalendarDensity.entries.forEach { density ->
                    ChoiceRow(
                        title = density.labelKm,
                        selected = settings.density == density,
                        onClick = { scope.launch { settingsStore.setDensity(density) } },
                    )
                }
            }

            HorizontalDivider()

            SettingsGroup("អ្វីដែលបង្ហាញលើប្រតិទិន") {
                SwitchRow(
                    title = "កាលបរិច្ឆេទចន្ទគតិខ្មែរ",
                    checked = settings.showKhmerLunarDates,
                    onChange = { scope.launch { settingsStore.setShowLunar(it) } },
                )
                SwitchRow(
                    title = "កាលបរិច្ឆេទសុរិយគតិ",
                    checked = settings.showGregorianDates,
                    onChange = { scope.launch { settingsStore.setShowGregorian(it) } },
                )
                SwitchRow(
                    title = "បុណ្យជាតិ",
                    checked = settings.showHolidays,
                    onChange = { scope.launch { settingsStore.setShowHolidays(it) } },
                )
                SwitchRow(
                    title = "សញ្ញាព្រឹត្តិការណ៍",
                    checked = settings.showEventDots,
                    onChange = { scope.launch { settingsStore.setShowDots(it) } },
                )
                SwitchRow(
                    title = "លេខសប្តាហ៍",
                    checked = settings.showWeekNumbers,
                    onChange = { scope.launch { settingsStore.setShowWeekNumbers(it) } },
                )
                SwitchRow(
                    title = "លេខខ្មែរ",
                    subtitle = "១២៣ ជំនួសឱ្យ 123",
                    checked = settings.useKhmerNumerals,
                    onChange = { scope.launch { settingsStore.setKhmerNumerals(it) } },
                )
                SwitchRow(
                    title = "បន្លិចថ្ងៃចុងសប្តាហ៍",
                    checked = settings.highlightWeekends,
                    onChange = { scope.launch { settingsStore.setHighlightWeekends(it) } },
                )
            }

            HorizontalDivider()

            SettingsGroup("ថ្ងៃដំបូងនៃសប្តាហ៍") {
                listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY).forEach { day ->
                    ChoiceRow(
                        title = com.khmercalendar.core.khmer.KhmerTerms.dayOfWeek(day),
                        selected = settings.weekStart == day,
                        onClick = { scope.launch { settingsStore.setWeekStart(day) } },
                    )
                }
            }

            HorizontalDivider()

            SettingsGroup("ផ្ទាំងខាងក្រោយ") {
                SettingsRow(
                    title = "រូបភាពផ្ទៃខាងក្រោយ",
                    subtitle = settings.backgroundImageUri?.let { "បានជ្រើសរើស" } ?: "គ្មាន",
                    onClick = { pickBackground.launch(arrayOf("image/*")) },
                    trailing = {
                        if (settings.backgroundImageUri != null) {
                            TextButton(
                                onClick = { scope.launch { settingsStore.setBackgroundImage(null) } },
                            ) { Text("លុប") }
                        }
                    },
                )
                if (settings.backgroundImageUri != null) {
                    SliderRow(
                        label = "ភាពស្រអាប់",
                        value = settings.backgroundOpacity,
                        range = 0f..0.6f,
                        steps = 5,
                        display = "${localeNumber((settings.backgroundOpacity * 100).toInt())}%",
                        onChange = { scope.launch { settingsStore.setBackgroundOpacity(it) } },
                    )
                }
            }

            HorizontalDivider()

            SettingsGroup("ធាតុក្រាហ្វិកលើអេក្រង់ដើម") {
                ThemeMode.entries.forEach { mode ->
                    ChoiceRow(
                        title = when (mode) {
                            ThemeMode.SYSTEM -> "តាមប្រព័ន្ធ"
                            ThemeMode.LIGHT -> "ភ្លឺ"
                            ThemeMode.DARK -> "ងងឹត"
                        },
                        selected = settings.widgetTheme == mode,
                        onClick = { scope.launch { settingsStore.setWidgetTheme(mode) } },
                    )
                }
                SliderRow(
                    label = "ភាពស្រអាប់នៃផ្ទៃខាងក្រោយ",
                    value = settings.widgetOpacity,
                    range = 0.2f..1f,
                    steps = 7,
                    display = "${localeNumber((settings.widgetOpacity * 100).toInt())}%",
                    onChange = { scope.launch { settingsStore.setWidgetOpacity(it) } },
                )
                SwitchRow(
                    title = "បង្ហាញកាលបរិច្ឆេទចន្ទគតិ",
                    checked = settings.widgetShowLunar,
                    onChange = { scope.launch { settingsStore.setWidgetShowLunar(it) } },
                )
            }

            HorizontalDivider()

            SettingsGroup("ផ្ទាំងដើម") {
                Text(
                    "ជ្រើសរើសកាតដែលចង់បង្ហាញ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                DashboardCard.entries.forEach { card ->
                    SwitchRow(
                        title = card.labelKm,
                        checked = card.key !in settings.hiddenDashboardCards,
                        onChange = { visible ->
                            val hidden = settings.hiddenDashboardCards.toMutableSet()
                            if (visible) hidden.remove(card.key) else hidden.add(card.key)
                            scope.launch { settingsStore.setDashboardHidden(hidden) }
                        },
                    )
                }
            }

            Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                display,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
        )
    }
}
