package app.linkdock.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.linkdock.desktop.app.AppController
import app.linkdock.desktop.app.AppUiState
import app.linkdock.desktop.domain.ServiceType
import app.linkdock.desktop.app.EnvironmentSource

@Composable
fun DownloadForm(
    controller: AppController,
    uiState: AppUiState,
    modifier: Modifier = Modifier
) {
    val isEditLocked =
        uiState.isDownloading || uiState.isInstalling || uiState.isCheckingEnvironment

    val isActionLocked =
        isEditLocked || uiState.isRefreshingEnvironment

    val canEditFields = !isEditLocked
    val canRunEnvironmentCheck = !isActionLocked
    val canInstallOrUpdate = !isActionLocked

    val canStartDownload =
        !isActionLocked &&
                uiState.environmentSource == EnvironmentSource.VERIFIED &&
                uiState.selectedService != null &&
                uiState.hasStreamlink &&
                uiState.email.isNotBlank() &&
                uiState.password.isNotBlank() &&
                uiState.url.isNotBlank()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InputCard(
            controller = controller,
            uiState = uiState,
            canEditFields = canEditFields
        )

        ActionCard(
            controller = controller,
            uiState = uiState,
            canRunEnvironmentCheck = canRunEnvironmentCheck,
            canInstallOrUpdate = canInstallOrUpdate,
            canStartDownload = canStartDownload
        )
    }
}

@Composable
private fun InputCard(
    controller: AppController,
    uiState: AppUiState,
    canEditFields: Boolean
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "입력",
                style = MaterialTheme.typography.titleSmall
            )

            ServiceSelector(
                selected = uiState.selectedService,
                enabled = canEditFields,
                onSelect = controller::updateService
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = controller::updateEmail,
                    label = { Text("이메일") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = canEditFields
                )

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = controller::updatePassword,
                    label = { Text("비밀번호") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = canEditFields,
                    visualTransformation = PasswordVisualTransformation()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.url,
                    onValueChange = controller::updateUrl,
                    label = {
                        Text(
                            uiState.selectedService?.let { "${it.displayName} URL" } ?: "서비스 URL"
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = canEditFields
                )

                OutlinedTextField(
                    value = uiState.outputDir,
                    onValueChange = controller::updateOutputDir,
                    label = { Text("저장 경로") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = canEditFields,
                    readOnly = true,
                    trailingIcon = {
                        IconButton(
                            onClick = controller::browseOutputDirectory,
                            enabled = canEditFields
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "저장 경로 선택"
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    controller: AppController,
    uiState: AppUiState,
    canRunEnvironmentCheck: Boolean,
    canInstallOrUpdate: Boolean,
    canStartDownload: Boolean
) {
    val actionHint = buildActionHint(uiState)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "실행",
                style = MaterialTheme.typography.titleSmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = controller::runEnvironmentCheck,
                    enabled = canRunEnvironmentCheck,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("설치 확인")
                }

                Button(
                    onClick = controller::installOrUpdateStreamlink,
                    enabled = canInstallOrUpdate,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when {
                            uiState.isInstalling -> "설치/업데이트 중"
                            uiState.environmentSource != EnvironmentSource.VERIFIED -> "Streamlink 설치/업데이트"
                            uiState.hasStreamlink -> "Streamlink 업데이트"
                            else -> "Streamlink 설치"
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = controller::startDownload,
                    enabled = canStartDownload,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (uiState.isDownloading) "다운로드 중" else "다운로드 시작"
                    )
                }

                OutlinedButton(
                    onClick = controller::stopDownload,
                    enabled = uiState.isDownloading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("중지")
                }
            }

            actionHint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ServiceSelector(
    selected: ServiceType?,
    enabled: Boolean,
    onSelect: (ServiceType) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = selected?.let { "선택됨: ${it.displayName}" } ?: "서비스 선택",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ServiceType.entries.forEach { service ->
                val isSelected = selected == service

                if (isSelected) {
                    FilledTonalButton(
                        onClick = { onSelect(service) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("✓ ${service.displayName}")
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelect(service) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(service.displayName)
                    }
                }
            }
        }
    }
}

private fun buildActionHint(uiState: AppUiState): String? = when {

    uiState.isCheckingEnvironment ->
        "설치 상태 확인 중입니다. 완료될 때까지 기다려 주세요."

    uiState.isDownloading ->
        "다운로드 진행 중입니다. 중지 버튼만 사용할 수 있습니다."

    uiState.isInstalling ->
        "설치/업데이트 진행 중입니다. 완료될 때까지 기다려 주세요."

    uiState.selectedService == null ->
        "먼저 서비스를 선택하세요."

    uiState.environmentSource == EnvironmentSource.VERIFIED && !uiState.hasStreamlink ->
        "Streamlink가 감지되지 않습니다. 설치하거나 다시 확인하세요."

    uiState.email.isBlank() ->
        "이메일을 입력하세요."

    uiState.password.isBlank() ->
        "비밀번호를 입력하세요."

    uiState.url.isBlank() ->
        "다운로드할 URL을 입력하세요."

    else -> null
}