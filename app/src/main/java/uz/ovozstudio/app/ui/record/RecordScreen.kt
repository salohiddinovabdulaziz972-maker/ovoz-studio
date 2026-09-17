package uz.ovozstudio.app.ui.record

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.ovozstudio.app.R
import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.RecorderConfig
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.ChoiceRow
import uz.ovozstudio.app.ui.common.SwitchRow
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.ui.common.spokenTime
import uz.ovozstudio.app.util.TimeFormat

@Composable
fun RecordScreen(
    onBack: () -> Unit,
    onOpenSaved: (String) -> Unit,
    viewModel: RecordViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val announce = rememberAnnouncer()

    var hasPermission by remember { mutableStateOf(context.hasRecordPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            announce(context.getString(R.string.record_permission_message))
        }
    }

    // Ovozli e'lon faqat holat o'zgarganda — taymer har soniyada o'qilmaydi.
    LaunchedEffect(state.isRecording) {
        if (state.isRecording) {
            announce(context.getString(R.string.record_started))
        }
    }

    // Ovozli shakl shu yerda — kompozitsiya ichida — hisoblanadi. `LaunchedEffect`
    // bloki kompozitsiya emas, shuning uchun `spokenTime` ni uning ichida
    // chaqirib bo'lmaydi; tayyor matn effektga uzatiladi.
    val savedSpoken = spokenTime(state.savedDurationMs)

    LaunchedEffect(state.savedPath) {
        state.savedPath?.let {
            announce(context.getString(R.string.record_saved, savedSpoken))
        }
    }

    val savedPath = state.savedPath

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            A11yOutlinedButton(
                label = stringResource(R.string.common_back),
                onClick = onBack,
            )
        }

        Text(
            text = stringResource(R.string.record_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        if (!hasPermission) {
            PermissionBlock(
                onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onOpenSettings = { context.openAppSettings() },
            )
            return@Column
        }

        TimerBlock(
            elapsedMs = state.elapsedMs,
            level = state.level,
            onAnnounceTime = { announce(it) },
        )

        RecordControls(
            state = state,
            onStart = { viewModel.startRecording() },
            onPause = { viewModel.pauseRecording() },
            onResume = { viewModel.resumeRecording() },
            onStop = { viewModel.stopRecording() },
            onMarker = {
                viewModel.addMarker()?.let { at ->
                    announce(context.getString(R.string.record_marker_added, state.markerCount + 1))
                }
            },
        )

        if (savedPath != null) {
            Text(
                text = stringResource(R.string.record_saved, TimeFormat.format(state.savedDurationMs)),
                style = MaterialTheme.typography.bodyLarge,
            )
            A11yButton(
                label = stringResource(R.string.home_action_trim),
                onClick = {
                    onOpenSaved(savedPath)
                    viewModel.consumeSaved()
                },
            )
        }

        state.errorMessage?.let { message ->
            Text(
                text = stringResource(R.string.record_error_generic, message),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            A11yOutlinedButton(
                label = stringResource(R.string.common_ok),
                onClick = { viewModel.clearError() },
            )
        }

        QualityBlock(
            config = state.config,
            enabled = state.configEditable,
            onChange = viewModel::updateConfig,
        )
    }
}

@Composable
private fun TimerBlock(
    elapsedMs: Long,
    level: Float,
    /** Tayyor ovozli matnni oladi: taymerni faqat foydalanuvchi so'raganda o'qish uchun. */
    onAnnounceTime: (String) -> Unit,
) {
    val levelDescription = stringResource(R.string.record_level_a11y, (level * 100).toInt())

    // Ovozli shakl kompozitsiyada hisoblanadi, tugma bosilganda esa faqat o'qiladi:
    // `onClick` bloki ham kompozitsiya emas, u yerda `spokenTime` chaqirilmaydi.
    val spokenElapsed = spokenTime(elapsedMs)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = TimeFormat.format(elapsedMs),
            style = MaterialTheme.typography.displaySmall,
        )

        // Daraja ko'rsatkichi avtomatik e'lon qilinmaydi: u tez o'zgaradi va
        // ekran o'quvchini bosib ketadi. Foydalanuvchi ustiga kelganda o'qiydi.
        LinearProgressIndicator(
            progress = { level.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .semantics { contentDescription = levelDescription },
        )

        A11yOutlinedButton(
            label = stringResource(R.string.record_announce_time),
            onClick = { onAnnounceTime(spokenElapsed) },
        )
    }
}

@Composable
private fun RecordControls(
    state: RecordUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onMarker: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            !state.isRecording -> A11yButton(
                label = stringResource(R.string.record_start),
                onClick = onStart,
            )

            state.isPaused -> {
                A11yButton(
                    label = stringResource(R.string.record_resume),
                    onClick = onResume,
                )
                A11yOutlinedButton(
                    label = stringResource(R.string.record_stop),
                    onClick = onStop,
                )
            }

            else -> {
                A11yButton(
                    label = stringResource(R.string.record_pause),
                    onClick = onPause,
                )
                A11yOutlinedButton(
                    label = stringResource(R.string.record_marker),
                    onClick = onMarker,
                    description = stringResource(R.string.record_marker_count, state.markerCount),
                )
                A11yOutlinedButton(
                    label = stringResource(R.string.record_stop),
                    onClick = onStop,
                )
            }
        }
    }
}

@Composable
private fun QualityBlock(
    config: RecorderConfig,
    enabled: Boolean,
    onChange: (RecorderConfig) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.record_quality_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        ChoiceRow(
            label = stringResource(R.string.record_sample_rate),
            options = RecorderConfig.SAMPLE_RATES.toList(),
            selected = config.sampleRate,
            onSelect = { onChange(config.copy(sampleRate = it)) },
            optionLabel = { "$it Hz" },
            enabled = enabled,
        )

        ChoiceRow(
            label = stringResource(R.string.record_bit_depth),
            options = BitDepth.entries.toList(),
            selected = config.bitDepth,
            onSelect = { onChange(config.copy(bitDepth = it)) },
            optionLabel = { "${it.bits} bit" },
            enabled = enabled,
        )

        SwitchRow(
            label = stringResource(R.string.record_channel_stereo),
            checked = config.stereo,
            onCheckedChange = { onChange(config.copy(stereo = it)) },
            enabled = enabled,
        )

        SwitchRow(
            label = stringResource(R.string.record_noise_suppression),
            checked = config.noiseSuppression,
            onCheckedChange = { onChange(config.copy(noiseSuppression = it)) },
            enabled = enabled,
        )

        SwitchRow(
            label = stringResource(R.string.record_echo_cancellation),
            checked = config.echoCancellation,
            onCheckedChange = { onChange(config.copy(echoCancellation = it)) },
            enabled = enabled,
        )
    }
}

@Composable
private fun PermissionBlock(
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.record_permission_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )
        Text(
            text = stringResource(R.string.record_permission_message),
            style = MaterialTheme.typography.bodyLarge,
        )
        A11yButton(
            label = stringResource(R.string.record_permission_grant),
            onClick = onRequest,
        )
        A11yOutlinedButton(
            label = stringResource(R.string.record_permission_settings),
            onClick = onOpenSettings,
        )
    }
}

private fun Context.hasRecordPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
}
