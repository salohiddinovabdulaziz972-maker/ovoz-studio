package uz.ovozstudio.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import uz.ovozstudio.app.R
import uz.ovozstudio.app.ui.home.HomeScreen
import uz.ovozstudio.app.ui.log.LogScreen
import uz.ovozstudio.app.ui.merge.MergeScreen
import uz.ovozstudio.app.ui.pdf.PdfMergeScreen
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
    const val PDF_MERGE = "pdf_merge"
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

    // Ekran almashganda uning sarlavhasi ovoz bilan aytiladi: ko'ruvchi
    // foydalanuvchi qaysi ekranga tushganini bir qarashda biladi, eshitish
    // bilan esa yangi sarlavhani topib yurguncha qayerdaligini bilmaydi.
    val view = LocalView.current
    val homeTitle = stringResource(R.string.home_title)
    val cutTitle = stringResource(R.string.trim_title_cut)
    val deleteTitle = stringResource(R.string.trim_title_delete)
    val mergeTitle = stringResource(R.string.merge_title)
    val pdfCutTitle = stringResource(R.string.pdf_title_cut)
    val pdfDeleteTitle = stringResource(R.string.pdf_title_delete)
    val pdfMergeTitle = stringResource(R.string.pdf_merge_title)
    val logTitle = stringResource(R.string.log_title)
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        val title = when (currentRoute) {
            Routes.HOME -> homeTitle
            Routes.AUDIO_CUT -> cutTitle
            Routes.AUDIO_DELETE -> deleteTitle
            Routes.AUDIO_MERGE -> mergeTitle
            Routes.PDF_CUT -> pdfCutTitle
            Routes.PDF_DELETE -> pdfDeleteTitle
            Routes.PDF_MERGE -> pdfMergeTitle
            Routes.LOG -> logTitle
            else -> null
        }
        if (title != null) view.announceForAccessibility(title)
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
                onPdfMerge = { navController.navigate(Routes.PDF_MERGE) },
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

        composable(Routes.PDF_MERGE) {
            PdfMergeScreen(onBack = back)
        }

        composable(Routes.LOG) {
            LogScreen(onBack = back)
        }
    }
}
