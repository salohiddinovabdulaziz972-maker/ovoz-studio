package uz.ovozstudio.app.ui.merge

import android.content.Context
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
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.importFailureText
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.ui.common.spokenTime
import uz.ovozstudio.app.util.Sharing
import java.io.File

/**
 * Bir nechta audio faylni ketma-ket birlashtirish.
 *
 * Foydalanuvchi fayllarni tanlaydi, tartibini belgilaydi (yuqoriga / pastga)
 * va birlashtiradi. Natija fayllar formatida chiqadi — hammasi bir xil
 * formatda bo'lishi shart, shuning uchun boshqa formatdagi fayl ro'yxatga
 * qo'shilmaydi va sababi aytiladi.
 */
@Composable
fun MergeScreen(
    onBack: () -> Unit,
    viewModel: MergeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val announce = rememberAnnouncer()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addFiles(uris)
    }

    val result = state.result
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(result?.mimeType ?: DEFAULT_MIME),
    ) { uri ->
        if (uri != null) viewModel.saveTo(uri)
    }

    val busyText = when (state.busy) {
        MergeBusy.NONE -> ""
        MergeBusy.ADDING -> stringResource(R.string.merge_busy_adding, state.addingCurrent, state.addingTotal)
        MergeBusy.MERGING -> stringResource(R.string.merge_busy_merging)
        MergeBusy.SAVING -> stringResource(R.string.audio_busy_saving)
    }
    LaunchedEffect(busyText) {
        if (busyText.isNotEmpty()) announce(busyText)
    }

    val summary = stringResource(R.string.merge_summary, state.items.size, spokenTime(state.totalDurationMs))
    LaunchedEffect(state.items.size) {
        if (state.items.isNotEmpty()) announce(summary)
    }

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
            onClick = onBack,
        )

        Text(
            text = stringResource(R.string.merge_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        Text(
            text = stringResource(R.string.merge_intro),
            style = MaterialTheme.typography.bodyLarge,
        )

        A11yButton(
            label = stringResource(R.string.merge_add),
            onClick = { picker.launch(FileTypes.AUDIO) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isBusy,
        )

        if (state.isBusy) {
            if (state.busy == MergeBusy.MERGING) {
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(text = busyText, style = MaterialTheme.typography.bodyMedium)
        }

        if (state.items.isNotEmpty()) {
            Text(
                text = stringResource(R.string.merge_list_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.a11yHeading(),
            )
            Text(
                text = stringResource(R.string.merge_format_line, state.formatName),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(text = summary, style = MaterialTheme.typography.bodyMedium)

            state.items.forEachIndexed { index, item ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.merge_item, index + 1, item.name, spokenTime(item.durationMs)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    A11yOutlinedButton(
                        label = stringResource(R.string.merge_move_up),
                        description = item.name,
                        onClick = { viewModel.move(item.id, -1) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isBusy && index > 0,
                    )
                    A11yOutlinedButton(
                        label = stringResource(R.string.merge_move_down),
                        description = item.name,
                        onClick = { viewModel.move(item.id, 1) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isBusy && index < state.items.size - 1,
                    )
                    A11yOutlinedButton(
                        label = stringResource(R.string.merge_remove),
                        description = item.name,
                        onClick = { viewModel.remove(item.id) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isBusy,
                    )
                }
            }

            A11yButton(
                label = stringResource(R.string.merge_apply),
                onClick = { viewModel.merge() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isBusy && state.items.size >= 2,
            )
            if (state.items.size < 2) {
                Text(
                    text = stringResource(R.string.merge_need_two_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
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

        if (state.failures.isNotEmpty()) {
            StatusMessage(
                message = state.failures.joinToString("\n") { failure -> failureText(context, failure) },
                onDismiss = { viewModel.clearFailures() },
                isError = true,
            )
        }

        state.error?.let { error ->
            StatusMessage(
                message = error.message(),
                onDismiss = { viewModel.clearError() },
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

/**
 * Bitta rad etilgan fayl haqidagi xabar. [Context] orqali: matn ro'yxat
 * yig'ilayotganda hosil bo'ladi va bu yerda Compose chaqiruvi mumkin emas.
 */
private fun failureText(context: Context, failure: MergeFailure): String {
    val reason = failure.reason
    return if (reason == null) {
        context.getString(
            R.string.merge_error_mixed,
            failure.fileName,
            failure.formatName,
            failure.listFormatName,
        )
    } else {
        context.getString(
            R.string.merge_error_file,
            failure.fileName,
            importFailureText(context, reason, failure.formatName),
        )
    }
}

@Composable
private fun MergeError.message(): String = stringResource(
    when (this) {
        MergeError.NEED_TWO -> R.string.merge_error_need_two
        MergeError.MERGE_FAILED -> R.string.merge_error_failed
        MergeError.EXPORT_FAILED -> R.string.trim_error_export_failed
        MergeError.SAVE_FAILED -> R.string.trim_error_save_failed
    },
)
