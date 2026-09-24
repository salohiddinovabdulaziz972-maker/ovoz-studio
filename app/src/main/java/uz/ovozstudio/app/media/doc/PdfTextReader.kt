package uz.ovozstudio.app.media.doc

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * PDF'dan matn ajratadi.
 *
 * PDF — matn saqlanadigan format emas, **chop etish** formati: unda harflar
 * sahifadagi koordinatalar bilan beriladi. Shu sababli matn bu yerda
 * «o'qilmaydi», balki sahifa mazmunidagi ko'rsatmalardan **tiklanadi**:
 * `BT`/`ET` bloklari orasidagi `Tj`, `TJ`, `'` operatorlari matnni beradi,
 * `Td`, `TD`, `T*` esa yangi qatorni bildiradi.
 *
 * Uch qadam:
 *  1. Fayldagi obyektlar yig'iladi (`N 0 obj … endobj`), oqimlar ochiladi.
 *  2. **Sahifalar tartibi** hujjat daraxtidan olinadi — obyekt raqamlari
 *     bo'yicha emas. Bu jimgina buziladigan joy: raqamlar tartibi chop
 *     etish tartibi emas, shuning uchun boblar aralashib ketardi.
 *  3. Har bir sahifadagi matn tiklanadi.
 *
 * Shriftlar. PDF matnni bayt ko'rinishida saqlaydi, harfni esa shrift
 * aytadi. Oddiy shriftlarda (WinAnsi) bayt = belgi. Ko'p tilli
 * hujjatlarda **Type0** shrifti ishlatiladi va bayt kodi ixtiyoriy bo'ladi —
 * uni faqat shriftning `ToUnicode` jadvali ochadi. O'sha jadval o'qiladi;
 * jadval bo'lmasa matn «savatcha» bo'lib chiqardi, shuning uchun bunday
 * hujjat **ochiq xato** bilan to'xtatiladi: noto'g'ri o'qilgan kitob jim
 * qolgan xatodan yomonroq.
 */
object PdfTextReader {

    /**
     * O'qiladigan eng katta hajm. Fayl butunlay xotiraga olinadi: PDF
     * oqimlari fayl bo'ylab sochilgan, bo'laklab o'qish ularni topishni
     * ancha murakkablashtiradi.
     */
    const val MAX_BYTES = 32L * 1024 * 1024

    /** Sahifalar daraxti uchun chegara: buzuq faylda halqa bo'lmasin. */
    private const val MAX_PAGES = 20_000

    /** Bitta oqimdan chiqishi mumkin bo'lgan eng katta matn. */
    private const val MAX_STREAM_CHARS = 4 * 1024 * 1024

    /**
     * Bitta oqim ochilgandan keyingi eng katta hajm.
     *
     * Nega kerak: Flate siqishi ~1000 martagacha boradi, ya'ni ichki
     * hajmi 32 MB bo'lgan PDF (`MAX_BYTES`) ochilganda o'nlab gigabayt
     * bo'lishi mumkin («dekompressiya bombasi»). Chegarasiz ochish
     * `OutOfMemoryError` beradi — u `Exception` emas, shuning uchun
     * ilova yiqilardi. Oddiy sahifa mazmuni bir necha yuz kilobayt.
     */
    const val MAX_STREAM_BYTES = 32L * 1024 * 1024

    /**
     * Butun hujjat bo'yicha ochilgan oqimlarning yig'indi chegarasi.
     * Har bir oqim [MAX_STREAM_BYTES] dan kichik bo'lsa ham, ularning
     * minglabi birga bomba bo'la oladi.
     */
    const val MAX_INFLATED_TOTAL_BYTES = 256L * 1024 * 1024

    /** Ochilgan oqim uchun boshlang'ich sig'im: o'sishi kerak bo'lsa, o'zi o'sadi. */
    private const val INITIAL_STREAM_CAPACITY = 1L shl 20

    private val PDF_HEADER = "%PDF-".toByteArray(Charsets.US_ASCII)

    fun read(file: File): String {
        if (file.length() > MAX_BYTES) throw DocumentTooLargeException(MAX_BYTES)
        return read(file.readBytes())
    }

