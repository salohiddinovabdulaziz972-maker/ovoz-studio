package uz.ovozstudio.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import uz.ovozstudio.app.ui.home.HomeScreen
import uz.ovozstudio.app.ui.log.LogScreen
import uz.ovozstudio.app.ui.merge.MergeScreen
import uz.ovozstudio.app.ui.pdf.PdfMode
import uz.ovozstudio.app.ui.pdf.PdfScreen
import uz.ovozstudio.app.ui.trim.TrimMode
import uz.ovozstudio.app.ui.trim.TrimScreen

/**
 * Ilovadagi ekranlar. Har biri alohida, fayl talab qilmaydi: fayl ekranning
 * o'zida tizim tanlagichi orqali olinadi.
 */
object Routes {
    const val HOME = "home"
    const val AUDIO_CUT = "audio_cut"
    const val AUDIO_DELETE = "audio_delete"
    const val AUDIO_MERGE = "audio_merge"
    const val PDF_CUT = "pdf_cut"
    const val PDF_DELETE = "pdf_delete"
    const val LOG = "log"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    // `popBackStack()` `false` qaytaradi, agar orqaga qaytadigan joy
    // qolmagan bo'lsa (masalan konfiguratsiya o'zgarishidan keyin
    // yo'naltiruvchi qayta qurilgan). O'sha holatda tizimning «orqaga»
    // tugmasi ilovani yopadi — tugma esa jimgina hech narsa qilmasdan
    // qolardi. Shuning uchun zaxira: stek bo'sh bo'lsa — chiqamiz.
    val back: () -> Unit = {
        if (!navController.popBackStack()) navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = true }
            launchSingleTop = true
        }
    }

    // Har bir ekran o'z ViewModel'iga ega, u ekran yopilganda tozalanadi:
    // ochiq audio va vaqtinchalik fayllar shu bilan birga ketadi.
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onAudioCut = { navController.navigate(Routes.AUDIO_CUT) },
                onAudioDelete = { navController.navigate(Routes.AUDIO_DELETE) },
                onAudioMerge = { navController.navigate(Routes.AUDIO_MERGE) },
                onPdfCut = { navController.navigate(Routes.PDF_CUT) },
                onPdfDelete = { navController.navigate(Routes.PDF_DELETE) },
                onLog = { navController.navigate(Routes.LOG) },
            )
        }

        composable(Routes.AUDIO_CUT) {
            TrimScreen(mode = TrimMode.CUT, onBack = back)
        }

        composable(Routes.AUDIO_DELETE) {
            TrimScreen(mode = TrimMode.DELETE, onBack = back)
        }

        composable(Routes.AUDIO_MERGE) {
            MergeScreen(onBack = back)
        }

        composable(Routes.PDF_CUT) {
            PdfScreen(mode = PdfMode.CUT, onBack = back)
        }

        composable(Routes.PDF_DELETE) {
            PdfScreen(mode = PdfMode.DELETE, onBack = back)
        }

        composable(Routes.LOG) {
            LogScreen(onBack = back)
        }
    }
}
