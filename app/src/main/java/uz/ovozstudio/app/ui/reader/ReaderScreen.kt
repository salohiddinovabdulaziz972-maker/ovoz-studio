package uz.ovozstudio.app.ui.reader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.ovozstudio.app.R
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.ChoiceRow
import uz.ovozstudio.app.ui.common.FileTypes
import uz.ovozstudio.app.ui.common.StatusMessage
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.rememberAnnouncer
import uz.ovozstudio.app.util.LocalizedNumber

/** O'qish tezligi variantlari. 1.0 — odatiy. */
private val RATES = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/**
 * Hujjatni o'qish.
 *
 * Ekran uch qismdan iborat: **o'qish** (matn va boshqaruv), **boblar**
 * (ro'yxatdan tanlash) va **ovoz sozlamalari**. Tizimning «orqaga» tugmasi
 * qismdan matnga qaytaradi, matndan esa ekrandan chiqaradi.
 *
 * Boshqaruv tugmalari matnning **tepasida** turadi: ekran o'quvchi
 * foydalanuvchisi tugmaga yetish uchun yuzlab abzatsdan o'tib bormasligi kerak.
 */
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val announce = rememberAnnouncer()

    BackHandler(enabled = state.page != ReaderPage.READING) {
        viewModel.setPage(ReaderPage.READING)
    }

    // Ovoz yoqilgan paytda e'lon qilinmaydi: ekran o'quvchining ovozi ilovaning
    // ovoziga qo'shilib, ikkalasini ham tushunib bo'lmas qilardi.
    val openingText = stringResource(R.string.reader_opening)
    LaunchedEffect(state.opening) {
        if (state.opening) announce(openingText)
    }
    val openedText = stringResource(R.string.reader_opened, state.documentName, state.chapterTitles.size)
    LaunchedEffect(state.documentName, state.chapterTitles.size) {
        if (state.isOpen) announce(openedText)
    }

    when (state.page) {
        ReaderPage.READING -> ReadingPage(state = state, viewModel = viewModel, onBack = onBack)
        ReaderPage.CHAPTERS -> ChaptersPage(state = state, viewModel = viewModel)
        ReaderPage.SETTINGS -> SettingsPage(state = state, viewModel = viewModel)
    }
}