    /**
     * [maxInflatedBytes] — barcha oqimlar ochilgandan keyingi umumiy hajm
     * chegarasi ([MAX_INFLATED_TOTAL_BYTES]). Parametr sinov uchun: haqiqiy
     * chegarani tekshirish yuzlab megabayt ishlatardi.
     */
    fun read(bytes: ByteArray, maxInflatedBytes: Long = MAX_INFLATED_TOTAL_BYTES): String {
        if (!startsWith(bytes, PDF_HEADER)) {
            throw DocumentFormatException("Bu fayl PDF emas: sarlavhasi topilmadi")
        }
        val text = Parser(String(bytes, Charsets.ISO_8859_1), maxInflatedBytes).extractText()
        if (text.isBlank()) {
            throw DocumentTextMissingException(
                "PDF ichida matn topilmadi — u skaner qilingan rasm bo'lishi mumkin"
            )
        }
        return text
    }

    private fun startsWith(bytes: ByteArray, prefix: ByteArray): Boolean {
        if (bytes.size < prefix.size) return false
        for (i in prefix.indices) if (bytes[i] != prefix[i]) return false
        return true
    }

    /**
     * Shrift: bayt kodlarini belgilarga aylantiradi.
     *
     * @param composite Type0 — ko'p baytli kod. Bunday shriftda `ToUnicode`
     *   jadvali **shart**: usiz kodlarni ochish mumkin emas.
     * @param bytesPerCode bitta belgi uchun baytlar soni.
     */
    private class Font(
        val composite: Boolean,
        val bytesPerCode: Int,
        val cmap: Map<Int, String>?,
    )

    /** `N 0 obj … endobj` bo'lagi: lug'ati va (bo'lsa) oqim baytlari. */
    private class Obj(val number: Int, val body: String, val data: ByteArray?)

