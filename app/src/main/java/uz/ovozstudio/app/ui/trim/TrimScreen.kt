package uz.ovozstudio.app.ui.trim

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.ovozstudio.app.R
import uz.ovozstudio.app.util.MediaSaver
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import android.Manifest
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.DocumentPicker
import uz.ovozstudio.app.ui.common.A11yChoiceRow
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.FileTypes
import uz.ovozstudio.app.ui.common.KeepScreenOn
import uz.ovozstudio.app.ui.common.ParamsGroup
import uz.ovozstudio.app.ui.common.RecentFilesBlock
import uz.ovozstudio.app.ui.common.StatusMessage
import uz.ovozstudio.app.ui.common.TimeInput
import uz.ovozstudio.app.ui.common.WorkProgress
import uz.ovozstudio.app.ui.common.a11yGroup
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.fileSummary
import uz.ovozstudio.app.ui.common.importFailureMessage
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.ui.common.spokenTime
import uz.ovozstudio.app.util.Sharing
import uz.ovozstudio.app.util.TimeFormat
import java.io.File

/**
 * Audioni kesib olish yoki undan qism o'chirish.
 *
 * Ikkala rejim bitta ekran: farq faqat sarlavha, tushuntirish va bitta
 * amal tugmasida. Natija **hamisha yuklangan faylning formatida** chiqadi.
 */
