package com.jpcottin.simpletorrent

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.jpcottin.simpletorrent.ui.main.MainScreen
import com.jpcottin.simpletorrent.ui.player.PlayerScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(onItemClick = { navKey -> backStack.add(navKey) })
        }
        entry<Player> { key ->
          PlayerScreen(
            filePath = key.filePath,
            title    = key.title,
            onBack   = { backStack.removeLastOrNull() },
          )
        }
      },
  )
}
