package com.khmercalendar.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.khmercalendar.ui.Routes
import kotlinx.coroutines.delay

/** The search box at the top of the settings index. */
@Composable
fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("ស្វែងរកការកំណត់") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "សម្អាត")
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
    )
}

/**
 * Settings matching [query], each opening straight at its own group.
 *
 * A result on the settings index itself does not navigate: pushing a second copy of this screen
 * would leave two in the back stack. It clears the search instead, and the group scrolls into
 * view on the list that is already here.
 */
@Composable
fun SettingsSearchResults(
    query: String,
    onNavigate: (String) -> Unit,
    onClearQuery: () -> Unit,
) {
    val results = remember(query) { SettingsIndex.search(query) }
    if (results.isEmpty()) {
        Text(
            "រកមិនឃើញការកំណត់ “$query”",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    results.forEach { entry ->
        SettingsRow(
            title = entry.titleKm,
            subtitle = listOfNotNull(entry.screenKm, entry.section?.takeIf { it != entry.titleKm })
                .joinToString(" › "),
            onClick = {
                SettingsFocus.section = entry.section
                if (entry.route == Routes.SETTINGS) onClearQuery() else onNavigate(entry.route)
            },
        )
    }
}

/**
 * Makes a settings group reveal itself when search sent the user to it.
 *
 * It waits a moment first: asking a scroll container to reveal a child before the screen has
 * been laid out does nothing. Then it scrolls, tints the group briefly so the eye lands on it,
 * and only then clears the request - clearing it earlier would restart this effect and cut the
 * tint off mid-way.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberSettingsFocus(title: String): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var flash by remember { mutableStateOf(false) }
    val targeted = SettingsFocus.section == title

    LaunchedEffect(targeted) {
        if (!targeted) return@LaunchedEffect
        delay(FOCUS_SETTLE_MS)
        requester.bringIntoView()
        flash = true
        delay(FOCUS_FLASH_MS)
        flash = false
        SettingsFocus.section = null
    }

    val tint by animateColorAsState(
        targetValue = if (flash) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        label = "settings-focus",
    )
    return Modifier
        .bringIntoViewRequester(requester)
        .background(tint)
}

private const val FOCUS_SETTLE_MS = 250L
private const val FOCUS_FLASH_MS = 1_400L
