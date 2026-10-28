package com.fieldpin.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fieldpin.data.repo.AuthRepository
import com.fieldpin.ui.camera.CameraScreen
import com.fieldpin.ui.detail.SiteDetailScreen
import com.fieldpin.ui.draw.DrawSiteScreen
import com.fieldpin.ui.inspection.InspectionScreen
import com.fieldpin.ui.login.LoginScreen
import com.fieldpin.ui.main.MainScreen
import org.koin.compose.koinInject

object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val DRAW = "draw"
    const val SITE = "site/{id}"
    const val INSPECTION = "inspection/{id}"
    const val CAMERA = "camera/{id}"

    fun site(id: String) = "site/$id"
    fun inspection(id: String) = "inspection/$id"
    fun camera(id: String) = "camera/$id"
}

@Composable
fun AppNav() {
    val auth: AuthRepository = koinInject()
    val session by auth.session.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val start = remember { if (auth.session.value != null) Routes.MAIN else Routes.LOGIN }
    val idArg = listOf(navArgument("id") { type = NavType.StringType })

    // The session is the single source of truth: signing in/out moves the whole back stack.
    LaunchedEffect(session != null) {
        val route = navController.currentBackStackEntry?.destination?.route
        if (session != null && route == Routes.LOGIN) {
            navController.navigate(Routes.MAIN) { popUpTo(Routes.LOGIN) { inclusive = true } }
        } else if (session == null && route != null && route != Routes.LOGIN) {
            navController.navigate(Routes.LOGIN) { popUpTo(navController.graph.id) { inclusive = true } }
        }
    }

    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = { slideInHorizontally(initialOffsetX = { it / 6 }) + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it / 6 }) + fadeOut() },
    ) {
        composable(Routes.LOGIN) { LoginScreen() }
        composable(Routes.MAIN) {
            MainScreen(
                onOpenSite = { navController.navigate(Routes.site(it)) },
                onMapSite = { navController.navigate(Routes.DRAW) },
                onOpenInspection = { navController.navigate(Routes.inspection(it)) },
            )
        }
        composable(Routes.DRAW) { DrawSiteScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SITE, arguments = idArg) { entry ->
            SiteDetailScreen(
                siteId = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenInspection = { navController.navigate(Routes.inspection(it)) },
            )
        }
        composable(Routes.INSPECTION, arguments = idArg) { entry ->
            InspectionScreen(
                inspectionId = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenCamera = { navController.navigate(Routes.camera(it)) },
            )
        }
        composable(Routes.CAMERA, arguments = idArg) { entry ->
            CameraScreen(
                inspectionId = entry.arguments?.getString("id").orEmpty(),
                onDone = { navController.popBackStack() },
            )
        }
    }
}
