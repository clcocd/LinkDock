package app.linkdock.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.linkdock.desktop.app.AppController
import app.linkdock.desktop.app.AppInfo

private val ContentMaxWidth = 1320.dp
private val TopPanelMinHeight = 120.dp

@Composable
fun MainScreen(
    controller: AppController,
    onExitApp: () -> Unit,
    onRestartApp: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val uiState by controller.uiState.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = ContentMaxWidth),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.60f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HeaderPanel(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = TopPanelMinHeight)
                        )

                        DownloadForm(
                            controller = controller,
                            uiState = uiState,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    LogPanel(
                        uiState = uiState,
                        topPanelMinHeight = TopPanelMinHeight,
                        modifier = Modifier.weight(0.40f)
                    )
                }
            }

            AppFooter(
                appVersion = AppInfo.version,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (uiState.showRestartDialog) {
        AlertDialog(
            onDismissRequest = controller::dismissRestartDialog,
            title = {
                Text("다시 시작 필요")
            },
            text = {
                Text(
                    uiState.restartDialogMessage
                        ?: "변경 사항을 반영하려면 앱을 다시 시작하는 것이 좋습니다."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        controller.confirmRestart(
                            onRestartApp = onRestartApp,
                            onExitApp = onExitApp
                        )
                    }
                ) {
                    Text("지금 다시 시작")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = controller::dismissRestartDialog
                ) {
                    Text("나중에")
                }
            }
        )
    }
}

@Composable
private fun HeaderPanel(
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "LinkDock",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = "ZAN / SPWN 전용 다운로드 도구",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
