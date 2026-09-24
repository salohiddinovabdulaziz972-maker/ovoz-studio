package uz.ovozstudio.app.ui.pdf

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import uz.ovozstudio.app.ui.common.DocumentPicker
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.FileTypes
import uz.ovozstudio.app.ui.common.StatusMessage
import uz.ovozstudio.app.ui.common.TextRow
import uz.ovozstudio.app.ui.common.WorkProgress
import uz.ovozstudio.app.ui.common.a11yGroup
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.util.Sharing
import java.io.File

/**
 * PDF dan sahifalarni kesib olish yoki o'chirish.
 *
 * Ikkala rejim bitta ekran: farq faqat sarlavha, tushuntirish va bitta amal
 * tugmasida. Sahifalar bitta matn maydonida yoziladi (`3, 5-8`).
 */
@Composable
fun PdfScreen(
    mode: PdfMode,
    onBack: () -> Unit,
    viewModel: PdfViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val announce = rememberAnnouncer()

    val picker = rememberLauncherForActivityResult(DocumentPicker.OpenAny) { uri ->
        if (uri != null) viewModel.open(uri)
    }

    // Uzoq ish boshlanganda ovozda aytiladi: ekran o'quvchi foydalanuvchisi
    // «hech narsa bo'lmayapti» deb o'ylamasligi kerak.
    val busyText = when (state.busy) {
        PdfBusy.NONE -> ""
        PdfBusy.OPENING -> stringResource(R.string.pdf_busy_opening)
        PdfBusy.WORKING -> stringResource(R.string.pdf_busy_working)
        PdfBusy.SAVING -> stringResource(R.string.audio_busy_saving)
    }
    LaunchedEffect(busyText) {
        if (busyText.isNotEmpty()) announce(busyText)
    }

    val openedMessage = stringResource(R.string.pdf_opened, state.fileName, state.pageCount)
    LaunchedEffect(state.fileName, state.pageCount) {
        if (state.isOpen) announce(openedMessage)
    }

    val result = state.result

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
            text = stringResource(if (mode == PdfMode.CUT) R.string.pdf_title_cut else R.string.pdf_title_delete),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        Text(
            text = stringResource(if (mode == PdfMode.CUT) R.string.pdf_intro_cut else R.string.pdf_intro_delete),
            style = MaterialTheme.typography.bodyLarge,
        )

        A11yButton(
            label = stringResource(if (state.isOpen) R.string.pdf_pick_other else R.string.pdf_pick),
            onClick = { picker.launch(FileTypes.PDF) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isBusy,
        )

        if (state.isBusy) {
            WorkProgress(label = busyText, progress = state.progress)
        }

        if (state.isOpen) {
            Column(modifier = Modifier.a11yGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.pdf_file_name, state.fileName),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.pdf_page_count, state.pageCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            TextRow(
                label = stringResource(R.string.pdf_pages_label),
                value = state.pagesText,
                onValueChange = viewModel::setPages,
                enabled = !state.isBusy,
                hint = stringResource(R.string.pdf_pages_hint, state.pageCount),
            )

            A11yButton(
                label = stringResource(if (mode == PdfMode.CUT) R.string.pdf_apply_cut else R.string.pdf_apply_delete),
                onClick = { viewModel.apply(mode) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isBusy,
            )
        }

        if (result != null) {
            Text(
                text = stringResource(R.string.pdf_result_pages, state.resultPages),
                style = MaterialTheme.typography.bodyLarge,
            )
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
                onClick = { viewModel.save() },
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

        state.error?.let { error ->
            StatusMessage(
                message = errorMessage(error, state.errorToken, state.pageCount),
                onDismiss = { viewModel.clearError() },
                isError = true,
            )
        }

        state.savedName?.let { name ->
            StatusMessage(
                message = stringResource(R.string.trim_saved, name, state.savedTo ?: name),
                onDismiss = { viewModel.clearSaved() },
            )
        }
    }
}

@Composable
private fun errorMessage(error: PdfError, token: String, pageCount: Int): String = when (error) {
    PdfError.RANGE_EMPTY -> stringResource(R.string.pdf_error_range_empty)
    PdfError.RANGE_SYNTAX -> stringResource(R.string.pdf_error_range_syntax, token)
    PdfError.RANGE_OUT_OF_RANGE -> stringResource(R.string.pdf_error_range_out, token, pageCount)
    PdfError.RANGE_REVERSED -> stringResource(R.string.pdf_error_range_reversed, token)
    PdfError.DELETE_ALL -> stringResource(R.string.pdf_error_delete_all)
    PdfError.PASSWORD -> stringResource(R.string.pdf_error_password)
    PdfError.PROTECTED -> stringResource(R.string.pdf_error_protected)
    PdfError.TOO_LARGE -> stringResource(R.string.pdf_error_too_large)
    PdfError.FAILED -> stringResource(R.string.pdf_error_failed)
    PdfError.SAVE_FAILED -> stringResource(R.string.trim_error_save_failed)
}