@Composable
private fun ReadingPage(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(uri)
    }
    val listState = rememberLazyListState()

    // O'qilayotgan abzats ekranda ko'rinib turishi uchun ro'yxat unga suriladi.
    LaunchedEffect(state.chapterIndex, state.paragraphIndex) {
        if (state.paragraphIndex in state.paragraphs.indices) {
            listState.animateScrollToItem(state.paragraphIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        A11yOutlinedButton(
            label = stringResource(R.string.common_back),
            onClick = {
                viewModel.pause()
                onBack()
            },
        )

        Text(
            text = stringResource(R.string.reader_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        if (!state.isOpen) {
            Text(
                text = stringResource(R.string.reader_intro),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        A11yButton(
            label = stringResource(if (state.isOpen) R.string.reader_pick_other else R.string.reader_pick),
            onClick = { picker.launch(FileTypes.DOCUMENTS) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.opening,
        )

        if (state.opening) {
            if (state.progressTotal > 0) {
                LinearProgressIndicator(
                    progress = { state.progressDone.toFloat() / state.progressTotal },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.reader_opening_progress, state.progressDone, state.progressTotal),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(text = stringResource(R.string.reader_opening), style = MaterialTheme.typography.bodyMedium)
            }
        }

        state.error?.let { error ->
            StatusMessage(
                message = stringResource(error.messageRes()),
                onDismiss = { viewModel.clearError() },
                isError = true,
            )
        }

        if (state.isOpen) {
            Text(
                text = stringResource(R.string.reader_doc_line, state.documentName, state.formatName),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    R.string.reader_chapter_line,
                    state.chapterIndex + 1,
                    state.chapterTitles.size,
                    state.chapterTitle,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.a11yHeading(),
            )

            if (state.speaking) {
                A11yButton(
                    label = stringResource(R.string.reader_pause),
                    onClick = { viewModel.pause() },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                A11yButton(
                    label = stringResource(R.string.reader_play),
                    onClick = { viewModel.play() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.paragraphs.isNotEmpty(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                A11yOutlinedButton(
                    label = stringResource(R.string.reader_prev_paragraph),
                    onClick = { viewModel.previousParagraph() },
                    modifier = Modifier.weight(1f),
                )
                A11yOutlinedButton(
                    label = stringResource(R.string.reader_next_paragraph),
                    onClick = { viewModel.nextParagraph() },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                A11yOutlinedButton(
                    label = stringResource(R.string.reader_prev_chapter),
                    onClick = { viewModel.previousChapter() },
                    modifier = Modifier.weight(1f),
                    enabled = state.chapterIndex > 0,
                )
                A11yOutlinedButton(
                    label = stringResource(R.string.reader_next_chapter),
                    onClick = { viewModel.nextChapter() },
                    modifier = Modifier.weight(1f),
                    enabled = state.chapterIndex + 1 < state.chapterTitles.size,
                )
            }
        }

        // Ovoz sozlamalari hujjat ochilmasdan ham kerak: avval ovozni tanlab olish mumkin.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.isOpen) {
                A11yOutlinedButton(
                    label = stringResource(R.string.reader_chapters),
                    onClick = { viewModel.setPage(ReaderPage.CHAPTERS) },
                    modifier = Modifier.weight(1f),
                )
            }
            A11yOutlinedButton(
                label = stringResource(R.string.reader_settings),
                onClick = { viewModel.setPage(ReaderPage.SETTINGS) },
                modifier = Modifier.weight(1f),
            )
        }

        if (state.isOpen) {
            val readFromHere = stringResource(R.string.reader_read_from_here)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.paragraphs) { index, paragraph ->
                    val current = index == state.paragraphIndex
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            )
                            .clickable(onClickLabel = readFromHere) { viewModel.playFrom(index) }
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChaptersPage(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        A11yOutlinedButton(
            label = stringResource(R.string.reader_back_to_text),
            onClick = { viewModel.setPage(ReaderPage.READING) },
        )
        Text(
            text = stringResource(R.string.reader_chapters_title, state.chapterTitles.size),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(state.chapterTitles) { index, title ->
                A11yOutlinedButton(
                    label = stringResource(R.string.reader_chapter_item, index + 1, title),
                    onClick = { viewModel.goToChapter(index) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
) {
    val context = LocalContext.current
    val announce = rememberAnnouncer()
    val locale = LocalConfiguration.current.locales[0]

    // ChoiceRow yorliqlarni oddiy funksiya orqali oladi, u ichida stringResource
    // chaqirib bo'lmaydi — shuning uchun yorliqlar oldindan tayyorlanadi.
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

    val settingsFailed = stringResource(R.string.reader_open_system_failed)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        A11yOutlinedButton(
            label = stringResource(R.string.reader_back_to_text),
            onClick = { viewModel.setPage(ReaderPage.READING) },
        )

        Text(
            text = stringResource(R.string.reader_settings_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        Text(
            text = stringResource(R.string.reader_settings_intro),
            style = MaterialTheme.typography.bodyLarge,
        )

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

        A11yOutlinedButton(
            label = stringResource(R.string.reader_open_system_settings),
            onClick = {
                if (!openVoiceSettings(context)) announce(settingsFailed)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Tizimning ovoz sozlamalarini ochadi: dvigatel va ovoz o'rnatish shu yerda.
 * `false` — hech bir sozlama oynasi ochilmadi.
 */
private fun openVoiceSettings(context: Context): Boolean {
    val actions = listOf("com.android.settings.TTS_SETTINGS", Settings.ACTION_SETTINGS)
    for (action in actions) {
        try {
            val intent = Intent(action)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        } catch (error: Exception) {
            // Bu qurilmada bunday oyna yo'q — keyingisini sinaymiz.
        }
    }
    return false
}

private fun ReaderError.messageRes(): Int = when (this) {
    ReaderError.UNSUPPORTED -> R.string.reader_error_unsupported
    ReaderError.BROKEN -> R.string.reader_error_broken
    ReaderError.TOO_LARGE -> R.string.reader_error_too_large
    ReaderError.NO_TEXT -> R.string.reader_error_no_text
    ReaderError.BROKEN_TEXT -> R.string.reader_error_broken_text
    ReaderError.PASSWORD -> R.string.reader_error_password
    ReaderError.VOICE_MISSING -> R.string.reader_error_voice_missing
    ReaderError.SPEAK_FAILED -> R.string.reader_error_speak_failed
}
