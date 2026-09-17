package uz.ovozstudio.app.media.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatSupportTest {

    private fun fmt(
        container: AudioContainer,
        codec: AudioCodec,
        rate: Int = 44_100,
        channels: Int = 2,
        depth: Int? = null,
        bitrate: Int? = null,
    ) = AudioFormat(container, codec, rate, channels, depth, bitrate)

    @Test
    fun `wma ochilmaydi qolganlari ochiladi`() {
        assertFalse(FormatSupport.canDecode(AudioContainer.WMA, AudioCodec.WMA))
        assertTrue(FormatSupport.canDecode(AudioContainer.WAV, AudioCodec.PCM))
        assertTrue(FormatSupport.canDecode(AudioContainer.FLAC, AudioCodec.FLAC))
        assertTrue(FormatSupport.canDecode(AudioContainer.MP3, AudioCodec.MP3))
        assertTrue(FormatSupport.canDecode(AudioContainer.M4A, AudioCodec.AAC))
        assertTrue(FormatSupport.canDecode(AudioContainer.OGG, AudioCodec.VORBIS))
        assertTrue(FormatSupport.canDecode(AudioContainer.OGG, AudioCodec.OPUS))
    }

    @Test
    fun `oz kodlovchilarimiz har qanday versiyada ishlaydi`() {
        for (api in listOf(24, 26, 29, 34)) {
            assertTrue(FormatSupport.canEncode(AudioContainer.WAV, AudioCodec.PCM, api))
            assertTrue(FormatSupport.canEncode(AudioContainer.FLAC, AudioCodec.FLAC, api))
            assertTrue(FormatSupport.canEncode(AudioContainer.MP3, AudioCodec.MP3, api))
        }
    }

    @Test
    fun `vorbis va wma hech qachon kodlanmaydi`() {
        for (api in listOf(24, 29, 34)) {
            assertFalse(FormatSupport.canEncode(AudioContainer.OGG, AudioCodec.VORBIS, api))
            assertFalse(FormatSupport.canEncode(AudioContainer.WMA, AudioCodec.WMA, api))
        }
    }

    @Test
    fun `opus faqat api 29 dan boshlab kodlanadi`() {
        assertFalse(FormatSupport.canEncode(AudioContainer.OGG, AudioCodec.OPUS, 28))
        assertTrue(FormatSupport.canEncode(AudioContainer.OGG, AudioCodec.OPUS, 29))
    }

    @Test
    fun `aac mp4 da kodlanadi ogg da yoq`() {
        assertTrue(FormatSupport.canEncode(AudioContainer.M4A, AudioCodec.AAC, 24))
        assertTrue(FormatSupport.canEncode(AudioContainer.AAC, AudioCodec.AAC, 24))
        assertFalse(FormatSupport.canEncode(AudioContainer.OGG, AudioCodec.AAC, 34))
    }

    @Test
    fun `manba formati saqlanadi mp3 uchun`() {
        val source = fmt(AudioContainer.MP3, AudioCodec.MP3, bitrate = 192_000)
        val decision = FormatSupport.resolve(source, apiLevel = 24)
        assertEquals(ExportDecision.Preserved(source), decision)
    }

    @Test
    fun `manba formati saqlanadi flac uchun`() {
        val source = fmt(AudioContainer.FLAC, AudioCodec.FLAC, rate = 96_000, channels = 1, depth = 24)
        assertEquals(ExportDecision.Preserved(source), FormatSupport.resolve(source, apiLevel = 26))
    }

    @Test
    fun `ogg vorbis uchun chekinish taklif qilinadi`() {
        val source = fmt(AudioContainer.OGG, AudioCodec.VORBIS, rate = 48_000, channels = 1)
        val decision = FormatSupport.resolve(source, apiLevel = 34)

        assertTrue("qaror chekinish bo'lishi kerak", decision is ExportDecision.Fallback)
        decision as ExportDecision.Fallback
        assertEquals(FallbackReason.NO_ENCODER, decision.reason)
        assertEquals(AudioContainer.FLAC, decision.recommended.container)
        // Namuna parametrlari o'zgarmasdan o'tadi.
        assertEquals(48_000, decision.recommended.sampleRate)
        assertEquals(1, decision.recommended.channels)
        assertTrue(decision.alternatives.any { it.container == AudioContainer.WAV })
        assertTrue(decision.alternatives.any { it.container == AudioContainer.M4A })
    }

    @Test
    fun `eski qurilmada opus uchun sabab boshqa`() {
        val source = fmt(AudioContainer.OGG, AudioCodec.OPUS)
        val decision = FormatSupport.resolve(source, apiLevel = 26) as ExportDecision.Fallback
        assertEquals(FallbackReason.API_TOO_OLD, decision.reason)
    }

    @Test
    fun `wma uchun ochish sababi korsatiladi`() {
        val source = fmt(AudioContainer.WMA, AudioCodec.WMA)
        val decision = FormatSupport.resolve(source, apiLevel = 34) as ExportDecision.Fallback
        assertEquals(FallbackReason.NO_DECODER, decision.reason)
    }

    @Test
    fun `ochiq tanlov chekinishni bekor qiladi`() {
        val source = fmt(AudioContainer.OGG, AudioCodec.VORBIS)
        val chosen = fmt(AudioContainer.M4A, AudioCodec.AAC, bitrate = 128_000)
        assertEquals(ExportDecision.Preserved(chosen), FormatSupport.resolve(source, 34, override = chosen))
    }

    @Test
    fun `yoqotishsiz manbaning bit chuqurligi saqlanadi`() {
        val source = fmt(AudioContainer.OGG, AudioCodec.VORBIS, depth = null)
        val decision = FormatSupport.resolve(source, 34) as ExportDecision.Fallback
        assertEquals(16, decision.recommended.bitDepth)
    }

    @Test
    fun `bit tezligi standarti kanal soniga qarab`() {
        assertEquals(128_000, FormatSupport.defaultBitrate(AudioCodec.AAC, 2))
        assertEquals(96_000, FormatSupport.defaultBitrate(AudioCodec.AAC, 1))
        assertEquals(0, FormatSupport.defaultBitrate(AudioCodec.FLAC, 2))
    }

    @Test
    fun `fayl nomi konteyner kengaytmasini oladi`() {
        assertEquals("ovoz.flac", fmt(AudioContainer.FLAC, AudioCodec.FLAC).fileName("ovoz"))
        assertEquals("ovoz.m4a", fmt(AudioContainer.M4A, AudioCodec.AAC).fileName("ovoz"))
    }

    @Test
    fun `import qobiliyati dekodlashga tayanadi`() {
        assertTrue(FormatSupport.canImport(DetectedFormat(AudioContainer.FLAC, AudioCodec.FLAC)))
        assertFalse(FormatSupport.canImport(DetectedFormat(AudioContainer.WMA, AudioCodec.WMA)))
    }

    @Test
    fun `opus qiriq tort bir kilogertsda yozilmaydi`() {
        // Opus 44.1 kHz ni bilmaydi. Kodlovchi borligi yetarli emas —
        // qurilmada bu kodlash paytida yiqilardi, shuning uchun oldindan
        // chekinish taklif qilinadi.
        val source = fmt(AudioContainer.OGG, AudioCodec.OPUS, rate = 44_100)
        val decision = FormatSupport.resolve(source, 34) as ExportDecision.Fallback
        assertEquals(FallbackReason.NO_ENCODER, decision.reason)
        assertEquals(AudioContainer.FLAC, decision.recommended.container)

        // 48 kHz esa Opus uchun to'g'ri keladi.
        val ok = fmt(AudioContainer.OGG, AudioCodec.OPUS, rate = 48_000)
        assertEquals(ExportDecision.Preserved(ok), FormatSupport.resolve(ok, 34))
    }

    @Test
    fun `eski qurilmada opus sababi boshqacha`() {
        val source = fmt(AudioContainer.OGG, AudioCodec.OPUS, rate = 48_000)
        val decision = FormatSupport.resolve(source, 26) as ExportDecision.Fallback
        assertEquals(FallbackReason.API_TOO_OLD, decision.reason)
    }

    @Test
    fun `aac faqat oz jadvalidagi chastotalarda yoziladi`() {
        assertTrue(FormatSupport.canEncode(AudioContainer.M4A, AudioCodec.AAC, 34, 44_100))
        assertTrue(FormatSupport.canEncode(AudioContainer.M4A, AudioCodec.AAC, 34, 48_000))
        assertFalse(FormatSupport.canEncode(AudioContainer.M4A, AudioCodec.AAC, 34, 47_000))
        // Chastota berilmasa, jadval tekshirilmaydi — eski xatti-harakat.
        assertTrue(FormatSupport.canEncode(AudioContainer.M4A, AudioCodec.AAC, 34))
    }

    @Test
    fun `wav va flac ixtiyoriy chastotani yozadi`() {
        // PCM va FLAC — bizning kodlovchilar: chastota ular uchun shunchaki
        // sarlavhadagi son.
        assertTrue(FormatSupport.canEncode(AudioContainer.WAV, AudioCodec.PCM, 24, 47_000))
        assertTrue(FormatSupport.canEncode(AudioContainer.FLAC, AudioCodec.FLAC, 24, 47_000))
    }

    @Test
    fun `mp3 oz chastotalar jadvaliga boyun sunadi`() {
        // MP3 ham bizniki (jump3r), lekin LAME ixtiyoriy chastotani bilmaydi:
        // MPEG-1/2/2.5 jadvallaridan tashqarisida kodlovchi umuman ishga
        // tushmaydi. Ilgari bu yerda «ixtiyoriy chastota» deb hisoblanardi va
        // eksport paytida kodlovchi yiqilardi.
        assertTrue(FormatSupport.canEncode(AudioContainer.MP3, AudioCodec.MP3, 24, 44_100))
        // Sintezator beradigan past chastotalar ham jadvalda bor.
        assertTrue(FormatSupport.canEncode(AudioContainer.MP3, AudioCodec.MP3, 24, 8_000))
        assertTrue(FormatSupport.canEncode(AudioContainer.MP3, AudioCodec.MP3, 24, 22_050))
        assertFalse(FormatSupport.canEncode(AudioContainer.MP3, AudioCodec.MP3, 24, 47_000))
    }

    @Test
    fun `konvertor royxatida faqat mos formatlar boladi`() {
        val source = fmt(AudioContainer.MP3, AudioCodec.MP3, rate = 44_100, channels = 2, depth = 16)
        val options = FormatSupport.convertOptions(source, apiLevel = 34)

        // Uchta doimiy variant + M4A/AAC. Opus yo'q: 44.1 kHz unga to'g'ri kelmaydi.
        assertTrue(options.any { it.container == AudioContainer.WAV })
        assertTrue(options.any { it.container == AudioContainer.FLAC })
        assertTrue(options.any { it.container == AudioContainer.MP3 })
        assertTrue(options.any { it.container == AudioContainer.M4A })
        assertFalse("Opus 44.1 kHz ni bilmaydi", options.any { it.codec == AudioCodec.OPUS })
        // Kodlovchisi yo'q formatlar umuman taklif qilinmaydi.
        assertFalse(options.any { it.codec == AudioCodec.VORBIS })
        assertFalse(options.any { it.codec == AudioCodec.WMA })

        // Har bir variant shu faylning o'z parametrlarida yoziladi.
        for (option in options) {
            assertEquals(44_100, option.sampleRate)
            assertEquals(2, option.channels)
            assertEquals(16, option.bitDepth)
        }
        // Yo'qotishli kodlovchilarga bit tezligi berilgan, yo'qotishsizlarga yo'q.
        assertTrue(options.filter { !it.codec.lossless }.all { (it.bitrate ?: 0) > 0 })
        assertTrue(options.filter { it.codec.lossless }.all { it.bitrate == null })
    }

    @Test
    fun `opus royxatga faqat oz chastotasida va yangi api da kiradi`() {
        val at48 = fmt(AudioContainer.WAV, AudioCodec.PCM, rate = 48_000)
        assertTrue(
            FormatSupport.convertOptions(at48, 29).any { it.codec == AudioCodec.OPUS },
        )
        // Eski qurilmada OGG muxeri yo'q.
        assertFalse(
            FormatSupport.convertOptions(at48, 28).any { it.codec == AudioCodec.OPUS },
        )
        // 44.1 kHz da kodlovchining o'zi bu chastotani bilmaydi.
        val at44 = fmt(AudioContainer.WAV, AudioCodec.PCM, rate = 44_100)
        assertFalse(
            FormatSupport.convertOptions(at44, 34).any { it.codec == AudioCodec.OPUS },
        )
    }

    @Test
    fun `taklif qilingan standart format royxatda boladi`() {
        // Ekran dastlab `resolve` taklif qilgan formatni tanlab turadi. Agar
        // unga mos variant ro'yxatda bo'lmasa, tanlov qatorida hech narsa
        // belgilanmay qolardi — foydalanuvchi esa tanlovi yo'qolganini
        // ko'rardi. Shuning uchun ikki jadval bir-biriga mos bo'lishi shart.
        val sources = listOf(
            fmt(AudioContainer.MP3, AudioCodec.MP3),
            fmt(AudioContainer.FLAC, AudioCodec.FLAC, rate = 96_000, channels = 1, depth = 24),
            fmt(AudioContainer.OGG, AudioCodec.VORBIS, rate = 48_000),
            fmt(AudioContainer.OGG, AudioCodec.OPUS, rate = 44_100),
            fmt(AudioContainer.M4A, AudioCodec.AAC, rate = 22_050),
            fmt(AudioContainer.AAC, AudioCodec.AAC, rate = 8_000, channels = 1),
        )

        for (source in sources) {
            val preferred = when (val decision = FormatSupport.resolve(source, apiLevel = 34)) {
                is ExportDecision.Preserved -> decision.format
                is ExportDecision.Fallback -> decision.recommended
            }
            val options = FormatSupport.convertOptions(source, apiLevel = 34)

            assertTrue(
                "$source uchun standart variant ro'yxatda yo'q: $preferred",
                options.any { it.container == preferred.container && it.codec == preferred.codec },
            )
            // Tanlangan qiymat ro'yxatdagi nusxa bilan bir xil bo'lishi kerak:
            // ChoiceRow tanlovni tenglik bo'yicha topadi.
            val target = FormatSupport.defaultTarget(source, apiLevel = 34)
            assertTrue("$source: standart tanlov ro'yxatda yo'q", options.contains(target))
            assertEquals("$source: standart tanlov manba formatida emas",
                preferred.container, target.container)
        }
    }

    @Test
    fun `manba bit tezligi noma'lum bolsa ham tanlov topiladi`() {
        // MP3 manbaning bit tezligi import paytida noma'lum qoladi (`null`),
        // ro'yxatdagi variantda esa aniq qiymat bor. Tenglik bo'yicha
        // solishtirilsa ular mos kelmasdi.
        val source = fmt(AudioContainer.MP3, AudioCodec.MP3, bitrate = null)
        val target = FormatSupport.defaultTarget(source, apiLevel = 34)

        assertEquals(AudioContainer.MP3, target.container)
        assertTrue("bit tezligi aniq bo'lishi kerak", (target.bitrate ?: 0) > 0)
        assertTrue(FormatSupport.convertOptions(source, apiLevel = 34).contains(target))
    }
}
