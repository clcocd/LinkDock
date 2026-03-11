package app.linkdock.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.linkdock.desktop.app.*
import app.linkdock.desktop.domain.ServiceType
import app.linkdock.desktop.download.getServiceUrlHintMessage
import app.linkdock.desktop.download.getServiceUrlPlaceholder
import app.linkdock.desktop.download.getUnsupportedServiceUrlMessage

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

    val unsupportedUrlMessage =
        getUnsupportedServiceUrlMessage(uiState.selectedService, uiState.url)

    val canStartDownload =
        !isActionLocked &&
                uiState.environmentSource == EnvironmentSource.VERIFIED &&
                uiState.selectedService != null &&
                uiState.hasStreamlink &&
                uiState.email.isNotBlank() &&
                uiState.password.isNotBlank() &&
                uiState.url.isNotBlank() &&
                unsupportedUrlMessage == null

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
    val emailHangulRejected = uiState.hangulRejectedField == HangulRejectedField.EMAIL
    val passwordHangulRejected = uiState.hangulRejectedField == HangulRejectedField.PASSWORD
    val urlHangulRejected = uiState.hangulRejectedField == HangulRejectedField.URL
    val unsupportedUrlMessage =
        getUnsupportedServiceUrlMessage(uiState.selectedService, uiState.url)

    val inputHintMessage =
        if (urlHangulRejected) {
            "URL에는 한글을 사용할 수 없습니다. 브라우저 주소창의 영문 주소를 그대로 붙여넣어 주세요."
        } else {
            getServiceUrlHintMessage(uiState.selectedService, uiState.url)
        }

    val inputHintIsError = urlHangulRejected || unsupportedUrlMessage != null

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
                    label = {
                        Text(
                            if (emailHangulRejected) {
                                "이메일에는 한글을 사용할 수 없습니다."
                            } else {
                                "이메일"
                            }
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = canEditFields,
                    isError = inputHintIsError
                )

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = controller::updatePassword,
                    label = {
                        Text(
                            if (passwordHangulRejected) {
                                "비밀번호에는 한글을 사용할 수 없습니다."
                            } else {
                                "비밀번호"
                            }
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = canEditFields,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = passwordHangulRejected
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
                            if (urlHangulRejected) {
                                "URL에는 한글을 사용할 수 없습니다."
                            } else {
                                "다운로드할 페이지 URL"
                            }
                        )
                    },
                    placeholder = {
                        Text(getServiceUrlPlaceholder(uiState.selectedService))
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = canEditFields,
                    isError = inputHintIsError
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

            Text(
                text = inputHintMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (inputHintIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

        }
    }
}

@Composable
private fun ActionCard(
    controller: AppController,
    uiState: AppUiState,
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

private fun buildActionHint(uiState: AppUiState): String? {
    val unsupportedUrlMessage =
        getUnsupportedServiceUrlMessage(uiState.selectedService, uiState.url)

    return when {
        uiState.postInstallState == PostInstallState.VERIFYING ->
            "설치 반영 상태를 확인 중입니다."

        uiState.postInstallState == PostInstallState.MAY_NEED_RESTART ->
            "설치는 완료되었지만 앱을 다시 실행해야 반영될 수 있습니다."

        uiState.postInstallState == PostInstallState.NEEDS_RECHECK ->
            "설치 후 상태를 다시 확인해 주세요."

        uiState.isCheckingEnvironment ->
            "설치 상태 확인 중입니다. 완료될 때까지 기다려 주세요."

        uiState.isDownloading ->
            "다운로드 진행 중입니다. 중지 버튼만 사용할 수 있습니다."

        uiState.isInstalling ->
            "설치/업데이트 진행 중입니다. 완료될 때까지 기다려 주세요."

        uiState.selectedService == null ->
            "먼저 서비스를 선택하세요."

        uiState.environmentSource == EnvironmentSource.VERIFIED && !uiState.hasStreamlink ->
            "Streamlink가 없습니다. 위의 '환경 및 실행 상태' 영역에서 설치 버튼을 눌러 주세요."

        uiState.email.isBlank() ->
            "이메일을 입력하세요."

        uiState.password.isBlank() ->
            "비밀번호를 입력하세요."

        uiState.url.isBlank() ->
            "다운로드할 페이지 URL을 입력하세요."

        unsupportedUrlMessage != null ->
            unsupportedUrlMessage

        else -> null
    }
}