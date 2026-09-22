package uz.ovozstudio.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import uz.ovozstudio.app.R
import uz.ovozstudio.app.media.WavInfo
import uz.ovozstudio.app.util.LocalizedNumber
import uz.ovozstudio.app.util.TimeFormat

/**
 * Fayl haqidagi bir qatorlik ma'lumot: kanal soni, chastota va uzunlik.
 *
 * Tahrirlash ekranlarining hammasi shu qatorni ko'rsatadi, shuning uchun u
 * bir joyda turadi: bir ekranda «1 kanal», boshqasida «mono» deb yozilishi
 * foydalanuvchi uchun bir xil narsaning ikki xil nomi bo'lardi.
 *
 * Uzunlik alohida olinadi: ba'zi ekranlarda u manba fayldan emas, kiritilgan
 * sozlamadan kelib chiqadi.
 */
@Composable
fun fileSummary(info: WavInfo, durationMs: Long): String =
    fileSummary(info.channels, info.sampleRate, durationMs)

/**
 * O'sha qator, lekin [WavInfo] bo'lmasa — xom qiymatlar bo'yicha.
 *
 * Kerak bo'ladi, chunki ba'zi ekranlarda (masalan, aralashtirish) yo'lning
 * ma'lumoti fayldan emas, loyihadan keladi: manba fayl o'chirilgan bo'lsa
 * ham yo'l sozlamalari saqlanadi.
 */
@Composable
fun fileSummary(channels: Int, sampleRate: Int, durationMs: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val channelsText = stringResource(
        if (channels == 1) R.string.audio_channels_mono else R.string.audio_channels_stereo
    )
    val rate = LocalizedNumber.format(sampleRate / 1000.0, locale, fractionDigits = 1)
    return "$channelsText, $rate kHz, ${TimeFormat.format(durationMs)}"
}
