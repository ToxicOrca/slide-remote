package com.toxicorca.slideremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.toxicorca.slideremote.ui.screens.ConnectScreen
import com.toxicorca.slideremote.ui.screens.DeckListScreen
import com.toxicorca.slideremote.ui.screens.EditorScreen
import com.toxicorca.slideremote.ui.screens.PresentScreen
import com.toxicorca.slideremote.ui.screens.SettingsScreen

private object Routes {
    const val DECKS = "decks"
    const val EDITOR = "editor/{deckId}"
    const val PRESENT = "present/{deckId}"
    const val CONNECT = "connect"
    const val SETTINGS = "settings"

    fun editor(id: String) = "editor/$id"
    fun present(id: String) = "present/$id"
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val deckArg = listOf(navArgument("deckId") { type = NavType.StringType })

    NavHost(
        navController = nav,
        startDestination = Routes.DECKS,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        composable(Routes.DECKS) {
            DeckListScreen(
                onPresent = { nav.navigate(Routes.present(it)) },
                onEdit = { nav.navigate(Routes.editor(it)) },
                onConnect = { nav.navigate(Routes.CONNECT) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.EDITOR, arguments = deckArg) {
            EditorScreen(
                onBack = { nav.popBackStack() },
                onPresent = { id ->
                    // Editor -> Present replaces the editor so Back returns to the list.
                    nav.navigate(Routes.present(id)) {
                        popUpTo(Routes.EDITOR) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.PRESENT, arguments = deckArg) {
            PresentScreen(
                onBack = { nav.popBackStack() },
                onEdit = { id ->
                    nav.navigate(Routes.editor(id)) {
                        popUpTo(Routes.PRESENT) { inclusive = true }
                    }
                },
                onConnect = { nav.navigate(Routes.CONNECT) },
            )
        }
        composable(Routes.CONNECT) {
            ConnectScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
