package uz.ovozstudio.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import uz.ovozstudio.app.settings.AppLanguage

/**
 * «Hozir ishlatilayotgan til» satrining hisobi.
 *
 * Satr foydalanuvchiga aniq javob beradi: «Tizim tili» tanlangan bo'lsa ham,
 * u qaysi tilda yozilganini ko'radi. Hisob xato bo'lsa, ekranda bir til
 * yozilib, ilova boshqa tilda ochilardi — ya'ni satr ishonchni yo'qotardi.
 */
class SettingsUiStateTest {

    @Test
    fun `tizim tanlovida qurilma tili korsatiladi`() {
        val state = SettingsUiState(
            language = AppLanguage.SYSTEM,
            systemLanguage = AppLanguage.UZ_CYRL,
        )

        assertEquals(AppLanguage.UZ_CYRL, state.activeLanguage)
    }

    @Test
    fun `aniq tanlov qurilma tilidan ustun`() {
        val state = SettingsUiState(
            language = AppLanguage.RU,
            systemLanguage = AppLanguage.UZ_LATN,
        )

        assertEquals(AppLanguage.RU, state.activeLanguage)
    }

    @Test
    fun `standart holat ozbek lotin tilini korsatadi`() {
        // `values/` — o'zbekcha (lotin), ya'ni hech narsa tanlanmagan va
        // qurilma tili noma'lum bo'lgan holatda ham ilova shu tilda ochiladi.
        assertEquals(AppLanguage.SYSTEM, SettingsUiState().language)
        assertEquals(AppLanguage.UZ_LATN, SettingsUiState().activeLanguage)
    }

    @Test
    fun `xato va versiya holatga tasir qilmaydi`() {
        val state = SettingsUiState(
            language = AppLanguage.EN,
            systemLanguage = AppLanguage.RU,
            simplified = true,
            version = "1.2.3",
            error = SettingsError.SAVE_FAILED,
        )

        assertEquals(AppLanguage.EN, state.activeLanguage)
        assertEquals(SettingsError.SAVE_FAILED, state.error)
        assertEquals("1.2.3", state.version)
    }
}