    /**
     * PDF o'quvchi: obyektlar, shriftlar, sahifalar va mazmun oqimlari.
     *
     * Holat sinf ichida saqlanadi, chunki obyektlar bir-biriga havola
     * qiladi (`/ToUnicode 12 0 R`) — har bir qadam oldingisiga tayanadi.
     */
    private class Parser(
        private val text: String,
        private val maxInflatedBytes: Long = MAX_INFLATED_TOTAL_BYTES,
    ) {

        private val objects = LinkedHashMap<Int, Obj>()
        private val fontNames = HashMap<String, Font>()

        /** Shu hujjat bo'yicha hozirgacha ochilgan baytlar (barcha oqimlar yig'indisi). */
        private var inflatedTotal = 0L

        fun extractText(): String {
            scanObjects()
            val fonts = collectFonts()
            val builder = StringBuilder()
            for (page in pageContents()) {
                val body = StringBuilder()
                for (number in page) {
                    val obj = objects[number] ?: continue
                    val data = streamOf(obj) ?: continue
                    // Shriftlar sahifaga bog'lanmagan: faylda nomlar bir xil
                    // bo'ladi (`/F1`), shuning uchun ularning birlashgan
                    // jadvali yetarli. Bu — soddalashtirish, lekin u faqat
                    // bir xil nomli ikki xil shriftli fayllarda seziladi.
                    val parser = ContentParser(fonts)
                    body.append(parser.parse(String(data, Charsets.ISO_8859_1)))
                }
                val pageText = tidy(body.toString())
                if (pageText.isEmpty()) continue
                // Sahifalar orasi ochiq ajratiladi: oxirgi qator keyingi
                // sahifaning birinchi qatoriga yopishib qolmasligi kerak.
                if (builder.isNotEmpty()) builder.append("\n\n")
                builder.append(pageText)
            }
            return builder.toString()
        }

        /** Obyektlarni yig'adi: sarlavhadan `endobj` gacha, oqimi bilan. */
        private fun scanObjects() {
            var i = 0
            var steps = 0
            while (steps++ < MAX_PAGES * 4) {
                val head = HEADER.find(text, i) ?: break
                val bodyStart = head.range.last + 1
                val end = text.indexOf("endobj", bodyStart)
                if (end < 0) break
                i = end + "endobj".length

                val number = head.groupValues[1].toIntOrNull() ?: continue
                val keyword = text.indexOf("stream", bodyStart)
                val dictEnd = if (keyword in bodyStart until end) keyword else end
                val body = text.substring(bodyStart, dictEnd)
                val data = if (dictEnd < end) streamAt(dictEnd, end) else null
                objects[number] = Obj(number, body, data)
            }
        }

        /**
         * Oqim baytlarini ajratadi (filtrini ochmaydi).
         *
         * Chegara `endstream` kalit so'zi bo'yicha topiladi, `/Length`
         * bo'yicha emas: ko'chirilgan fayllarda u ko'pincha noto'g'ri
         * bo'ladi, `endstream` esa har doim joyida.
         */
        private fun streamAt(streamKeyword: Int, limit: Int): ByteArray? {
            var start = streamKeyword + "stream".length
            if (start < text.length && text[start] == '\r') start++
            if (start < text.length && text[start] == '\n') start++
            val end = text.indexOf("endstream", start)
            if (end < 0 || end > limit) return null
            return text.substring(start, end).toByteArray(Charsets.ISO_8859_1)
        }

        /**
         * Obyektning oqimini ochib beradi.
         *
         * Filtr qo'llab-quvvatlanmasa `null`: buzuq baytlarni «matn» deb
         * qaytarishdan ko'ra oqimni tashlagan ma'qul.
         */
        private fun streamOf(obj: Obj): ByteArray? {
            val raw = obj.data ?: return null
            val filter = FILTER.find(obj.body) ?: return raw
            val names = if (filter.groupValues[1].isNotEmpty()) {
                filter.groupValues[1].split('/').map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                listOf(filter.groupValues[2])
            }
            return when (names.firstOrNull()?.lowercase()) {
                null, "" -> raw
                "flatedecode" -> inflate(raw)
                "asciihexdecode" -> unhex(raw)
                else -> null
            }
        }

        private fun inflate(raw: ByteArray): ByteArray? {
            val inflater = Inflater()
            return try {
                inflater.setInput(raw)
                // Boshlang'ich sig'im ataylab kichik: `raw.size * 4` kabi
                // taxmin 32 MB'lik oqim uchun darrov 128 MB ajratardi.
                val capacity = minOf(raw.size * 4L + 64L, INITIAL_STREAM_CAPACITY).toInt()
                val out = ByteArrayOutputStream(capacity)
                val buffer = ByteArray(16 * 1024)
                var streamTotal = 0L
                while (!inflater.finished()) {
                    val got = inflater.inflate(buffer)
                    if (got == 0) {
                        if (inflater.needsInput() || inflater.needsDictionary()) break
                    } else {
                        streamTotal += got
                        inflatedTotal += got
                        // Chegara yozishdan OLDIN tekshiriladi: bomba xotiraga
                        // tushib ulgurmaydi.
                        if (streamTotal > MAX_STREAM_BYTES) throw DocumentTooLargeException(MAX_STREAM_BYTES)
                        if (inflatedTotal > maxInflatedBytes) {
                            throw DocumentTooLargeException(maxInflatedBytes)
                        }
                        out.write(buffer, 0, got)
                    }
                }
                if (out.size() == 0) null else out.toByteArray()
            } catch (error: DataFormatException) {
                null
            } finally {
                // Xato bo'lganda ham bo'shatiladi: `Inflater` native xotira ushlaydi.
                inflater.end()
            }
        }

        /** `ASCIIHexDecode`: baytlar o'n oltilik yozuvda, oxirida `>`. */
        private fun unhex(raw: ByteArray): ByteArray? {
            val digits = StringBuilder()
            for (byte in raw) {
                val c = byte.toInt().toChar()
                if (c == '>') break
                if (c.isWhitespace()) continue
                if (c.digitToIntOrNull(16) == null) return null
                digits.append(c)
            }
            val out = ByteArrayOutputStream(digits.length / 2 + 1)
            var i = 0
            while (i + 1 < digits.length) {
                out.write(digits.substring(i, i + 2).toIntOrNull(16) ?: return null)
                i += 2
            }
            if (i < digits.length) out.write((digits[i].digitToIntOrNull(16) ?: 0) shl 4)
            return out.toByteArray()
        }

        /**
         * Shriftlarni yig'adi va nomlarini bog'laydi.
         *
         * Nom bo'yicha bog'lash shart: mazmun oqimida shrift `/F1`
         * ko'rinishida — sahifa resurslaridagi nom bilan — tilga olinadi,
         * obyekt raqami esa u yerda ko'rinmaydi.
         */
        private fun collectFonts(): Map<String, Font> {
            // Obyekt raqami bo'yicha: nomlar keyingi qadamda shunga bog'lanadi.
            val fonts = HashMap<Int, Font>()
            for (obj in objects.values) {
                if (!BASE_FONT.containsMatchIn(obj.body)) continue
                val composite = TYPE0.containsMatchIn(obj.body)
                val reference = TO_UNICODE.find(obj.body)?.groupValues?.get(1)?.toIntOrNull()
                val cmap = reference?.let { number ->
                    objects[number]?.let { target ->
                        streamOf(target)?.let { bytes -> CMap.parse(String(bytes, Charsets.ISO_8859_1)) }
                    }
                }
                if (composite && cmap?.map.isNullOrEmpty()) {
                    throw DocumentFormatException(
                        "PDF dagi shrift (Type0) uchun ToUnicode jadvali yo'q — matnni o'qib bo'lmaydi"
                    )
                }
                // Kod kengligi Type0 bo'lsa ham har doim 2 emas: ToUnicode
                // jadvalidagi `bfchar` yozuvi bir baytli kod berishi mumkin
                // (`<00> <0410>`). Kenglik jadvalning o'zidan olinadi —
                // aks holda butun matn ikki barobar surilib ketardi.
                val width = cmap?.width ?: if (composite) 2 else 1
                fonts[obj.number] = Font(
                    composite = composite,
                    bytesPerCode = width,
                    cmap = cmap?.map,
                )
            }

            val byName = HashMap<String, Font>()
            for (match in FONT_RESOURCES.findAll(text)) {
                for (pair in FONT_PAIR.findAll(match.groupValues[1])) {
                    val number = pair.groupValues[2].toIntOrNull() ?: continue
                    fonts[number]?.let { byName[pair.groupValues[1]] = it }
                }
            }
            return byName
        }

        /**
         * Sahifalar mazmuni — hujjat tartibida.
         *
         * Daraxt topilmasa zaxira yo'l bor: fayldagi barcha mazmun
         * oqimlari obyekt tartibida olinadi. Bu tartib **noto'g'ri**
         * bo'lishi mumkin, lekin daraxtsiz fayllarda boshqa manba yo'q.
         */
        private fun pageContents(): List<List<Int>> {
            val catalog = objects.values.firstOrNull { CATALOG.containsMatchIn(it.body) }
            val root = catalog?.let { PAGE_TREE.find(it.body)?.groupValues?.get(1)?.toIntOrNull() }
            val pages = ArrayList<List<Int>>()
            if (root != null) collectPages(root, HashSet(), pages, 0)
            if (pages.isNotEmpty()) return pages

            val fallback = ArrayList<List<Int>>()
            for (obj in objects.values) {
                val contents = CONTENTS.find(obj.body)?.groupValues?.get(1) ?: continue
                val numbers = referenceList(contents)
                if (numbers.isNotEmpty()) fallback += numbers
            }
            return fallback
        }

        /** Daraxtni rekursiv yuradi: `/Kids` ichida yana `/Pages` bo'lishi mumkin. */
        private fun collectPages(
            number: Int,
            seen: MutableSet<Int>,
            out: MutableList<List<Int>>,
            depth: Int,
        ) {
            if (depth > 64 || out.size >= MAX_PAGES) return
            if (!seen.add(number)) return
            val obj = objects[number] ?: return

            val kids = KIDS.find(obj.body)?.groupValues?.get(1)
            if (kids != null) {
                for (kid in referenceList(kids)) collectPages(kid, seen, out, depth + 1)
                return
            }
            if (!PAGE_TYPE.containsMatchIn(obj.body)) return
            val contents = CONTENTS.find(obj.body)?.groupValues?.get(1) ?: return
            referenceList(contents).let { if (it.isNotEmpty()) out += it }
        }

        /** `12 0 R` va `[3 0 R 4 0 R]` ko'rinishidagi havolalar ro'yxati. */
        private fun referenceList(value: String): List<Int> =
            REFERENCE.findAll(value).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()

        /** Ortiqcha bo'sh joy va ketma-ket bo'sh qatorlarni yo'q qiladi. */
        private fun tidy(raw: String): String {
            val lines = ArrayList<String>()
            for (line in raw.split('\n')) {
                val trimmed = SPACES.replace(line, " ").trim()
                if (trimmed.isNotEmpty()) {
                    lines += trimmed
                } else if (lines.isNotEmpty() && lines.last().isNotEmpty()) {
                    lines += ""
                }
            }
            while (lines.isNotEmpty() && lines.last().isEmpty()) lines.removeAt(lines.size - 1)
            return lines.joinToString("\n")
        }

        private companion object {
            val HEADER = Regex("(?m)^\\s*(\\d+)\\s+\\d+\\s+obj")
            val FILTER = Regex("/Filter\\s*(?:\\[([^\\]]*)\\]|/(\\w+))")
            val BASE_FONT = Regex("/BaseFont\\s*/")
            val TYPE0 = Regex("/Subtype\\s*/Type0")
            val TO_UNICODE = Regex("/ToUnicode\\s+(\\d+)\\s+\\d+\\s+R")
            val CATALOG = Regex("/Type\\s*/Catalog")
            val PAGE_TREE = Regex("/Pages\\s+(\\d+)\\s+\\d+\\s+R")
            val KIDS = Regex("/Kids\\s*\\[([^\\]]*)\\]")
            val PAGE_TYPE = Regex("/Type\\s*/Page(?![a-zA-Z])")
            val CONTENTS = Regex("/Contents\\s*(\\[[^\\]]*\\]|\\d+\\s+\\d+\\s+R)")
            val REFERENCE = Regex("(\\d+)\\s+\\d+\\s+R")
            val FONT_RESOURCES = Regex("/Font\\s*<<([^>]*)>>")
            val FONT_PAIR = Regex("/(\\w+)\\s+(\\d+)\\s+\\d+\\s+R")
            val SPACES = Regex("[ \\t\\u0000]+")
        }
    }

