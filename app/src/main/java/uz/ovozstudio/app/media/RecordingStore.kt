package uz.ovozstudio.app.media

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ekranda ko'rsatiladigan bitta yozuv. */
data class Recording(
    val file: File,
    val info: WavInfo,
) {
    val title: String get() = file.nameWithoutExtension
    val durationMs: Long get() = info.durationMs
}

/**
 * Fayllar bilan ishlash.
 *
 * Yozuvlar ilovaning tashqi papkasida saqlanadi (`Android/data/…/files/OvozStudio`).
 * Bu papka uchun hech qanday ruxsat so'ralmaydi, fayllar esa fayl menejeridan
 * ko'rinadi. Kelajakda «Musiqa» papkasiga chiqarish MediaStore orqali qo'shiladi.
 */
class RecordingStore(context: Context) {

    private val appContext = context.applicationContext

    /** Yakuniy yozuvlar. */
    val directory: File =
        File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "OvozStudio")
            .apply { mkdirs() }

    /** Tahrirlash paytidagi oraliq fayllar — foydalanuvchiga ko'rsatilmaydi. */
    val editsDirectory: File = File(directory, "tahrir").apply { mkdirs() }

    fun newRecordingFile(prefix: String = "yozuv"): File = uniqueFile(directory, prefix)

    fun newEditFile(tag: String): File = uniqueFile(editsDirectory, tag)

    /**
     * Bo'sh nom qaytaradi.
     *
     * Vaqt tamg'asi sekundgacha aniq, shu sababli bir sekund ichida ikki marta
     * saqlash bir xil nom berardi — va saqlash mavjud faylning ustiga yozardi.
     * Ya'ni yangi yozuv eski yozuvni yo'q qilishi mumkin edi. Endi nom band
     * bo'lsa, oxiriga raqam qo'shiladi.
     */
    private fun uniqueFile(directory: File, rawPrefix: String): File {
        // Prefiks endi tashqaridan (masalan, bo'lingan faylning nomidan) kelishi
        // mumkin, shuning uchun faqat xavfsiz belgilar qoldiriladi: `/` yoki
        // `..` fayl nomida bo'lsa, yozuv butunlay boshqa papkaga tushib qolardi.
        val prefix = rawPrefix
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(40)
            .ifEmpty { "yozuv" }
        val stamp = timestamp()
        var candidate = File(directory, "$prefix-$stamp.wav")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(directory, "$prefix-$stamp-$counter.wav")
            counter++
        }
        return candidate
    }

    /** Papkadagi barcha o'qiladigan WAV fayllar — eng yangisi birinchi. */
    fun list(): List<Recording> {
        val files = directory.listFiles { file -> file.isFile && file.extension.equals("wav", true) }
            ?: return emptyList()
        return files
            .sortedByDescending { it.lastModified() }
            .mapNotNull { file ->
                runCatching { Recording(file, WavFile.readInfo(file)) }
                    .onFailure { Log.w(TAG, "O'qilmadi: ${file.name}", it) }
                    .getOrNull()
            }
    }

    fun delete(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

    /** Tahrirlashdan keyin eski oraliq fayllarni tozalaydi (fayl saqlangandan keyin chaqiriladi). */
    fun clearEdits(keep: File? = null) {
        editsDirectory.listFiles()?.forEach { file ->
            if (file.absolutePath != keep?.absolutePath) runCatching { file.delete() }
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private companion object {
        const val TAG = "RecordingStore"
    }
}
