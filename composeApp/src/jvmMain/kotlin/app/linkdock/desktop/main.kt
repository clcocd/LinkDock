package app.linkdock.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.linkdock.desktop.app.AppInfo

fun main() = application {

    val osName = System.getProperty("os.name").lowercase()

    val defaultWindowWidth = 960.dp
    val defaultWindowHeight = when {
        osName.contains("win") -> 777.dp
        osName.contains("mac") || osName.contains("darwin") -> 765.dp
        else -> 765.dp
    }

    val defaultWindowSize = DpSize(
        width = defaultWindowWidth,
        height = defaultWindowHeight
    )

    val windowState = rememberWindowState(
        size = defaultWindowSize
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "LinkDock v${AppInfo.version}",
        state = windowState,
        resizable = false
    ){
        App()
    }
}