    /**
     * Mazmun oqimini o'qish: matn operatorlarini ajratib oladi.
     *
     * Operandlar uyumi emas, ro'yxat saqlanadi: matn operatorlariga kerak
     * bo'lgan yagona operand — oxirgi satr yoki massiv, qolganlari
     * (masalan `Tf` ning o'lchami) shunchaki tashlab yuboriladi.
     */
    private class ContentParser(private val fonts: Map<String, Font>) {

        private val out = StringBuilder()
        private val operands = ArrayList<Any>()
        private var currentFont: Font? = null
        private var currentName: String? = null
        private var sawAnyText = false
        private var pendingBreak = false

        fun parse(content: String): String {
            out.setLength(0)
            operands.clear()
            sawAnyText = false
            pendingBreak = false
            currentFont = null
            currentName = null

            var i = 0
            while (i < content.length && out.length < MAX_STREAM_CHARS) {
                val c = content[i]
                i = when {
                    c == '%' -> {
                        val end = content.indexOf('\n', i)
                        if (end < 0) content.length else end + 1
                    }

                    c == '(' -> string(content, i)
                    c == '<' -> if (i + 1 < content.length && content[i + 1] == '<') i + 2
                    else hexString(content, i)

                    c == '[' -> array(content, i)
                    c == '/' -> name(content, i)
                    c == ']' -> i + 1
                    c.isWhitespace() -> i + 1
                    c.isDigit() || c == '-' || c == '+' || c == '.' -> number(content, i)
                    else -> word(content, i)
                }
            }
            return if (sawAnyText) out.toString() else ""
        }

