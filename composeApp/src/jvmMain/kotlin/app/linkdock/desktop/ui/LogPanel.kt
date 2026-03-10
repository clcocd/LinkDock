package app.linkdock.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.linkdock.desktop.app.AppUiState
import app.linkdock.desktop.app.EnvironmentSource
import app.linkdock.desktop.domain.OsType

@Composable
fun LogPanel(
    uiState: AppUiState,
    topPanelMinHeight: Dp,
    canRunEnvironmentCheck: Boolean,
    canInstallOrUpdate: Boolean,
    onRunEnvironmentCheck: () -> Unit,
    onInstallOrUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val environmentSummary = buildEnvironmentSummary(uiState)
    val executionSummary = buildExecutionSummary(uiState)
    val isTaskRunning =
        uiState.isDownloading || uiState.isInstalling || uiState.isCheckingEnvironment

    val installButtonText = when {
        uiState.isInstalling -> "진행 중"
        uiState.environmentSource != EnvironmentSource.VERIFIED -> "설치/업데이트"
        uiState.hasStreamlink -> "업데이트"
        else -> "설치"
    }

    LaunchedEffect(uiState.logs.size) {
        if (uiState.logs.isNotEmpty()) {
            listState.animateScrollToItem(uiState.logs.lastIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = topPanelMinHeight),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "환경 및 실행 상태",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRunEnvironmentCheck,
                            enabled = canRunEnvironmentCheck,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) {
                            Text("설치 확인")
                        }

                        OutlinedButton(
                            onClick = onInstallOrUpdate,
                            enabled = canInstallOrUpdate,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) {
                            Text(installButtonText)
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "환경 상태",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = environmentSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "실행 상태",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = executionSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isTaskRunning) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    )
                }
            }
        }

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "로그",
                    style = MaterialTheme.typography.titleSmall
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.logs) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun buildEnvironmentSummary(uiState: AppUiState): String = when {
    uiState.environmentSource != EnvironmentSource.VERIFIED ->
        "아직 확인된 환경 정보가 없습니다. 설치 확인으로 현재 상태를 다시 확인하세요."

    uiState.osType == null ->
        "운영체제 정보를 확인하지 못했습니다."

    uiState.osType == OsType.UNSUPPORTED ->
        "지원하지 않는 운영체제입니다."

    uiState.hasStreamlink ->
        "Streamlink가 설치된 것으로 확인되었습니다."

    else ->
        "Streamlink가 감지되지 않았습니다."
}

private fun buildExecutionSummary(uiState: AppUiState): String = when {
    uiState.downloadProgress != null -> uiState.downloadProgress.toDisplayText()
    uiState.installProgressText != null -> uiState.installProgressText
    uiState.isCheckingEnvironment -> "설치 상태 확인 중입니다."
    uiState.isDownloading -> "진행 정보 수신 대기 중..."
    uiState.isInstalling -> "설치 또는 업데이트 작업이 실행 중입니다."
    else -> "현재 실행 중인 작업은 없습니다."
}