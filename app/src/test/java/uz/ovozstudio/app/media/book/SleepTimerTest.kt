package uz.ovozstudio.app.media.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {

    @Test
    fun `boshlanganda vaqt sanala boshlaydi`() {
        val timer = SleepTimer()
        assertEquals(SleepState.IDLE, timer.state)

        timer.start(60)

        assertEquals(SleepState.RUNNING, timer.state)
        assertEquals(60L, timer.remainingSeconds)
        assertFalse(timer.isExpired)
    }

    @Test
    fun `vaqt tugaganda tugadi deb belgilanadi`() {
        val timer = SleepTimer()
        timer.start(10)

        assertFalse("hali tugamadi", timer.elapse(6))
        assertEquals(4L, timer.remainingSeconds)

        assertTrue("tugadi", timer.elapse(4))
        assertEquals(SleepState.EXPIRED, timer.state)
        assertEquals(0L, timer.remainingSeconds)
    }

    @Test
    fun `ortiqcha vaqt manfiy songa olib kelmaydi`() {
        val timer = SleepTimer()
        timer.start(5)

        timer.elapse(100)

        assertEquals(0L, timer.remainingSeconds)
        assertTrue(timer.isExpired)
    }

    @Test
    fun `pauzada vaqt sanalmaydi`() {
        val timer = SleepTimer()
        timer.start(60)
        timer.elapse(10)
        timer.pause()

        timer.elapse(30)

        assertEquals(SleepState.PAUSED, timer.state)
        assertEquals(50L, timer.remainingSeconds)
    }

    @Test
    fun `pauzadan keyin davom etadi`() {
        val timer = SleepTimer()
        timer.start(60)
        timer.pause()
        timer.resume()

        assertTrue(timer.isRunning)
        timer.elapse(10)
        assertEquals(50L, timer.remainingSeconds)
    }

    @Test
    fun `bekor qilinsa vaqt nolga qaytadi`() {
        val timer = SleepTimer()
        timer.start(60)
        timer.elapse(10)

        timer.cancel()

        assertEquals(SleepState.IDLE, timer.state)
        assertEquals(0L, timer.remainingSeconds)
        assertFalse(timer.isExpired)
    }

    @Test
    fun `bekor qilingan taymer vaqtni sanamaydi`() {
        val timer = SleepTimer()
        timer.start(60)
        timer.cancel()
        timer.elapse(5)

        assertEquals(SleepState.IDLE, timer.state)
        assertEquals(0L, timer.remainingSeconds)
    }

    @Test
    fun `tugagan taymer qayta tiklanmaydi`() {
        // Tugagan taymerni «pauza» yoki «davom» orqali qayta ishga tushirish
        // jim xato bo'lardi: taymer o'chgan, kitob esa o'qilaverardi.
        val timer = SleepTimer()
        timer.start(1)
        timer.elapse(1)
        timer.pause()
        timer.resume()

        assertEquals(SleepState.EXPIRED, timer.state)
        assertFalse(timer.isRunning)
    }

    @Test
    fun `nol soniya darhol tugaydi`() {
        val timer = SleepTimer()
        timer.start(0)

        assertEquals(SleepState.EXPIRED, timer.state)
        assertEquals(0L, timer.remainingSeconds)
    }

    @Test
    fun `manfiy vaqt ham darhol tugaydi`() {
        val timer = SleepTimer()
        timer.start(-5)

        assertTrue(timer.isExpired)
    }

    @Test
    fun `bob oxirigacha rejimida toxtash chegarani kutadi`() {
        val timer = SleepTimer()
        timer.start(1, stopAtChapterEnd = true)
        timer.elapse(1)

        assertFalse("bob o'rtasida to'xtatilmasligi kerak", timer.shouldStop(atChapterEnd = false))
        assertTrue("bob tugaganda to'xtaydi", timer.shouldStop(atChapterEnd = true))
    }

    @Test
    fun `oddiy rejimda darhol toxtaydi`() {
        val timer = SleepTimer()
        timer.start(1)
        timer.elapse(1)

        assertTrue(timer.shouldStop(atChapterEnd = false))
    }

    @Test
    fun `vaqt tugamagan bolsa toxtatilmaydi`() {
        val timer = SleepTimer()
        timer.start(60)
        timer.elapse(10)

        assertFalse(timer.shouldStop(atChapterEnd = true))
        assertFalse(timer.shouldStop(atChapterEnd = false))
    }

    @Test
    fun `vaqtni qayta boshlash eski taymerni almashtiradi`() {
        val timer = SleepTimer()
        timer.start(60)
        timer.elapse(30)
        timer.start(120)

        assertEquals(120L, timer.remainingSeconds)
        assertFalse(timer.isExpired)
    }
}