        /** Operator bajariladi va operandlar tozalanadi. */
        private fun operator(word: String, content: String, next: Int): Int {
            when (word) {
                "Tj", "'" -> {
                    lastString()?.let { text(it) }
                    if (word == "'") breakLine()
                }

                "\"" -> {
                    lastString()?.let { text(it) }
                    breakLine()
                }

                "TJ" -> {
                    val items = operands.lastOrNull { it is ArrayList<*> } as? ArrayList<*> ?: return clear(next)
                    for (item in items) {
                        when (item) {
                            is IntArray -> text(item)
                            // Katta manfiy son — so'zlar orasidagi bo'shliq
                            // (mingdan bir emda). PDF'da bo'shliq ko'pincha
                            // shunday beriladi, shuning uchun u saqlanadi.
                            is Double -> if (item <= -120) space()
                            else -> Unit
                        }
                    }
                }

                "Td", "TD", "T*", "ET" -> breakLine()

                "Tf" -> {
                    currentName = (operands.lastOrNull { it is Name } as? Name)?.value
                    currentFont = currentName?.let { fonts[it] }
                    // Shrift nomi tanildi, lekin shriftning o'zi topilmadi —
                    // matnni ochish uchun hech narsa yo'q. Oddiy shriftlarda
                    // bayt = belgi, shuning uchun bu holda Lotin-1 ishlaydi.
                    if (currentFont == null && currentName != null) {
                        currentFont = PLAIN_FONT
                    }
                }

                // Ichki rasm: `BI … ID <baytlar> EI`. Baytlar ichida qavs
                // bo'lishi mumkin, shuning uchun tokenlar emas, kalit so'z
                // bo'yicha o'tib ketiladi.
                "BI" -> return skipInlineImage(content, next)
            }
            return clear(next)
        }

