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
private val HeaderPanelMinHeight = 120.dp
private val StatusPanelMinHeight = 88.dp

@Composable
fun MainScreen(
    controller: AppController,
    modifier: Modifier = Modifier
) {
    val uiState by controller.uiState.collectAsState()

    val isEditLocked =
        uiState.isDownloading || uiState.isInstalling || uiState.isCheckingEnvironment

    val isActionLocked =
        isEditLocked || uiState.isRefreshingEnvironment

    val canRunEnvironmentCheck = !isActionLocked
    val canInstallOrUpdate = !isActionLocked

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
                                .heightIn(min = HeaderPanelMinHeight)
                        )

                        DownloadForm(
                            controller = controller,
                            uiState = uiState,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    LogPanel(
                        uiState = uiState,
                        topPanelMinHeight = StatusPanelMinHeight,
                        canRunEnvironmentCheck = canRunEnvironmentCheck,
                        canInstallOrUpdate = canInstallOrUpdate,
                        onRunEnvironmentCheck = controller::runEnvironmentCheck,
                        onInstallOrUpdate = controller::installOrUpdateStreamlink,
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
