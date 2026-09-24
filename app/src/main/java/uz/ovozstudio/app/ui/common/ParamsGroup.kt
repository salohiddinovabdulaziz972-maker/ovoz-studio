package uz.ovozstudio.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uz.ovozstudio.app.R

/**
 * Bir nechta parametrni (masalan boshlanish/tugash vaqti) **bitta yopiq
 * qatorga** yig'adi: ochilmagan holatda faqat qisqa xulosa va bitta tugma
 * ko'rinadi, kerak bo'lsagina ichidagi hamma maydon ochiladi.
 *
 * Nega kerak. Har bir vaqt maydoni (soat, daqiqa, soniya, millisoniya) TalkBack
 * uchun alohida to'xtash nuqtasi: boshlanish va tugash uchun sakkizta maydon,
 * ustiga hali "Eshitish" tugmasi — natijada boshdan "Kesib olish" tugmasigacha
 * o'nlab marta bir tomonga suradi. Aksariyat holatda foydalanuvchi standart
 * qiymatlar bilan roziy (butun faylni kesish/o'chirish), shuning uchun
 * standart holat — YOPIQ: xulosa + bitta tugma, ya'ni ikkita to'xtash nuqtasi.
 * Parametrni o'zgartirish kerak bo'lsagina foydalanuvchi tugmani bosadi.
 *
 * [summary] — yopiq holatdagi qisqa matn (masalan "0:00 dan 1:30 gacha").
 * [content] — faqat ochiq holatda chiziladi.
 */
@Composable
fun ParamsGroup(
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!expanded) {
            Text(text = summary, style = MaterialTheme.typography.bodyLarge)
            A11yOutlinedButton(
                label = stringResource(R.string.params_change),
                onClick = { onExpandedChange(true) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            content()
            A11yOutlinedButton(
                label = stringResource(R.string.params_collapse),
                onClick = { onExpandedChange(false) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
