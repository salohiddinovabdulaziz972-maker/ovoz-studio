package uz.ovozstudio.app.ui.common

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Ekran o'quvchi uchun yordamchi qatlam.
 *
 * Qoida: bu ilovada MATNLI yorlig'i bo'lmagan tugma bo'lmaydi. Shuning uchun
 * barcha tugmalar shu fayldagi komponentlar orqali yasaladi — yorliq
 * majburiy parametr, uni tushirib qoldirib bo'lmaydi.
 */

/** Har bir tugma kamida 48×48 dp bo'lishi kerak — barmoq bilan ham, TalkBack bilan ham. */
private val MinTouchTarget = 48.dp

@Composable
fun A11yButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = MinTouchTarget, minWidth = MinTouchTarget)
            .a11yDescription(label, description),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun A11yOutlinedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = MinTouchTarget, minWidth = MinTouchTarget)
            .a11yDescription(label, description),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Qo'shimcha izohni tugma yorlig'iga QO'SHADI, almashtirmaydi.
 *
 * Agar izoh yorliqni to'liq almashtirsa, foydalanuvchi tugma nima qilishini
 * eshitmay qoladi — masalan «Belgi qo'yish» o'rniga faqat «3 belgi» eshitiladi.
 */
private fun Modifier.a11yDescription(label: String, description: String?): Modifier =
    if (description == null) this
    else semantics { contentDescription = "$label. $description" }

/** Ekran sarlavhasi — TalkBack «sarlavha» sifatida e'lon qiladi. */
fun Modifier.a11yHeading(): Modifier = semantics { heading() }

/**
 * Ovozi bilan e'lon qilish.
 *
 * MUHIM: taymer har soniyada yangilanadi, lekin u AVTOMATIK e'lon qilinmaydi —
 * aks holda ekran o'quvchi foydalanuvchisi hech narsa eshitmay qoladi. Vaqtni
 * faqat foydalanuvchi so'raganda (`a11yButton` bosilganda) yoki holat
 * o'zgarganda (yozish boshlandi / to'xtadi) e'lon qilamiz.
 */
@Composable
fun rememberAnnouncer(): (String) -> Unit {
    val view = LocalView.current
    return remember(view) { { message: String -> view.announceForAccessibility(message) } }
}
