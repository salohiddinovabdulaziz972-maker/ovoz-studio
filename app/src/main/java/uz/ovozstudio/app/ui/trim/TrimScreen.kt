package uz.ovozstudio.app.ui.trim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.ovozstudio.app.R
import uz.ovozstudio.app.media.AudioTrimmer
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.SwitchRow
import uz.ovozstudio.app.ui.common.TimeInput
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.ui.common.spokenTime
import uz.ovozstudio.app.util.TimeFormat
import uz.ovozstudio.app.util.TimeParts

@Composable
fun TrimScreen(
    filePath: String,
    onBack: () -> Unit,
    viewModel: TrimViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val announce = rememberAnnouncer()

    LaunchedEffect(filePath) { viewModel.load(filePath) }

    LaunchedEffect(state.savedPath) {
        if (state.savedPath != null) {
            announce(context.getString(R.string.trim_saved, state.savedPath!!.substringAfterLast('/')))
        }
    }

    LaunchedEffect(state.splitSecondPath) {
        if (state.splitSecondPath != null) {
            announce(
                context.getString(
                    R.string.trim_split_done,
                    state.splitSecondPath!!.substringAfterLast('/'),
                ),
            )
        }
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
            text = stringResource(R.string.trim_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        if (state.fileName.isNotEmpty()) {
            Text(text = state.fileName, style = MaterialTheme.typography.bodyLarge)
        }

        Text(
            text = stringResource(R.string.trim_file_duration, TimeFormat.format(state.durationMs)),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (state.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )

        TimeInput(
            label = stringResource(R.string.trim_end_label),
            parts = state.endParts,
            onPartsChange = viewModel::setEnd,
            isError = !state.endParts.isEmpty && state.endParts.toMillisOrNull() == null,
            enabled = !state.busy,
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
            A11yButton(
                label = stringResource(R.string.trim_stop),
                onClick = { viewModel.stopPlayback() },
            )
        } else {
            A11yButton(
                label = stringResource(R.string.trim_play),
                onClick = { viewModel.playSelection() },
                enabled = !state.busy && endMs > startMs,
            )
        }

        FadeBlock(
            fadeIn = state.fadeIn,
            fadeOut = state.fadeOut,
            fadeMs = state.fadeMs,
            enabled = !state.busy,
            onFadeIn = viewModel::setFadeIn,
            onFadeOut = viewModel::setFadeOut,
            onFadeMs = viewModel::setFadeMs,
        )

        SplitBlock(
            parts = state.splitParts,
            enabled = !state.busy,
            onPartsChange = viewModel::setSplitPoint,
            onApply = { viewModel.applySplit() },
        )

        MultiCutBlock(
            cuts = state.cuts,
            enabled = !state.busy,
            onAdd = { viewModel.addCut() },
            onRemove = { index -> viewModel.removeCut(index) },
            onClear = { viewModel.clearCuts() },
            onApply = { viewModel.applyCuts() },
        )

        EditControls(
            state = state,
            onApply = { viewModel.applyTrim() },
            onDelete = { viewModel.deleteSelection() },
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            onSave = { viewModel.save() },
        )

        state.error?.let { error ->
            Text(
                text = error.message(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            A11yOutlinedButton(
                label = stringResource(R.string.common_ok),
                onClick = { viewModel.clearError() },
            )
        }

        state.savedPath?.let { path ->
            Text(
                text = stringResource(R.string.trim_saved, path.substringAfterLast('/')),
                style = MaterialTheme.typography.bodyLarge,
            )
            A11yOutlinedButton(
                label = stringResource(R.string.common_ok),
                onClick = { viewModel.consumeSaved() },
            )
        }

        state.splitSecondPath?.let { path ->
            Text(
                text = stringResource(R.string.trim_split_done, path.substringAfterLast('/')),
                style = MaterialTheme.typography.bodyLarge,
            )
            A11yOutlinedButton(
                label = stringResource(R.string.common_ok),
                onClick = { viewModel.consumeSplit() },
            )
        }
    }
}

@Composable
private fun FadeBlock(
    fadeIn: Boolean,
    fadeOut: Boolean,
    fadeMs: String,
    enabled: Boolean,
    onFadeIn: (Boolean) -> Unit,
    onFadeOut: (Boolean) -> Unit,
    onFadeMs: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchRow(
            label = stringResource(R.string.trim_fade_in),
            checked = fadeIn,
            onCheckedChange = onFadeIn,
            enabled = enabled,
        )
        SwitchRow(
            label = stringResource(R.string.trim_fade_out),
            checked = fadeOut,
            onCheckedChange = onFadeOut,
            enabled = enabled,
        )
        if (fadeIn || fadeOut) {
            OutlinedTextField(
                value = fadeMs,
                onValueChange = onFadeMs,
                label = { Text(stringResource(R.string.trim_fade_length_label)) },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.width(160.dp),
            )
        }
    }
}

/**
 * Faylni ikki qismga bo'lish.
 *
 * Bo'lish nuqtasi alohida maydonda kiritiladi — tanlovning «boshlanishi» ni
 * bo'lish nuqtasi sifatida ishlatish mumkin edi, lekin u holda bitta maydon
 * ikki xil ma'noni bildirardi va ekran o'quvchi foydalanuvchisi qaysi raqam
 * nimaga tegishli ekanini bilib olmasdi.
 */
@Composable
private fun SplitBlock(
    parts: TimeParts,
    enabled: Boolean,
    onPartsChange: (TimeParts) -> Unit,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.trim_split_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )
        Text(
            text = stringResource(R.string.trim_split_note),
            style = MaterialTheme.typography.bodyMedium,
        )
        TimeInput(
            label = stringResource(R.string.trim_split_label),
            parts = parts,
            onPartsChange = onPartsChange,
            isError = !parts.isEmpty && parts.toMillisOrNull() == null,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        // Tugma maydon to'ldirilmagan bo'lsa ham bosiladi: sabab umumiy
        // «bo'lmadi» emas, aniq aytilishi kerak.
        A11yOutlinedButton(
            label = stringResource(R.string.trim_split_apply),
            onClick = onApply,
            enabled = enabled,
        )
    }
}

/**
 * Ko'p nuqtali o'chirish: bo'laklar avval ro'yxatga yig'iladi, keyin bir marta
 * o'chiriladi. Har bir bo'lak uchun «olib tashlash» tugmasi bor — bekor qilish
 * uchun tarixga qaytish shart emas.
 */
@Composable
private fun MultiCutBlock(
    cuts: List<AudioTrimmer.Cut>,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.trim_cuts_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )
        Text(
            text = stringResource(R.string.trim_cuts_note),
            style = MaterialTheme.typography.bodyMedium,
        )
        A11yOutlinedButton(
            label = stringResource(R.string.trim_cut_add),
            onClick = onAdd,
            enabled = enabled,
        )

        if (cuts.isNotEmpty()) {
            Text(
                text = stringResource(R.string.trim_cut_count, cuts.size),
                style = MaterialTheme.typography.bodyLarge,
            )
            cuts.forEachIndexed { index, cut ->
                val range = stringResource(
                    R.string.trim_selection_range,
                    TimeFormat.format(cut.startMs),
                    TimeFormat.format(cut.endMs),
                )
                // Har bir tugma o'z bo'lagini nomlaydi: «olib tashlash» degan
                // yorliq bilan ekran o'quvchi qaysi bo'lak o'chishini aytmasdi.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = range, style = MaterialTheme.typography.bodyLarge)
                    A11yOutlinedButton(
                        label = stringResource(R.string.trim_cut_remove, range),
                        onClick = { onRemove(index) },
                        enabled = enabled,
                    )
                }
            }
            A11yOutlinedButton(
                label = stringResource(R.string.trim_cut_clear),
                onClick = onClear,
                enabled = enabled,
            )
            A11yButton(
                label = stringResource(R.string.trim_cut_apply),
                onClick = onApply,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun EditControls(
    state: TrimUiState,
    onApply: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            A11yOutlinedButton(
                label = stringResource(R.string.trim_undo),
                onClick = onUndo,
                enabled = state.canUndo && !state.busy,
            )
            A11yOutlinedButton(
                label = stringResource(R.string.trim_redo),
                onClick = onRedo,
                enabled = state.canRedo && !state.busy,
            )
        }

        A11yButton(
            label = stringResource(R.string.trim_apply),
            onClick = onApply,
            enabled = !state.busy,
        )

        A11yOutlinedButton(
            label = stringResource(R.string.trim_delete),
            onClick = onDelete,
            enabled = !state.busy,
        )

        A11yButton(
            label = stringResource(R.string.trim_save),
            onClick = onSave,
            enabled = !state.busy,
        )
    }
}

/** Xatolik kodini joriy tilga o'giradi. */
@Composable
private fun TrimError.message(): String = stringResource(
    when (this) {
        TrimError.FILE_NOT_FOUND -> R.string.trim_error_file_not_found
        TrimError.SELECTION_EMPTY -> R.string.trim_error_selection_empty
        TrimError.SELECTION_ALL -> R.string.trim_error_selection_all
        TrimError.NOTHING_TO_UNDO -> R.string.trim_error_nothing_to_undo
        TrimError.CUTS_EMPTY -> R.string.trim_error_cuts_empty
        TrimError.CUTS_ALL -> R.string.trim_error_cuts_all
        TrimError.SPLIT_POINT_INVALID -> R.string.trim_error_split_point
        TrimError.EDIT_FAILED -> R.string.trim_error_edit_failed
        TrimError.SAVE_FAILED -> R.string.trim_error_save_failed
    },
)
