package uz.ovozstudio.app.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import uz.ovozstudio.app.R
import uz.ovozstudio.app.media.format.ImportFailure

/**
 * Audio fayl ochilmaganining sababi — foydalanuvchi tilida.
 *
 * Kesish, o'chirish va birlashtirish ekranlari bir xil fayl ochish yo'lidan
 * o'tadi, shuning uchun xabarlar ham bir joyda: bir xil sabab bir xil so'z
 * bilan aytiladi.
 *
 * Matn resursi har doim bitta argument (`formatName` — `MP3`, `OGG (Vorbis)`)
 * bilan olinadi. Ko'p sabab matnida joy belgisi yo'q va argument
 * e'tiborga olinmaydi; faqat format bilan bog'liq ikki sabab uni ishlatadi:
 * foydalanuvchi qaysi format qaytarilmasligini bilishi kerak.
 */
@StringRes
fun importFailureRes(failure: ImportFailure): Int = when (failure) {
    ImportFailure.UNKNOWN_FORMAT -> R.string.audio_error_unknown_format
    ImportFailure.NO_DECODER -> R.string.audio_error_no_decoder
    ImportFailure.READ_FAILED -> R.string.audio_error_read_failed
    ImportFailure.EMPTY -> R.string.audio_error_empty
    ImportFailure.UNSUPPORTED_DEPTH -> R.string.audio_error_bit_depth
    ImportFailure.CANNOT_WRITE_FORMAT -> R.string.audio_error_cannot_write
    ImportFailure.OLD_ANDROID -> R.string.audio_error_old_android
}

@Composable
fun importFailureMessage(failure: ImportFailure, formatName: String): String =
    stringResource(importFailureRes(failure), formatName)

/** Xuddi shu xabar, lekin Compose'dan tashqarida (ro'yxat yig'ishda) — [Context] orqali. */
fun importFailureText(context: Context, failure: ImportFailure, formatName: String): String =
    context.getString(importFailureRes(failure), formatName)
