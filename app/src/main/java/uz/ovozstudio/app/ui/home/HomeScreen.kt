package uz.ovozstudio.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.ovozstudio.app.R
import uz.ovozstudio.app.media.Recording
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.util.TimeFormat

@Composable
fun HomeScreen(
    onRecord: () -> Unit,
    onOpenFile: (String) -> Unit,
    onConvertFile: (String) -> Unit,
    onConvert: () -> Unit,
    onEqFile: (String) -> Unit,
    onEq: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val recordings by viewModel.recordings.collectAsState()
    var pendingDelete by remember { mutableStateOf<Recording?>(null) }

    // Ro'yxat har safar ekranga qaytilganda qayta o'qiladi. ViewModel ekrandan
    // uzoq yashaydi, shuning uchun faqat `init` dagi o'qish yetarli emas edi:
    // yangi yozuv yoki saqlangan tahrir ro'yxatda ko'rinmay qolardi.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Edge-to-edge rejimida tizim panellari ostiga tushmaslik uchun.
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
        )

        A11yButton(
            label = stringResource(R.string.home_action_record),
            onClick = onRecord,
            modifier = Modifier.fillMaxWidth(),
        )

        A11yOutlinedButton(
            label = stringResource(R.string.home_action_convert),
            onClick = onConvert,
            modifier = Modifier.fillMaxWidth(),
        )

        A11yOutlinedButton(
            label = stringResource(R.string.eq_title),
            onClick = onEq,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.home_recent_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        if (recordings.isEmpty()) {
            Text(
                text = stringResource(R.string.home_empty),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(recordings, key = { it.file.absolutePath }) { recording ->
                    RecordingRow(
                        recording = recording,
                        onOpen = { onOpenFile(recording.file.absolutePath) },
                        onConvert = { onConvertFile(recording.file.absolutePath) },
                        onEq = { onEqFile(recording.file.absolutePath) },
                        onDeleteRequest = { pendingDelete = recording },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(target.title) },
            confirmButton = {
                A11yButton(
                    label = stringResource(R.string.common_delete),
                    onClick = {
                        viewModel.delete(target)
                        pendingDelete = null
                    },
                )
            },
            dismissButton = {
                A11yOutlinedButton(
                    label = stringResource(R.string.common_cancel),
                    onClick = { pendingDelete = null },
                )
            },
        )
    }
}

@Composable
private fun RecordingRow(
    recording: Recording,
    onOpen: () -> Unit,
    onConvert: () -> Unit,
    onEq: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val duration = TimeFormat.format(recording.durationMs)
    val title = recording.title

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                // Butun qator bosiladi va TalkBack uni bitta element sifatida o'qiydi:
                // «yozuv-20260917-2112, 00:03:14» — fayl nomi va uzunligi birga.
                .clickable(role = Role.Button, onClickLabel = title, onClick = onOpen)
                .padding(vertical = 14.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = duration,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Konvertatsiya shu qatordan boshlanadi, chunki ilovaning o'z
        // papkasidagi fayllar tizim tanlagichida ko'rinmaydi: foydalanuvchi
        // ularni faqat shu ro'yxat orqali topa oladi.
        IconButton(onClick = onConvert) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.home_action_convert) + ": " + title,
            )
        }

        IconButton(onClick = onEq) {
            Icon(
                imageVector = Icons.Filled.Build,
                contentDescription = stringResource(R.string.eq_title) + ": " + title,
            )
        }

        IconButton(onClick = onDeleteRequest) {
            Icon(
                imageVector = Icons.Filled.Delete,
                // Yorliqsiz tugma bo'lmasligi qoidasi: nom fayl nomi bilan aytiladi,
                // shunda foydalanuvchi qaysi faylni o'chirishini biladi.
                contentDescription = stringResource(R.string.common_delete) + ": " + title,
            )
        }
    }
}
