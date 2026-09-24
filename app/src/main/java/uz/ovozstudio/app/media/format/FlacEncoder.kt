package uz.ovozstudio.app.media.format

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import uz.ovozstudio.app.log.ErrorLog

/**
 * FLAC (Free Lossless Audio Codec) kodlovchi — sof Kotlin, tashqi kutubxonasiz.
 *
 * Nima uchun o'zimiz yozdik: Android'da FLAC uchun *dekoder* bor, *kodlovchi*
 * yo'q. Mavjud sof-Java kutubxonalar (jflac) 2012-yildan beri yangilanmagan
 * va `javax.sound.sampled` ga tayanadi — u Android'da umuman yo'q; bunday
 * sinfga havola qilgan kod qurilmada yiqiladi. Bu loyihada aynan shunday
 * sinfdagi xatolar allaqachon uchragan, shuning uchun tashqi kodga tayanmaymiz.
 *
 * Kodlovchi yo'qotishsiz: chiqqan fayl manba namunalarni aynan qaytaradi,
 * buni ffmpeg bilan tekshirish mumkin (testlarga qarang).
 *
 * Ishlatilgan usullar (FLAC formati spetsifikatsiyasi bo'yicha):
 *  - blok ichida doimiy (CONSTANT), qat'iy bashoratli (FIXED 0–4) yoki
 *    xom (VERBATIM) pastki freymlardan eng kichigi tanlanadi;
 *  - qoldiqlar Rice usulida bo'laklarga (partition) bo'lib kodlanadi;
 *  - barcha namunalar uchun umumiy bo'lgan pastdagi nol bitlar («isrof
 *    bitlar») olib tashlanadi — jimjit oraliqlar deyarli nol joy egallaydi.
 *
 * Sarlavhadagi MD5 va freym o'lchamlari fayl oxirida qayta yoziladi, shuning
 * uchun fayl to'liq tekshiriladigan (`flac -t`) bo'ladi.
 */
