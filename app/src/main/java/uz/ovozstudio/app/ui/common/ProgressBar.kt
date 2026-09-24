package uz.ovozstudio.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uz.ovozstudio.app.R

/**
 * Uzoq ishning holati: matn tepada, chizig'i ostida, foiz esa TalkBack'ga
 * **cheklab** e'lon qilinadi.
 *
 * [progress] `null` bo'lsa — uzunlik noma'lum (masalan konteyner buni
 * aytmaydi): aniq bo'lmagan chiziq va faqat [label] ko'rsatiladi, foiz yo'q.
 *
 * Foiz nega cheklanadi. Har bo'lakda (soniyasiga o'nlab marta) e'lon qilinsa,
 * TalkBack «1%... 2%... 3%...» deb tinmay gapirib, boshqa hech narsa
 * eshitilmay qolardi. Shu sababli e'lon faqat har 10 foizda bir marta
 * chiqadi (10, 20, …, 100); ekrandagi RAQAM esa istalgan tez-tezlikda
 * yangilanaveradi — buni ko'rish bilan ishlaydigan foydalanuvchi baribir
 * ko'radi, TalkBack esa faqat cheklangan nuqtalarda gapiradi.
 */
@Composable
fun WorkProgress(label: String, progress: Float?, modifier: Modifier = Modifier) {
    val announce = rememberAnnouncer()
    val percent = progress?.let { (it.coerceIn(0f, 1f) * 100).toInt() }
    // 0..10: qaysi 10 foizlik bosqichga yetgan. `null` — hali boshlanmagan
    // yoki uzunlik noma'lum.
    val checkpoint = percent?.let { it / 10 }
    val announcement = if (percent != null) {
        stringResource(R.string.progress_percent, label, percent)
    } else {
        label
    }
    LaunchedEffect(checkpoint) {
        if (checkpoint != null && checkpoint > 0) announce(announcement)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (percent != null) stringResource(R.string.progress_percent, label, percent) else label,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
