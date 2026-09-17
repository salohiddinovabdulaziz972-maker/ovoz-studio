package uz.ovozstudio.app.ui.convert

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
import uz.ovozstudio.app.media.format.AudioFormat
import uz.ovozstudio.app.media.format.FallbackReason
import uz.ovozstudio.app.media.format.ImportFailure
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.ChoiceRow
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.util.TimeFormat
import java.util.Locale

/**
 * Format konvertori ekrani.
 *
 * Ekranning oqimi ikki qadamdan iborat va foydalanuvchi uchun ikkisi ham
 * ko'rinadi: fayl tanlanadi, keyin maqsad format tanlanadi. Manba fayl
 * hech qachon o'zgartirilmaydi — natija yangi fayl bo'lib chiqadi.
 */
@Composable
fun ConvertScreen(
    initialPath: String,
    onBack: () -> Unit,
    viewModel: ConvertViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val announce = rememberAnnouncer()

    // Yo'l berilgan bo'lsa (ilova ichidagi fayl) — o'sha darhol ochiladi,
    // aks holda foydalanuvchi faylni o'zi tanlaydi.
    LaunchedEffect(initialPath) {
        if (initialPath.isNotBlank()) viewModel.openPath(initialPath)
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(uri)
    }

    LaunchedEffect(state.outputPath) {
        val path = state.outputPath ?: return@LaunchedEffect
        announce(context.getString(R.string.convert_done, path.substringAfterLast('/')))
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
            text = stringResource(R.string.convert_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        Text(
            text = stringResource(R.string.convert_source_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )

        if (state.sourceFormat == null) {
            Text(
                text = stringResource(R.string.convert_hint),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text(text = state.sourceName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = sourceSummary(state),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        A11yButton(
            label = stringResource(R.string.convert_pick),
            onClick = { pickFile.launch(AUDIO_MIME) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )

        if (state.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.convert_busy),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        val failure = state.importFailure
        if (failure != null) {
            Text(
                text = failure.message(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            A11yOutlinedButton(
                label = stringResource(R.string.common_ok),
                onClick = { viewModel.clearError() },
            )
        }

        val error = state.error
        if (error != null) {
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

        if (state.targets.isNotEmpty()) {
            // Manba formatini saqlab bo'lmasa, buni ochiq aytamiz: "jimgina
            // boshqa formatda saqlandi" — eng yomon xatti-harakat.
            val reason = state.fallbackReason
            Text(
                text = if (reason == null) {
                    stringResource(R.string.convert_preserved_note)
                } else {
                    stringResource(R.string.convert_fallback_note, reason.message())
                },
                style = MaterialTheme.typography.bodyLarge,
            )

            // Yorliqlar oldindan yasaladi: `ChoiceRow` ning `optionLabel` i
            // oddiy lambda, u @Composable bo'la olmaydi, tarjima esa
            // `stringResource` orqali olinadi.
            val labels = ArrayList<String>(state.targets.size)
            for (format in state.targets) labels += targetLabel(state, format)

            ChoiceRow(
                label = stringResource(R.string.convert_target_title),
                options = state.targets,
                selected = state.target ?: state.targets.first(),
                onSelect = { viewModel.selectTarget(it) },
                optionLabel = { labels[state.targets.indexOf(it)] },
                enabled = !state.busy,
            )

            Text(
                text = stringResource(R.string.convert_limited_note),
                style = MaterialTheme.typography.bodyMedium,
            )

            A11yButton(
                label = stringResource(R.string.convert_action),
                onClick = { viewModel.convert() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy && state.target != null,
            )
        }

        state.outputPath?.let { path ->
            Text(
                text = stringResource(R.string.convert_done, path.substringAfterLast('/')),
                style = MaterialTheme.typography.bodyLarge,
            )
            A11yOutlinedButton(
                label = stringResource(R.string.common_ok),
                onClick = { viewModel.consumeOutput() },
            )
        }
    }
}

/** Manba haqidagi bir qatorlik ma'lumot: format, chastota, kanallar, uzunlik. */
@Composable
private fun sourceSummary(state: ConvertUiState): String {
    val format = state.sourceFormat ?: return ""
    val detected = state.sourceDetected
    // Konteyner nomi fayl sarlavhasidan olinadi: fayl nomi "ovoz.mp3" bo'lib
    // ichida FLAC bo'lishi mumkin, va to'g'ri javob — ikkinchisi.
    val name = when {
        detected == null -> stringResource(R.string.convert_source_unknown)
        detected.container.displayName == detected.codec.displayName ->
            detected.container.displayName

        else -> "${detected.container.displayName} (${detected.codec.displayName})"
    }

    return stringResource(
        R.string.convert_source_summary,
        name,
        sampleRateText(format.sampleRate),
        channelsText(format.channels),
        TimeFormat.format(state.durationMs),
    )
}

/**
 * Maqsad format yorlig'i.
 *
 * Yo'qotishsiz va siqilgan farqi ochiq yoziladi: foydalanuvchi "MP3" va
 * "FLAC" orasidagi farqni bilmasligi mumkin, lekin "siqilgan" va
 * "yo'qotishsiz" so'zlari o'zi tushunarli.
 */
@Composable
private fun targetLabel(state: ConvertUiState, format: AudioFormat): String {
    val kind = stringResource(
        if (format.codec.lossless) R.string.convert_kind_lossless else R.string.convert_kind_lossy
    )
    val bitrate = format.bitrate
    val suffix = if (bitrate == null) "" else ", ${bitrate / 1000} kbit/s"
    val same = state.target == format && state.preserving

    return buildString {
        append(format.container.displayName)
        append(" — ")
        append(kind)
        append(suffix)
        if (same) append(", ").append(stringResource(R.string.convert_kind_same))
    }
}

@Composable
private fun sampleRateText(rate: Int): String = if (rate % 1000 == 0) {
    "${rate / 1000} kHz"
} else {
    String.format(Locale.getDefault(), "%.1f kHz", rate / 1000.0)
}

@Composable
private fun channelsText(channels: Int): String = when (channels) {
    1 -> stringResource(R.string.convert_channels_mono)
    2 -> stringResource(R.string.convert_channels_stereo)
    else -> stringResource(R.string.convert_channels_multi, channels)
}

/** Import sababini joriy tilga o'giradi. */
@Composable
private fun ImportFailure.message(): String = stringResource(
    when (this) {
        ImportFailure.UNKNOWN_FORMAT -> R.string.convert_error_unknown_format
        ImportFailure.NO_DECODER -> R.string.convert_error_no_decoder
        ImportFailure.READ_FAILED -> R.string.convert_error_read_failed
        ImportFailure.EMPTY -> R.string.convert_error_empty
    },
)

/** Konvertatsiya xatosini joriy tilga o'giradi. */
@Composable
private fun ConvertError.message(): String = stringResource(
    when (this) {
        ConvertError.ENCODE_FAILED -> R.string.convert_error_encode_failed
        ConvertError.WRITE_FAILED -> R.string.convert_error_write_failed
        ConvertError.FILE_NOT_FOUND -> R.string.convert_error_file_not_found
    },
)

/** Manba formatini saqlab bo'lmasligining sababi. */
@Composable
private fun FallbackReason.message(): String = stringResource(
    when (this) {
        FallbackReason.NO_ENCODER -> R.string.convert_reason_no_encoder
        FallbackReason.NO_DECODER -> R.string.convert_reason_no_decoder
        FallbackReason.API_TOO_OLD -> R.string.convert_reason_api_too_old
    },
)

/** Tizim tanlagichi faqat audio fayllarni ko'rsatadi. */
private val AUDIO_MIME = arrayOf("audio/*")
