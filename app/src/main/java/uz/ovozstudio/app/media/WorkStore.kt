package uz.ovozstudio.app.media

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ishchi fayllar uchun joy.
 *
 * Ilova endi fayllarni o'z papkasida **saqlamaydi**: foydalanuvchi faylni
 * tanlaydi, ilova uni vaqtincha ochib tahrirlaydi, natijani esa foydalanuvchi
 * o'zi tanlagan joyga saqlaydi yoki boshqa ilovaga ulashadi. Shu sababli
 * hamma narsa ilovaning **kesh** papkasida turadi va tizim uni istalgan payt
 * tozalashi mumkin — bu ataylab.
 *
 * Uch xil papka:
 *  - `manba/` — tanlangan faylning nusxasi (tizim havolasi doimiy emas);
 *  - `tahrir/` — oraliq WAV fayllar (tahrir zanjiri);
 *  - `natija/` — tayyor fayl. Bu papka **hamma ekran uchun umumiy** va
 *    `FileProvider` faqat shu papkani ulashadi (`res/xml/file_paths.xml`).
 *
 * [scope] ekranni ajratadi: bir ekranning tozalashi boshqasining ochiq
 * fayliga tegmasligi kerak.
 */
class WorkStore(context: Context, scope: String) {

    private val cache: File = context.applicationContext.cacheDir
    private val root: File = File(File(cache, ROOT_NAME), scope)

    /** Tanlangan fayllarning nusxalari. */
    val sourcesDirectory: File = File(root, "manba")

    /** Tahrirlash paytidagi oraliq fayllar — foydalanuvchiga ko'rsatilmaydi. */
    val editsDirectory: File = File(root, "tahrir")

    /** Tayyor natijalar. Barcha ekranlar uchun bitta papka. */
    val outputsDirectory: File = File(cache, OUTPUTS_NAME)

    init {
        sourcesDirectory.mkdirs()
        editsDirectory.mkdirs()
        outputsDirectory.mkdirs()
    }

    /**
     * Yuklangan fayl nusxasi uchun joy. [extension] — manbaning kengaytmasi
     * (`mp3`, `m4a`…): shunda nusxa asl nomga yaqin bo'ladi.
     */
    fun newSourceFile(extension: String): File = uniqueFile(sourcesDirectory, "manba", extension)

    /** Oraliq fayl uchun joy. Standart kengaytma — `wav`. */
    fun newEditFile(tag: String, extension: String = "wav"): File =
        uniqueFile(editsDirectory, tag, extension)

    /**
     * Tayyor natija uchun joy: `natija/<vaqt>/<nom><qo'shimcha>.<kengaytma>`.
     *
     * Har bir natija o'z papkasida turadi, shuning uchun fayl nomi toza
     * qoladi (`ovoz-kesilgan.mp3`) va ikkinchi natija birinchisining ustiga
     * yozilmaydi. Nom ulashilganda qabul qiluvchi ilovada ham shu ko'rinishda
     * chiqadi.
     */
    fun newOutputFile(baseName: String, suffix: String, extension: String): File {
        val folder = uniqueDirectory(outputsDirectory)
        val name = cleanName(baseName) + cleanName(suffix) + "." + cleanExtension(extension)
        return File(folder, name)
    }

    /**
     * Natija tayyor bo'lgach chaqiriladi — fayl yoniga "tayyor" belgisi qo'yiladi.
     *
     * Nega kerak. Uzoq ish jarayon o'lishi bilan uzilib qolishi mumkin (tizim
     * xotirani bo'shatdi, foydalanuvchi ilovani surib tashladi) — u holda na
     * tozalash, na xato xabari ishlamaydi, chala fayl esa joyida qoladi.
     * Faylning o'zi bu holatda aldamchi: o'lchandi — MP3 ning birinchi uchdan
     * bir qismi yozilgan bo'lsa ham u **yaroqli, o'ynaladigan** fayl bo'lib
     * chiqadi (yarim yozilgan MP3 ham to'g'ri sarlavha bilan o'ynaladi).
     * Ya'ni foydalanuvchi to'liq kitob
     * deb o'ylab, yarim kitobni ulashib yuborishi mumkin edi.
     *
     * Bundan keyin "tayyor" degan savolga javob faylning o'zi beradi: belgi
     * bor — tayyor. [sweep] belgisiz natijani darhol o'chiradi, ya'ni chala
     * fayl bir kun yashab qolmaydi.
     */
    fun markOutputReady(file: File) {
        runCatching { File(file.parentFile, file.name + READY_SUFFIX).createNewFile() }
    }

    /** Natija haqiqatan tugallanganmi — [markOutputReady] qo'ygan belgi bo'yicha. */
    fun isOutputReady(file: File): Boolean =
        File(file.parentFile, file.name + READY_SUFFIX).exists()

    /** Oraliq fayllarni tozalaydi. [keep] berilsa, o'sha fayl qoladi. */
    fun clearEdits(keep: File? = null) {
        editsDirectory.listFiles()?.forEach { file ->
            if (file.absolutePath != keep?.absolutePath) runCatching { file.delete() }
        }
    }

    /**
     * Ekran yopilganda: manba nusxalari va oraliq fayllar o'chadi.
     *
     * Natijalar **qoldiriladi**. Ulashilgan fayl boshqa ilovada keyinroq,
     * ekran yopilgandan keyin ham o'qilishi mumkin (masalan xabar yuboruvchi
     * ilova faylni orqa fonda yuklaydi); uni darhol o'chirish yuklashni
     * buzardi. Natijalarni [sweep] eskirgach tozalaydi.
     */
    fun clearWork() {
        deleteChildren(sourcesDirectory)
        deleteChildren(editsDirectory)
    }

