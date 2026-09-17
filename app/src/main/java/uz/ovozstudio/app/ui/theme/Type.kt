package uz.ovozstudio.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Ilova shrift o'lchamlari ataylab Tizim sozlamalariga tayanadi
 * (`fontScale` Compose tomonidan avtomatik qo'llaniladi), shuning uchun
 * bu yerda faqat asosiy nisbatlar belgilanadi.
 *
 * Taymer va vaqt ko'rsatkichlari uchun monofazali shrift ishlatiladi —
 * raqamlar sakrab ketmaydi, ekran o'quvchi ham barqaror o'qiydi.
 */
val OvozTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
)
