package uz.ovozstudio.app.ui.stem

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.ovozstudio.app.R
import uz.ovozstudio.app.media.dsp.StemSeparator
import uz.ovozstudio.app.media.format.ImportFailure
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.ChoiceRow
import uz.ovozstudio.app.ui.common.NumericRow
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.fileSummary
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.util.LocalizedNumber

/**
 * Vokal va cholg'uni ajratish ekrani.
 *
 * Ekran ochiq aytadigan cheklov: bu **kanal usuli**, neyron model emas.
 * Vokal odatda markazda turadi, shuning uchun u markazdan, cholg'u esa
 * yonlardan olinadi. Natijada ikkala faylning yig'indisi aynan manbani
 * beradi — ya'ni hech narsa yo'qolmaydi. Lekin markazda turgan cholg'uni
 * (bas, baraban) bu usul ajratmaydi; buni yashirmaslik uchun natija
 * yonida **o'lchangan** «yon/markaz» nisbati ko'rsatiladi.
 */
@Composable
fun StemScreen(
    initialPath: String,
    onBack: () -> Unit,
    onOpenSaved: (String) -> Unit,
    viewModel: StemViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val announce = rememberAnnouncer()

    LaunchedEffect(initialPath) {
        if (initialPath.isNotBlank()) viewModel.load(initialPath)
    }

    LaunchedEffect(state.savedVocalPath) {
        if (state.savedVocalPath != null) {
            announce(context.getString(R.string.stem_done))
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(uri)
    }

    // `ChoiceRow` variantlari oddiy lambda bilan yorliqlanadi, ya'ni
    // `stringResource` ni o'sha yerda chaqirib bo'lmaydi — yorliqlar
    // oldindan yasaladi.
    val modeLabels = StemSeparator.Mode.entries.associateWith { mode ->
        when (mode) {
            StemSeparator.Mode.SPLIT -> stringResource(R.string.stem_mode_split)
            StemSeparator.Mode.REMOVE_VOCALS -> stringResource(R.string.stem_mode_remove)
        }
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
            text = stringResource(R.string.stem_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        val info = state.info
        if (info == null) {
            Text(
                text = stringResource(R.string.stem_hint),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text(text = state.fileName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = fileSummary(info, state.durationMs),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Usulning chegarasi fayl tanlashdan **oldin** aytiladi: mono
        // yozuvni tanlagan foydalanuvchi natijani kutmasin.
        Text(
            text = stringResource(R.string.stem_stereo_only),
            style = MaterialTheme.typography.bodyMedium,
        )

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
                text = stringResource(R.string.stem_busy),
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

        if (info != null) {
            ChoiceRow(
                label = stringResource(R.string.stem_mode_label),
                options = StemSeparator.Mode.entries,
                selected = state.mode,
                onSelect = viewModel::setMode,
                optionLabel = { mode -> modeLabels.getValue(mode) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            )

            Text(
                text = stringResource(
                    when (state.mode) {
                        StemSeparator.Mode.SPLIT -> R.string.stem_mode_note_split
                        StemSeparator.Mode.REMOVE_VOCALS -> R.string.stem_mode_note_remove
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            // «Pasaytirish»/«Ko'tarish» yorliqlari boshqa ekranlarniki bilan
            // bir xil: amal bir xil — qiymatni bir qadam surish.
            val decrease = stringResource(R.string.eq_gain_decrease)
            val increase = stringResource(R.string.eq_gain_increase)
            val strengthLabel = stringResource(R.string.stem_strength_label)

            NumericRow(
                label = strengthLabel,
                value = state.strength,
                onValueChange = { viewModel.setStrength(it) },
                onNudge = { delta -> viewModel.nudgeStrength(delta) },
                step = STRENGTH_STEP,
                decreaseDescription = "$decrease: $strengthLabel",
                increaseDescription = "$increase: $strengthLabel",
                enabled = !state.busy && state.strengthUsed,
            )
            // Maydon o'chiq turganda sabab aytiladi: aks holda
            // foydalanuvchi uni o'zgartira olmay, sababini bilmay qolardi.
            Text(
                text = stringResource(
                    if (state.strengthUsed) {
                        R.string.stem_strength_note
                    } else {
                        R.string.stem_strength_unused
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            A11yButton(
                label = stringResource(R.string.stem_action),
                onClick = { viewModel.apply() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy && !state.notStereo,
            )
        }

        val vocalPath = state.savedVocalPath
        val instrumentalPath = state.savedInstrumentalPath
        if (vocalPath != null && instrumentalPath != null) {
            Text(
                text = stringResource(R.string.stem_saved_vocal, vocalPath.substringAfterLast('/')),
                style = MaterialTheme.typography.bodyLarge,
            )
            A11yButton(
                label = stringResource(R.string.stem_open_vocal),
                onClick = { onOpenSaved(vocalPath) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(
                    R.string.stem_saved_instrumental,
                    instrumentalPath.substringAfterLast('/'),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            A11yButton(
                label = stringResource(R.string.stem_open_instrumental),
                onClick = { onOpenSaved(instrumentalPath) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(
                    R.string.stem_side_ratio,
                    LocalizedNumber.format(state.sideToMidDb, locale),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (state.nearlyMono) {
                Text(
                    text = stringResource(R.string.stem_nearly_mono),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            A11yOutlinedButton(
                label = stringResource(R.string.common_ok),
                onClick = { viewModel.consumeSaved() },
            )
        }
    }
}

/** Vokal/cholg'u ajratish xatosini joriy tilga o'giradi. */
@Composable
private fun StemError.message(): String = stringResource(
    when (this) {
        StemError.FILE_NOT_FOUND -> R.string.stem_error_file_not_found
        StemError.NOT_STEREO -> R.string.stem_error_not_stereo
        StemError.MONO_CONTENT -> R.string.stem_error_mono_content
        StemError.EDIT_FAILED -> R.string.stem_error_edit_failed
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

/** Kuchning bir qadamli surilishi. */
private const val STRENGTH_STEP = 0.5

/** Tizim tanlagichi faqat audio fayllarni ko'rsatadi. */
private val AUDIO_MIME = arrayOf("audio/*")
