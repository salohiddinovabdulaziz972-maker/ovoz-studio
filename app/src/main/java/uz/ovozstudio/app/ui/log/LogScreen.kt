package uz.ovozstudio.app.ui.log

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.ovozstudio.app.R
import uz.ovozstudio.app.ui.common.A11yButton
import uz.ovozstudio.app.ui.common.A11yOutlinedButton
import uz.ovozstudio.app.ui.common.StatusMessage
import uz.ovozstudio.app.ui.common.a11yHeading
import uz.ovozstudio.app.util.Sharing

/**
 * Xatolar jurnali: ilovada yuz bergan xatolar ro'yxati.
 *
 * «Ishlamayapti» deb yozgan foydalanuvchining xabari sababni aytmaydi, jurnal
 * esa aytadi: qaysi amal, qaysi xato. Shuning uchun uni ulashish tugmasi
 * ekranning eng tepasida.
 */
@Composable
fun LogScreen(
    onBack: () -> Unit,
    viewModel: LogViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) viewModel.saveTo(uri)
    }

    val shareTitle = stringResource(R.string.log_share_title)

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
            text = stringResource(R.string.log_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.a11yHeading(),
        )

        Text(
            text = stringResource(R.string.log_intro),
            style = MaterialTheme.typography.bodyLarge,
        )

        if (state.loaded) {
            Text(
                text = if (state.entryCount == 0) {
                    stringResource(R.string.log_empty)
                } else {
                    stringResource(
                        R.string.log_count,
                        state.entryCount,
                        Formatter.formatShortFileSize(context, state.sizeBytes),
                    )
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        A11yButton(
            label = stringResource(R.string.log_share),
            onClick = {
                val file = viewModel.exportForShare()
                val started = file != null && Sharing.share(context, file, "text/plain", shareTitle)
                if (!started) viewModel.shareFailed()
            },
            modifier = Modifier.fillMaxWidth(),
        )

        A11yOutlinedButton(
            label = stringResource(R.string.log_save),
            onClick = { saver.launch(LogViewModel.SAVE_NAME) },
            modifier = Modifier.fillMaxWidth(),
        )

        A11yOutlinedButton(
            label = stringResource(R.string.log_refresh),
            onClick = { viewModel.refresh() },
            modifier = Modifier.fillMaxWidth(),
        )

        A11yOutlinedButton(
            label = stringResource(R.string.log_clear),
            onClick = { viewModel.clear() },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.entryCount > 0,
        )

        state.notice?.let { notice ->
            StatusMessage(
                message = stringResource(
                    when (notice) {
                        LogNotice.CLEARED -> R.string.log_cleared
                        LogNotice.SAVED -> R.string.log_saved
                        LogNotice.SAVE_FAILED -> R.string.log_error_save
                        LogNotice.SHARE_FAILED -> R.string.log_error_share
                    },
                ),
                onDismiss = { viewModel.clearNotice() },
                isError = notice == LogNotice.SAVE_FAILED || notice == LogNotice.SHARE_FAILED,
            )
        }

        if (state.tail.isNotEmpty()) {
            Text(
                text = stringResource(R.string.log_preview_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.a11yHeading(),
            )
            Text(
                text = state.tail,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
