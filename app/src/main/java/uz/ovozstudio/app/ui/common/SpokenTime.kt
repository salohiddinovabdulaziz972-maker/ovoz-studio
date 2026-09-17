package uz.ovozstudio.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import uz.ovozstudio.app.R
import uz.ovozstudio.app.util.TimeFormat

/**
 * Joriy tilga mos ovozli vaqt shakli: "3 daqiqa 12 soniya".
 *
 * Birlik nomlari `strings.xml` dan olinadi, shuning uchun ekran o'quvchi
 * qaysi tilda bo'lsa, vaqt ham shu tilda o'qiladi.
 */
@Composable
fun rememberSpokenUnits(): TimeFormat.SpokenUnits {
    val hours = stringResource(R.string.time_unit_hours)
    val minutes = stringResource(R.string.time_unit_minutes)
    val seconds = stringResource(R.string.time_unit_seconds)
    return remember(hours, minutes, seconds) {
        TimeFormat.SpokenUnits(hours = hours, minutes = minutes, seconds = seconds)
    }
}

/** [TimeFormat.formatSpoken] ning joriy tilga moslangan ko'rinishi. */
@Composable
fun spokenTime(ms: Long): String {
    val units = rememberSpokenUnits()
    return remember(ms, units) { TimeFormat.formatSpoken(ms, units) }
}
