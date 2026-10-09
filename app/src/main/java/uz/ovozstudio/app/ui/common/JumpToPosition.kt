package uz.ovozstudio.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uz.ovozstudio.app.util.TimeParts

/**
 * Ijro boshi joyini ko'rsatadi va uni aniq vaqtga o'tkazishga beradi.
 *
 * Nega slayder emas. Slayder ko'ruvchi foydalanuvchiga qulay, ekran o'quvchi
 * foydalanuvchisiga esa deyarli yaroqsiz: barmoqni bir necha pikselga surish
 * kerak, eshitish bilan esa «hozir qayerdaman»ni bilib bo'lmaydi. Natijada
 * slayder qo'yilsa imkoniyat teng bo'lmaydi — ko'ruvchi uni ishlatadi,
 * ekran o'quvchi foydalanuvchisi esa yo'q. Shuning uchun bu yerda ikkalasi
 * ham baribir qila oladigan ish qoldirilgan: vaqtni kiritib, o'sha joyga
 * sakrash.
 *
 * Ikkala foydalanuvchi ham bir xil narsani ko'radi va bir xil ishlatadi —
 * «ekran o'quvchi rejimi» degan alohida narsa yo'q.
 *
 * [positionText] — joriy joy, allaqachon o'qishga tayyor matn (masalan
 * «1 daqiqa 20 soniya»). Matn tayyor holda beriladi, chunki uni yasash
 * ko'p hollarda `@Composable` funksiyani talab qiladi.
 */
@Composable
fun JumpToPosition(
    label: String,
    positionText: String,
    parts: TimeParts,
    onPartsChange: (TimeParts) -> Unit,
    onJump: () -> Unit,
    jumpLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Joriy joy — nom va qiymat bitta to'xtash nuqtasida. Ko'ruvchi uni
        // ko'rsatkichdan bir qarashda biladi; ekran o'quvchi foydalanuvchisi
        // uchun bu yagona manba, shuning uchun nom majburiy.
        Text(
            text = positionText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.a11yState(label = label, value = positionText),
        )

        TimeInput(
            label = label,
            parts = parts,
            onPartsChange = onPartsChange,
            isError = isError,
            enabled = enabled,
        )

        A11yOutlinedButton(
            label = jumpLabel,
            onClick = onJump,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
    }
}
