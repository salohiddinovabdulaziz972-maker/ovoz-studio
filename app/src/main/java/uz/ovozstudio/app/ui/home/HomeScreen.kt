package uz.ovozstudio.app.ui.home

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uz.ovozstudio.app.R
import uz.ovozstudio.app.settings.About
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.ui.common.rememberAnnouncer

/**
 * Bosh ekran: ilovaning barcha imkoniyatlari bitta ro'yxatda.
 *
 * Ro'yxat qisqa (yetti tugma), shuning uchun ekran o'quvchi foydalanuvchisi
 * hammasini bir yo'la ko'radi. Bo'limlar sarlavhali: TalkBack sarlavhadan
 * sarlavhaga sakray oladi.
 */
@Composable
fun HomeScreen(
    onAudioCut: () -> Unit,
    onAudioDelete: () -> Unit,
    onAudioMerge: () -> Unit,
    onPdfCut: () -> Unit,
    onPdfDelete: () -> Unit,
    onReader: () -> Unit,
    onLog: () -> Unit,
) {
    val context = LocalContext.current
    val announce = rememberAnnouncer()
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val linkFailed = stringResource(R.string.about_link_failed)

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Edge-to-edge rejimida tizim panellari ostiga tushmaslik uchun.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
        )

        Text(
            text = stringResource(R.string.home_section_audio),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )
        A11yButton(
            label = stringResource(R.string.home_audio_cut),
            onClick = onAudioCut,
            modifier = Modifier.fillMaxWidth(),
        )
        A11yButton(
            label = stringResource(R.string.home_audio_delete),
            onClick = onAudioDelete,
            modifier = Modifier.fillMaxWidth(),
        )
        A11yButton(
            label = stringResource(R.string.home_audio_merge),
            onClick = onAudioMerge,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.home_section_pdf),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )
        A11yButton(
            label = stringResource(R.string.home_pdf_cut),
            onClick = onPdfCut,
            modifier = Modifier.fillMaxWidth(),
        )
        A11yButton(
            label = stringResource(R.string.home_pdf_delete),
            onClick = onPdfDelete,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.home_section_documents),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )
        A11yButton(
            label = stringResource(R.string.home_reader),
            onClick = onReader,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.home_section_other),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading(),
        )
        A11yOutlinedButton(
            label = stringResource(R.string.home_log),
            onClick = onLog,
            modifier = Modifier.fillMaxWidth(),
        )
        A11yOutlinedButton(
            label = stringResource(R.string.home_about),
            onClick = { showAbout = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(text = stringResource(R.string.about_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.about_text,
                        versionName(context),
                        About.LICENSE_NAME,
                    ),
                )
            },
            confirmButton = {
                A11yButton(
                    label = stringResource(R.string.about_open_source),
                    onClick = {
                        if (!openUrl(context, About.SOURCE_URL)) announce(linkFailed)
                    },
                )
            },
            dismissButton = {
                A11yOutlinedButton(
                    label = stringResource(R.string.common_ok),
                    onClick = { showAbout = false },
                )
            },
        )
    }
}

/** Ilova versiyasi; aniqlanmasa — «?». */
private fun versionName(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
} catch (error: Exception) {
    "?"
}

/** Havolani brauzerda ochadi. `false` — ochib bo'lmadi (brauzer yo'q). */
private fun openUrl(context: Context, url: String): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (error: Exception) {
    false
}
