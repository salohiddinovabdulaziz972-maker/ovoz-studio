package uz.ovozstudio.app.ui.mix

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.ovozstudio.app.R
import uz.ovozstudio.app.media.Recording
import uz.ovozstudio.app.media.mix.MixProject
import uz.ovozstudio.app.media.mix.MixTrackText
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.NumericRow
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.fileSummary
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.util.DecimalText
import uz.ovozstudio.app.util.Sharing
import uz.ovozstudio.app.util.TimeFormat
import java.io.File

/**
 * Ko'p yo'lli aralashtirish ekrani.
 *
 * Oqim: yozuvlar ro'yxatidan fayllar qo'shiladi → har biriga balandlik,
 * chap/o'ng joylashuv va siljish beriladi → «Aralashtirish» bosiladi →
 * natija yangi fayl bo'lib chiqadi va uni ulashish mumkin.
 *
 * Manbalar **WAV**: mikser har bir faylni ikki marta o'qiydi (avval cho'qqini
 * o'lchaydi, keyin yozadi), boshqa formatda bu mumkin emas. Shuning uchun
 * ekranda konvertorga o'tish tugmasi bor — fayl ilovaning o'zida WAV ga
 * o'tkaziladi, boshqa dastur qidirish shart emas.
 *
 * Barcha sozlamalar **qo'lda kiritiladi** (ilova bo'ylab yagona qoida):
 * sirg'anma bilan aniq desibelni qo'yib bo'lmaydi — ekran o'quvchi uchun ham,
 * barmoq uchun ham.
 */
