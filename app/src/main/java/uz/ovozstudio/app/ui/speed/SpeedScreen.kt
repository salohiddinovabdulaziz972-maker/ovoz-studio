package uz.ovozstudio.app.ui.speed

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
import uz.ovozstudio.app.media.format.ImportFailure
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.NumericRow
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.fileSummary
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.util.SpeedText
import uz.ovozstudio.app.util.TimeFormat

/**
 * Tezlik va ohang ekrani.
 *
 * Ikkala sozlama ham **qo'lda kiritiladi** (ilova bo'ylab yagona qoida):
 * ekran o'quvchi sirg'anmani aniq qiymatga qo'yib bo'lmaydi, maydonga esa
 * kerakli son klaviaturadan yoziladi.
 *
 * Natijadagi uzunlik darhol ko'rsatiladi: tezlik uzunlikni o'zgartiradi va
 * foydalanuvchi buni eshitmasdan turib ham bilishi kerak.
 */
@Composable
fun SpeedScreen(
    initialPath: String,
    onBack: () -> Unit,
    onOpenSaved: (String) -> Unit,
    viewModel: SpeedViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val announce = rememberAnnouncer()

    LaunchedEffect(initialPath) {
        if (initialPath.isNotBlank()) viewModel.load(initialPath)
    }

    LaunchedEffect(state.savedPath) {
        val path = state.savedPath ?: return@LaunchedEffect
        announce(context.getString(R.string.speed_done, path.substringAfterLast('/')))
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
            text = stringResource(R.string.speed_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        val info = state.info
        if (info == null) {
            Text(
                text = stringResource(R.string.speed_hint),
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
                text = stringResource(R.string.speed_busy),
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
            // «Pasaytirish»/«Ko'tarish» yorliqlari ekvalayzerniki bilan bir
            // xil: amal bir xil — qiymatni bir qadam surish.
            val decrease = stringResource(R.string.eq_gain_decrease)
            val increase = stringResource(R.string.eq_gain_increase)
            val speedLabel = stringResource(R.string.speed_label)
            val pitchLabel = stringResource(R.string.speed_pitch_label)

            NumericRow(
                label = speedLabel,
                value = state.speed,
                onValueChange = { viewModel.setSpeed(it) },
                onNudge = { delta -> viewModel.nudgeSpeed(delta) },
                step = SpeedText.STEP_SPEED,
                decreaseDescription = "$decrease: $speedLabel",
                increaseDescription = "$increase: $speedLabel",
                enabled = !state.busy,
            )
            Text(
                text = stringResource(R.string.speed_note),
                style = MaterialTheme.typography.bodyMedium,
            )

            NumericRow(
                label = pitchLabel,
                value = state.semitones,
                onValueChange = { viewModel.setSemitones(it) },
                onNudge = { delta -> viewModel.nudgeSemitones(delta) },
                step = SpeedText.STEP_SEMITONES,
                decreaseDescription = "$decrease: $pitchLabel",
                increaseDescription = "$increase: $pitchLabel",
                enabled = !state.busy,
            )
            Text(
                text = stringResource(R.string.speed_pitch_note),
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = stringResource(
                    R.string.speed_result,
                    TimeFormat.format(state.resultDurationMs),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )

            A11yButton(
                label = stringResource(R.string.speed_action),
                onClick = { viewModel.apply() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            )
        }

        state.savedPath?.let { path ->
            Text(
                text = stringResource(R.string.speed_done, path.substringAfterLast('/')),
                style = MaterialTheme.typography.bodyLarge,
            )
            A11yButton(
                label = stringResource(R.string.speed_open_saved),
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

/** Tezlik yoki ohang xatosini joriy tilga o'giradi. */
@Composable
private fun SpeedError.message(): String = stringResource(
    when (this) {
        SpeedError.FILE_NOT_FOUND -> R.string.speed_error_file_not_found
        SpeedError.EDIT_FAILED -> R.string.speed_error_edit_failed
        SpeedError.NOTHING_TO_APPLY -> R.string.speed_error_nothing_to_apply
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

/** Tizim tanlagichi faqat audio fayllarni ko'rsatadi. */
private val AUDIO_MIME = arrayOf("audio/*")
