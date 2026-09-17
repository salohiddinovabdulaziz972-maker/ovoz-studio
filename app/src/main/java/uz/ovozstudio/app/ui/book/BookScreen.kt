package uz.ovozstudio.app.ui.book

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
import uz.ovozstudio.app.ui.common.NumericRow
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.util.SpeedText
import uz.ovozstudio.app.util.TimeFormat

/**
 * Hujjatdan audio-kitob ekrani.
 *
 * Ekranning oqimi uch qadamdan iborat va uchtasi ham ko'rinadi: hujjat
 * tanlanadi, ilova uni boblarga bo'lib ko'rsatadi (nima o'qilishini oldindan
 * bilish uchun), keyin kitob yasaladi. Har bir bob — alohida MP3 fayl.
 *
 * Tezlik va balandlik — ovoz sinovi ekranidagi bilan **bir xil maydonlar**:
 * foydalanuvchi bir xil birlikni (yarim ton) ko'radi va o'rgangan joyini
 * qayta qidirolmaydi. Satrlar `NumericRow` orqali qo'lda kiritiladi —
 * ilova bo'ylab yagona qoida.
 */
@Composable
fun BookScreen(
    onBack: () -> Unit,
    viewModel: BookViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val announce = rememberAnnouncer()

    // Turli fayl menejerlari va bulut xizmatlari bir xil faylni turli MIME
    // bilan beradi (Telegram yuklagan DOCX ba'zan `application/octet-stream`).
    // Shu sababli "*/*" ham qo'shiladi: tanlash imkoni har doim bo'lsin,
    // yaroqsiz fayl esa o'z xatosi bilan qaytariladi.
    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(uri)
    }

    // Kitob tayyor bo'lganda bir marta e'lon qilinadi: har bo'lakda emas —
    // aks holda ekran o'quvchi butun yasash davomida gaplashib chiqardi.
    LaunchedEffect(state.output) {
        if (state.output.isEmpty()) return@LaunchedEffect
        announce(
            context.getString(
                R.string.book_result,
                state.output.size,
                TimeFormat.format(state.durationMs),
            )
        )
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
            text = stringResource(R.string.book_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        Text(
            text = stringResource(R.string.book_hint),
            style = MaterialTheme.typography.bodyLarge,
        )

        A11yButton(
            label = if (state.hasDocument) {
                stringResource(R.string.book_pick_other)
            } else {
                stringResource(R.string.book_pick)
            },
            onClick = { pickDocument.launch(DOCUMENT_MIME) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )

        // Dvigatel holati: tugma nima uchun o'chiq turgani ko'rinishi kerak.
        if (!state.ready) {
            Text(
                text = stringResource(R.string.voice_preparing),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.hasDocument) {
            Text(
                text = stringResource(R.string.book_file, state.documentName),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.book_format, state.formatName),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.book_chapters, state.chapterCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.book_chars, state.charCount),
                style = MaterialTheme.typography.bodyMedium,
            )

            val tag = state.languageTag
            if (tag != null) {
                Text(
                    text = stringResource(R.string.voice_language, tag),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = stringResource(R.string.voice_language_missing),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Boblar ro'yxati — foydalanuvchi uzoq kitobni yasashdan oldin
            // bo'linish to'g'ri bo'lganiga ishonch hosil qiladi.
            Text(
                text = stringResource(R.string.book_titles_header),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.a11yHeading(),
            )
            state.titles.forEachIndexed { index, title ->
                Text(
                    text = "${index + 1}. $title",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val rest = state.chapterCount - state.titles.size
            if (rest > 0) {
                Text(
                    text = stringResource(R.string.book_titles_more, rest),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        NumericRow(
            label = stringResource(R.string.voice_rate_label),
            value = state.rate,
            onValueChange = viewModel::setRate,
            onNudge = viewModel::nudgeRate,
            step = SpeedText.STEP_SPEED,
            decreaseDescription = stringResource(R.string.eq_gain_decrease),
            increaseDescription = stringResource(R.string.eq_gain_increase),
            enabled = state.ready && !state.busy,
        )
        Text(
            text = stringResource(
                R.string.voice_rate_note,
                SpeedText.formatSpeed(SpeedText.MIN_SPEED),
                SpeedText.formatSpeed(SpeedText.MAX_SPEED),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )

        NumericRow(
            label = stringResource(R.string.voice_pitch_label),
            value = state.semitones,
            onValueChange = viewModel::setSemitones,
            onNudge = viewModel::nudgeSemitones,
            step = SpeedText.STEP_SEMITONES,
            decreaseDescription = stringResource(R.string.eq_gain_decrease),
            increaseDescription = stringResource(R.string.eq_gain_increase),
            enabled = state.ready && !state.busy,
        )
        Text(
            text = stringResource(R.string.voice_pitch_note),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (state.busy) {
            A11yButton(
                label = stringResource(R.string.voice_stop),
                onClick = viewModel::cancel,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(
                    R.string.book_busy,
                    state.chapterNumber,
                    state.done,
                    state.total,
                    state.percent,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            A11yButton(
                label = stringResource(R.string.book_build),
                onClick = viewModel::build,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.ready && state.hasDocument,
            )
        }

        if (state.output.isNotEmpty()) {
            Text(
                text = stringResource(R.string.book_result_header),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.a11yHeading(),
            )
            Text(
                text = stringResource(
                    R.string.book_result,
                    state.output.size,
                    TimeFormat.format(state.durationMs),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            state.output.forEach { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = stringResource(R.string.book_saved_note),
                style = MaterialTheme.typography.bodyMedium,
            )
            A11yOutlinedButton(
                label = stringResource(R.string.common_ok),
                onClick = viewModel::consumeOutput,
            )
        }

        state.error?.let { error ->
            Text(
                text = stringResource(error.messageRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            A11yOutlinedButton(
                label = stringResource(R.string.common_ok),
                onClick = viewModel::dismissError,
            )
        }
    }
}

/** Fayl tanlagichga beriladigan turlar. */
private val DOCUMENT_MIME = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/epub+zip",
    "text/plain",
    "text/markdown",
    "*/*",
)

private fun BookUiError.messageRes(): Int = when (this) {
    BookUiError.NO_TEXT -> R.string.book_error_no_text
    BookUiError.UNSUPPORTED_FORMAT -> R.string.book_error_unsupported
    BookUiError.TOO_LARGE -> R.string.book_error_too_large
    BookUiError.BROKEN_DOCUMENT -> R.string.book_error_broken
    BookUiError.EMPTY_DOCUMENT -> R.string.book_error_empty
    BookUiError.VOICE_MISSING -> R.string.voice_error_not_available
    BookUiError.SPEAK_FAILED -> R.string.book_error_speak_failed
    BookUiError.WRITE_FAILED -> R.string.book_error_write_failed
    BookUiError.CANCELLED -> R.string.book_error_cancelled
}
