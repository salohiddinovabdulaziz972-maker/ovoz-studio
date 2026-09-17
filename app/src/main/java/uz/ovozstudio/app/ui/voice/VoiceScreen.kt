package uz.ovozstudio.app.ui.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.ovozstudio.app.R
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.NumericRow
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.util.SpeedText

/**
 * Ovoz sinovi ekrani.
 *
 * Vazifasi — qurilmaning ovoz sintezatorini **tekshirish**: qurilmada
 * qanday ovozlar bor, o'zbek ovozi bormi, uzun matn bo'laklarga to'g'ri
 * bo'linaryaptimi. Audio-kitob ekrani shu dvigatel ustiga quriladi, shuning
 * uchun avval dvigatelning o'zi ishlashiga ishonch hosil qilish kerak.
 *
 * Tezlik va balandlik **qo'lda kiritiladi** (ilova bo'ylab yagona qoida).
 * Balandlik yarim tonlarda beriladi: bu ilovaning boshqa ekranlarida ham
 * shunday, ya'ni foydalanuvchi bir xil birlikni ko'radi.
 */
@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    viewModel: VoiceViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val announce = rememberAnnouncer()

    // Bo'lak almashganda ovoz bilan e'lon qilinmaydi: o'sha paytda sintezator
    // gapiryapti, ustiga ekran o'quvchi ham qo'shilsa hech narsa eshitilmaydi.
    // Holat ekranda ko'rinadi va fokuslanganda o'qiladi.
    LaunchedEffect(state.speaking) {
        if (!state.speaking && state.chunkTotal > 0) {
            announce(context.getString(R.string.voice_done))
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
        )

        Text(
            text = stringResource(R.string.voice_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        Text(
            text = stringResource(R.string.voice_hint),
            style = MaterialTheme.typography.bodyLarge,
        )

        OutlinedTextField(
            value = state.text,
            onValueChange = viewModel::setText,
            label = { Text(stringResource(R.string.voice_text_label)) },
            supportingText = { Text(stringResource(R.string.voice_text_note)) },
            // Ko'p qatorli maydon: kitobdan parcha ham sig'sin.
            singleLine = false,
            minLines = 3,
            enabled = state.ready,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Default,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        // Dvigatel holati. Foydalanuvchi tugma nima uchun o'chiq turganini
        // bilishi kerak — sababsiz o'chiq tugma nosozlikka o'xshaydi.
        Text(
            text = if (state.ready) {
                stringResource(R.string.voice_ready)
            } else {
                stringResource(R.string.voice_preparing)
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        if (state.ready) {
            Text(
                text = stringResource(R.string.voice_voices, state.voiceCount),
                style = MaterialTheme.typography.bodyMedium,
            )

            val tag = state.languageTag
            if (tag != null) {
                Text(
                    text = stringResource(R.string.voice_language, tag),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (state.languageMissing) {
                Text(
                    text = stringResource(R.string.voice_language_missing),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        NumericRow(
            label = stringResource(R.string.voice_rate_label),
            value = state.rate,
            onValueChange = viewModel::setRate,
            onNudge = viewModel::nudgeRate,
            step = VoiceUiState.STEP_RATE,
            decreaseDescription = stringResource(R.string.eq_gain_decrease),
            increaseDescription = stringResource(R.string.eq_gain_increase),
            enabled = state.ready,
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
            step = VoiceUiState.STEP_SEMITONES,
            decreaseDescription = stringResource(R.string.eq_gain_decrease),
            increaseDescription = stringResource(R.string.eq_gain_increase),
            enabled = state.ready,
        )
        Text(
            text = stringResource(R.string.voice_pitch_note),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (state.speaking) {
            A11yButton(
                label = stringResource(R.string.voice_stop),
                onClick = viewModel::stop,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(
                    R.string.voice_chunk,
                    state.chunkNumber,
                    state.chunkTotal,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            A11yButton(
                label = stringResource(R.string.voice_play),
                onClick = viewModel::speak,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.ready,
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

private fun VoiceUiError.messageRes(): Int = when (this) {
    VoiceUiError.NOT_AVAILABLE -> R.string.voice_error_not_available
    VoiceUiError.EMPTY_TEXT -> R.string.voice_error_empty_text
    VoiceUiError.SPEAK_FAILED -> R.string.voice_error_speak_failed
}
