package com.khmercalendar.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khmercalendar.data.db.CategoryEntity
import com.khmercalendar.data.repo.EventRepository
import com.khmercalendar.ui.components.ColorDot
import com.khmercalendar.ui.theme.AccentPalette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val repository: EventRepository) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(category: CategoryEntity) {
        viewModelScope.launch { repository.upsertCategory(category) }
    }

    fun delete(category: CategoryEntity) {
        viewModelScope.launch { repository.deleteCategory(category) }
    }
}

/**
 * Category management.
 *
 * Built-in categories can be renamed and recoloured but not deleted: deleting one would
 * orphan every event filed under it, and there is no benefit to the user in allowing it when
 * renaming does the same job.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel, onBack: () -> Unit) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ប្រភេទព្រឹត្តិការណ៍") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "ត្រឡប់")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editing = CategoryEntity(
                        name = "",
                        colorArgb = AccentPalette.first().second,
                        sortOrder = categories.size,
                    )
                },
            ) { Icon(Icons.Outlined.Add, contentDescription = "បន្ថែម") }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(categories.size) { index ->
                val category = categories[index]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editing = category }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ColorDot(Color(category.colorArgb), size = 14)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(category.name, style = MaterialTheme.typography.bodyLarge)
                        if (category.isBuiltIn) {
                            Text(
                                "ប្រភេទស្តង់ដារ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!category.isBuiltIn) {
                        IconButton(onClick = { confirmDelete = category }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "លុប")
                        }
                    }
                }
                HorizontalDivider()
            }
            item { Spacer(Modifier.padding(40.dp)) }
        }
    }

    editing?.let { category ->
        CategoryDialog(
            category = category,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
        )
    }

    confirmDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("លុបប្រភេទ?") },
            text = {
                Text("ព្រឹត្តិការណ៍ក្នុងប្រភេទនេះនឹងមិនត្រូវបានលុបទេ — គ្រាន់តែគ្មានប្រភេទប៉ុណ្ណោះ។")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(category)
                        confirmDelete = null
                    },
                ) { Text("លុប") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("បោះបង់") }
            },
        )
    }
}

@Composable
private fun CategoryDialog(
    category: CategoryEntity,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity) -> Unit,
) {
    var name by remember { mutableStateOf(category.name) }
    var color by remember { mutableStateOf(category.colorArgb) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category.id == 0L) "ប្រភេទថ្មី" else "កែប្រភេទ") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ឈ្មោះ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.padding(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AccentPalette.forEach { (_, argb) ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (color == argb) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { color = argb },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(category.copy(name = name.trim(), colorArgb = color)) },
                enabled = name.isNotBlank(),
            ) { Text("រក្សាទុក") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("បោះបង់") } },
    )
}
