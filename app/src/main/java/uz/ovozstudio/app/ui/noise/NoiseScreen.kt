package uz.ovozstudio.app.ui.noise

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
import uz.ovozstudio.app.media.dsp.NoiseReducer
import uz.ovozstudio.app.media.format.ImportFailure
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.NumericRow
import uz.ovozstudio.app.ui.common.TimeInput
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.fileSummary
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.util.LocalizedNumber
import uz.ovozstudio.app.util.TimeFormat

/**
 * Shovqin tozalash ekrani.
 *
 * Ekranda ikkita talab birlashadi: **shovqin namunasi oraliqi** (vaqt
 * maydonlari) va **ikki sozlama** (kuch, qoldiq). Hammasi qo'lda kiritiladi —
 * ilova bo'ylab yagona qoida: ekran o'quvchi sirg'anmani aniq qiymatga
 * qo'yib bo'lmaydi, maydonga esa kerakli son klaviaturadan yoziladi.
 *
 * Namunaning boshlang'ich qiymati — faylning birinchi yarim sekundi:
 * yozuv boshida odatda hali gapirilmagan bo'ladi va faqat fon shovqini
 * eshitiladi. Ko'p hollarda foydalanuvchi bu oraliqni umuman o'zgartirmaydi.
 */
@Composable
fun NoiseScreen(
    initialPath: String,
    onBack: () -> Unit,
    onOpenSaved: (String) -> Unit,
    viewModel: NoiseViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val announce = rememberAnnouncer()

    LaunchedEffect(initialPath) {
        if (initialPath.isNotBlank()) viewModel.load(initialPath)
    }

    LaunchedEffect(state.savedPath) {
        val path = state.savedPath ?: return@LaunchedEffect
        announce(
            context.getString(
                R.string.noise_done,
                path.substringAfterLast('/'),
                LocalizedNumber.format(state.savedDropDb, locale),
            ),
        )
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
            text = stringResource(R.string.noise_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        val info = state.info
        if (info == null) {
            Text(
                text = stringResource(R.string.noise_hint),
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
                text = stringResource(R.string.noise_busy),
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
            Text(
                text = stringResource(R.string.noise_sample_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.a11yHeading(),
            )
            Text(
                text = stringResource(R.string.noise_sample_note),
                style = MaterialTheme.typography.bodyMedium,
            )

            TimeInput(
                label = stringResource(R.string.noise_sample_from),
                parts = state.noiseStart,
                onPartsChange = viewModel::setNoiseStart,
                isError = !state.noiseStart.isEmpty && state.noiseStart.toMillisOrNull() == null,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )

            TimeInput(
                label = stringResource(R.string.noise_sample_to),
                parts = state.noiseEnd,
                onPartsChange = viewModel::setNoiseEnd,
                isError = !state.noiseEnd.isEmpty && state.noiseEnd.toMillisOrNull() == null,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(
                    R.string.noise_sample_range,
                    TimeFormat.format(state.startMs),
                    TimeFormat.format(state.endMs),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )

            // «Pasaytirish»/«Ko'tarish» yorliqlari ekvalayzerniki bilan bir
            // xil: amal bir xil — qiymatni bir qadam surish.
            val decrease = stringResource(R.string.eq_gain_decrease)
            val increase = stringResource(R.string.eq_gain_increase)

            val strengthLabel = stringResource(R.string.noise_strength_label)
            NumericRow(
                label = strengthLabel,
                value = state.strength,
                onValueChange = { viewModel.setStrength(it) },
                onNudge = { delta -> viewModel.nudgeStrength(delta) },
                step = STRENGTH_STEP,
                decreaseDescription = "$decrease: $strengthLabel",
                increaseDescription = "$increase: $strengthLabel",
                enabled = !state.busy,
            )
            Text(
                text = stringResource(
                    R.string.noise_strength_note,
                    LocalizedNumber.format(NoiseReducer.MAX_STRENGTH, locale),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            val floorLabel = stringResource(R.string.noise_floor_label)
            NumericRow(
                label = floorLabel,
                value = state.floorDb,
                onValueChange = { viewModel.setFloorDb(it) },
                onNudge = { delta -> viewModel.nudgeFloorDb(delta) },
                step = FLOOR_STEP,
                decreaseDescription = "$decrease: $floorLabel",
                increaseDescription = "$increase: $floorLabel",
                enabled = !state.busy,
            )
            Text(
                text = stringResource(R.string.noise_floor_note),
                style = MaterialTheme.typography.bodyMedium,
            )

            A11yButton(
                label = stringResource(R.string.noise_action),
                onClick = { viewModel.apply() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            )
        }

        state.savedPath?.let { path ->
            Text(
                text = stringResource(
                    R.string.noise_done,
                    path.substringAfterLast('/'),
                    LocalizedNumber.format(state.savedDropDb, locale),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            A11yButton(
                label = stringResource(R.string.noise_open_saved),
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

/** Shovqin tozalash xatosini joriy tilga o'giradi. */
@Composable
private fun NoiseError.message(): String = stringResource(
    when (this) {
        NoiseError.FILE_NOT_FOUND -> R.string.noise_error_file_not_found
        NoiseError.EDIT_FAILED -> R.string.noise_error_edit_failed
        NoiseError.SAMPLE_RANGE -> R.string.noise_error_sample_range
        NoiseError.SAMPLE_QUIET -> R.string.noise_error_sample_quiet
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

/** Kuch va qoldiqning bir qadamli surilishi. */
private const val STRENGTH_STEP = 0.5
private const val FLOOR_STEP = 1.0

/** Tizim tanlagichi faqat audio fayllarni ko'rsatadi. */
private val AUDIO_MIME = arrayOf("audio/*")
