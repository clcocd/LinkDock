package app.linkdock.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.linkdock.desktop.app.AppInfo
import java.awt.Dimension

fun main() = application {


    val windowState = rememberWindowState(
        size = DpSize(1080.dp, 665.dp)
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "LinkDock v${AppInfo.version}",
        state = windowState
    ){
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(900, 665)
        }

        App()
    }
}
