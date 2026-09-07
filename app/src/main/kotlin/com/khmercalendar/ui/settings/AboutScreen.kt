package com.khmercalendar.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khmercalendar.BuildConfig
import com.khmercalendar.core.khmer.Chhankitek
import com.khmercalendar.ui.components.localeNumber

/**
 * About, and the honest limits of the calendar arithmetic.
 *
 * The supported-range note is not boilerplate: the Chhankitek reckoning implemented here is
 * tabulated from a 1900 epoch, and telling the user where it stops is more useful than
 * silently returning a wrong lunar date for the year 2300.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("អំពីកម្មវិធី") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "ត្រឡប់")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("ប្រតិទិនខ្មែរ", style = MaterialTheme.typography.headlineSmall)
            Text(
                "កំណែ ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            Paragraph(
                title = "ឯកជនភាព",
                body = "ទិន្នន័យប្រតិទិន ការរំលឹក និងការសន្ទនាជាមួយជំនួយការ " +
                    "ត្រូវបានរក្សាទុកក្នុងឧបករណ៍របស់អ្នកតែប៉ុណ្ណោះ។ " +
                    "កម្មវិធីមិនត្រូវការគណនី និងមិនផ្ញើទិន្នន័យទៅម៉ាស៊ីនមេណាមួយឡើយ។ " +
                    "អ៊ីនធឺណិតត្រូវការតែពេលទាញយកម៉ូដែល AI ដែលជាជម្រើសប៉ុណ្ណោះ។",
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Paragraph(
                title = "ការគណនាចន្ទគតិ",
                body = "ការគណនាប្រតិទិនចន្ទគតិខ្មែរ (ចន្ទគតិ) ធ្វើតាមវិធីសាស្ត្រ អាហារគុណ " +
                    "អាវមាន និងបូតិថី ដែលបានចេញផ្សាយដោយ Phylypo Tum (Cam-CC) " +
                    "និងអនុវត្តក្នុងគម្រោង momentkh។ " +
                    "ដែនកំណត់៖ ${localeNumber(Chhankitek.MIN_DATE.year)} ដល់ " +
                    "${localeNumber(Chhankitek.MAX_DATE.year)}។ " +
                    "សូមមើល docs/CALENDAR.md សម្រាប់ព័ត៌មានលម្អិត។",
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Paragraph(
                title = "បុណ្យជាតិ",
                body = "បុណ្យដែលមានកាលបរិច្ឆេទថេរត្រូវបានកំណត់ជាក់លាក់។ " +
                    "បុណ្យតាមចន្ទគតិត្រូវបានគណនា។ " +
                    "កាលបរិច្ឆេទឈប់សម្រាកផ្លូវការត្រូវកំណត់ដោយអនុក្រឹត្យប្រចាំឆ្នាំ " +
                    "ហើយអាចខុសពីការគណនាបុរាណមួយថ្ងៃ។ " +
                    "នៅកន្លែងដែលដឹងអនុក្រឹត្យ កម្មវិធីប្រើតាមអនុក្រឹត្យ។",
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Paragraph(
                title = "តម្រូវការប្រព័ន្ធ",
                body = "Android 8.0 (API 26) ឡើងទៅ។ " +
                    "ជំនួយការ AI ត្រូវការឧបករណ៍ 64-bit និង RAM យ៉ាងតិច 3 GB។",
            )

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun Paragraph(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
