package uz.ovozstudio.app.media.dsp

import uz.ovozstudio.app.media.WavInfo
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.pow

/**
 * Tezlik va ohang — ikkita mustaqil boshqaruv.
 *
 * Bular ko'pchilik uchun bitta narsa bo'lib tuyuladi, lekin aslida ikki
 * xil amal:
 *
 *   - **Tezlik** — fayl qancha vaqt ichida o'qiladi. Tovush balandligi
 *     o'zgarmaydi ([Wsola]).
 *   - **Ohang** — tovush balandligi. Fayl uzunligi o'zgarmaydi
 *     ([Resampler] ustiga [Wsola]).
 *
 * Ikkalasi birga ishlashi ham mumkin: masalan, «2 marta tez va yarim oktava
 * past». Buning uchun avval qayta namunalash, keyin cho'zish bajariladi.
 * Qayta namunalash ohangni `p` marta ko'tarib uzunlikni `p` marta
 * qisqartiradi; qolgan uzunlikni tiklash uchun cho'zish `tezlik / p`
 * koeffitsienti bilan chaqiriladi. Natijada uzunlik `kirish / tezlik`,
 * ohang esa `p` marta yuqori bo'ladi.
 *
 * Ohang yarim tonlarda beriladi: musiqachi uchun «+7» «+41.4%» dan ancha
 * tushunarli, ekran o'quvchi ham «plyus yetti yarim ton» deb o'qiydi.
 */
object SpeedPitch {

    const val MIN_SPEED = 0.5
    const val MAX_SPEED = 2.0

    const val MIN_SEMITONES = -12.0
    const val MAX_SEMITONES = 12.0

    /** Bir yarim ton — `2^(1/12)`. */
    private const val SEMITONE = 1.0 / 12.0

    private const val EPSILON = 1e-6

    /**
     * Sozlama.
     *
     * [speed] — tezlik (1.0 — o'zgarmagan), [semitones] — ohang siljishi
     * (0 — o'zgarmagan, ±12 — bir oktava).
     */
    data class Settings(val speed: Double = 1.0, val semitones: Double = 0.0) {

        /** Ohang koeffitsienti: yarim tonlardan nisbatga. */
        val pitchRatio: Double get() = 2.0.pow(semitones * SEMITONE)

        /** Uzunlik qanday o'zgaradi: `chiqish = kirish / speed`. */
        val lengthRatio: Double get() = 1.0 / speed

        /** Sozlama hech narsani o'zgartirmaydimi. */
        val isIdentity: Boolean
            get() = abs(speed - 1.0) < EPSILON && abs(semitones) < EPSILON

        /**
         * Qo'llash mumkinmi.
         *
         * Tekshiruv shu yerda, chunki chegaradan chiqqan son jimgina
         * «deyarli shunday» natija bermaydi — u ishni butunlay buzadi
         * (masalan, tezlik 0 ga yaqinlashsa chiqish fayli cheksiz uzun
         * bo'lardi).
         */
        fun isValid(): Boolean =
            speed in MIN_SPEED..MAX_SPEED && semitones in MIN_SEMITONES..MAX_SEMITONES
    }

    /** Natija: chiqish faylining sarlavhasi. */
    data class Result(val info: WavInfo)

    /**
     * [source] ga tezlik va ohang amalini qo'llab, [dest] ga yozadi.
     *
     * Har bir amal YANGI faylga yozadi — manba fayl hech qachon
     * o'zgartirilmaydi.
     */
    @Throws(IOException::class)
    fun apply(
        source: File,
        dest: File,
        settings: Settings,
        onProgress: (Float) -> Unit = {},
    ): Result {
        if (!settings.isValid()) {
            throw IOException("Tezlik yoki ohang chegaradan chiqqan")
        }
        if (settings.isIdentity) {
            throw IOException("Tezlik ham, ohang ham o'zgarmagan")
        }

        val pitch = settings.pitchRatio
        if (abs(pitch - 1.0) < EPSILON) {
            return Result(Wsola.stretch(source, dest, settings.speed, onProgress))
        }

        // Oraliq fayl: qayta namunalash natijasi. U manba yonida emas,
        // vaqtinchalik katalogda turadi — foydalanuvchi kutubxonasida
        // ko'rinmasligi kerak.
        val scratch = File.createTempFile("ovoz-tezlik-", ".wav")
        try {
            Resampler.resample(source, scratch, pitch) { onProgress(it * 0.5f) }
            val info = Wsola.stretch(scratch, dest, settings.speed / pitch) {
                onProgress(0.5f + it * 0.5f)
            }
            return Result(info)
        } finally {
            scratch.delete()
        }
    }
}
