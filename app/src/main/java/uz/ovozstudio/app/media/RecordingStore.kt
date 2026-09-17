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

    /**
     * Import qilingan manba fayllar.
     *
     * Alohida papka, chunki [clearEdits] tahrir papkasini butunlay tozalaydi:
     * manba o'sha yerda yotsa, saqlashdan keyin foydalanuvchining yuklagan
     * fayli yo'q bo'lardi — asl formatni tiklash uchun esa u kerak.
     */
    val sourcesDirectory: File = File(directory, "manba").apply { mkdirs() }

    fun newRecordingFile(prefix: String = "yozuv"): File = uniqueFile(directory, prefix)

    fun newEditFile(tag: String): File = uniqueFile(editsDirectory, tag)

    /**
     * Yuklangan fayl nusxasi uchun joy. [extension] — manbaning kengaytmasi
     * (`mp3`, `m4a`…): nusxa asl nomga yaqin bo'lib qolsa, fayl keyin
     * fayl menejerida ham tushunarli ko'rinadi.
     */
    fun newSourceFile(extension: String): File = uniqueFile(sourcesDirectory, "manba", extension)

    /** Konvertatsiya natijasi uchun joy — kengaytma maqsad formatdan olinadi. */
    fun newOutputFile(prefix: String, extension: String): File =
        uniqueFile(directory, prefix, extension)

    /**
     * Bo'sh nom qaytaradi.
     *
     * Vaqt tamg'asi sekundgacha aniq, shu sababli bir sekund ichida ikki marta
     * saqlash bir xil nom berardi — va saqlash mavjud faylning ustiga yozardi.
     * Ya'ni yangi yozuv eski yozuvni yo'q qilishi mumkin edi. Endi nom band
     * bo'lsa, oxiriga raqam qo'shiladi.
     */
    private fun uniqueFile(directory: File, rawPrefix: String, extension: String = "wav"): File {
        // Prefiks endi tashqaridan (masalan, bo'lingan faylning nomidan yoki
        // import qilingan fayldan) kelishi mumkin, shuning uchun faqat xavfsiz
        // belgilar qoldiriladi: `/` yoki `..` fayl nomida bo'lsa, yozuv
        // butunlay boshqa papkaga tushib qolardi.
        val prefix = rawPrefix
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(40)
            .ifEmpty { "yozuv" }
        // Kengaytma ham tashqaridan keladi — uni ham tozalash shart, aks
        // holda `../` kabi qiymat papkadan chiqarib yuborardi.
        val suffix = extension
            .replace(Regex("[^A-Za-z0-9]"), "")
            .lowercase()
            .take(5)
            .ifEmpty { "wav" }
        val stamp = timestamp()
        var candidate = File(directory, "$prefix-$stamp.$suffix")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(directory, "$prefix-$stamp-$counter.$suffix")
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

    /**
     * Ilova papkasidagi MP3 fayllar — eng yangisi birinchi.
     *
     * Nega alohida ro'yxat: [list] faqat WAV qaytaradi (davomiylik WAV
     * sarlavhasidan o'qiladi, MP3 da u boshqa tuzilma). Konvertor va
     * audio-kitob esa MP3 yozadi — teg ekrani ularni ko'rsatmasa,
     * foydalanuvchi o'zi yasagan faylni topa olmasdi: tizim tanlagichi
     * ilovaning ichki papkasini ko'rmaydi.
     */
    fun listMp3(): List<File> =
        directory.listFiles { file -> file.isFile && file.extension.equals("mp3", true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

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
