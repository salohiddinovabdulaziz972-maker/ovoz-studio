package uz.ovozstudio.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uz.ovozstudio.app.R
import uz.ovozstudio.app.util.FolderOpener

/**
 * Saqlangan fayl haqidagi blok: qayerga saqlangani va papkani ochish tugmasi.
 *
 * Nima uchun bitta joyda. Ilgari har bir ekran (kesish, o'chirish,
 * birlashtirish, PDF) saqlash natijasini o'zi yozardi va faqat fayl nomini
 * aytardi. Nom eshitilsa ham faylni topish oson emas: ko'ruvchi foydalanuvchi
 * bildirishnomadagi yozuvni bosib bir zumda papkaga tushadi, eshitish bilan
 * esa qo'lda papkalar ichida yurishga to'g'ri kelardi. Shu sababli chiqish shu
 * yerda umumiy yozilgan — ikkala foydalanuvchi turi bir xil imkoniyat oladi.
 *
 * `isAudio` ekrandan keladi, [savedPath] dan o'qilmaydi: papka nomidan tur
 * taxmin qilish «Documents» so'zi fayl nomida uchrasa xato berardi.
 */
@Composable
fun SavedFileBlock(
    name: String,
    savedPath: String?,
    isAudio: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val openLabel = stringResource(R.string.result_open_folder)
    val openFailed = stringResource(R.string.result_folder_failed)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (savedPath.isNullOrBlank()) {
                stringResource(R.string.result_saved_plain, name)
            } else {
                stringResource(R.string.result_saved_in, folderOf(savedPath))
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        A11yButton(
            label = openLabel,
            onClick = { FolderOpener.open(context, isAudio, openFailed) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Yo'ldan papkani odam o'qiy oladigan ko'rinishga keltiradi.
 *
 * Eshitish uchun `/storage/emulated/0/Music/Ovoz Studio` juda uzun va
 * tushunarsiz — faqat «Music/Ovoz Studio» qismi muhim. Ildiz qismi
 * olib tashlanadi, ilova papkasi oldidagi hamma narsa qisqartiriladi.
 */
private fun folderOf(path: String): String {
    val folder = path.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = path)
    for (marker in APP_FOLDERS) {
        val cut = folder.indexOf(marker)
        if (cut >= 0) return folder.substring(cut + 1)
    }
    return folder
}

/** Ilova yozadigan joylar — [uz.ovozstudio.app.util.MediaSaver] bilan bir xil. */
private val APP_FOLDERS = arrayOf("Music/Ovoz Studio", "Documents/Ovoz Studio")