    private fun uniqueFile(directory: File, rawPrefix: String, extension: String): File {
        // Prefiks tashqaridan (masalan import qilingan fayl nomidan) kelishi
        // mumkin, shuning uchun faqat xavfsiz belgilar qoldiriladi: `/` yoki
        // `..` bo'lsa, fayl butunlay boshqa papkaga tushib qolardi.
        val prefix = rawPrefix
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(40)
            .ifEmpty { "fayl" }
        val suffix = cleanExtension(extension)
        val stamp = timestamp()
        var candidate = File(directory, "$prefix-$stamp.$suffix")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(directory, "$prefix-$stamp-$counter.$suffix")
            counter++
        }
        return candidate
    }

    private fun uniqueDirectory(parent: File): File {
        val stamp = timestamp()
        var candidate = File(parent, stamp)
        var counter = 2
        while (candidate.exists()) {
            candidate = File(parent, "$stamp-$counter")
            counter++
        }
        candidate.mkdirs()
        return candidate
    }

    private fun deleteChildren(directory: File) {
        directory.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    /**
     * Fayl nomi: taqiqlangan belgilar almashtiriladi, o'zbek harflari
     * (`o'`, `g'`, kirill) esa saqlanadi — foydalanuvchi o'z nomini ko'radi.
     */
    private fun cleanName(text: String): String =
        text.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trim('.')
            .take(MAX_NAME_CHARS)

    /** Kengaytma ham tashqaridan keladi: `../` kabi qiymat papkadan chiqarib yuborardi. */
    private fun cleanExtension(text: String): String =
        text.replace(Regex("[^A-Za-z0-9]"), "").lowercase().take(5).ifEmpty { "bin" }

    companion object {
        private const val ROOT_NAME = "ish"
        private const val OUTPUTS_NAME = "natija"
        private const val MAX_NAME_CHARS = 60
        private const val DAY_MS = 24L * 60 * 60 * 1000

        /** "Tayyor" belgisining qo'shimchasi: `ovoz-kesilgan.mp3.tayyor`. */
        private const val READY_SUFFIX = ".tayyor"

        /**
         * Eski fayllarni tozalaydi.
         *
         * [workMaxAgeMs] — ish fayllari (manba nusxalari, oraliq WAV): shundan
         * eskisi o'chadi, `0` — hammasi. [outputMaxAgeMs] — tayyor natijalar.
         *
         * Ilova jarayoni yangi boshlanganda ish fayllari to'liq tozalanadi
         * (hech bir ekran hali ochiq emas), natijalar esa **bir kun** saqlanadi:
         * foydalanuvchi faylni Telegram yoki pochtaga ulashgan bo'lsa, qabul
         * qiluvchi ilova uni ilova qayta ishga tushganidan keyin ham yuklab
         * turgan bo'lishi mumkin.
         */
        fun sweep(context: Context, workMaxAgeMs: Long = 0L, outputMaxAgeMs: Long = DAY_MS) {
            val cache = context.applicationContext.cacheDir
            purge(File(cache, ROOT_NAME), workMaxAgeMs)
            val outputs = File(cache, OUTPUTS_NAME)
            purge(outputs, outputMaxAgeMs)
            discardUnfinished(outputs)
        }

        /**
         * Belgisiz natijalarni — ya'ni jarayon o'limi tufayli chala qolganlarini —
         * darhol o'chiradi.
         *
         * Belgi fayllari (`*.tayyor`) esa har doim o'chadi: ular faylning o'zi
         * bilan birga yashaydi, [purge] esa faqat eski fayllarni ko'radi, ya'ni
         * bugun yasalgan natijaning belgisi bir kundan keyin yolg'iz qolib
         * ketardi.
         */
        private fun discardUnfinished(outputs: File) {
            if (!outputs.isDirectory) return
            for (file in outputs.walkBottomUp()) {
                if (file.name.endsWith(READY_SUFFIX)) {
                    runCatching { file.delete() }
                    continue
                }
                if (!file.isFile) continue
                // Chala fayl: natija o'zi, lekin belgisi yo'q.
                if (hasOutputExtension(file) && !isReady(file)) runCatching { file.delete() }
            }
        }

        private fun isReady(file: File): Boolean = File(file.parentFile, file.name + READY_SUFFIX).exists()

        /**
         * Belgini faqat **natija fayllaridan** talab qilamiz. Papkada yotgan
         * boshqa hamma narsa (masalan boshqa ekranning vaqtinchalik fayli)
         * o'z tartibida yashaydi — bu tekshiruv unga aralashmaydi.
         */
        private fun hasOutputExtension(file: File): Boolean =
            OUTPUT_EXTENSIONS.any { file.name.endsWith(".$it", ignoreCase = true) }

        private val OUTPUT_EXTENSIONS = listOf("mp3", "wav", "flac", "m4a", "aac", "ogg", "opus", "pdf", "txt")

        private fun purge(base: File, maxAgeMs: Long) {
            if (!base.isDirectory) return
            val cutoff = if (maxAgeMs <= 0L) Long.MAX_VALUE else System.currentTimeMillis() - maxAgeMs
            // Avval bolalar, keyin ota: bo'sh bo'lmagan papka o'chmaydi.
            for (file in base.walkBottomUp()) {
                if (file == base) continue
                if (file.lastModified() < cutoff) runCatching { file.delete() }
            }
        }
    }
}
