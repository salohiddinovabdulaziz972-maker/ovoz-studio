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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.ovozstudio.app.R
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.DocumentPicker
import uz.ovozstudio.app.ui.common.FileTypes
import uz.ovozstudio.app.ui.common.ParamsGroup
import uz.ovozstudio.app.ui.common.RecentFilesBlock
import uz.ovozstudio.app.ui.common.StatusMessage
import uz.ovozstudio.app.ui.common.WorkProgress
import uz.ovozstudio.app.ui.common.a11yGroup
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.util.Sharing
import java.io.File

/**
 * Bir nechta PDF ni bitta faylga birlashtirish.
 *
 * Ekran [uz.ovozstudio.app.ui.merge.MergeScreen] bilan bir xil ritmda
 * yuriladi: fayllar tanlanadi, tartibi belgilanadi va birlashtiriladi.
 * Farqi — bu yerda format bitta (PDF), shuning uchun mos kelmagan fayl
 * «boshqa formatda» deb emas, aniq sabab bilan qaytariladi: parol, ruxsat,
 * xotira yoki buzuq fayl.
 */
@Composable
fun PdfMergeScreen(
    onBack: () -> Unit,
    viewModel: PdfMergeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val announce = rememberAnnouncer()

    // Fayllar odatda tanlangan tartibda qoladi, shuning uchun ro'yxat YOPIQ
    // boshlanadi — «Birlashtirish» tugmasigacha yo'l fayllar sonidan
    // qat'i nazar qisqa qoladi.
    var listExpanded by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(DocumentPicker.OpenDocuments) { uris ->
        viewModel.addFiles(uris)
    }

    val result = state.result

    val busyText = when (state.busy) {
        PdfMergeBusy.NONE -> ""
        PdfMergeBusy.ADDING ->
            stringResource(R.string.pdf_merge_busy_adding, state.addingCurrent, state.addingTotal)
        PdfMergeBusy.MERGING -> stringResource(R.string.pdf_merge_busy_merging)
        PdfMergeBusy.SAVING -> stringResource(R.string.audio_busy_saving)
    }
    // Kalit — bandlik turi, matnning o'zi emas: «fayl ochilmoqda» matni har
    // bir faylda o'zgaradi, matn kalit bo'lsa raqam har faylda ovozda
    // takrorlanib, oldingi e'lonni bosib ketardi.
    LaunchedEffect(state.busy) {
        if (busyText.isNotEmpty()) announce(busyText)
    }

    val summary = stringResource(R.string.pdf_merge_summary, state.items.size, state.totalPages)
    // Kalitga `revision` ham kiradi: fayl rad etilib ro'yxat o'zgarmasa,
    // son o'zgarmaydi va e'lon umuman bo'lmay qolardi.
    LaunchedEffect(state.items.size, state.revision) {
        if (state.items.isNotEmpty()) announce(summary)
    }

    // Ro'yxat bir marta o'qiladi; har fayl qo'shilganda `revision` o'zgaradi
    // va yangi nom o'sha zahoti paydo bo'ladi.
    LaunchedEffect(state.revision) { viewModel.refreshRecent() }

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
            text = stringResource(R.string.pdf_merge_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        Text(
            text = stringResource(R.string.pdf_merge_intro),
            style = MaterialTheme.typography.bodyLarge,
        )

        A11yButton(
            label = stringResource(R.string.pdf_merge_add),
            onClick = { picker.launch(FileTypes.PDF) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isBusy,
        )

        // Ro'yxat bo'sh bo'lganda ko'rsatiladi: fayllar tanlangach ekranda
        // allaqachon tartib va amal tugmalari bor, uchinchi ro'yxat ortiqcha.
        if (state.items.isEmpty()) {
            RecentFilesBlock(
                files = state.recentFiles,
                query = state.recentQuery,
                onQueryChange = viewModel::searchRecent,
                onClear = viewModel::clearRecent,
                onPick = { picker.launch(FileTypes.PDF) },
                enabled = !state.isBusy,
            )
        }

        if (state.isBusy) {
            val mergeProgress = state.mergeProgress
            val progressLabel = if (state.busy == PdfMergeBusy.MERGING && state.progress != null) {
                stringResource(
                    R.string.pdf_merge_progress,
                    state.progress?.first ?: 0,
                    state.progress?.second ?: 0,
                )
            } else {
                busyText
            }
            WorkProgress(label = progressLabel, progress = mergeProgress)
        }

        if (state.items.isNotEmpty()) {
            Column(modifier = Modifier.a11yGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.pdf_merge_list_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.a11yHeading(),
                )
                Text(text = summary, style = MaterialTheme.typography.bodyMedium)
            }

            ParamsGroup(
                summary = stringResource(R.string.pdf_merge_list_collapsed, state.items.size),
                expanded = listExpanded,
                onExpandedChange = { listExpanded = it },
            ) {
                state.items.forEachIndexed { index, item ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(
                                R.string.pdf_merge_item,
                                index + 1,
                                item.name,
                                item.pageCount,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        A11yOutlinedButton(
                            label = stringResource(R.string.pdf_merge_move_up),
                            description = item.name,
                            onClick = { viewModel.move(item.id, -1) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isBusy && index > 0,
                        )
                        A11yOutlinedButton(
                            label = stringResource(R.string.pdf_merge_move_down),
                            description = item.name,
                            onClick = { viewModel.move(item.id, 1) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isBusy && index < state.items.size - 1,
                        )
                        A11yOutlinedButton(
                            label = stringResource(R.string.pdf_merge_remove),
                            description = item.name,
                            onClick = { viewModel.remove(item.id) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isBusy,
                        )
                    }
                }
            }

            A11yButton(
                label = stringResource(R.string.pdf_merge_apply),
                onClick = { viewModel.merge() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isBusy && state.items.size >= 2,
            )
            if (state.items.size < 2) {
                Text(
                    text = stringResource(R.string.pdf_merge_need_two_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (result != null) {
            Text(
                text = stringResource(
                    R.string.pdf_merge_result,
                    result.name,
                    state.totalPages,
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
                                val started =
                        Sharing.share(context, File(result.path), result.mimeType, shareTitle)
                    if (!started) announce(shareFailed)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isBusy,
            )
        }

        if (state.failures.isNotEmpty()) {
            // Matn Compose ichida yasaladi: `joinToString` lambdasi
            // @Composable emas, shuning uchun `stringResource` ni to'g'ridan
            // to'g'ri chaqirib bo'lmaydi.
            val failureText = state.failures.joinToString("\n") { failure ->
                context.getString(R.string.pdf_merge_error_file, failure.fileName, failure.text)
            }
            StatusMessage(
                message = failureText,
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
                message = stringResource(R.string.trim_saved, name, state.savedTo ?: name),
                onDismiss = { viewModel.clearSaved() },
            )
        }
    }
}

@Composable
private fun PdfMergeError.message(): String = stringResource(
    when (this) {
        PdfMergeError.NEED_TWO -> R.string.pdf_merge_error_need_two
        PdfMergeError.MERGE_FAILED -> R.string.pdf_merge_error_failed
        PdfMergeError.SAVE_FAILED -> R.string.trim_error_save_failed
    },
)