@Composable
fun TrimScreen(
    mode: TrimMode,
    onBack: () -> Unit,
    viewModel: TrimViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val announce = rememberAnnouncer()

    // Parametrlar (boshlanish/tugash vaqti) YOPIQ boshlanadi: standart tanlov —
    // butun fayl — aksariyat foydalanuvchiga yetarli, shuning uchun bosh holatda
    // faqat xulosa va bitta tugma ko'rinadi ("Kesib olish" tugmasigacha yo'l
    // qisqa bo'lsin). Kengaytirilgan holat ekran aylanganda ham saqlanadi.
    var paramsExpanded by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(DocumentPicker.OpenAudio) { uri ->
        if (uri != null) viewModel.open(uri)
    }

    // «Saqlash» oynasi yo'q: tugma bosilishi bilan fayl qurilmaning
    // papkasiga yoziladi (bitta bosish). Fayl turi natijaga qarab o'zgaradi.
    val result = state.result

    // Android 9 va undan pastda MediaStore'ga yozish uchun ruxsat kerak.
    // So'rov javobidan qat'i nazar saqlashga urinamiz: rad etilsa xato
    // jurnalga tushadi va ekranda ko'rinadi — foydalanuvchi tugmani ikkinchi
    // marta bosib qolmasin deb, javobni kutib turmaymiz.
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.save() }

    val saveResult: () -> Unit = {
        if (MediaSaver.needsLegacyPermission &&
            ContextCompat.checkSelfPermission(context, WRITE_PERMISSION) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(WRITE_PERMISSION)
        } else {
            viewModel.save()
        }
    }

    // Uzoq ish boshlanganda ovozda aytiladi: ekran o'quvchi foydalanuvchisi
    // ekranni ko'rmaydi, «hech narsa bo'lmayapti» deb o'ylamasligi kerak.
    val busyText = when (state.busy) {
        TrimBusy.NONE -> ""
        TrimBusy.OPENING -> stringResource(R.string.audio_busy_opening)
        TrimBusy.EDITING -> stringResource(R.string.audio_busy_editing)
        TrimBusy.PREPARING -> stringResource(R.string.audio_busy_preparing)
        TrimBusy.SAVING -> stringResource(R.string.audio_busy_saving)
    }
    LaunchedEffect(busyText) {
        if (busyText.isNotEmpty()) announce(busyText)
    }

    val spokenDuration = spokenTime(state.durationMs)
    val openedMessage = stringResource(R.string.audio_opened, state.fileName, state.formatName, spokenDuration)
    LaunchedEffect(state.fileName, state.formatName) {
        if (state.isOpen) announce(openedMessage)
    }

    val editDoneMessage = stringResource(R.string.trim_edit_done, spokenDuration)
    LaunchedEffect(state.revision) {
        if (state.revision > 0) announce(editDoneMessage)
    }

    // Ro'yxat bir marta, ekran ochilganda o'qiladi. Har fayl ochilganda
    // yangilanadi — bu holda `revision` o'zgaradi.
    LaunchedEffect(state.revision, state.isOpen) {
        if (!state.isOpen) viewModel.refreshRecent()
    }

    // Tinglash davomida ekran o'chmasin: foydalanuvchi tinglab o'tiradi va
    // ekranga tegmaydi, ekran o'chsa ekran o'quvchi bilan boshqarish qiyin.
    KeepScreenOn(active = state.isPlaying || state.isPlayingRemoved)

    val startMs = viewModel.selectionStartMs(state)
    val endMs = viewModel.selectionEndMs(state)

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
            onClick = {
                viewModel.stopPlayback()
                onBack()
            },
        )

        Text(
            text = stringResource(if (mode == TrimMode.CUT) R.string.trim_title_cut else R.string.trim_title_delete),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        Text(
            text = stringResource(if (mode == TrimMode.CUT) R.string.trim_intro_cut else R.string.trim_intro_delete),
            style = MaterialTheme.typography.bodyLarge,
        )

        A11yButton(
            label = stringResource(if (state.isOpen) R.string.audio_pick_other else R.string.audio_pick),
            onClick = { picker.launch(FileTypes.AUDIO) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isBusy,
        )

        // Oxirgi fayllar faqat ish boshlanmagan paytda ko'rsatiladi: fayl
        // ochilgach ekranda o'z ishi ko'p, ro'yxat esa faqat chalg'itardi.
        if (!state.isOpen) {
            RecentFilesBlock(
                files = state.recentFiles,
                query = state.recentQuery,
                onQueryChange = viewModel::searchRecent,
                onClear = viewModel::clearRecent,
                onPick = { picker.launch(FileTypes.AUDIO) },
                enabled = !state.isBusy,
            )
        }

        if (state.isBusy) {
            WorkProgress(label = busyText, progress = state.progress)
        }

        val info = state.info
        if (info != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.a11yGroup(),
            ) {
                Text(
                    text = stringResource(R.string.audio_file_name, state.fileName),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.audio_format_line, state.formatName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.trim_file_duration, TimeFormat.format(state.durationMs)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = fileSummary(info, state.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Uch bosqichli ish. Har bir bosqich o'z sarlavhasi bilan turadi,
            // shuning uchun ekran o'quvchisi bilan yurganda foydalanuvchi
            // qaysi qadamda ekanini va yana nechta qadam qolganini biladi.
            Text(
                text = stringResource(R.string.trim_step1_heading),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.a11yHeading(),
            )

            val paramsSummary = stringResource(
                R.string.trim_params_summary,
                TimeFormat.format(startMs),
                TimeFormat.format(endMs),
                spokenTime((endMs - startMs).coerceAtLeast(0)),
            )
            ParamsGroup(
                summary = paramsSummary,
                expanded = paramsExpanded,
                onExpandedChange = { paramsExpanded = it },
            ) {
                Text(
                    text = stringResource(R.string.trim_selection_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.a11yHeading(),
                )

                TimeInput(
                    label = stringResource(R.string.trim_start_label),
                    parts = state.startParts,
                    onPartsChange = viewModel::setStart,
                    isError = !state.startParts.isEmpty && state.startParts.toMillisOrNull() == null,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                )

                TimeInput(
                    label = stringResource(R.string.trim_end_label),
                    parts = state.endParts,
                    onPartsChange = viewModel::setEnd,
                    isError = !state.endParts.isEmpty && state.endParts.toMillisOrNull() == null,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = paramsSummary,
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (state.isPlaying) {
                    val backDone = stringResource(R.string.trim_play_back_done)
                    val forwardDone = stringResource(R.string.trim_play_forward_done)
                    // Oldinga/orqaga surish tugmalari. Ekran o'quvchi
                    // foydalanuvchisi uchun bu sakrashning yagona yo'li:
                    // slayderni barmoq bilan aniq nishonga olish imkoni yo'q.
                    // Har bosishda yangi pozitsiya ovozda aytiladi.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        A11yOutlinedButton(
                            label = stringResource(R.string.trim_play_back),
                            onClick = {
                                viewModel.skipPlayback(-SKIP_MS)
                                announce(skipMessage(backDone, -SKIP_MS, state.playPositionMs))
                            },
                            modifier = Modifier.weight(1f),
                        )
                        A11yOutlinedButton(
                            label = stringResource(R.string.trim_play_forward),
                            onClick = {
                                viewModel.skipPlayback(SKIP_MS)
                                announce(skipMessage(forwardDone, SKIP_MS, state.playPositionMs))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    A11yOutlinedButton(
                        label = stringResource(R.string.trim_stop),
                        onClick = { viewModel.stopPlayback() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    A11yOutlinedButton(
                        label = stringResource(R.string.trim_play),
                        onClick = { viewModel.playSelection() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isBusy && endMs > startMs,
                    )
                }

                // Tezlik va balandlik. Uchtadan bittasi tanlanadi: slayder
                // o'rniga tugmalar — sabab A11yChoiceRow izohida.
                val speedLabels = listOf(
                    stringResource(R.string.trim_speed_slow),
                    stringResource(R.string.trim_speed_normal),
                    stringResource(R.string.trim_speed_fast),
                )
                val speedSelected = speedIndex(state.playSpeed)
                A11yChoiceRow(
                    label = stringResource(R.string.trim_speed_title),
                    description = stringResource(R.string.trim_speed_value, speedLabels[speedSelected]),
                    options = speedLabels.mapIndexed { index, name -> name to (index == speedSelected) },
                    enabled = !state.isBusy,
                    onSelect = { index ->
                        viewModel.setSpeed(SPEED_VALUES[index])
                        announce(speedLabels[index])
                    },
                )

                val pitchLabels = listOf(
                    stringResource(R.string.trim_pitch_low),
                    stringResource(R.string.trim_pitch_normal),
                    stringResource(R.string.trim_pitch_high),
                )
                val pitchSelected = pitchIndex(state.playPitch)
                A11yChoiceRow(
                    label = stringResource(R.string.trim_pitch_title),
                    description = stringResource(R.string.trim_pitch_value, pitchLabels[pitchSelected]),
                    options = pitchLabels.mapIndexed { index, name -> name to (index == pitchSelected) },
                    enabled = !state.isBusy,
                    onSelect = { index ->
                        viewModel.setPitch(PITCH_VALUES[index])
                        announce(pitchLabels[index])
                    },
                )
            }

            // Bitta amal tugmasi: rejimga qarab «kesib olish» yoki «o'chirish».
            A11yButton(
                label = stringResource(
                    if (mode == TrimMode.CUT) R.string.trim_apply_cut else R.string.trim_apply_delete,
                ),
                onClick = {
                    if (mode == TrimMode.CUT) viewModel.cutSelection() else viewModel.deleteSelection()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isBusy,
            )

            A11yOutlinedButton(
                label = stringResource(R.string.trim_undo),
                onClick = { viewModel.undo() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canUndo && !state.isBusy,
            )

            // «O'chirilgan qismlarni eshitish»: tahrirdan keyin foydalanuvchi
            // nima o'chganini qulog'i bilan tekshira olsin. Ekran o'quvchi
            // foydalanuvchisi natijani ko'rmaydi — eshitadi.
            if (state.removedRangesAvailable) {
                Text(
                    text = stringResource(R.string.trim_removed_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.a11yHeading(),
                )
                Text(
                    text = stringResource(R.string.trim_removed_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (state.isPlayingRemoved) {
                    Text(
                        text = stringResource(
                            R.string.trim_removed_position,
                            spokenTime(state.playRemovedPositionMs),
                            spokenTime(state.removedPreviewDurationMs),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.trim_removed_skip_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val backDone = stringResource(R.string.trim_play_back_done)
                    val forwardDone = stringResource(R.string.trim_play_forward_done)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        A11yOutlinedButton(
                            label = stringResource(R.string.trim_play_back),
                            onClick = {
                                viewModel.skipRemovedPlayback(-SKIP_MS)
                                announce(skipMessage(backDone, -SKIP_MS, state.playRemovedPositionMs))
                            },
                            modifier = Modifier.weight(1f),
                        )
                        A11yOutlinedButton(
                            label = stringResource(R.string.trim_play_forward),
                            onClick = {
                                viewModel.skipRemovedPlayback(SKIP_MS)
                                announce(skipMessage(forwardDone, SKIP_MS, state.playRemovedPositionMs))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    A11yOutlinedButton(
                        label = stringResource(R.string.trim_removed_stop),
                        onClick = { viewModel.stopRemovedPlayback() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (state.removedPreviewReady) {
                    A11yOutlinedButton(
                        label = stringResource(R.string.trim_removed_play),
                        onClick = { viewModel.playRemovedPreview() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isBusy,
                    )
                } else {
                    A11yOutlinedButton(
                        label = stringResource(R.string.trim_removed_prepare),
                        onClick = { viewModel.prepareRemovedPreview() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isBusy,
                    )
                    if (state.removedPreviewDurationMs > 0L) {
                        Text(
                            text = stringResource(
                                R.string.trim_removed_ready,
                                spokenTime(state.removedPreviewDurationMs),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.trim_step2_heading),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.a11yHeading(),
            )
            Text(
                text = stringResource(R.string.trim_result_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.trim_result_note, state.formatName),
                style = MaterialTheme.typography.bodyMedium,
            )

            A11yButton(
                label = stringResource(R.string.trim_prepare),
                onClick = { viewModel.prepareResult(mode) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canUndo && !state.isBusy,
            )
            if (!state.canUndo) {
                Text(
                    text = stringResource(R.string.trim_prepare_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (result != null) {
                Text(
                    text = stringResource(
                        R.string.trim_result_ready,
                        result.name,
                        Formatter.formatShortFileSize(context, result.sizeBytes),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.trim_step3_heading),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.a11yHeading(),
                )
                A11yButton(
                    label = stringResource(R.string.trim_save),
                    onClick = saveResult,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isBusy,
                )
                val shareTitle = stringResource(R.string.common_share_title)
                val shareFailed = stringResource(R.string.common_share_failed)
                A11yOutlinedButton(
                    label = stringResource(R.string.trim_share),
                    onClick = {
                        val started = Sharing.share(context, File(result.path), result.mimeType, shareTitle)
                        if (!started) announce(shareFailed)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isBusy,
                )
            }
        }

        state.error?.let { error ->
            StatusMessage(
                message = error.message(),
                onDismiss = { viewModel.clearError() },
                isError = true,
            )
        }

        state.openFailure?.let { failure ->
            StatusMessage(
                message = importFailureMessage(failure, state.openFailureFormat),
                onDismiss = { viewModel.clearOpenFailure() },
                isError = true,
            )
        }

        state.savedName?.let { name ->
            StatusMessage(
                message = stringResource(R.string.trim_saved, name, state.savedTo ?: name),
                onDismiss = { viewModel.clearSaved() },
            )
        }
    }
}

/** «Saqlash» oynasi uchun zaxira tur: natija hali yo'q paytda ham kontrakt yaratilishi kerak. */

@Composable
private fun TrimError.message(): String = stringResource(
    when (this) {
        TrimError.SELECTION_EMPTY -> R.string.trim_error_selection_empty
        TrimError.SELECTION_ALL -> R.string.trim_error_selection_all
        TrimError.NOTHING_TO_UNDO -> R.string.trim_error_nothing_to_undo
        TrimError.EDIT_FAILED -> R.string.trim_error_edit_failed
        TrimError.EXPORT_FAILED -> R.string.trim_error_export_failed
        TrimError.SAVE_FAILED -> R.string.trim_error_save_failed
        TrimError.REMOVED_PREVIEW_FAILED -> R.string.trim_removed_error
    },
)

/** Android 9 va pastda MediaStore'ga yozish uchun so'raladigan ruxsat. */
private const val WRITE_PERMISSION = android.Manifest.permission.WRITE_EXTERNAL_STORAGE

/** Bir bosishda suriladigan oraliq: 10 soniya. */
private const val SKIP_MS = 10_000L

/** Tezlik tugmalariga mos qiymatlar (sekin / oddiy / tez). */
private val SPEED_VALUES = listOf(0.75f, 1f, 1.5f)

/** Balandlik tugmalariga mos qiymatlar (past / oddiy / baland). */
private val PITCH_VALUES = listOf(0.85f, 1f, 1.15f)

/**
 * Surish natijasini ovozda aytish uchun matn.
 *
 * [beforeMs] — tugma bosilishidan OLDINGI pozitsiya; yangi pozitsiya shundan
 * hisoblanadi, chunki holat yangilanishi bir kadr orqada qolishi mumkin.
 *
 * Ataylab `@Composable` emas: matn `onClick` ichida yasaladi, u joyda esa
 * `stringResource` chaqirib bo'lmaydi. Shuning uchun tayyor shablon matni
 * tashqaridan beriladi.
 */
private fun skipMessage(template: String, deltaMs: Long, beforeMs: Long): String {
    val target = (beforeMs + deltaMs).coerceAtLeast(0L)
    return template.format(spokenTime(target))
}

/** Tezlik qiymatiga mos tugma indeksi (0 — sekin, 1 — oddiy, 2 — tez). */
private fun speedIndex(speed: Float): Int = when {
    speed < 0.95f -> 0
    speed > 1.05f -> 2
    else -> 1
}

/** Balandlik qiymatiga mos tugma indeksi. */
private fun pitchIndex(pitch: Float): Int = when {
    pitch < 0.95f -> 0
    pitch > 1.05f -> 2
    else -> 1
}
