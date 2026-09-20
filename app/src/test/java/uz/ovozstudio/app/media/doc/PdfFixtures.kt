package uz.ovozstudio.app.media.doc

import java.io.File

/**
 * PDF sinov namunalarini topadi (`app/src/test/fixtures/`).
 *
 * Yo'l ataylab **ishchi katalogdan yuqoriga qarab** izlanadi. Namunalar
 * `app/src/test/fixtures` da yotadi, ishchi katalog esa uni ishga tushirgan
 * muhitga qarab har xil bo'ladi: `bin/run-tests.sh` ildizdan ishga tushiradi,
 * Gradle esa test JVM'ini `app/` da ochadi. Bitta qat'iy nisbiy yo'l
 * ikkalasida ishlamaydi — Gradle'da u `app/app/src/...` bo'lib qoladi.
 *
 * Aynan shu sababdan CI'da oltita sinov yiqilgan edi (beshta PDF sinovi va
 * `DocumentLoaderTest` ning PDF ishi), holda ular JVM to'plamida yashil
 * edi. Ildizni qidirish ikkala muhitni ham to'g'ri qamraydi.
 *
 * Topilmasa jim qolmaymiz: xato matni izlangan yo'lni va ishchi katalogni
 * ko'rsatadi — keyingi safar sabab darhol ko'rinadi.
 */
internal fun pdfFixture(name: String): File {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
        val candidate = File(dir, "app/src/test/fixtures/$name")
        if (candidate.isFile) return candidate
        dir = dir.parentFile
    }
    throw AssertionError(
        "namuna topilmadi: $name (ishchi katalog: ${File("").absolutePath})",
    )
}