        private fun skipInlineImage(content: String, from: Int): Int {
            var i = from
            while (i < content.length - 1) {
                if (content[i] == 'E' && content[i + 1] == 'I' &&
                    (i == 0 || content[i - 1].isWhitespace())
                ) {
                    return i + 2
                }
                i++
            }
            return content.length
        }

        private fun clear(next: Int): Int {
            operands.clear()
            return next
        }

        private fun lastString(): IntArray? = operands.lastOrNull { it is IntArray } as? IntArray

        private fun text(codes: IntArray) {
            val decoded = decode(codes, currentFont)
            if (decoded.isEmpty()) return
            if (pendingBreak && out.isNotEmpty()) out.append('\n')
            pendingBreak = false
            out.append(decoded)
            sawAnyText = true
        }

        private fun space() {
            if (out.isNotEmpty() && out.last() != ' ' && out.last() != '\n') out.append(' ')
        }

        private fun breakLine() {
            pendingBreak = true
        }

        /** Kodlarni belgilarga aylantiradi: avval `ToUnicode`, keyin Lotin-1. */
        private fun decode(codes: IntArray, font: Font?): String {
            val cmap = font?.cmap
            val width = font?.bytesPerCode ?: 1
            if (width > 1) {
                val builder = StringBuilder()
                var i = 0
                while (i + width <= codes.size) {
                    var code = 0
                    for (b in 0 until width) code = (code shl 8) or (codes[i + b] and 0xFF)
                    val mapped = cmap?.get(code)
                    if (mapped != null) {
                        builder.append(mapped)
                    } else if (width == 2) {
                        // Jadvalda yo'q — kod UTF-16 birligi sifatida o'qiladi
                        // (aks holda belgi butunlay yo'qolardi).
                        builder.append(code.toChar())
                    }
                    i += width
                }
                return builder.toString()
            }
            val builder = StringBuilder(codes.size)
            for (code in codes) {
                val mapped = cmap?.get(code)
                if (mapped != null) builder.append(mapped) else builder.append(code.toChar())
            }
            return builder.toString()
        }

        // --- tokenlar ---

        /** `(matn)` — qavslar ichma-ich bo'lishi va `\` bilan qochirilishi mumkin. */
        private fun string(content: String, from: Int): Int {
            val codes = ByteArrayOutputStream()
            var depth = 1
            var i = from + 1
            while (i < content.length) {
                val c = content[i]
                when {
                    c == '\\' -> {
                        val escaped = escape(content, i + 1)
                        escaped.first.forEach { codes.write(it) }
                        i = escaped.second
                    }

                    c == '(' -> {
                        depth++
                        codes.write('('.code)
                        i++
                    }

                    c == ')' -> {
                        depth--
                        if (depth == 0) return push(codes, i + 1)
                        codes.write(')'.code)
                        i++
                    }

                    else -> {
                        codes.write(c.code and 0xFF)
                        i++
                    }
                }
            }
            return push(codes, content.length)
        }

