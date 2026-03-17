package app.linkdock.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import app.linkdock.desktop.app.*
import app.linkdock.desktop.domain.ServiceType
import app.linkdock.desktop.download.SpwnPartOption
import app.linkdock.desktop.download.getServiceUrlHintMessage
import app.linkdock.desktop.download.getServiceUrlPlaceholder
import app.linkdock.desktop.download.getUnsupportedServiceUrlMessage

@Composable
fun DownloadForm(
    controller: AppController,
    uiState: AppUiState,
    modifier: Modifier = Modifier
) {
    val isEditLocked = uiState.isInputLocked
    val isActionLocked = uiState.isActionLocked

    val canEditFields = !isEditLocked

    val unsupportedUrlMessage =
        getUnsupportedServiceUrlMessage(uiState.selectedService, uiState.url)

    val requiresSpwnSelection =
        uiState.showSpwnPartSelector && uiState.spwnPartOptions.isNotEmpty()

    val hasRequiredSpwnSelection =
        !requiresSpwnSelection || uiState.selectedSpwnPartStreamKey != null

    val canStartDownload =
        !isActionLocked &&
                uiState.environmentSource == EnvironmentSource.VERIFIED &&
                uiState.selectedService != null &&
                uiState.hasStreamlink &&
                uiState.hasFfmpeg &&
                uiState.email.isNotBlank() &&
                uiState.password.isNotBlank() &&
                uiState.url.isNotBlank() &&
                unsupportedUrlMessage == null &&
                hasRequiredSpwnSelection

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

    val inputHintMessage = getServiceUrlHintMessage(uiState.selectedService, uiState.url)

    val debugUseFakeSpwnOptions = false

    val debugSpwnPartOptions = if (debugUseFakeSpwnOptions) {
        listOf(
            SpwnPartOption(
                displayLabel = "Stage 1",
                bestStreamKey = "part3_1080p",
                partKey = "part3",
                rawLabel = "rgrp5/stage1_v1 [VOD]",
                originalOrder = 0
            ),
            SpwnPartOption(
                displayLabel = "Stage 2",
                bestStreamKey = "part4_1080p",
                partKey = "part4",
                rawLabel = "rgrp5/stage2_v1 [VOD]",
                originalOrder = 1
            )
        )
    } else {
        emptyList()
    }

    val effectiveSpwnPartOptions =
        uiState.spwnPartOptions.ifEmpty {
            debugSpwnPartOptions
        }

    val effectiveShowSpwnPartSelector =
        uiState.showSpwnPartSelector || effectiveSpwnPartOptions.isNotEmpty()

    val urlIsError = urlHangulRejected || unsupportedUrlMessage != null

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
                    isError = emailHangulRejected
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
                    isError = urlIsError
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
                color = if (urlIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            var expanded by remember(
                effectiveSpwnPartOptions,
                uiState.selectedSpwnPartStreamKey
            ) {
                mutableStateOf(false)
            }

            val selectedOption = effectiveSpwnPartOptions.firstOrNull {
                it.bestStreamKey == uiState.selectedSpwnPartStreamKey
            }

            val canSelectSpwnPart =
                effectiveShowSpwnPartSelector &&
                        effectiveSpwnPartOptions.isNotEmpty() &&
                        !uiState.isPreparingDownload &&
                        !uiState.isDownloading &&
                        !uiState.isInstalling &&
                        !uiState.isCheckingEnvironment &&
                        !uiState.isRefreshingEnvironment

            val density = LocalDensity.current
            var spwnSelectorWidthPx by remember { mutableIntStateOf(0) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        spwnSelectorWidthPx = coordinates.size.width
                    }
            ) {
                OutlinedTextField(
                    value = selectedOption?.displayLabel ?: "",
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    enabled = canSelectSpwnPart,
                    label = { Text("다운로드 VOD") },
                    placeholder = {
                        Text("받을 항목을 선택해 주세요")
                    },
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(width = 38.dp, height = 32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (canSelectSpwnPart) {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (expanded) {
                                    Icons.Default.ArrowDropUp
                                } else {
                                    Icons.Default.ArrowDropDown
                                },
                                contentDescription = "다운로드 VOD 선택",
                                modifier = Modifier.size(24.dp),
                                tint = if (canSelectSpwnPart) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(enabled = canSelectSpwnPart) {
                            expanded = !expanded
                        }
                )

                DropdownMenu(
                    expanded = expanded && canSelectSpwnPart,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.width(
                        with(density) { spwnSelectorWidthPx.toDp() }
                    )
                ) {
                    effectiveSpwnPartOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.displayLabel,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            onClick = {
                                controller.selectSpwnPart(option)
                                expanded = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Text(
                text = if (effectiveSpwnPartOptions.isNotEmpty()) {
                    "받을 영상이 여러 개 있습니다. 원하는 항목을 선택해 주세요."
                } else {
                    "받을 영상이 여러 개면 원하는 항목을 선택할 수 있습니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        when {
                            uiState.isPreparingDownload -> "준비 중"
                            uiState.isDownloading -> "다운로드 중"
                            else -> "다운로드"
                        }
                    )
                }

                OutlinedButton(
                    onClick = controller::stopDownload,
                    enabled = uiState.isPreparingDownload || uiState.isDownloading,
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

        uiState.isRefreshingEnvironment && uiState.environmentSource == EnvironmentSource.CACHED ->
            "저장된 설치 상태를 표시 중입니다. 백그라운드에서 현재 상태를 다시 확인하고 있으니 잠시만 기다려 주세요."

        uiState.isRefreshingEnvironment ->
            "앱 시작 후 설치 상태를 확인 중입니다. 잠시 후 다시 시도해 주세요."

        uiState.showSpwnPartSelector && uiState.selectedSpwnPartStreamKey == null ->
            "받을 항목을 먼저 선택해 주세요."

        uiState.showSpwnPartSelector ->
            "받을 항목을 선택한 뒤 다운로드 버튼을 눌러 주세요."

        uiState.isPreparingDownload ->
            "다운로드할 항목을 확인하고 있습니다. 잠시만 기다려 주세요."

        uiState.isDownloading ->
            "다운로드 진행 중입니다. 중지 버튼만 사용할 수 있습니다."

        uiState.isInstalling ->
            "설치/업데이트 진행 중입니다. 완료될 때까지 기다려 주세요."

        uiState.environmentSource != EnvironmentSource.VERIFIED ->
            "다운로드 전에 위의 '설치 및 실행 확인' 영역에서 설치 확인을 먼저 실행해 주세요."

        uiState.selectedService == null ->
            "먼저 서비스를 선택하세요."

        !uiState.hasStreamlink && !uiState.hasFfmpeg ->
            "Streamlink와 FFmpeg가 없습니다. 위의 '설치 및 실행 확인' 영역에서 설치 진행 버튼을 눌러 주세요."

        !uiState.hasStreamlink ->
            "Streamlink가 없습니다. 위의 '설치 및 실행 확인' 영역에서 Streamlink 설치 버튼을 눌러 주세요."

        !uiState.hasFfmpeg ->
            "FFmpeg가 없습니다. 위의 '설치 및 실행 확인' 영역에서 FFmpeg 설치 버튼을 눌러 주세요."

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