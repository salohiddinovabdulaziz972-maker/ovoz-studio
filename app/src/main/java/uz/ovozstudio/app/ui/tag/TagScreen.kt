package uz.ovozstudio.app.ui.tag

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.ovozstudio.app.R
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.TextRow
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.util.LocalizedNumber
import uz.ovozstudio.app.util.Sharing
import java.io.File
import java.util.Locale

/**
 * Teglar va ulashish ekrani.
 *
 * Oqim: fayl tanlanadi → maydonlar **mavjud teg bilan** to'ldiriladi →
 * foydalanuvchi tahrirlaydi → yangi fayl saqlanadi → uni ulash mumkin.
 *
 * Maydonlar qo'lda to'ldiriladi (ilova bo'ylab yagona qoida: sirg'anma
 * bilan aniq qiymat kiritib bo'lmaydi, ekran o'quvchi uchun ham, barmoq
 * uchun ham).
 */
@Composable
fun TagScreen(
    initialPath: String,
    onBack: () -> Unit,
    onConvert: () -> Unit,
    viewModel: TagViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val files by viewModel.files.collectAsState()
    val context = LocalContext.current
    val announce = rememberAnnouncer()

    // Yo'l berilgan bo'lsa (ilova ichidagi fayl) — darhol ochiladi.
    LaunchedEffect(initialPath) {
        if (initialPath.isNotBlank()) viewModel.openPath(initialPath)
    }

    // Ro'yxat ekranga har qaytilganda qayta o'qiladi: konvertor yoki
    // audio-kitob shu orada yangi fayl yozgan bo'lishi mumkin.
    LaunchedEffect(Unit) { viewModel.refreshFiles() }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(uri)
    }

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.setCover(uri)
    }

    LaunchedEffect(state.outputPath) {
        val path = state.outputPath ?: return@LaunchedEffect
        announce(context.getString(R.string.tag_done, path.substringAfterLast('/')))
    }

    val fieldsEnabled = state.supported && !state.busy

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
            text = stringResource(R.string.tag_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        if (state.sourcePath == null) {
            Text(text = stringResource(R.string.tag_hint), style = MaterialTheme.typography.bodyLarge)
        }

        Text(
            text = stringResource(R.string.tag_source_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )

        if (state.sourcePath != null) {
            Text(
                text = listOf(state.sourceName, state.sourceFormat)
                    .filter { it.isNotBlank() }
                    .joinToString(" — "),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        A11yButton(
            label = stringResource(R.string.tag_pick),
            onClick = { pickFile.launch(AUDIO_MIME) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )

        // Ilovaning o'z fayllari. Tizim tanlagichi bu papkani ko'rmaydi
        // (Android 11+ `Android/data` ni yopadi), shuning uchun ro'yxat shu
        // yerda beriladi — aks holda konvertor yasagan MP3 ni umuman
        // topib bo'lmasdi. Fayl ochilgach ro'yxat yashiriladi: ekranning
        // ishi — tahrirlash.
        if (state.sourcePath == null) {
            Text(
                text = stringResource(R.string.tag_local_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.a11yHeading(),
            )
            if (files.isEmpty()) {
                Text(
                    text = stringResource(R.string.tag_local_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                files.forEach { file ->
                    SourceRow(file = file, onOpen = { viewModel.openPath(file.absolutePath) })
                }
            }
        }

        if (state.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(text = stringResource(R.string.tag_busy), style = MaterialTheme.typography.bodyMedium)
        }

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

        // Format ID3 ni qo'llamasa, sabab va yo'l ko'rsatiladi: konvertor
        // shu ilovaning o'zida, foydalanuvchi boshqa dastur qidirmasin.
        if (state.sourcePath != null && !state.supported && !state.busy) {
            Text(
                text = stringResource(R.string.tag_mp3_only_note),
                style = MaterialTheme.typography.bodyMedium,
            )
            A11yOutlinedButton(
                label = stringResource(R.string.tag_convert),
                onClick = onConvert,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.tag_fields_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )

        TagField.entries.forEach { field ->
            TextRow(
                label = field.label(),
                value = field.value(state),
                onValueChange = { viewModel.setField(field, it) },
                enabled = fieldsEnabled,
                keyboardType = field.keyboard(),
                hint = if (field == TagField.TRACK) stringResource(R.string.tag_track_note) else null,
            )
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.tag_cover_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )

        Text(text = coverSummary(state), style = MaterialTheme.typography.bodyLarge)

        A11yButton(
            label = stringResource(R.string.tag_cover_pick),
            onClick = { pickCover.launch(IMAGE_MIME) },
            modifier = Modifier.fillMaxWidth(),
            enabled = fieldsEnabled,
        )

        if (state.hasCover) {
            A11yOutlinedButton(
                label = stringResource(R.string.tag_cover_clear),
                onClick = { viewModel.clearCover() },
                modifier = Modifier.fillMaxWidth(),
                enabled = fieldsEnabled,
            )
        }

        HorizontalDivider()

        A11yButton(
            label = stringResource(R.string.tag_action),
            onClick = { viewModel.save() },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canWrite,
        )

        state.outputPath?.let { path ->
            Text(
                text = stringResource(R.string.tag_done, path.substringAfterLast('/')),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(text = stringResource(R.string.tag_done_note), style = MaterialTheme.typography.bodyMedium)
            A11yOutlinedButton(
                label = stringResource(R.string.common_ok),
                onClick = { viewModel.consumeOutput() },
            )
        }

        // Faylni teg yozmasdan ham ulashish mumkin: ba'zan fayl shunchaki
        // kerak bo'ladi. Shuning uchun tugma har doim faol — natija bo'lsa
        // yangi faylni, bo'lmasa manbani beradi.
        val sharePath = state.sharePath
        if (sharePath != null) {
            A11yButton(
                label = stringResource(R.string.tag_share),
                onClick = {
                    val file = File(sharePath)
                    val started = Sharing.share(
                        context = context,
                        file = file,
                        mime = Sharing.MP3_MIME,
                        chooserTitle = context.getString(R.string.tag_share_chooser),
                    )
                    if (!started) announce(context.getString(R.string.tag_share_failed))
                },
                modifier = Modifier.fillMaxWidth(),
                description = sharePath.substringAfterLast('/'),
                enabled = !state.busy,
            )
            Text(text = stringResource(R.string.tag_share_note), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Ilovadagi fayl qatori: butun qator bosiladi va ekran o'quvchi uni bir element deb o'qiydi. */
@Composable
private fun SourceRow(file: File, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = file.name, onClick = onOpen)
            .padding(vertical = 14.dp),
    ) {
        Text(text = file.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = sizeText(file.length()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

/** Fayl hajmi: kichigi kilobaytda, kattasi megabaytda. */
private fun sizeText(bytes: Long): String {
    val locale = Locale.getDefault()
    return if (bytes >= 1024 * 1024) {
        "${LocalizedNumber.format(bytes / (1024.0 * 1024.0), locale)} MB"
    } else {
        "${LocalizedNumber.format(bytes / 1024.0, locale)} KB"
    }
}

/** Muqova holati: tanlangan rasm nomi, fayldagi muqova yoki «yo'q». */
@Composable
private fun coverSummary(state: TagUiState): String = when {
    !state.hasCover -> stringResource(R.string.tag_cover_none)
    state.coverName.isNotBlank() -> stringResource(R.string.tag_cover_chosen, state.coverName)
    else -> stringResource(
        R.string.tag_cover_ready,
        LocalizedNumber.format(state.coverSize / 1024.0, Locale.getDefault()),
    )
}

/** Maydon yorlig'i. */
@Composable
private fun TagField.label(): String = stringResource(
    when (this) {
        TagField.TITLE -> R.string.tag_field_title
        TagField.ARTIST -> R.string.tag_field_artist
        TagField.ALBUM -> R.string.tag_field_album
        TagField.YEAR -> R.string.tag_field_year
        TagField.GENRE -> R.string.tag_field_genre
        TagField.TRACK -> R.string.tag_field_track
    },
)

/** Maydonning joriy qiymati. */
private fun TagField.value(state: TagUiState): String = when (this) {
    TagField.TITLE -> state.title
    TagField.ARTIST -> state.artist
    TagField.ALBUM -> state.album
    TagField.YEAR -> state.year
    TagField.GENRE -> state.genre
    TagField.TRACK -> state.track
}

/**
 * Klaviatura turi.
 *
 * Tartib raqami maydonida «3/12» ham yozilishi mumkin, shuning uchun u
 * raqamli klaviatura emas: raqamli klaviaturaning «/» tugmasi yo'q va
 * foydalanuvchi jami sonni umuman kirita olmasdi.
 */
private fun TagField.keyboard(): KeyboardType = when (this) {
    TagField.YEAR -> KeyboardType.Number
    else -> KeyboardType.Text
}

/** Xatoni joriy tilga o'giradi. */
@Composable
private fun TagUiError.message(): String = stringResource(
    when (this) {
        TagUiError.NO_FILE -> R.string.tag_error_no_file
        TagUiError.UNSUPPORTED_FORMAT -> R.string.tag_error_unsupported
        TagUiError.EMPTY_TAGS -> R.string.tag_error_empty
        TagUiError.COVER_TOO_LARGE -> R.string.tag_error_cover_too_large
        TagUiError.WRITE_FAILED -> R.string.tag_error_write
        TagUiError.FILE_NOT_FOUND -> R.string.tag_error_file_missing
    },
)

/** Tizim tanlagichi faqat audio fayllarni ko'rsatadi. */
private val AUDIO_MIME = arrayOf("audio/*")

/** Muqova uchun faqat rasmlar. */
private const val IMAGE_MIME = "image/*"