@Composable
fun MixScreen(
    onBack: () -> Unit,
    onConvert: () -> Unit,
    onOpenSaved: (String) -> Unit,
    viewModel: MixViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val files by viewModel.files.collectAsState()
    val context = LocalContext.current
    val announce = rememberAnnouncer()

    // Ro'yxat ekranga har qaytilganda qayta o'qiladi: shu orada yangi yozuv
    // paydo bo'lgan yoki fayl o'chirilgan bo'lishi mumkin.
    LaunchedEffect(Unit) { viewModel.refreshFiles() }

    LaunchedEffect(state.outputPath) {
        val path = state.outputPath ?: return@LaunchedEffect
        announce(context.getString(R.string.mix_done, path.substringAfterLast('/')))
    }

    val enabled = !state.busy
    val full = state.tracks.size >= MixProject.MAX_TRACKS

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
            text = stringResource(R.string.mix_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )
        Text(text = stringResource(R.string.mix_hint), style = MaterialTheme.typography.bodyLarge)
        Text(text = stringResource(R.string.mix_wav_note), style = MaterialTheme.typography.bodyMedium)

        A11yOutlinedButton(
            label = stringResource(R.string.mix_convert),
            onClick = onConvert,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        Text(
            text = stringResource(R.string.mix_files_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )

        if (files.isEmpty()) {
            Text(text = stringResource(R.string.mix_files_empty), style = MaterialTheme.typography.bodyLarge)
        } else {
            files.forEach { recording ->
                SourceRow(
                    recording = recording,
                    enabled = enabled && !full,
                    onAdd = { viewModel.add(recording) },
                )
            }
        }

        // Chegaraga yetganda qatorlar bosilmaydi, sabab esa ochiq aytiladi:
        // «bosdim, hech narsa bo'lmadi» degan holat qolmasligi kerak.
        if (full) {
            Text(text = stringResource(R.string.mix_error_too_many), style = MaterialTheme.typography.bodyMedium)
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.mix_tracks_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )

        if (state.tracks.isEmpty()) {
            Text(text = stringResource(R.string.mix_tracks_empty), style = MaterialTheme.typography.bodyLarge)
        }

        state.tracks.forEachIndexed { index, track ->
            TrackSection(
                track = track,
                enabled = enabled,
                onGain = { viewModel.setGain(index, it) },
                onGainNudge = { viewModel.nudgeGain(index, it) },
                onPan = { viewModel.setPan(index, it) },
                onPanNudge = { viewModel.nudgePan(index, it) },
                onOffset = { viewModel.setOffset(index, it) },
                onOffsetNudge = { viewModel.nudgeOffset(index, it) },
                onMute = { viewModel.toggleMute(index) },
                onSolo = { viewModel.toggleSolo(index) },
                onRemove = { viewModel.remove(index) },
            )
            HorizontalDivider()
        }

        if (state.tracks.isNotEmpty()) {
            Text(
                text = stringResource(R.string.mix_length, TimeFormat.format(state.totalMs)),
                style = MaterialTheme.typography.bodyLarge,
            )

            // Orqaga qaytarish — xatodan himoya. Yo'lni o'chirib qo'ygan
            // foydalanuvchi uchun bu yagona yo'l orqaga.
            A11yOutlinedButton(
                label = stringResource(R.string.mix_undo),
                onClick = { viewModel.undo() },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && state.canUndo,
            )
            A11yOutlinedButton(
                label = stringResource(R.string.mix_redo),
                onClick = { viewModel.redo() },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && state.canRedo,
            )
        }

        NumericRow(
            label = stringResource(R.string.mix_master),
            value = state.masterText,
            onValueChange = { viewModel.setMaster(it) },
            onNudge = { viewModel.nudgeMaster(it) },
            step = MixTrackText.GAIN_STEP_DB,
            decreaseDescription = stringResource(R.string.mix_decrease),
            increaseDescription = stringResource(R.string.mix_increase),
            enabled = enabled,
        )

        A11yButton(
            label = stringResource(R.string.mix_action),
            onClick = { viewModel.mix() },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canMix,
        )

        if (state.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(text = stringResource(R.string.mix_busy), style = MaterialTheme.typography.bodyMedium)
        }

        state.error?.let { error ->
            Text(
                text = error.message(state.errorDetail),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            A11yOutlinedButton(
                label = stringResource(R.string.common_ok),
                onClick = { viewModel.clearError() },
            )
        }

        state.outputPath?.let { path ->
            Text(
                text = stringResource(R.string.mix_done, path.substringAfterLast('/')),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(text = stringResource(R.string.mix_done_note), style = MaterialTheme.typography.bodyMedium)

            if (state.limited) {
                // Balandlik kuchaytirilgan-u, natija balandroq bo'lmagan holat:
                // jimgina qoldirilsa, foydalanuvchi buni xato deb o'ylardi.
                Text(
                    text = stringResource(R.string.mix_limited, loweringText(state.appliedGainDb)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            A11yOutlinedButton(
                label = stringResource(R.string.common_ok),
                onClick = { viewModel.consumeOutput() },
            )
            A11yOutlinedButton(
                label = stringResource(R.string.mix_open_saved),
                onClick = { onOpenSaved(path) },
                modifier = Modifier.fillMaxWidth(),
            )
            A11yButton(
                label = stringResource(R.string.mix_share),
                onClick = {
                    val started = Sharing.share(
                        context = context,
                        file = File(path),
                        mime = Sharing.WAV_MIME,
                        chooserTitle = context.getString(R.string.mix_share_chooser),
                    )
                    if (!started) announce(context.getString(R.string.mix_share_failed))
                },
                modifier = Modifier.fillMaxWidth(),
                description = path.substringAfterLast('/'),
                enabled = !state.busy,
            )
            Text(text = stringResource(R.string.mix_share_note), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Bitta yo'l: nomi, manba ma'lumoti, uchta sozlama va uchta tugma.
 *
 * Tugmalar **to'liq kenglikda va ustma-ust**: yorliqlari uzun («Faqat shuni
 * eshitish»), yonma-yon qo'yilsa ular kichik ekranda kesilib qolardi.
 */
@Composable
private fun TrackSection(
    track: MixTrackUi,
    enabled: Boolean,
    onGain: (String) -> Unit,
    onGainNudge: (Double) -> Unit,
    onPan: (String) -> Unit,
    onPanNudge: (Double) -> Unit,
    onOffset: (String) -> Unit,
    onOffsetNudge: (Double) -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = track.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )

        if (track.missing) {
            Text(
                text = stringResource(R.string.mix_track_missing),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = fileSummary(track.channels, track.sampleRate, track.durationMs),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        NumericRow(
            label = stringResource(R.string.mix_track_gain, track.name),
            value = track.gainText,
            onValueChange = onGain,
            onNudge = onGainNudge,
            step = MixTrackText.GAIN_STEP_DB,
            decreaseDescription = "${stringResource(R.string.mix_decrease)}: ${track.name}",
            increaseDescription = "${stringResource(R.string.mix_increase)}: ${track.name}",
            enabled = enabled,
        )

        NumericRow(
            label = stringResource(R.string.mix_track_pan, track.name),
            value = track.panText,
            onValueChange = onPan,
            onNudge = onPanNudge,
            step = MixTrackText.PAN_STEP,
            decreaseDescription = "${stringResource(R.string.mix_decrease)}: ${track.name}",
            increaseDescription = "${stringResource(R.string.mix_increase)}: ${track.name}",
            enabled = enabled,
        )

        NumericRow(
            label = stringResource(R.string.mix_track_offset, track.name),
            value = track.offsetText,
            onValueChange = onOffset,
            onNudge = onOffsetNudge,
            step = MixTrackText.OFFSET_STEP_MS,
            decreaseDescription = "${stringResource(R.string.mix_decrease)}: ${track.name}",
            increaseDescription = "${stringResource(R.string.mix_increase)}: ${track.name}",
            enabled = enabled,
        )

        // Yorliq har doim BOSILGANDA nima bo'lishini aytadi, ya'ni u joriy
        // holatni ham bildiradi: «Ovozini o'chirish» — hozir eshitilyapti.
        A11yOutlinedButton(
            label = stringResource(if (track.muted) R.string.mix_unmute else R.string.mix_mute),
            onClick = onMute,
            modifier = Modifier.fillMaxWidth(),
            description = track.name,
            enabled = enabled,
        )

        A11yOutlinedButton(
            label = stringResource(if (track.solo) R.string.mix_unsolo else R.string.mix_solo),
            onClick = onSolo,
            modifier = Modifier.fillMaxWidth(),
            description = track.name,
            enabled = enabled,
        )

        A11yOutlinedButton(
            label = stringResource(R.string.mix_remove),
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth(),
            description = track.name,
            enabled = enabled,
        )
    }
}

/** Qo'shish uchun yozuv qatori: butun qator bosiladi va bir element bo'lib o'qiladi. */
@Composable
private fun SourceRow(recording: Recording, enabled: Boolean, onAdd: () -> Unit) {
    val title = recording.title
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = title,
                onClick = onAdd,
            )
            .padding(vertical = 14.dp),
    ) {
        Text(text = recording.file.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = fileSummary(recording.info, recording.durationMs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

/** Xatoni joriy tilga o'giradi. [detail] — sababni aniqlashtiruvchi ma'lumot. */
@Composable
private fun MixUiError.message(detail: String): String = stringResource(
    when (this) {
        MixUiError.EMPTY -> R.string.mix_error_empty
        MixUiError.TOO_MANY -> R.string.mix_error_too_many
        MixUiError.ALREADY_ADDED -> R.string.mix_error_already
        MixUiError.FILE_MISSING -> R.string.mix_error_missing
        MixUiError.SAMPLE_RATE_MISMATCH -> R.string.mix_error_rate
        MixUiError.UNSUPPORTED_CHANNELS -> R.string.mix_error_channels
        MixUiError.NO_AUDIBLE_TRACK -> R.string.mix_error_silent
        MixUiError.WRITE_FAILED -> R.string.mix_error_write
    },
    detail,
)

/**
 * Tushirish miqdori, desibelda — ishorasiz.
 *
 * Ishorasiz, chunki xabar «N dB tushirildi» deb yozilgan: manfiy ishora
 * o'sha gapni ikki marta aytgan bo'lardi.
 */
private fun loweringText(appliedGainDb: Float): String =
    DecimalText.format(Math.abs(appliedGainDb).toDouble(), 0.0, MAX_LOWERING_DB, 1)

/** Tushirish 60 dB dan oshmaydi — 1/1000 koeffitsient, undan kattasi jimlik. */
private const val MAX_LOWERING_DB = 60.0

