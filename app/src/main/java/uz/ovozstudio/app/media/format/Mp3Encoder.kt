package uz.ovozstudio.app.media.format

import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.LameGlobalFlags
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.VbrMode
import de.sciss.jump3r.mp3.Version
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * MP3 kodlovchisi — `jump3r` kutubxonasining past darajali `Lame` API'si
 * ustida.
 *
 * Nega aynan past darajali API: kutubxonaning qulay o'ramlari
 * (`de.sciss.jump3r.lowlevel.LameEncoder`) `javax.sound.sampled` ga tayanadi,
 * u esa Android'da umuman yo'q. `mp3` paketining o'zi Android'ga bog'liq
 * emas — sof arifmetika, shuning uchun u qurilmada ham ishlaydi.
 *
 * Bit tezligi [AudioFormat.bitrate] dan olinadi (bit/s), LAME esa kbit/s
 * kutadi — o'girish shu yerda.
 */
class Mp3Encoder(
    file: File,
    override val format: AudioFormat,
    /** LAME sifat darajasi: 0 — eng yaxshi va eng sekin, 9 — eng tez. */
    private val quality: Int = DEFAULT_QUALITY,
) : AudioEncoder {

    // Tekshiruv eng boshida turadi: LAME modullarini ulash ham, faylni
    // ochish ham resurs talab qiladi, noto'g'ri format uchun ularni
    // ishga tushirib, keyin tashlab yuborish keraksiz.
    init {
        require(format.channels in 1..2) {
            "MP3 faqat mono va stereoni qo'llab-quvvatlaydi: ${format.channels} kanal"
        }
        require(!format.codec.lossless) { "MP3 — yo'qotishli kodek" }
    }

    private val lame: Lame = Lame().also { wireModules(it) }
    private val flags: LameGlobalFlags = lame.lame_init()
    private val out = BufferedOutputStream(FileOutputStream(file))
    private val mp3Buffer = ByteArray(Lame.LAME_MAXMP3BUFFER)

    private var left = IntArray(0)
    private var right = IntArray(0)
    private var finished = false

    /**
     * LAME 32-bitli namuna kutadi: to'liq shkala — 2^31. Shuning uchun
     * faylning bit chuqurligiga qarab chapga suriladi.
     */
    private val shift = 32 - (format.bitDepth ?: DEFAULT_BIT_DEPTH)

    init {
        flags.num_channels = format.channels
        flags.in_samplerate = format.sampleRate
        flags.out_samplerate = format.sampleRate
        flags.mode = if (format.channels == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO
        flags.quality = quality.coerceIn(0, 9)
        flags.bWriteVbrTag = true
        // Avtomatik ID3 teg yozilmaydi: tegni biz o'zimiz boshqaramiz,
        // aks holda bo'sh teg fayl boshiga qo'shilib qolardi.
        flags.write_id3tag_automatic = false

        if (format.bitrate != null) {
            flags.VBR = VbrMode.vbr_off
            flags.brate = (format.bitrate / 1000).coerceIn(MIN_BITRATE_KBPS, MAX_BITRATE_KBPS)
        } else {
            flags.VBR = VbrMode.vbr_default
            flags.VBR_q = VBR_QUALITY
        }

        val status = lame.lame_init_params(flags)
        check(status >= 0) { "LAME sozlanmadi (kod $status)" }
    }

    override fun finish() {
        if (finished) return
        finished = true
        try {
            val count = lame.lame_encode_flush(flags, mp3Buffer, 0, mp3Buffer.size)
            if (count > 0) out.write(mp3Buffer, 0, count)
        } finally {
            lame.lame_close(flags)
            out.flush()
            out.close()
        }
    }

    override fun close() = finish()

    @Throws(IOException::class)
    override fun write(samples: IntArray, frames: Int) {
        check(!finished) { "Kodlovchi allaqachon yopilgan" }
        require(frames >= 0 && frames * format.channels <= samples.size) {
            "Massiv juda kichik: $frames kadr kerak, massivda ${samples.size / format.channels} bor"
        }
        if (frames == 0) return

        ensureCapacity(frames)
        if (format.channels == 1) {
            var i = 0
            while (i < frames) {
                left[i] = samples[i] shl shift
                i++
            }
        } else {
            var i = 0
            while (i < frames) {
                left[i] = samples[i * 2] shl shift
                right[i] = samples[i * 2 + 1] shl shift
                i++
            }
        }

        // Monoda LAME faqat chap buferni o'qiydi — o'ngga ham o'sha massiv
        // beriladi, shunda ortiqcha nusxa ko'chirilmaydi.
        val rightBuffer = if (format.channels == 1) left else right
        val count = lame.lame_encode_buffer_int(flags, left, rightBuffer, frames, mp3Buffer, 0, mp3Buffer.size)
        if (count < 0) throw IOException("MP3 kodlashda xato (kod $count)")
        if (count > 0) out.write(mp3Buffer, 0, count)
    }

    /**
     * LAME modullarni qo'lda ulashni talab qiladi: `lame_init_params`
     * ishga tushishidan oldin ular `setModules` orqali berilishi shart,
     * aks holda ichki modul `null` bo'lib qoladi va birinchi chaqiruvdayoq
     * `NullPointerException` beradi. Qulay o'ramlar buni o'zi bajaradi,
     * past darajali API'da esa bu bizning zimmamizda.
     *
     * Bog'lanishlar **pastdan yuqoriga** o'rnatiladi: ichki modullar bir
     *-biriga ikki tomonlama bog'langan (masalan, `BitStream` `VBRTag` ni
     * biladi, `VBRTag` esa `BitStream` ni), shuning uchun tartib shunchaki
     * "ehtiyotkorlik" emas — uni buzish `null` maydonlarga olib keladi.
     *
     * `Lame.setModules` ichida `enc` (asosiy kodlash sikli) ham o'z
     * bog'lanishlarini oladi — uni alohida ulash shart emas.
     */
    private fun wireModules(lame: Lame) {
        val gain = GainAnalysis()
        val bitStream = BitStream()
        val presets = Presets()
        val quantize = Quantize()
        val quantizePVT = QuantizePVT()
        val vbrTag = VBRTag()
        val version = Version()
        val id3 = ID3Tag()
        val takehiro = Takehiro()
        val reservoir = Reservoir()

        val common = Common()
        val iface = Interface()
        val mpgLib = MPGLib()

        mpgLib.setModules(iface, common)
        iface.setModules(vbrTag, common)
        reservoir.setModules(bitStream)
        takehiro.setModules(quantizePVT)
        quantize.setModules(bitStream, reservoir, quantizePVT, takehiro)
        vbrTag.setModules(lame, bitStream, version)
        bitStream.setModules(gain, mpgLib, version, vbrTag)
        id3.setModules(bitStream, version)
        presets.setModules(lame)

        lame.setModules(
            gain, bitStream, presets, quantizePVT,
            quantize, vbrTag, version, id3, mpgLib,
        )

        // Psixoakustik model `Lame` ning ichida yaratiladi va `lame.setModules`
        // uni `enc` ga uzatadi. `QuantizePVT` ham **o'sha** nusxani olishi
        // shart: model holati `lame_init_params` da to'ldiriladi, boshqa
        // nusxa esa bo'sh qolib, kodlash paytida yiqilardi.
        quantizePVT.setModules(takehiro, reservoir, lame.enc.psy)
    }

    private fun ensureCapacity(capacity: Int) {
        if (left.size < capacity) left = IntArray(capacity)
        if (format.channels == 2 && right.size < capacity) right = IntArray(capacity)
    }

    private companion object {
        const val DEFAULT_QUALITY = 2
        const val DEFAULT_BIT_DEPTH = 16
        const val VBR_QUALITY = 4
        const val MIN_BITRATE_KBPS = 8
        const val MAX_BITRATE_KBPS = 320
    }
}