class FlacEncoder(
    private val file: File,
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val blockSize: Int = DEFAULT_BLOCK_SIZE,
) : AudioEncoder {

    override val format = AudioFormat(
        container = AudioContainer.FLAC,
        codec = AudioCodec.FLAC,
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = bitsPerSample,
    )

    private val out = RandomAccessFile(file, "rw")
    private val digest = MessageDigest.getInstance("MD5")

    /** Namunalar shu yerda to'planadi; blok to'lganda bitta freym yoziladi. */
    private val pending = IntArray(blockSize * channels)
    private var pendingFrames = 0

    private var frameNumber = 0
    private var totalSamples = 0L
    private var minBlockSeen = Int.MAX_VALUE
    private var maxBlockSeen = 0
    private var minFrameSize = Int.MAX_VALUE
    private var maxFrameSize = 0

    private val channelSamples = Array(channels) { IntArray(blockSize) }
    private val residual = IntArray(blockSize)
    private val zigzag = IntArray(blockSize)
    private val prefix = LongArray(blockSize + 1)

    /** MD5 uchun ochilmagan namunalarning bayt ko'rinishi. */
    private val pcmBytes = ByteArray(blockSize * channels * MAX_BYTES_PER_SAMPLE)

    private var finished = false

    init {
        require(sampleRate in 1..MAX_SAMPLE_RATE) { "Noto'g'ri chastota: $sampleRate" }
        require(channels in 1..8) { "Noto'g'ri kanal soni: $channels" }
        require(bitsPerSample in 4..32) { "Noto'g'ri bit chuqurligi: $bitsPerSample" }
        require(blockSize in 16..65536) { "Noto'g'ri blok hajmi: $blockSize" }

        // "fLaC" + metama'lumot bloki sarlavhasi + 34 baytli STREAMINFO.
        // STREAMINFO hozircha nol bilan to'ldiriladi: haqiqiy qiymatlar
        // (MD5, freym o'lchamlari) faqat oxirida ma'lum bo'ladi.
        out.write(FLaC)
        out.write(byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x22))
        out.write(ByteArray(STREAMINFO_BYTES))
    }

    /**
     * Oraliq (interleaved) namunalarni qabul qiladi. [frames] — kadrlar soni,
     * ya'ni `samples` massividagi elementlar soni `frames * channels` bo'lishi
     * kerak. Massiv o'zgartirilmaydi.
     */
    @Throws(IOException::class)
    override fun write(samples: IntArray, frames: Int) = writePcm(samples, frames)

    @Throws(IOException::class)
    fun writePcm(samples: IntArray, frames: Int) {
        check(!finished) { "Kodlovchi allaqachon yopilgan" }
        require(frames >= 0 && frames * channels <= samples.size) {
            "Massiv juda kichik: $frames kadr kerak, massivda ${samples.size / channels} bor"
        }

        val maxValue = (1 shl (bitsPerSample - 1)) - 1
        val minValue = -(1 shl (bitsPerSample - 1))
        val bytesPerSample = bitsPerSample / 8

        totalSamples += frames

        var offset = 0
        while (offset < frames) {
            val take = minOf(blockSize - pendingFrames, frames - offset)
            val base = pendingFrames * channels
            val srcBase = offset * channels
            val limit = take * channels

            // Qiymat bitsPerSample ichiga sig'masligi mumkin (chaqiruvchi
            // xatosi). Qisqartirish — yagona to'g'ri yo'l: aks holda fayldagi
            // namuna bilan MD5 mos kelmay, fayl tekshiruvdan o'tmasdi.
            var byteCount = 0
            for (i in 0 until limit) {
                val v = samples[srcBase + i].coerceIn(minValue, maxValue)
                pending[base + i] = v
                for (b in 0 until bytesPerSample) {
                    pcmBytes[byteCount++] = ((v shr (8 * b)) and 0xFF).toByte()
                }
            }
            digest.update(pcmBytes, 0, byteCount)

            pendingFrames += take
            offset += take
            if (pendingFrames == blockSize) writeFrame(blockSize)
        }
    }

    /** Sarlavhani haqiqiy qiymatlar bilan to'ldirib, faylni yopadi. */
    @Throws(IOException::class)
    override fun finish() {
        check(!finished) { "Kodlovchi allaqachon yopilgan" }
        finished = true
        var failed = false
        try {
            if (pendingFrames > 0) writeFrame(pendingFrames)
            pendingFrames = 0
            out.seek(STREAMINFO_OFFSET.toLong())
            out.write(streamInfo())
        } catch (error: Throwable) {
            failed = true
            throw error
        } finally {
            runCatching { out.close() }
            // Yarim yozilgan FLAC — buzuq fayl: STREAMINFO nolda qolgan,
            // MD5 ham, namunalar soni ham noto'g'ri, `flac -t` uni rad
            // etadi. Uni saqlab qolishdan ko'ra o'chirgan ma'qul —
            // MediaCodec kodlovchisi ham shunday qiladi.
            if (failed) {
                ErrorLog.error("audio.flac", "FLAC faylini yozib bo'lmadi: ${file.name}", null)
                runCatching { file.delete() }
            }
        }
    }

    override fun close() {
        if (!out.channel.isOpen) return
        finished = true
        out.close()
    }

    // ---------------------------------------------------------------- freym

    private fun writeFrame(frames: Int) {
        val body = ByteArrayOutputStream(frames * channels * (bitsPerSample / 8 + 1) + 64)
        val bw = BitWriter(body)

        // --- Freym sarlavhasi (CRC-8 shu baytlar ustida hisoblanadi) ---
        val header = ByteArrayOutputStream(16)
        val hw = BitWriter(header)
        hw.write(SYNC_CODE, 14)
        hw.write(0, 1) // zaxira bit
        hw.write(0, 1) // bloklash usuli: qat'iy blok hajmi
        hw.write(BLOCK_SIZE_CODE, 4) // blok hajmi sarlavha oxirida, 16 bit
        hw.write(0, 4) // chastota kodi 0 — STREAMINFO'dan olinadi
        hw.write(channels - 1, 4) // kanallar mustaqil kodlanadi
        hw.write(0, 3) // bit chuqurligi kodi 0 — STREAMINFO'dan olinadi
        hw.write(0, 1) // zaxira bit
        writeCodedNumber(hw, frameNumber)
        hw.write(frames - 1, 16)
        hw.align()

        val headerBytes = header.toByteArray()
        body.write(headerBytes)
        body.write(crc8(headerBytes))

        // --- Har bir kanal uchun pastki freym ---
        for (c in 0 until channels) {
            val dst = channelSamples[c]
            for (i in 0 until frames) dst[i] = pending[i * channels + c]
            writeSubframe(bw, dst, frames, bitsPerSample)
        }

        bw.align()
        val frameBytes = body.toByteArray()
        val crc = crc16(frameBytes, frameBytes.size)
        out.write(frameBytes)
        out.write((crc ushr 8) and 0xFF)
        out.write(crc and 0xFF)

        val frameSize = frameBytes.size + 2
        if (frameSize < minFrameSize) minFrameSize = frameSize
        if (frameSize > maxFrameSize) maxFrameSize = frameSize
        if (frames < minBlockSeen) minBlockSeen = frames
        if (frames > maxBlockSeen) maxBlockSeen = frames

        frameNumber++
        pendingFrames = 0
    }

    private fun writeCodedNumber(bw: BitWriter, value: Int) {
        val v = value.toLong() and 0xFFFFFFFFL
        val bytes = when {
            v < 0x80L -> 1
            v < 0x800L -> 2
            v < 0x10000L -> 3
            v < 0x200000L -> 4
            v < 0x4000000L -> 5
            else -> 6
        }
        if (bytes == 1) {
            bw.write(v, 8)
            return
        }
        val first = (0xFF shl (8 - bytes)).toLong()
        val payloadBits = 7 - bytes
        bw.write(first or ((v shr (6 * (bytes - 1))) and ((1L shl payloadBits) - 1)), 8)
        for (i in bytes - 2 downTo 0) {
            bw.write(0x80L or ((v shr (6 * i)) and 0x3FL), 8)
        }
    }

    // ------------------------------------------------------------- pastki freym

    private fun writeSubframe(bw: BitWriter, samples: IntArray, n: Int, bps: Int) {
        // Butun blok uchun umumiy bo'lgan pastdagi nol bitlar. Jimjit
        // oraliqlarda bu deyarli butun blokni nolga aylantiradi.
        var wasted = bps - 1
        for (i in 0 until n) {
            val v = samples[i]
            if (v != 0) {
                val tz = Integer.numberOfTrailingZeros(v)
                if (tz < wasted) wasted = tz
            }
        }
        if (wasted > 0) {
            for (i in 0 until n) samples[i] = samples[i] shr wasted
        }
        val effBps = bps - wasted

        var constant = true
        for (i in 1 until n) {
            if (samples[i] != samples[0]) {
                constant = false
                break
            }
        }

        bw.write(0, 1) // pastki freym sarlavhasi har doim nol bitdan boshlanadi
        if (constant) {
            writeSubframeHeader(bw, TYPE_CONSTANT, wasted)
            bw.write(samples[0].toLong(), effBps)
            return
        }

        // Eng yaxshi qat'iy bashorat tartibini arzon baho bilan tanlaymiz,
        // so'ng faqat g'olib uchun aniq (qimmat) qidiruv qilamiz.
        val maxOrder = minOf(MAX_FIXED_ORDER, n - 1)
        var bestOrder = -1
        var bestEstimate = n.toLong() * effBps // VERBATIM narxi
        for (order in 0..maxOrder) {
            fixedResidual(samples, n, order, residual)
            val count = n - order
            var sum = 0L
            for (i in 0 until count) sum += if (residual[i] < 0) -residual[i].toLong() else residual[i].toLong()
            val mean = sum / count
            val k = if (mean == 0L) 0 else (64 - java.lang.Long.numberOfLeadingZeros(mean)).toInt()
            val estimate = order.toLong() * effBps + count.toLong() * (k + 1)
            if (estimate < bestEstimate) {
                bestEstimate = estimate
                bestOrder = order
            }
        }

        if (bestOrder < 0) {
            writeSubframeHeader(bw, TYPE_VERBATIM, wasted)
            for (i in 0 until n) bw.write(samples[i].toLong(), effBps)
            return
        }

        writeSubframeHeader(bw, TYPE_FIXED or bestOrder, wasted)
        for (i in 0 until bestOrder) bw.write(samples[i].toLong(), effBps)

        val count = n - bestOrder
        fixedResidual(samples, n, bestOrder, residual)
        for (i in 0 until count) {
            val r = residual[i]
            zigzag[i] = (r shl 1) xor (r shr 31)
        }
        writeRiceResiduals(bw, zigzag, n, bestOrder)
    }

    /**
     * Pastki freym sarlavhasi: 6 bit tur + "isrof bitlar" bayrog'i.
     *
     * Bayroq FAQAT isrof bitlar bo'lganda 1 bo'ladi — aks holda dekoder
     * keyingi bitlarni unar o'lcham deb o'qib, butun oqim surilib ketadi
     * (aynan shu xato ffmpeg tekshiruvida topilgan).
     */
    private fun writeSubframeHeader(bw: BitWriter, type: Int, wasted: Int) {
        bw.write(type, 6)
        if (wasted > 0) {
            bw.write(1, 1)
            bw.writeUnary(wasted - 1)
        } else {
            bw.write(0, 1)
        }
    }

    /** `samples` dan `order`-tartibli qat'iy bashorat qoldiqlarini hisoblaydi. */
    private fun fixedResidual(samples: IntArray, n: Int, order: Int, out: IntArray) {
        when (order) {
            0 -> System.arraycopy(samples, 0, out, 0, n)
            1 -> for (i in 1 until n) out[i - 1] = samples[i] - samples[i - 1]
            2 -> for (i in 2 until n) {
                out[i - 2] = samples[i] - 2 * samples[i - 1] + samples[i - 2]
            }
            3 -> for (i in 3 until n) {
                out[i - 3] = samples[i] - 3 * samples[i - 1] + 3 * samples[i - 2] - samples[i - 3]
            }
            else -> for (i in 4 until n) {
                out[i - 4] = samples[i] - 4 * samples[i - 1] + 6 * samples[i - 2] -
                    4 * samples[i - 3] + samples[i - 4]
            }
        }
    }

    /**
     * Qoldiqlarni Rice usulida kodlaydi. [n] — pastki freym blokining hajmi
     * (qoldiqlar soni emas!), [order] — bashorat tartibi.
     *
     * Bo'laklar blok hajmi bo'yicha bo'linadi: har bir bo'lak `n >> po`
     * namuna oladi, faqat BIRINCHISI `order` taga kam — chunki bashorat
     * uchun ishlatiladigan dastlabki `order` namuna qoldiq bermaydi.
     * Shuning uchun bo'laklash tartibi faqat `n` 2^po ga bo'linganda
     * joiz bo'ladi, aks holda ba'zi qoldiqlar kodlanmay qolardi.
     *
     * Tartib avval arzon baho bilan tanlanadi, parametrlar esa har bir
     * bo'lak uchun aniq hisoblanadi.
     */
    private fun writeRiceResiduals(bw: BitWriter, u: IntArray, n: Int, order: Int) {
        val count = n - order

        // Prefiks yig'indilari: har qanday bo'lak yig'indisi O(1) da olinadi.
        prefix[0] = 0
        for (i in 0 until count) prefix[i + 1] = prefix[i] + u[i]

        var maxPo = 0
        while (maxPo < MAX_PARTITION_ORDER &&
            (n shr (maxPo + 1)) > order &&
            n % (1 shl (maxPo + 1)) == 0
        ) {
            maxPo++
        }

        var bestPo = 0
        var bestCost = Long.MAX_VALUE
        for (po in 0..maxPo) {
            var cost = 4L // bo'laklash tartibi maydoni
            forEachPartition(n, po, order) { start, len ->
                val sum = prefix[start + len] - prefix[start]
                val mean = sum / len
                val k = if (mean == 0L) 0 else (64 - java.lang.Long.numberOfLeadingZeros(mean)).toInt()
                cost += 4 + (sum shr k) + len.toLong() * (1 + k)
            }
            if (cost < bestCost) {
                bestCost = cost
                bestPo = po
            }
        }

        // Qoldiq kodlash usuli: 0 — 4 bitli Rice parametrlari. Bu maydon
        // majburiy; tushib qolsa dekoder bo'laklash tartibini 2 bitga surib
        // o'qiydi va oqim butunlay buziladi (ffmpeg: "invalid residual").
        bw.write(RICE_METHOD_4BIT, 2)
        bw.write(bestPo, 4)
        forEachPartition(n, bestPo, order) { start, len ->
            val k = bestRiceParameter(u, start, len)
            bw.write(k, 4)
            val end = start + len
            var i = start
            if (k == 0) {
                while (i < end) {
                    bw.writeUnary(u[i])
                    i++
                }
            } else {
                val mask = (1 shl k) - 1
                while (i < end) {
                    bw.writeUnary(u[i] ushr k)
                    bw.write((u[i] and mask).toLong(), k)
                    i++
                }
            }
        }
    }

    /** Har bir bo'lakni (qoldiq indeksi, uzunlik) ko'rinishida aylanib chiqadi. */
    private inline fun forEachPartition(n: Int, po: Int, order: Int, action: (Int, Int) -> Unit) {
        val parts = 1 shl po
        val partSize = n shr po
        for (p in 0 until parts) {
            if (p == 0) {
                action(0, partSize - order)
            } else {
                action(p * partSize - order, partSize)
            }
        }
    }

    /** Bo'lak uchun eng arzon Rice parametri (0–14). */
    private fun bestRiceParameter(u: IntArray, offset: Int, len: Int): Int {
        var sum = 0L
        val end = offset + len
        for (i in offset until end) sum += u[i].toLong()
        val mean = if (len == 0) 0L else sum / len
        val estimate =
            if (mean == 0L) 0 else (64 - java.lang.Long.numberOfLeadingZeros(mean)).toInt()

        var best = -1
        var bestCost = Long.MAX_VALUE
        for (k in maxOf(0, estimate - 1)..minOf(MAX_RICE_PARAMETER, estimate + 1)) {
            var cost = len.toLong() * (1 + k)
            for (i in offset until end) cost += (u[i] ushr k).toLong()
            if (cost < bestCost) {
                bestCost = cost
                best = k
            }
        }
        return if (best < 0) 0 else best
    }

    // ------------------------------------------------------------- sarlavha

    private fun streamInfo(): ByteArray {
        val buf = ByteArray(STREAMINFO_BYTES)
        val minBlock = if (minBlockSeen == Int.MAX_VALUE) blockSize else minBlockSeen
        val minFrame = if (minFrameSize == Int.MAX_VALUE) 0 else minFrameSize
        putBits(buf, 0, minBlock.toLong(), 16)
        putBits(buf, 16, maxOf(maxBlockSeen, minBlock).toLong(), 16)
        putBits(buf, 32, minFrame.toLong(), 24)
        putBits(buf, 56, maxFrameSize.toLong(), 24)
        putBits(buf, 80, sampleRate.toLong(), 20)
        putBits(buf, 100, (channels - 1).toLong(), 3)
        putBits(buf, 103, (bitsPerSample - 1).toLong(), 5)
        putBits(buf, 108, totalSamples, 36)
        val md5 = digest.digest()
        System.arraycopy(md5, 0, buf, 18, 16)
        return buf
    }

    private fun putBits(buf: ByteArray, bitPos: Int, value: Long, count: Int) {
        for (i in count - 1 downTo 0) {
            val bit = (value ushr i) and 1L
            val pos = bitPos + (count - 1 - i)
            val index = pos ushr 3
            val mask = 1 shl (7 - (pos and 7))
            buf[index] = if (bit == 1L) {
                (buf[index].toInt() or mask).toByte()
            } else {
                (buf[index].toInt() and mask.inv()).toByte()
            }
        }
    }

    companion object {
        /** Standart blok hajmi: striming to'plamiga (streamable subset) mos. */
        const val DEFAULT_BLOCK_SIZE = 4096

        private const val MAX_BYTES_PER_SAMPLE = 4
        private const val MAX_SAMPLE_RATE = 1 shl 20
        private const val MAX_FIXED_ORDER = 4
        private const val MAX_PARTITION_ORDER = 6
        private const val MAX_RICE_PARAMETER = 14

        /** Qoldiq kodlash usuli: 4 bitli Rice parametrlari (0b00). */
        private const val RICE_METHOD_4BIT = 0b00

        private const val SYNC_CODE = 0x3FFEL
        private const val BLOCK_SIZE_CODE = 0b0111 // blok hajmi sarlavha oxirida
        private const val TYPE_CONSTANT = 0b000000
        private const val TYPE_VERBATIM = 0b000001
        private const val TYPE_FIXED = 0b001000

        private val FLaC = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte())

        /** "fLaC" (4) + metama'lumot bloki sarlavhasi (4). */
        private const val STREAMINFO_OFFSET = 8
        private const val STREAMINFO_BYTES = 34

        private val CRC8_TABLE = IntArray(256) { i ->
            var c = i
            repeat(8) {
                c = if (c and 0x80 != 0) ((c shl 1) xor 0x07) and 0xFF else (c shl 1) and 0xFF
            }
            c
        }

        private val CRC16_TABLE = IntArray(256) { i ->
            var c = i shl 8
            repeat(8) {
                c = if (c and 0x8000 != 0) {
                    ((c shl 1) xor 0x8005) and 0xFFFF
                } else {
                    (c shl 1) and 0xFFFF
                }
            }
            c
        }

        internal fun crc8(data: ByteArray): Int {
            var crc = 0
            for (b in data) crc = CRC8_TABLE[(crc xor (b.toInt() and 0xFF)) and 0xFF]
            return crc
        }

        internal fun crc16(data: ByteArray, length: Int): Int {
            var crc = 0
            for (i in 0 until length) {
                crc = ((crc shl 8) xor CRC16_TABLE[((crc shr 8) xor (data[i].toInt() and 0xFF)) and 0xFF]) and 0xFFFF
            }
            return crc
        }
    }
}

/**
 * Bit darajasida yozuvchi. Baytlar to'lganda darhol oqimga chiqadi.
 */
private class BitWriter(private val out: ByteArrayOutputStream) {

    private var accumulator = 0L
    private var bits = 0

    fun write(value: Int, count: Int) = write(value.toLong(), count)

    fun write(value: Long, count: Int) {
        if (count == 0) return
        val masked = value and ((1L shl count) - 1)
        accumulator = (accumulator shl count) or masked
        bits += count
        while (bits >= 8) {
            bits -= 8
            out.write(((accumulator ushr bits) and 0xFF).toInt())
        }
    }

    /** [q] ta nol bit, keyin bitta bir bit. */
    fun writeUnary(q: Int) {
        var remaining = q
        while (remaining >= 32) {
            write(0L, 32)
            remaining -= 32
        }
        write(1L, remaining + 1)
    }

    fun align() {
        if (bits > 0) write(0L, 8 - bits)
    }
}
