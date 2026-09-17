package uz.ovozstudio.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.ovozstudio.app.R
import uz.ovozstudio.app.settings.About
import uz.ovozstudio.app.settings.AppLanguage
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.ChoiceRow
import uz.ovozstudio.app.ui.common.SwitchRow
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.findActivity

/**
 * Sozlamalar ekrani: til, soddalashtirilgan rejim va ilova haqida.
 *
 * Uchta bo'lim — egasining tavsifidagi uchta band. Boshqa sozlamalar ataylab
 * qo'shilmadi: har bir qator ekran o'quvchi uchun alohida to'xtash nuqtasi,
 * ya'ni foydasi bo'lmagan sozlama vaqtni oladi.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Yorliqlar oldindan yasaladi: `ChoiceRow` ning `optionLabel` i oddiy
    // lambda (composable emas), ya'ni `stringResource` ni ichida chaqirib
    // bo'lmaydi.
    val languageLabels = AppLanguage.entries.associateWith { languageName(it) }

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
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        Text(text = stringResource(R.string.settings_language_note), style = MaterialTheme.typography.bodyMedium)

        ChoiceRow(
            label = stringResource(R.string.settings_language),
            options = AppLanguage.entries,
            selected = state.language,
            onSelect = { language ->
                // Ekran faqat til haqiqatan saqlangandan keyin qayta ochiladi:
                // aks holda qayta ochilgan Activity eski tilni o'qib qo'yardi.
                val applied = viewModel.setLanguage(language)
                if (applied) context.findActivity()?.recreate()
            },
            optionLabel = { language -> languageLabels.getValue(language) },
        )

        Text(
            text = stringResource(
                R.string.settings_language_active,
                languageName(state.activeLanguage),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider()

        SwitchRow(
            label = stringResource(R.string.settings_simplified_mode),
            checked = state.simplified,
            onCheckedChange = { viewModel.setSimplified(it) },
            description = stringResource(R.string.settings_simplified_mode_desc),
        )

        HorizontalDivider()

        Text(
            text = stringResource(R.string.settings_about),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )

        if (state.version.isNotEmpty()) {
            Text(
                text = stringResource(R.string.settings_version, state.version),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Text(
            text = stringResource(R.string.settings_license_value, About.LICENSE_NAME),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(text = stringResource(R.string.settings_license_note), style = MaterialTheme.typography.bodyMedium)

        A11yButton(
            label = stringResource(R.string.settings_open_source),
            onClick = {
                if (!openUrl(context, About.SOURCE_URL)) viewModel.linkFailed()
            },
            modifier = Modifier.fillMaxWidth(),
            description = About.SOURCE_URL,
        )

        A11yOutlinedButton(
            label = stringResource(R.string.settings_open_license),
            onClick = {
                if (!openUrl(context, About.LICENSE_URL)) viewModel.linkFailed()
            },
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let { error ->
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
    }
}

/**
 * Til yorlig'i.
 *
 * «Tizim tili bilan bir xil» qatori qaysi tilga olib kelishini aytmaydi —
 * u pastdagi «Hozir ishlatilayotgan til» satrida ko'rinadi: qurilma tili
 * ro'yxatdagi tillardan biri bo'lmasa, ilova o'zbekcha ochiladi va
 * foydalanuvchi buni oldindan biladi.
 */
@Composable
private fun languageName(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.UZ_LATN -> R.string.settings_language_uz_latn
        AppLanguage.UZ_CYRL -> R.string.settings_language_uz_cyrl
        AppLanguage.RU -> R.string.settings_language_ru
        AppLanguage.EN -> R.string.settings_language_en
    },
)

/** Xatoni joriy tilga o'giradi. */
@Composable
private fun SettingsError.message(): String = stringResource(
    when (this) {
        SettingsError.SAVE_FAILED -> R.string.settings_error_save
        SettingsError.LINK_FAILED -> R.string.settings_error_link
    },
)

/** Havolani tizim brauzerida ochadi. Brauzer topilmasa — `false`. */
private fun openUrl(context: Context, url: String): Boolean = runCatching {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}.isSuccess