        private fun push(codes: ByteArrayOutputStream, next: Int): Int {
            operands += codes.toByteArray().map { it.toInt() and 0xFF }.toIntArray()
            return next
        }

        /** Qochirilgan belgi: `\n`, `\t`, `\(`, `\\`, sakkizlik `\052`. */
        private fun escape(content: String, from: Int): Pair<IntArray, Int> {
            if (from >= content.length) return Pair(IntArray(0), content.length)
            val c = content[from]
            return when {
                c == 'n' -> Pair(intArrayOf(0x0A), from + 1)
                c == 'r' -> Pair(intArrayOf(0x0D), from + 1)
                c == 't' -> Pair(intArrayOf(0x09), from + 1)
                c == 'b' -> Pair(intArrayOf(0x08), from + 1)
                c == 'f' -> Pair(intArrayOf(0x0C), from + 1)
                // Satr oxiridagi `\` — satr davomi, belgi qo'shilmaydi.
                c == '\n' -> Pair(IntArray(0), from + 1)
                c == '\r' -> Pair(
                    IntArray(0),
                    if (from + 1 < content.length && content[from + 1] == '\n') from + 2 else from + 1,
                )

                c in '0'..'7' -> {
                    var value = 0
                    var i = from
                    var digits = 0
                    while (i < content.length && digits < 3 && content[i] in '0'..'7') {
                        value = value * 8 + (content[i] - '0')
                        i++
                        digits++
                    }
                    Pair(intArrayOf(value and 0xFF), i)
                }

                else -> Pair(intArrayOf(c.code and 0xFF), from + 1)
            }
        }

        /** `<41 42>` — o'n oltilik satr. Toq sonli raqam nol bilan to'ldiriladi. */
        private fun hexString(content: String, from: Int): Int {
            val builder = StringBuilder()
            var i = from + 1
            while (i < content.length && content[i] != '>') {
                if (content[i].digitToIntOrNull(16) != null) builder.append(content[i])
                i++
            }
            if (builder.length % 2 == 1) builder.append('0')
            val codes = IntArray(builder.length / 2) { index ->
                builder.substring(index * 2, index * 2 + 2).toIntOrNull(16) ?: 0
            }
            operands += codes
            return if (i < content.length) i + 1 else content.length
        }

        /** `[ (bir) -120 (ikki) ]` — `TJ` massivi. */
        private fun array(content: String, from: Int): Int {
            val items = ArrayList<Any>()
            var i = from + 1
            while (i < content.length && content[i] != ']') {
                val c = content[i]
                val before = operands.size
                i = when {
                    c == '(' -> string(content, i)
                    c == '<' -> hexString(content, i)
                    c.isDigit() || c == '-' || c == '+' || c == '.' -> number(content, i)
                    else -> i + 1
                }
                if (operands.size > before) items += operands.removeAt(operands.size - 1)
            }
            // Massiv bitta operand bo'lib qoladi: `TJ` uni shu holida oladi.
            operands.add(items)
            return if (i < content.length) i + 1 else content.length
        }

        private fun name(content: String, from: Int): Int {
            var i = from + 1
            val builder = StringBuilder()
            while (i < content.length && !content[i].isWhitespace() && content[i] !in DELIMITERS) {
                builder.append(content[i])
                i++
            }
            operands += Name(builder.toString())
            return i
        }

        private fun number(content: String, from: Int): Int {
            var i = from
            val builder = StringBuilder()
            while (i < content.length && (content[i].isDigit() || content[i] in "+-.eE")) {
                builder.append(content[i])
                i++
            }
            operands += builder.toString().toDoubleOrNull() ?: 0.0
            return i
        }

        /** Operator yoki tanilmagan kalit so'z. */
        private fun word(content: String, from: Int): Int {
            var i = from
            val builder = StringBuilder()
            while (i < content.length && !content[i].isWhitespace() && content[i] !in DELIMITERS) {
                builder.append(content[i])
                i++
            }
            if (builder.isEmpty()) return i + 1
            return operator(builder.toString(), content, i)
        }

