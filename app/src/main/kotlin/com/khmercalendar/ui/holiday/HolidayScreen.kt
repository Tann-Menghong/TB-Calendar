package com.khmercalendar.ui.holiday

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khmercalendar.core.holiday.Holiday
import com.khmercalendar.core.holiday.HolidayKind
import com.khmercalendar.core.holiday.HolidaySource
import com.khmercalendar.core.khmer.Chhankitek
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.ui.components.localeNumber
import java.time.LocalDate

/**
 * Cambodian public holidays and religious observances for a chosen year.
 *
 * Every row says where its date came from - fixed, computed from the lunar calendar, or taken
 * from a published sub-decree - because for the moveable holidays that distinction is the
 * difference between "traditionally observed" and "an official day off".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidayScreen(onBack: () -> Unit) {
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    val holidays = remember(year) {
        if (year in Chhankitek.MIN_DATE.year..Chhankitek.MAX_DATE.year) {
            com.khmercalendar.core.holiday.KhmerHolidays.forYear(year)
        } else {
            emptyList()
        }
    }
    val today = remember { LocalDate.now() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("បុណ្យជាតិ និងពិធីបុណ្យ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "ត្រឡប់")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = { year-- }) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "ឆ្នាំមុន")
                }
                Text(
                    "ឆ្នាំ ${localeNumber(year)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { year++ }) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "ឆ្នាំក្រោយ")
                }
            }

            if (holidays.isEmpty()) {
                Text(
                    "ឆ្នាំនេះនៅក្រៅដែនកំណត់នៃការគណនាចន្ទគតិ " +
                        "(${localeNumber(Chhankitek.MIN_DATE.year)}–${localeNumber(Chhankitek.MAX_DATE.year)})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                return@Column
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                val byMonth = holidays.groupBy { it.date.monthValue }.toSortedMap()
                byMonth.forEach { (month, entries) ->
                    item(key = "m$month") {
                        Text(
                            KhmerTerms.solarMonth(month),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                        )
                    }
                    items(entries.size) { index ->
                        HolidayRow(entries[index], isToday = entries[index].date == today)
                        HorizontalDivider()
                    }
                }
                item { Spacer(Modifier.padding(24.dp)) }
            }
        }
    }
}

@Composable
private fun HolidayRow(holiday: Holiday, isToday: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                localeNumber(holiday.date.dayOfMonth),
                style = MaterialTheme.typography.titleMedium,
                color = if (isToday) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(holiday.nameKm, style = MaterialTheme.typography.bodyLarge)
            Text(
                holiday.nameEn,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Badge(
                text = when (holiday.kind) {
                    HolidayKind.PUBLIC -> "ឈប់សម្រាក"
                    HolidayKind.OBSERVANCE -> "ពិធីបុណ្យ"
                },
                error = holiday.kind == HolidayKind.PUBLIC,
            )
            Text(
                text = when (holiday.source) {
                    HolidaySource.FIXED -> "កាលបរិច្ឆេទថេរ"
                    HolidaySource.LUNAR -> "គណនាចន្ទគតិ"
                    HolidaySource.GAZETTED -> "អនុក្រឹត្យ"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun Badge(text: String, error: Boolean) {
    Surface(
        color = if (error) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
        contentColor = if (error) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        },
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
