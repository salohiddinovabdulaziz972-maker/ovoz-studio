package uz.ovozstudio.app.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import uz.ovozstudio.app.ui.convert.ConvertScreen
import uz.ovozstudio.app.ui.eq.EqScreen
import uz.ovozstudio.app.ui.home.HomeScreen
import uz.ovozstudio.app.ui.record.RecordScreen
import uz.ovozstudio.app.ui.trim.TrimScreen

object Routes {
    const val HOME = "home"
    const val RECORD = "record"
    const val TRIM = "trim?path={path}"

    /**
     * Konvertor ekrani. Yo'l ixtiyoriy: ilova ichidagi fayl uchun u beriladi,
     * boshqa hollarda fayl tizim tanlagichi orqali olinadi.
     */
    const val CONVERT = "convert?path={path}"

    /**
     * Ekvalayzer ekrani. Yo'l ixtiyoriy: ilova ichidagi fayl uchun beriladi,
     * aks holda fayl tizim tanlagichi orqali olinadi.
     */
    const val EQ = "eq?path={path}"

    fun trim(path: String): String = "trim?path=${Uri.encode(path)}"

    fun convert(path: String): String = "convert?path=${Uri.encode(path)}"

    fun eq(path: String): String = "eq?path=${Uri.encode(path)}"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onRecord = { navController.navigate(Routes.RECORD) },
                onOpenFile = { path -> navController.navigate(Routes.trim(path)) },
                onConvertFile = { path -> navController.navigate(Routes.convert(path)) },
                onConvert = { navController.navigate(Routes.convert("")) },
                onEqFile = { path -> navController.navigate(Routes.eq(path)) },
                onEq = { navController.navigate(Routes.eq("")) },
            )
        }

        composable(Routes.RECORD) {
            RecordScreen(
                onBack = { navController.popBackStack() },
                onOpenSaved = { path ->
                    navController.navigate(Routes.trim(path)) {
                        // Yozish ekrani tarixda qolmasin — orqaga bosilganda
                        // foydalanuvchi to'g'ridan-to'g'ri bosh ekranga qaytadi.
                        popUpTo(Routes.RECORD) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.TRIM,
            arguments = listOf(navArgument("path") { type = NavType.StringType }),
        ) { entry ->
            val path = entry.arguments?.getString("path").orEmpty()
            TrimScreen(
                filePath = Uri.decode(path),
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.CONVERT,
            arguments = listOf(navArgument("path") { type = NavType.StringType }),
        ) { entry ->
            val path = entry.arguments?.getString("path").orEmpty()
            ConvertScreen(
                initialPath = Uri.decode(path),
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.EQ,
            arguments = listOf(navArgument("path") { type = NavType.StringType }),
        ) { entry ->
            val path = entry.arguments?.getString("path").orEmpty()
            EqScreen(
                initialPath = Uri.decode(path),
                onBack = { navController.popBackStack() },
                // Natija kutubxonaga tushadi; uni darhol kesish ekranida
                // ochish mumkin — foydalanuvchi ro'yxatdan qidirmasin.
                onOpenSaved = { saved -> navController.navigate(Routes.trim(saved)) },
            )
        }
    }
}
