package app.linkdock.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LinearProgressIndicator
import app.linkdock.desktop.app.AppUiState
import app.linkdock.desktop.app.EnvironmentSource
import app.linkdock.desktop.domain.OsType

@Composable
fun LogPanel(
    uiState: AppUiState,
    topPanelMinHeight: Dp,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val environmentHint = buildEnvironmentHint(uiState)
    val isTaskRunning =
        uiState.isDownloading || uiState.isInstalling || uiState.isCheckingEnvironment


    LaunchedEffect(uiState.logs.size) {
        if (uiState.logs.isNotEmpty()) {
            listState.animateScrollToItem(uiState.logs.lastIndex)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = topPanelMinHeight),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ){
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "실행 모니터",
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = uiState.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!environmentHint.isNullOrBlank()) {
                    Text(
                        text = environmentHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val progressText = when {
                    uiState.downloadProgress != null -> uiState.downloadProgress.toDisplayText()
                    uiState.installProgressText != null -> uiState.installProgressText
                    uiState.isCheckingEnvironment -> "환경 검사 작업이 실행 중입니다."
                    uiState.isDownloading -> "진행 정보 수신 대기 중..."
                    uiState.isInstalling -> "설치 또는 업데이트 작업이 실행 중입니다."
                    else -> "현재 실행 중인 작업은 없습니다."
                }

                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isTaskRunning) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }
        }

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 540.dp),
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

private fun buildEnvironmentHint(uiState: AppUiState): String? {
    if (
        uiState.isDownloading ||
        uiState.isInstalling ||
        uiState.isCheckingEnvironment ||
        uiState.environmentSource != EnvironmentSource.VERIFIED
    ) {
        return null
    }

    return when (uiState.osType) {
        null -> null
        OsType.UNSUPPORTED -> "환경 상태: 지원하지 않는 운영체제"
        OsType.MAC, OsType.WINDOWS ->
            if (uiState.hasStreamlink) "Streamlink 설치됨" else "Streamlink 미설치"
    }
}