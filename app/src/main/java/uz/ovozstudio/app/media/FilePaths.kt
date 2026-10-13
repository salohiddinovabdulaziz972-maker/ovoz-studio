package uz.ovozstudio.app.media

import java.io.File
import java.io.IOException

/**
 * Fayl yo'llari bilan ishlash uchun Android'ga bog'liq bo'lmagan yordamchi
 * (shuning uchun oddiy JVM sinovida tekshiriladi).
 */
object FilePaths {

    /**
     * [file] haqiqatan [directory] ichida turadimi.
     *
     * Yo'llar `canonicalPath` bilan solishtiriladi: `..` va simvolik havolalar
     * hisobga olinadi, shuningdek `ish` papkasi bilan bir xil boshlanadigan
     * qo'shni papka (`ish-boshqa`) ichkarida hisoblanmaydi. Tekshirib bo'lmasa —
     * `false` (shubhali holatda fayl «o'zimizniki emas» deb olinadi va tegilmaydi).
     */
    fun isInside(file: File, directory: File): Boolean =
        try {
            file.canonicalPath.startsWith(directory.canonicalPath + File.separator)
        } catch (error: IOException) {
            false
        }
}
