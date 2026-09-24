package uz.ovozstudio.app.ui.audiobook

import android.text.format.Formatter
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.ovozstudio.app.R
import uz.ovozstudio.app.media.voice.AudioBookExporter
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.ChoiceRow
import uz.ovozstudio.app.ui.common.FileTypes
import uz.ovozstudio.app.ui.common.StatusMessage
import uz.ovozstudio.app.ui.common.WorkProgress
import uz.ovozstudio.app.ui.common.a11yGroup
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.util.LocalizedNumber
import uz.ovozstudio.app.util.Sharing
import java.io.File

/** O'qish tezligi variantlari. 1.0 — odatiy. */
private val RATES = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/**
 * Hujjatni MP3 audio-kitobga aylantirish.
 *
 * Eksport [uz.ovozstudio.app.media.voice.AudioBookService] ichida ketadi —
 * bu ekrandan chiqib ketilsa yoki telefon qulflansa ham davom etadi;
 * ViewModel shu xizmatning holatini kuzatib turadi.
 */
@Composable
fun AudioBookScreen(
    onBack: () -> Unit,
    viewModel: AudioBookViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(uri)
    }

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mpeg"),
    ) { uri ->
        if (uri != null) viewModel.saveTo(uri)
    }

    val busyText = when {
        state.opening -> stringResource(R.string.reader_opening)
        else -> ""
    }

    val defaultEngine = stringResource(R.string.reader_engine_default)
    val engineOptions: List<String?> = listOf(null) + state.engines.map { it.packageName }
    val engineLabels: Map<String?, String> =
        mapOf<String?, String>(null to defaultEngine) + state.engines.associate { it.packageName to it.label }

    val autoVoice = stringResource(R.string.reader_voice_auto)
    val voiceOptions: List<String?> = listOf(null) + state.voices.map { it.id }
    val voiceLabels: Map<String?, String> =
        mapOf<String?, String>(null to autoVoice) + state.voices.associate { it.id to "${it.name} (${it.localeTag})" }

    val rateLabels: Map<Float, String> = RATES.associateWith { rate ->
        stringResource(R.string.reader_rate_option, LocalizedNumber.format(rate.toDouble(), locale, fractionDigits = 2))
    }

    val stageLabel = when (state.stage) {
        AudioBookExporter.Stage.SYNTHESIZING, null -> stringResource(R.string.audiobook_stage_voice)
        AudioBookExporter.Stage.JOINING -> stringResource(R.string.audiobook_stage_join)
        AudioBookExporter.Stage.ENCODING -> stringResource(R.string.audiobook_stage_mp3)
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
            text = stringResource(R.string.audiobook_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        Text(
            text = stringResource(R.string.audiobook_intro),
            style = MaterialTheme.typography.bodyLarge,
        )

        A11yButton(
            label = stringResource(if (state.isOpen) R.string.reader_pick_other else R.string.reader_pick),
            onClick = { picker.launch(FileTypes.DOCUMENTS) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.opening && !state.running,
        )

        if (state.opening) {
            WorkProgress(label = busyText, progress = null)
        }

        state.error?.let { error ->
            StatusMessage(
                message = stringResource(error.messageRes()),
                onDismiss = { viewModel.clearError() },
                isError = true,
            )
        }

        if (state.isOpen && !state.running) {
            Column(modifier = Modifier.a11yGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.audiobook_opened, state.documentName, state.paragraphCount),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            if (!state.engineReady) {
                Text(
                    text = stringResource(R.string.reader_engine_preparing),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (!state.hasUzbekVoice) {
                Text(
                    text = stringResource(R.string.reader_no_uzbek),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val voiceSummary = voiceLabels[state.selectedVoiceId].orEmpty()
            Text(
                text = stringResource(R.string.audiobook_voice_summary, voiceSummary),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (state.engines.size > 1) {
                ChoiceRow(
                    label = stringResource(R.string.reader_engine_title),
                    options = engineOptions,
                    selected = state.selectedEngine,
                    onSelect = { viewModel.selectEngine(it) },
                    optionLabel = { engineLabels[it] ?: it.orEmpty() },
                )
            }
            if (state.voices.isNotEmpty()) {
                ChoiceRow(
                    label = stringResource(R.string.reader_voice_title),
                    options = voiceOptions,
                    selected = state.selectedVoiceId,
                    onSelect = { viewModel.selectVoice(it) },
                    optionLabel = { voiceLabels[it] ?: it.orEmpty() },
                )
            }
            ChoiceRow(
                label = stringResource(R.string.reader_rate_title),
                options = RATES,
                selected = state.rate,
                onSelect = { viewModel.setRate(it) },
                optionLabel = { rateLabels[it].orEmpty() },
            )

            A11yButton(
                label = stringResource(R.string.audiobook_start),
                onClick = { viewModel.start() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.engineReady,
            )
        }

        if (state.running) {
            WorkProgress(
                label = stringResource(R.string.audiobook_progress, stageLabel),
                progress = state.progress,
            )
            A11yOutlinedButton(
                label = stringResource(R.string.audiobook_cancel),
                onClick = { viewModel.cancel() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.audiobook_background_note),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        state.result?.let { result ->
            Text(
                text = stringResource(
                    R.string.trim_result_ready,
                    result.name,
                    Formatter.formatShortFileSize(context, result.sizeBytes),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            A11yButton(
                label = stringResource(R.string.trim_save),
                onClick = { saver.launch(result.name) },
                modifier = Modifier.fillMaxWidth(),
            )
            val shareTitle = stringResource(R.string.common_share_title)
            A11yOutlinedButton(
                label = stringResource(R.string.trim_share),
                onClick = { Sharing.share(context, File(result.path), "audio/mpeg", shareTitle) },
                modifier = Modifier.fillMaxWidth(),
            )
            A11yOutlinedButton(
                label = stringResource(R.string.common_ok),
                onClick = { viewModel.clearResult() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AudioBookError.messageRes(): Int = when (this) {
    AudioBookError.UNSUPPORTED -> R.string.reader_error_unsupported
    AudioBookError.BROKEN -> R.string.reader_error_broken
    AudioBookError.TOO_LARGE -> R.string.reader_error_too_large
    AudioBookError.NO_TEXT -> R.string.reader_error_no_text
    AudioBookError.BROKEN_TEXT -> R.string.reader_error_broken_text
    AudioBookError.PASSWORD -> R.string.reader_error_password
    AudioBookError.VOICE_MISSING -> R.string.reader_error_voice_missing
    AudioBookError.FAILED -> R.string.audiobook_error_failed
    AudioBookError.CANCELLED -> R.string.audiobook_error_cancelled
    AudioBookError.NOT_STARTED -> R.string.audiobook_error_not_started
}
