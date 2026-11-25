package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AspectRatioMode
import com.example.data.model.PopupSizePreset
import com.example.data.preferences.PlayerPreferencesState
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    prefs: PlayerPreferencesState,
    notificationPermissionGranted: Boolean,
    supportsSystemPip: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onBackgroundVideoAudioChange: (Boolean) -> Unit,
    onAutoPipChange: (Boolean) -> Unit,
    onPopupWidthChange: (Float) -> Unit,
    onDefaultAspectRatioChange: (AspectRatioMode) -> Unit,
    onResumePromptlessChange: (Boolean) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("재생 및 앱 설정", style = MaterialTheme.typography.headlineMedium)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                            Text("백그라운드 미디어 알림 권한", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "잠금 화면 및 상단 알림창에서 백그라운드 재생 컨트롤을 표시하려면 알림 권한을 허용하세요.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = onRequestNotificationPermission) {
                            Text("알림 권한 허용")
                        }
                    }
                }
            }
        }

        item {
            SettingsSectionCard(title = "백그라운드 & 팝업 재생", icon = Icons.Rounded.Headphones) {
                SettingsToggleRow(
                    title = "동영상 백그라운드 오디오 재생",
                    subtitle = "앱을 최소화하거나 화면을 꺼도 동영상의 오디오를 계속 재생합니다.",
                    checked = prefs.backgroundVideoAudio,
                    onCheckedChange = onBackgroundVideoAudioChange,
                    testTag = "setting_background_audio_switch"
                )
                SettingsToggleRow(
                    title = "앱 이탈 시 자동 PIP 팝업 전환",
                    subtitle = "동영상 재생 중 홈으로 이동하면 시스템 PIP 팝업 창으로 전환합니다.",
                    checked = prefs.autoPipOnLeave,
                    onCheckedChange = onAutoPipChange,
                    testTag = "setting_auto_pip_switch"
                )
                SettingsToggleRow(
                    title = "마지막 위치에서 자동 이어보기",
                    subtitle = "이전 재생 기록이 있는 파일을 바로 해당 시점부터 시작합니다.",
                    checked = prefs.resumePlaybackPromptless,
                    onCheckedChange = onResumePromptlessChange,
                    testTag = "setting_resume_switch"
                )
            }
        }

        item {
            SettingsSectionCard(title = "인앱 팝업 크기 설정", icon = Icons.Rounded.PictureInPictureAlt) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("기본 팝업 너비", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${prefs.popupWidthDp.roundToInt()} dp",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = prefs.popupWidthDp,
                    onValueChange = onPopupWidthChange,
                    valueRange = 160f..380f
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopupSizePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = kotlin.math.abs(prefs.popupWidthDp - preset.widthDp) < 20f,
                            onClick = { onPopupWidthChange(preset.widthDp) },
                            label = { Text("${preset.label} (${preset.widthDp.toInt()}dp)") }
                        )
                    }
                }
            }
        }

        item {
            SettingsSectionCard(title = "기본 화면 비율", icon = Icons.Rounded.AspectRatio) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AspectRatioMode.entries.forEach { mode ->
                        FilterChip(
                            selected = prefs.defaultAspectRatio == mode,
                            onClick = { onDefaultAspectRatioChange(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }

        item {
            SettingsSectionCard(title = "테마 & 화면", icon = Icons.Rounded.DarkMode) {
                SettingsToggleRow(
                    title = "다크 스튜디오 테마",
                    subtitle = "영상 감상에 최적화된 어두운 Obsidian 테마를 사용합니다.",
                    checked = prefs.darkThemeMode,
                    onCheckedChange = onDarkThemeChange,
                    testTag = "setting_dark_theme_switch"
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsToggleRow(
                        title = "Material You 다이나믹 컬러",
                        subtitle = "기기 배경화면 색상에 맞춰 UI 컬러를 자동 조정합니다.",
                        checked = prefs.dynamicColorEnabled,
                        onCheckedChange = onDynamicColorChange,
                        testTag = "setting_dynamic_color_switch"
                    )
                }
            }
        }

        item {
            SettingsSectionCard(title = "권한 및 기기 호환성", icon = Icons.Rounded.Security) {
                Text(
                    text = "• 백그라운드 재생 알림: ${if (notificationPermissionGranted) "허용됨" else "미허용 (상단 버튼으로 허용 가능)"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• 시스템 PIP(다른 앱 위 플로팅): ${if (supportsSystemPip) "기기 지원됨" else "기기 미지원 (앱 내 팝업 자동 사용)"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• 파일 및 폴더 선택: Google Play 보안 정책을 준수하는 무권한 시스템 선택기(Photo Picker / SAF) 및 차량용 호환 선택기를 자동 사용합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
