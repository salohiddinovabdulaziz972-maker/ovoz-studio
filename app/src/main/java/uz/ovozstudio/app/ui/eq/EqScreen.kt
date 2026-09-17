package uz.ovozstudio.app.ui.eq

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.ovozstudio.app.R
import uz.ovozstudio.app.media.dsp.EqBandCount
import uz.ovozstudio.app.media.dsp.EqPreset
import uz.ovozstudio.app.media.format.ImportFailure
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.ChoiceRow
import uz.ovozstudio.app.ui.common.NumericRow
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.fileSummary
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.util.LocalizedNumber
import java.util.Locale

/**
 * Parametrik ekvalayzer ekrani.
 *
 * Ekranning tuzilishi accessibility talabidan kelib chiqadi: har bir polosa
 * **alohida raqamli maydon**, sirg'anma tugma emas. Sirg'anmani ekran
 * o'quvchi bilan aniq qiymatga qo'yib bo'lmaydi; maydonga esa kerakli son
 * klaviaturadan kiritiladi.
 */
@Composable
fun EqScreen(
    initialPath: String,
    onBack: () -> Unit,
    onOpenSaved: (String) -> Unit,
    viewModel: EqViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val announce = rememberAnnouncer()
    val locale = LocalConfiguration.current.locales[0]

    LaunchedEffect(initialPath) {
        if (initialPath.isNotBlank()) viewModel.load(initialPath)
    }

    LaunchedEffect(state.savedPath) {
        val path = state.savedPath ?: return@LaunchedEffect
        announce(context.getString(R.string.eq_done, path.substringAfterLast('/')))
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(uri)
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
            enabled = !state.busy,
        )

        Text(
            text = stringResource(R.string.eq_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        val info = state.info
        if (info == null) {
            Text(
                text = stringResource(R.string.eq_hint),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text(text = state.fileName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = fileSummary(info, state.durationMs),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Ilovaning o'z papkasidagi fayllar tizim tanlagichida ko'rinmaydi,
        // shuning uchun ekrandan fayl almashtirish yo'li ham qoladi.
        A11yButton(
            label = stringResource(R.string.convert_pick),
            onClick = { pickFile.launch(AUDIO_MIME) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )

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

        if (state.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.eq_busy),
                style = MaterialTheme.typography.bodyMedium,
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

        if (state.centers.isNotEmpty()) {
            // Yorliqlar oldindan yasaladi: `ChoiceRow` ning `optionLabel` i
            // oddiy lambda, u @Composable bo'la olmaydi.
            val countLabels = EqBandCount.entries.map { bandCountLabel(it) }
            ChoiceRow(
                label = stringResource(R.string.eq_band_count_title),
                options = EqBandCount.entries,
                selected = state.bandCount,
                onSelect = { viewModel.setBandCount(it) },
                optionLabel = { countLabels[EqBandCount.entries.indexOf(it)] },
                enabled = !state.busy,
            )

            val presetLabels = EqPreset.entries.map { presetLabel(it) }
            ChoiceRow(
                label = stringResource(R.string.eq_preset_title),
                options = EqPreset.entries,
                selected = state.preset,
                onSelect = { viewModel.setPreset(it) },
                optionLabel = { presetLabels[EqPreset.entries.indexOf(it)] },
                enabled = !state.busy,
            )

            val lowCutLabels = LOW_CUT_OPTIONS.map { lowCutLabel(it) }
            ChoiceRow(
                label = stringResource(R.string.eq_lowcut_title),
                options = LOW_CUT_OPTIONS,
                selected = state.lowCutHz,
                onSelect = { viewModel.setLowCut(it) },
                optionLabel = { lowCutLabels[LOW_CUT_OPTIONS.indexOf(it)] },
                enabled = !state.busy,
            )
            Text(
                text = stringResource(R.string.eq_lowcut_note),
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = stringResource(R.string.eq_bands_heading),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.a11yHeading(),
            )

            val hzUnit = stringResource(R.string.eq_band_unit_hz)
            val khzUnit = stringResource(R.string.eq_band_unit_khz)
            val bandLabels = remember(state.centers, locale, hzUnit, khzUnit) {
                state.centers.map { LocalizedNumber.frequency(it, hzUnit, khzUnit, locale) }
            }
            val decrease = stringResource(R.string.eq_gain_decrease)
            val increase = stringResource(R.string.eq_gain_increase)

            for (index in state.centers.indices) {
                NumericRow(
                    label = bandLabels[index],
                    value = state.gains.getOrNull(index).orEmpty(),
                    onValueChange = { viewModel.setGain(index, it) },
                    onNudge = { delta -> viewModel.nudgeGain(index, delta) },
                    step = STEP,
                    decreaseDescription = "$decrease: ${bandLabels[index]}",
                    increaseDescription = "$increase: ${bandLabels[index]}",
                    enabled = !state.busy,
                )
            }

            Text(
                text = stringResource(R.string.eq_gain_note),
                style = MaterialTheme.typography.bodyMedium,
            )

            A11yButton(
                label = stringResource(R.string.eq_action),
                onClick = { viewModel.apply() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            )
        }

        state.savedPath?.let { path ->
            Text(
                text = stringResource(R.string.eq_done, path.substringAfterLast('/')),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (state.headroomDb < HEADROOM_EPSILON) {
                // Jimgina pasaytirib qo'yish eng yomon xatti-harakat bo'lardi:
                // foydalanuvchi «kuchaytirdim, lekin balandroq bo'lmadi» deb
                // tushunmay qolardi.
                Text(
                    text = stringResource(
                        R.string.eq_headroom_note,
                        LocalizedNumber.decibels(state.headroomDb, DB_UNIT, locale),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            A11yButton(
                label = stringResource(R.string.eq_open_saved),
                onClick = { onOpenSaved(path) },
                modifier = Modifier.fillMaxWidth(),
            )
            A11yOutlinedButton(
                label = stringResource(R.string.common_ok),
                onClick = { viewModel.consumeSaved() },
            )
        }
    }
}

@Composable
private fun bandCountLabel(count: EqBandCount): String = stringResource(
    when (count) {
        EqBandCount.TEN -> R.string.eq_band_count_ten
        EqBandCount.THIRTY_ONE -> R.string.eq_band_count_thirty_one
    },
)

@Composable
private fun presetLabel(preset: EqPreset): String = stringResource(
    when (preset) {
        EqPreset.FLAT -> R.string.eq_preset_flat
        EqPreset.VOICE -> R.string.eq_preset_voice
        EqPreset.BASS -> R.string.eq_preset_bass
        EqPreset.TREBLE -> R.string.eq_preset_treble
        EqPreset.LOUDNESS -> R.string.eq_preset_loudness
    },
)

@Composable
private fun lowCutLabel(hertz: Int): String = if (hertz <= 0) {
    stringResource(R.string.eq_lowcut_off)
} else {
    "${hertz} Hz"
}

/** Ekvalayzer xatosini joriy tilga o'giradi. */
@Composable
private fun EqError.message(): String = stringResource(
    when (this) {
        EqError.FILE_NOT_FOUND -> R.string.eq_error_file_not_found
        EqError.EDIT_FAILED -> R.string.eq_error_edit_failed
        EqError.SAVE_FAILED -> R.string.eq_error_save_failed
        EqError.NOTHING_TO_APPLY -> R.string.eq_error_nothing_to_apply
    },
)

/**
 * Import sababini joriy tilga o'giradi.
 *
 * Matnlar konvertor ekraniniki bilan bir xil: sabab bir xil, uni ikki joyda
 * ikki xil so'z bilan aytishning ma'nosi yo'q.
 */
@Composable
private fun ImportFailure.message(): String = stringResource(
    when (this) {
        ImportFailure.UNKNOWN_FORMAT -> R.string.convert_error_unknown_format
        ImportFailure.NO_DECODER -> R.string.convert_error_no_decoder
        ImportFailure.READ_FAILED -> R.string.convert_error_read_failed
        ImportFailure.EMPTY -> R.string.convert_error_empty
    },
)

/** Past chastotani kesish variantlari (Hz); 0 — o'chirilgan. */
private val LOW_CUT_OPTIONS = listOf(0, 80, 120, 180)

/** Tizim tanlagichi faqat audio fayllarni ko'rsatadi. */
private val AUDIO_MIME = arrayOf("audio/*")

/** «−» va «+» tugmalari bir bosishda shuncha suradi. */
private const val STEP = 0.5

/** Shundan kichik pasaytirish haqida gapirishning ma'nosi yo'q. */
private const val HEADROOM_EPSILON = -0.05

private const val DB_UNIT = "dB"