        private companion object {
            const val DELIMITERS = "()<>[]{}/%"

            /** Shrift topilmaganda ishlatiladigan «oddiy» shrift: bayt = belgi. */
            val PLAIN_FONT = Font(composite = false, bytesPerCode = 1, cmap = null)
        }
    }

    private class Name(val value: String)

    /** `ToUnicode` jadvali: kodlar → belgilar va kodning kengligi. */
    private class CodeMap(val map: Map<Int, String>, val width: Int)

    /**
     * `ToUnicode` jadvalini o'qish.
     *
     * Jadval `bfchar` (bittalab) va `bfrange` (oraliq) bo'limlaridan
     * iborat. Kodning kengligi **yozuvning o'zidan** olinadi: `<0041>`
     * ikki baytli, `<41>` bir baytli. Bu muhim — aks holda ko'p baytli
     * shrift bir bayt deb o'qilib, butun matn surilib ketardi.
     */
    private object CMap {

        fun parse(text: String): CodeMap? {
            val map = HashMap<Int, String>()
            var width = 0

            var i = 0
            while (i < text.length) {
                val start = text.indexOf("beginbf", i)
                if (start < 0) break
                val end = text.indexOf("endbf", start)
                if (end < 0) break
                val section = text.substring(start, end)
                i = end

                if (section.startsWith("beginbfchar")) {
                    val hexes = HEX.findAll(section).map { it.groupValues[1] }.toList()
                    var n = 0
                    while (n + 1 < hexes.size) {
                        val code = hexes[n].toIntOrNull(16) ?: break
                        if (width == 0) width = hexes[n].length / 2
                        map[code] = decodeUtf16(hexes[n + 1])
                        n += 2
                    }
                } else {
                    val rangeWidth = parseRange(section, map)
                    if (width == 0) width = rangeWidth
                }
            }
            if (map.isEmpty()) return null
            return CodeMap(map, if (width <= 0) 1 else width)
        }

        /**
         * `bfrange`: `<lo> <hi> <dst>` yoki `<lo> <hi> [<d1> <d2> …]`.
         *
         * Oraliqda oxirgi kod birligi oshiriladi — surrogat juftliklarda
         * ham shu to'g'ri: o'sha juftlikning kichik qismi oshadi.
         */
        private fun parseRange(section: String, map: HashMap<Int, String>): Int {
            var width = 0
            for (line in section.lines()) {
                val trimmed = line.trim()
                if (!trimmed.startsWith("<")) continue
                val hexes = HEX.findAll(trimmed).map { it.groupValues[1] }.toList()
                if (hexes.size < 3) continue
                val low = hexes[0].toIntOrNull(16) ?: continue
                val high = hexes[1].toIntOrNull(16) ?: continue
                if (width == 0) width = hexes[0].length / 2
                if (high < low || high - low > 65_536) continue

                if (trimmed.contains('[')) {
                    val targets = hexes.drop(2)
                    for (n in 0..(high - low)) {
                        val target = targets.getOrNull(n) ?: break
                        map[low + n] = decodeUtf16(target)
                    }
                } else {
                    val units = decodeUtf16Units(hexes[2])
                    if (units.isEmpty()) continue
                    for (n in 0..(high - low)) map[low + n] = encodeUtf16(advance(units, n))
                }
            }
            return width
        }

        private fun decodeUtf16(hex: String): String = encodeUtf16(decodeUtf16Units(hex))

        private fun decodeUtf16Units(hex: String): IntArray {
            val padded =
                if (hex.length % 4 == 0) hex else hex.padEnd(hex.length + (4 - hex.length % 4), '0')
            return IntArray(padded.length / 4) { index ->
                padded.substring(index * 4, index * 4 + 4).toIntOrNull(16) ?: 0
            }
        }

        private fun encodeUtf16(units: IntArray): String =
            String(units.map { it.toChar() }.toCharArray())

        private fun advance(units: IntArray, by: Int): IntArray {
            val copy = units.copyOf()
            if (copy.isNotEmpty()) copy[copy.size - 1] += by
            return copy
        }

        private val HEX = Regex("<([0-9A-Fa-f]+)>")
    }
}
