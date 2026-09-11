package com.khmercalendar.ui.agenda

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khmercalendar.data.db.CategoryEntity
import com.khmercalendar.domain.SearchFilter
import com.khmercalendar.domain.SearchKind
import com.khmercalendar.domain.SearchStatus
import com.khmercalendar.domain.TaskPriority

/**
 * The chips under the search box.
 *
 * One horizontally scrolling row rather than a wrapping block: search results are what the screen
 * is for, and a block of fourteen chips pushes them below the keyboard. At large font sizes the
 * row scrolls instead of running off the edge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterBar(
    filter: SearchFilter,
    categories: List<CategoryEntity>,
    onChange: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var categoryMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchKind.entries.forEach { kind ->
            FilterChip(
                selected = kind in filter.kinds,
                onClick = { onChange(filter.toggled(kind)) },
                label = { Text(kind.labelKm) },
            )
        }
        SearchStatus.entries.forEach { status ->
            FilterChip(
                selected = filter.status == status,
                onClick = { onChange(filter.copy(status = if (filter.status == status) null else status)) },
                label = { Text(status.labelKm) },
            )
        }
        // High first: it is the one anybody filters by.
        listOf(TaskPriority.HIGH, TaskPriority.NORMAL, TaskPriority.LOW).forEach { priority ->
            FilterChip(
                selected = filter.priority == priority,
                onClick = { onChange(filter.copy(priority = if (filter.priority == priority) null else priority)) },
                label = { Text(priority.labelKm) },
            )
        }
        Box {
            FilterChip(
                selected = filter.categoryId != null,
                onClick = { categoryMenu = true },
                label = { Text(categories.firstOrNull { it.id == filter.categoryId }?.name ?: "ប្រភេទ") },
                trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
            )
            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                DropdownMenuItem(
                    text = { Text("គ្រប់ប្រភេទ") },
                    onClick = {
                        onChange(filter.copy(categoryId = null))
                        categoryMenu = false
                    },
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            onChange(filter.copy(categoryId = category.id))
                            categoryMenu = false
                        },
                    )
                }
            }
        }
    }
}
