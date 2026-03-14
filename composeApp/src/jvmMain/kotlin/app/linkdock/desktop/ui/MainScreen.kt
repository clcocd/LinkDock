package app.linkdock.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.linkdock.desktop.app.AppController
import app.linkdock.desktop.app.AppInfo
import app.linkdock.desktop.app.ReleaseNotesDialogMode
import app.linkdock.desktop.app.isActionLocked
import app.linkdock.desktop.release.AppReleaseNotes
import app.linkdock.desktop.release.ReleaseNoteEntry

private val ContentMaxWidth = 1320.dp
private val HeaderPanelMinHeight = 120.dp
private val StatusPanelMinHeight = 88.dp
private val ReleaseNotesDialogMaxHeight = 420.dp

@Composable
fun MainScreen(
    controller: AppController,
    modifier: Modifier = Modifier
) {
    val uiState by controller.uiState.collectAsState()
    val isActionLocked = uiState.isActionLocked
    val canRunEnvironmentCheck = !isActionLocked
    val canInstallOrUpdate = !isActionLocked

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {

        uiState.releaseNoteToShow?.let { releaseNote ->
            ReleaseNotesDialog(
                currentVersion = releaseNote.version,
                mode = uiState.releaseNotesDialogMode,
                onDismiss = controller::dismissReleaseNotesDialog
            )
        }

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
                onShowReleaseNotes = controller::showReleaseNotesDialog,
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

@Composable
private fun ReleaseNotesDialog(
    currentVersion: String,
    mode: ReleaseNotesDialogMode,
    onDismiss: () -> Unit
) {
    val notes = remember(currentVersion, mode) {
        when (mode) {
            ReleaseNotesDialogMode.RECENT -> AppReleaseNotes.recent(5)
            ReleaseNotesDialogMode.ALL -> AppReleaseNotes.all()
        }
    }

    val expandedVersions = remember(currentVersion, mode) {
        mutableStateMapOf<String, Boolean>().apply {
            notes.drop(1).forEach { entry ->
                this[entry.version] = false
            }
        }
    }

    val titleText = when (mode) {
        ReleaseNotesDialogMode.RECENT -> "새로운 업데이트"
        ReleaseNotesDialogMode.ALL -> "변경 이력"
    }

    val subtitleText = when (mode) {
        ReleaseNotesDialogMode.RECENT -> "LinkDock v$currentVersion"
        ReleaseNotesDialogMode.ALL -> "전체 릴리즈 노트"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인")
            }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(titleText)
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = ReleaseNotesDialogMaxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                notes.forEachIndexed { index, entry ->
                    val isLatest = index == 0
                    val isExpanded = if (isLatest) {
                        true
                    } else {
                        expandedVersions[entry.version] == true
                    }

                    ReleaseNoteSection(
                        entry = entry,
                        expanded = isExpanded,
                        collapsible = !isLatest,
                        onToggle = {
                            if (!isLatest) {
                                expandedVersions[entry.version] = !isExpanded
                            }
                        }
                    )

                    if (index < notes.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    )
}

@Composable
private fun ReleaseNoteSection(
    entry: ReleaseNoteEntry,
    expanded: Boolean,
    collapsible: Boolean,
    onToggle: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (collapsible) {
            TextButton(
                onClick = onToggle,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("v${entry.version}")
            }
        } else {
            Text(
                text = "v${entry.version}",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                entry.items.forEach { item ->
                    Text(
                        text = "• $item",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}