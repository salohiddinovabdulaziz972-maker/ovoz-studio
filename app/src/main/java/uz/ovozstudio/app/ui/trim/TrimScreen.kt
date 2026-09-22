package uz.ovozstudio.app.ui.trim

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.ovozstudio.app.R
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.FileTypes
import uz.ovozstudio.app.ui.common.StatusMessage
import uz.ovozstudio.app.ui.common.TimeInput
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.fileSummary
import uz.ovozstudio.app.ui.common.importFailureMessage
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.ui.common.spokenTime
import uz.ovozstudio.app.util.Sharing
import uz.ovozstudio.app.util.TimeFormat
import java.io.File

/**
 * Audioni kesib olish yoki undan qism o'chirish.
 *
 * Ikkala rejim bitta ekran: farq faqat sarlavha, tushuntirish va bitta
 * amal tugmasida. Natija **hamisha yuklangan faylning formatida** chiqadi.
 */
@Composable
fun TrimScreen(
    mode: TrimMode,
    onBack: () -> Unit,
    viewModel: TrimViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val announce = rememberAnnouncer()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(uri)
    }

    // «Saqlash» oynasining fayl turi natijaga qarab o'zgaradi (MP3, M4A, WAV…).
    val result = state.result
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(result?.mimeType ?: DEFAULT_MIME),
    ) { uri ->
        if (uri != null) viewModel.saveTo(uri)
    }

    // Uzoq ish boshlanganda ovozda aytiladi: ekran o'quvchi foydalanuvchisi
    // ekranni ko'rmaydi, «hech narsa bo'lmayapti» deb o'ylamasligi kerak.
    val busyText = when (state.busy) {
        TrimBusy.NONE -> ""
        TrimBusy.OPENING -> stringResource(R.string.audio_busy_opening)
        TrimBusy.EDITING -> stringResource(R.string.audio_busy_editing)
        TrimBusy.PREPARING -> stringResource(R.string.audio_busy_preparing)
        TrimBusy.SAVING -> stringResource(R.string.audio_busy_saving)
    }
    LaunchedEffect(busyText) {
        if (busyText.isNotEmpty()) announce(busyText)
    }

    val spokenDuration = spokenTime(state.durationMs)
    val openedMessage = stringResource(R.string.audio_opened, state.fileName, state.formatName, spokenDuration)
    LaunchedEffect(state.fileName, state.formatName) {
        if (state.isOpen) announce(openedMessage)
    }

    val editDoneMessage = stringResource(R.string.trim_edit_done, spokenDuration)
    LaunchedEffect(state.revision) {
        if (state.revision > 0) announce(editDoneMessage)
    }

    val startMs = viewModel.selectionStartMs(state)
    val endMs = viewModel.selectionEndMs(state)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        A11yOutlinedButton(
            label = stringResource(R.string.common_back),
            onClick = {
                viewModel.stopPlayback()
                onBack()
            },
        )

        Text(
            text = stringResource(if (mode == TrimMode.CUT) R.string.trim_title_cut else R.string.trim_title_delete),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        Text(
            text = stringResource(if (mode == TrimMode.CUT) R.string.trim_intro_cut else R.string.trim_intro_delete),
            style = MaterialTheme.typography.bodyLarge,
        )

        A11yButton(
            label = stringResource(if (state.isOpen) R.string.audio_pick_other else R.string.audio_pick),
            onClick = { picker.launch(FileTypes.AUDIO) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isBusy,
        )

        if (state.isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(text = busyText, style = MaterialTheme.typography.bodyMedium)
        }

        val info = state.info
        if (info != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.audio_file_name, state.fileName),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.audio_format_line, state.formatName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.trim_file_duration, TimeFormat.format(state.durationMs)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = fileSummary(info, state.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                text = stringResource(R.string.trim_selection_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.a11yHeading(),
            )

            TimeInput(
                label = stringResource(R.string.trim_start_label),
                parts = state.startParts,
                onPartsChange = viewModel::setStart,
                isError = !state.startParts.isEmpty && state.startParts.toMillisOrNull() == null,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            )

            TimeInput(
                label = stringResource(R.string.trim_end_label),
                parts = state.endParts,
                onPartsChange = viewModel::setEnd,
                isError = !state.endParts.isEmpty && state.endParts.toMillisOrNull() == null,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(
                        R.string.trim_selection_range,
                        TimeFormat.format(startMs),
                        TimeFormat.format(endMs),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(
                        R.string.trim_duration_a11y,
                        spokenTime((endMs - startMs).coerceAtLeast(0)),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (state.isPlaying) {
                A11yOutlinedButton(
                    label = stringResource(R.string.trim_stop),
                    onClick = { viewModel.stopPlayback() },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                A11yOutlinedButton(
                    label = stringResource(R.string.trim_play),
                    onClick = { viewModel.playSelection() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isBusy && endMs > startMs,
                )
            }

            // Bitta amal tugmasi: rejimga qarab «kesib olish» yoki «o'chirish».
            A11yButton(
                label = stringResource(
                    if (mode == TrimMode.CUT) R.string.trim_apply_cut else R.string.trim_apply_delete,
                ),
                onClick = {
                    if (mode == TrimMode.CUT) viewModel.cutSelection() else viewModel.deleteSelection()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isBusy,
            )

            A11yOutlinedButton(
                label = stringResource(R.string.trim_undo),
                onClick = { viewModel.undo() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canUndo && !state.isBusy,
            )

            Text(
                text = stringResource(R.string.trim_result_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.a11yHeading(),
            )
            Text(
                text = stringResource(R.string.trim_result_note, state.formatName),
                style = MaterialTheme.typography.bodyMedium,
            )

            A11yButton(
                label = stringResource(R.string.trim_prepare),
                onClick = { viewModel.prepareResult(mode) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canUndo && !state.isBusy,
            )
            if (!state.canUndo) {
                Text(
                    text = stringResource(R.string.trim_prepare_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (result != null) {
                Text(
                    text = stringResource(
                        R.string.trim_result_ready,
                        result.name,
                        Formatter.formatShortFileSize(context, result.sizeBytes),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                A11yButton(
                    label = stringResource(R.string.trim_save),
                    onClick = { saver.launch(result.name) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isBusy,
                )
                val shareTitle = stringResource(R.string.common_share_title)
                val shareFailed = stringResource(R.string.common_share_failed)
                A11yOutlinedButton(
                    label = stringResource(R.string.trim_share),
                    onClick = {
                        val started = Sharing.share(context, File(result.path), result.mimeType, shareTitle)
                        if (!started) announce(shareFailed)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isBusy,
                )
            }
        }

        state.error?.let { error ->
            StatusMessage(
                message = error.message(),
                onDismiss = { viewModel.clearError() },
                isError = true,
            )
        }

        state.openFailure?.let { failure ->
            StatusMessage(
                message = importFailureMessage(failure, state.openFailureFormat),
                onDismiss = { viewModel.clearOpenFailure() },
                isError = true,
            )
        }

        state.savedName?.let { name ->
            StatusMessage(
                message = stringResource(R.string.trim_saved, name),
                onDismiss = { viewModel.clearSaved() },
            )
        }
    }
}

/** «Saqlash» oynasi uchun zaxira tur: natija hali yo'q paytda ham kontrakt yaratilishi kerak. */
private const val DEFAULT_MIME = "audio/*"

@Composable
private fun TrimError.message(): String = stringResource(
    when (this) {
        TrimError.SELECTION_EMPTY -> R.string.trim_error_selection_empty
        TrimError.SELECTION_ALL -> R.string.trim_error_selection_all
        TrimError.NOTHING_TO_UNDO -> R.string.trim_error_nothing_to_undo
        TrimError.EDIT_FAILED -> R.string.trim_error_edit_failed
        TrimError.EXPORT_FAILED -> R.string.trim_error_export_failed
        TrimError.SAVE_FAILED -> R.string.trim_error_save_failed
    },
)
