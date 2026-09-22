package uz.ovozstudio.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uz.ovozstudio.app.R

/**
 * Ekran ostidagi xabar: xato yoki «bajarildi». Xabar matn bilan birga
 * **ovozda ham e'lon qilinadi**.
 *
 * Bu — ilova bo'ylab qoida. Ko'rish bilan ishlaydigan foydalanuvchi xatoni
 * ekranda ko'radi; ekran o'quvchi foydalanuvchisi esa tugmani bosadi va
 * javob eshitmasa, tugma ishlamadi deb o'ylaydi. Matn ekranning pastida
 * paydo bo'lgani uchun uni o'zi topib ham bo'lmaydi. E'lon shu bo'shliqni
 * yopadi.
 */
@Composable
fun StatusMessage(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val announce = rememberAnnouncer()
    LaunchedEffect(message) { announce(message) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = message,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
        )
        A11yOutlinedButton(
            label = stringResource(R.string.common_ok),
            onClick = onDismiss,
        )
    }
}
