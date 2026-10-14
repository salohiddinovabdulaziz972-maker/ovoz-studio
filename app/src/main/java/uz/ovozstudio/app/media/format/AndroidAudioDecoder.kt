package uz.ovozstudio.app.media.format

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import uz.ovozstudio.app.log.ErrorLog
import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavWriter
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Siqilgan faylni (MP3, M4A, FLAC, OGG…) WAV ga dekodlaydi.
 *
 * Nega kerak: tahrirlash ichkarida yo'qotishsiz PCM ustida ketadi, lekin
 * foydalanuvchi istalgan formatni yuklashi mumkin. Import paytida fayl bir
 * marta WAV ga ochiladi, ish tugagach esa [FormatPreservingExporter] uni
 * **manba formatiga qaytaradi** — foydalanuvchi talab qilgan aynan shu.
 *
 * Dekodlash `MediaExtractor` + `MediaCodec` orqali: Android'ning o'zi
 * beradigan yagona yo'l. Qurilmada sinaladi, JVM'da emas.
 *
 * ## Dekoder yiqilsa
 *
 * Qurilmaning o'z dekoderi (Samsung'da `c2.sec…`) ba'zi fayllarda fayl
 * boshida yoki o'rtasida `IllegalStateException` bilan to'xtaydi. Bunda
 * kesish, o'chirish va birlashtirishning hammasi fayl ochishda to'xtardi.
 * Shuning uchun [decode] bitta dekoderga bog'lanib qolmaydi: avval Android'ning
 * o'z barqaror dekoderlarini sinaydi, yiqilsa — keyingisiga o'tadi, har safar
 * faylni boshidan o'qib ([DecoderPolicy]). Qisqargan audio hech qachon
 * «tayyor» deb qabul qilinmaydi.
 *
 * Uzun fayl (soatlab yozuv) uchun bu bir necha soniyadan ortiq davom etishi
 * mumkin, shuning uchun [decode] jarayon foizini beradi: foydalanuvchi
 * (ayniqsa ekran o'quvchi bilan) ekran «osilib qoldimi» deb o'ylamasligi kerak.
 */
class AndroidAudioDecoder {

    sealed interface Result {
        /** [wav] — ochilgan fayl; [format] — undagi namuna parametrlari. */
        data class Done(val wav: File, val format: AudioFormat, val frames: Long) : Result

        /** Bu faylni ochib bo'lmadi. Fayl yaratilmaydi. */
        data class Failed(val reason: FallbackReason) : Result
    }

    /**
     * [destination] ga WAV yozadi. Manba fayl o'zgartirilmaydi.
     *
     * Bekor qilish yo'q: xato bo'lsa fayl shunchaki qoldirilmaydi, yangi
     * urinish yangi nom bilan boshlanadi.
     *
     * [onProgress] 0…1 oralig'ida, taxminiy: manba konteynerning o'zi
     * uzunlikni aytmasa (kamdan-kam), chaqirilmaydi — bu holda chaqiruvchi
     * aniq foizsiz («ishlayapti…») ko'rsatishi kerak. Zaxira dekoderga
     * o'tilganda foiz yana noldan boshlanadi.
     */
    fun decode(source: File, destination: File, onProgress: (Float) -> Unit = {}): Result {
        val mime = probeMime(source) ?: return Result.Failed(FallbackReason.NO_DECODER)

        // Ro'yxatni olib bo'lmasa — Android'ning o'z tanloviga qoldiramiz
        // (`null` = `createDecoderByType`), ilgarigi xatti-harakat.
        val listed: List<String?> = decoderNames(mime).take(MAX_DECODERS)
        val names: List<String?> = if (listed.isEmpty()) listOf<String?>(null) else listed

        // Bo'sh natija (0 kadr) ham «yaramadi» hisoblanadi: boshqa dekoder
        // faylni to'liq ochishi mumkin. Hammasi bo'sh bersa — oxirgisi
        // qaytariladi, import uni «fayl bo'sh» deb ko'rsatadi.
        var empty: Result.Done? = null
        for ((position, name) in names.withIndex()) {
            val outcome = decodeWith(source, destination, mime, name, position + 1, names.size, onProgress)
            if (outcome is Result.Done) {
                if (outcome.frames > 0L) {
                    if (position > 0) {
                        ErrorLog.info(
                            "audio.decode",
                            "Fayl zaxira dekoder bilan ochildi: ${name ?: STANDARD} ($mime)",
                        )
                    }
                    return outcome
                }
                empty = outcome
            }
        }
        return empty ?: Result.Failed(FallbackReason.NO_DECODER)
    }

    private class Track(val index: Int)

    private fun selectAudioTrack(extractor: MediaExtractor): Track? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return Track(i)
        }
        return null
    }

    /** Faylning audio izi MIME turi. Fayl ochilmasa yoki audio izi bo'lmasa — `null`. */
    private fun probeMime(source: File): String? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(source.absolutePath)
            val track = selectAudioTrack(extractor)
            if (track == null) {
                ErrorLog.info("audio.decode", "Faylda audio izi topilmadi")
                return null
            }
            return extractor.getTrackFormat(track.index).getString(MediaFormat.KEY_MIME)
        } catch (error: Exception) {
            // Fayl ochilmadi: konteyner buzuq yoki Android uni umuman
            // bilmaydi (WMA shunday). Faqat `IOException` emas, hamma istisno
            // ushlanadi: `MediaExtractor` yaroqsiz faylda `IllegalArgumentException`
            // ham tashlaydi, u esa ilovani yiqitmasligi kerak.
            ErrorLog.error("audio.decode", "MediaExtractor faylni ocha olmadi", error)
            return null
        } finally {
            extractor.release()
        }
    }

    /**
     * Shu turni dekodlay oladigan dekoderlar, sinash tartibida
     * ([DecoderPolicy.order]). Ro'yxat olinmasa — bo'sh.
     */
    private fun decoderNames(mime: String): List<String> {
        val listed: List<String> = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { info ->
                    !info.isEncoder && info.supportedTypes.any { type -> type.equals(mime, ignoreCase = true) }
                }
                .map { info -> info.name }
        } catch (error: Exception) {
            ErrorLog.error("audio.decode", "Dekoderlar ro'yxatini olib bo'lmadi", error)
            emptyList()
        }
        return DecoderPolicy.order(listed)
    }

    /**
     * Faylni bitta dekoder bilan, boshidan dekodlaydi. Har urinish o'z
     * `MediaExtractor` va `MediaCodec` ini oladi va ularni o'zi bo'shatadi:
     * yiqilgan dekoderdan qolgan holat keyingi urinishga o'tmaydi.
     *
     * [decoderName] `null` bo'lsa — Android'ning o'z tanlovi.
     */
    private fun decodeWith(
        source: File,
        destination: File,
        mime: String,
        decoderName: String?,
        position: Int,
        total: Int,
        onProgress: (Float) -> Unit,
    ): Result {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source.absolutePath)
            val track = selectAudioTrack(extractor) ?: return Result.Failed(FallbackReason.NO_DECODER)
            extractor.selectTrack(track.index)

            val input = extractor.getTrackFormat(track.index)
            // `0` — «noma'lum»: bunday konteynerda foiz emas, faqat aniq
            // bo'lmagan «ishlayapti» ko'rsatiladi. `getLong(nom, standart)`
            // faqat Android 10+ da bor, shuning uchun `containsKey` ishlatiladi.
            val durationUs = if (input.containsKey(MediaFormat.KEY_DURATION)) {
                input.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            val created = if (decoderName == null) {
                MediaCodec.createDecoderByType(mime)
            } else {
                MediaCodec.createByCodecName(decoderName)
            }
            codec = created
            created.configure(input, null, null, 0)
            created.start()

            return pump(extractor, created, destination, durationUs, onProgress)
        } catch (error: Exception) {
            // `MediaCodec` yaroqsiz yoki qo'llab-quvvatlanmagan oqimda
            // `CodecException`, `IllegalStateException` ham tashlaydi.
            // Dekoder nomi va turi jurnalga yoziladi: qaysi qurilma dekoderi
            // qaysi formatda yiqilgani keyin shundan ko'rinadi.
            destination.delete()
            ErrorLog.error(
                "audio.decode",
                "Dekodlash bajarilmadi (dekoder $position/$total: ${decoderName ?: STANDARD}, format: $mime)",
                error,
            )
            return Result.Failed(FallbackReason.NO_DECODER)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    /**
     * Dekoder chiqishini WAV ga quyadi.
     *
     * Bit chuqurligi dekoderning o'zi aytadi: `KEY_PCM_ENCODING` 16-bit yoki
     * suzuvchi nuqta bo'lishi mumkin. Suzuvchi nuqta bersa — 24-bit yozamiz,
     * aks holda aniqlik behuda yo'qolardi (masalan 24-bitli FLAC manba).
     */
    @Throws(IOException::class)
    private fun pump(
        extractor: MediaExtractor,
        codec: MediaCodec,
        destination: File,
        durationUs: Long,
        onProgress: (Float) -> Unit,
    ): Result {
        val info = MediaCodec.BufferInfo()
        var writer: WavWriter? = null
        var format: AudioFormat? = null
        var channels = 0
        var frames = 0L
        var inputDone = false
        var outputDone = false
        var floatOutput = false
        var stalls = 0

        /**
         * Dekoderning chiqish formatini qabul qiladi.
         *
         * Bir xil format takror e'lon qilinsa — hech narsa o'zgarmaydi. Ilgari
         * har e'londa yozuvchi qayta ochilib, fayl noldan boshlanardi: ba'zi
         * dekoderlar formatni ikki marta e'lon qiladi, natijada ilgari yozilgan
         * audio jimgina yo'qolardi.
         *
         * Format haqiqatan o'zgargan bo'lsa-yu, audio allaqachon yozilgan bo'lsa,
         * davom etib bo'lmaydi: xato beriladi (keyingi dekoder sinaladi).
         */
        fun adopt(output: MediaFormat) {
            val nowFloat = isFloat(output)
            val nowChannels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val nowRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val known = format
            if (writer != null && known != null &&
                known.sampleRate == nowRate && channels == nowChannels && floatOutput == nowFloat
            ) {
                return
            }
            if (frames > 0L) throw IOException("Dekoder chiqish formatini fayl o'rtasida o'zgartirdi")

            val depth = if (nowFloat) BitDepth.BIT_24 else BitDepth.BIT_16
            writer?.close()
            floatOutput = nowFloat
            channels = nowChannels
            writer = WavWriter(destination, nowRate, nowChannels, depth)
            format = AudioFormat(AudioContainer.WAV, AudioCodec.PCM, nowRate, nowChannels, depth.bits)
        }

        try {
            while (!outputDone) {
                // Har bir aylanish nimadir berganini kuzatamiz. Buferlar
                // almashmay turgan aylanishlar cheksiz davom etishi mumkin:
                // buzuq faylda MediaCodec oxirini e'lon qilmasa, tsikl hech
                // qachon tugamaydi va import "abadiy ishlayapti" bo'lib qoladi.
                var progressed = false

                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        progressed = true
                        val buffer = codec.getInputBuffer(index)
                            ?: throw IOException("Dekoder kirish buferini bermadi")
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            // Navbatga qo'yilgan namunaning vaqti — o'qilgan
                            // joyning taxminiy foizi uchun ham ishlatiladi:
                            // qo'shimcha chaqiruv shart emas.
                            val timeUs = extractor.sampleTime
                            codec.queueInputBuffer(index, 0, size, timeUs, 0)
                            extractor.advance()
                            if (durationUs > 0) onProgress((timeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                        }
                    }
                }

                when (val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    // Chiqish formati o'zgargani «bufer berdi» degani emas.
                    // Ilgari shu shox `progressed` ni ko'tarardi va `stalls`
                    // nolga qaytardi; dekoder esa chiqish buferini umuman
                    // bermasa, tsikl cheksiz aylanib qolardi. Har aylanishda
                    // faqat formati o'zgarib turadigan buzuq fayl importni
                    // «abadiy ishlayapti» holatiga olib kelardi.
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> adopt(codec.outputFormat)

                    else -> if (index >= 0) {
                        progressed = true
                        val buffer = codec.getOutputBuffer(index)
                        if (buffer != null && info.size > 0) {
                            // Ba'zi dekoderlar chiqish formatini alohida
                            // e'lon qilmaydi: birinchi buferdan oldin joriy
                            // formatni o'zimiz so'raymiz.
                            if (writer == null) adopt(codec.outputFormat)
                            val sink = writer
                                ?: throw IOException("Dekoder formatni e'lon qilmadi")
                            frames += writeBuffer(sink, buffer, info, floatOutput, channels)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }

                stalls = if (progressed) 0 else stalls + 1
                if (stalls > MAX_STALLS) throw IOException("Dekoder $MAX_STALLS urinishdan keyin ham javob bermadi")
            }
        } catch (error: IllegalStateException) {
            // Qurilmaning dekoderi (jumladan Samsung SM-A716S, Android 13)
            // `IllegalStateException` bilan yiqilishi mumkin. Ikki xil holat:
            //
            // 1) Kirish hali tugamagan (`inputDone` yolg'on): dekoder faylning
            //    faqat boshini bergan. Uni jimgina «tayyor» deb qabul qilish
            //    foydalanuvchiga qisqartirilgan audioni to'liq deb ko'rsatardi.
            //    Xato beriladi, `decode` esa keyingi dekoderni sinaydi.
            //
            // 2) Kirish tugagan: yozilgan qism faqat shunda saqlanadi, agar u
            //    aytilgan davomiylikka yetsa ([DecoderPolicy.endCrashAcceptable]).
            //    Qisqa faylda kirish bir zumda tugaydi, dekoder esa birinchi
            //    bufergayoq yiqilishi mumkin — bunda qism to'liq emas.
            if (!inputDone) {
                throw IOException("Dekoder fayl o'rtasida to'xtadi ($frames kadr yozilgandan keyin)", error)
            }
            val rate = format?.sampleRate ?: 0
            if (!DecoderPolicy.endCrashAcceptable(frames, rate, durationUs)) {
                throw IOException("Dekoder oxirida yiqildi, audio to'liq emas ($frames kadr)", error)
            }
            ErrorLog.info(
                "audio.decode",
                "Dekoder oxirida yiqildi, yozilgan qism saqlandi: $frames freym",
            )
        } finally {
            runCatching { writer?.close() }
        }

        val actual = format ?: run {
            destination.delete()
            return Result.Failed(FallbackReason.NO_DECODER)
        }
        return Result.Done(destination, actual, frames)
    }

    private fun isFloat(output: MediaFormat): Boolean {
        val encoding = if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            output.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            MediaCodecInfoEncoding.PCM_16BIT
        }
        return encoding == MediaCodecInfoEncoding.PCM_FLOAT
    }

    /**
     * Dekoder chiqishini namunalarga aylantirib, WAV ga yozadi va yozilgan
     * kadrlar sonini qaytaradi.
     *
     * [channels] — dekoder e'lon qilgan kanal soni; u [sink] bilan bir xil
     * bo'lishi shart, chunki ikkalasi ham bitta formatdan olingan.
     */
    private fun writeBuffer(
        sink: WavWriter,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        floatOutput: Boolean,
        channels: Int,
    ): Long {
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        buffer.order(ByteOrder.nativeOrder())

        if (floatOutput) {
            val count = info.size / 4
            val samples = FloatArray(count)
            buffer.asFloatBuffer().get(samples)
            sink.write(samples, count)
            return (count / channels).toLong()
        }

        val count = info.size / 2
        // Bufer bir yo'la o'qiladi (xotiradan xotiraga ko'chirish): har bir
        // namunani alohida `get(i)` bilan olish uzun faylda sezilarli sekin.
        val shorts = ShortArray(count)
        buffer.asShortBuffer().get(shorts)
        val samples = IntArray(count)
        for (i in 0 until count) samples[i] = shorts[i].toInt()
        // 16-bitli namunalar WavWriter ning butun sonli yo'lidan o'tadi:
        // float orqali aylantirish 24-bitda aniqlikni yo'qotardi.
        sink.writeIntegers(samples, count)
        return (count / channels).toLong()
    }

    private companion object {
        const val TIMEOUT_US = 10_000L

        /**
         * Nechta bo'sh aylanishdan keyin dekoder buzuq deb hisoblanadi.
         *
         * Har biri 10 ms kutadi, ya'ni 200 ta — taxminan ikki soniya uzluksiz
         * jimjitlik. Sog'lom dekoder bunday qilmaydi, buzuq fayl esa aynan
         * shunday qiladi.
         */
        const val MAX_STALLS = 200

        /**
         * Bitta fayl uchun eng ko'pi bilan nechta dekoder sinaladi. Har bir
         * yiqilgan urinish vaqt oladi: to'rttadan keyin ham ochilmagan fayl
         * aslida buzuq, foydalanuvchini uzoq kutdirishning ma'nosi yo'q.
         */
        const val MAX_DECODERS = 4

        /** Jurnalda: dekoder nomi berilmaganda (`createDecoderByType`). */
        const val STANDARD = "standart"
    }

    /** `MediaFormat` konstantalari Android'ning turli versiyalarida nomlanadi. */
    private object MediaCodecInfoEncoding {
        const val PCM_16BIT = 2
        const val PCM_FLOAT = 4
    }
}
