package com.example.c001apk.compose.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.c001apk.compose.R
import com.example.c001apk.compose.logic.providable.LocalUserPreferences
import com.example.c001apk.compose.ui.component.BackButton
import com.example.c001apk.compose.ui.component.settings.BasicListItem
import com.example.c001apk.compose.util.CdidClient
import com.example.c001apk.compose.util.IosDevice
import com.example.c001apk.compose.util.IosDeviceUtils
import com.example.c001apk.compose.util.IosHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParamsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
) {
    val prefs = LocalUserPreferences.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var device by remember { mutableStateOf(IosDeviceUtils.current()) }

    fun refreshDevice() {
        device = IosDeviceUtils.current()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton { onBackClick() } },
                title = { Text(text = stringResource(id = R.string.params_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            BasicListItem(
                headlineText = "iOS 设备 ID",
                supportingText = device.id + if (IosDeviceUtils.hasCdid()) "（CDID）" else "（匿名 UUID）",
            )
            BasicListItem(
                headlineText = "X-App-Device",
                supportingText = IosHeaders.appDevice(device),
            )
            BasicListItem(
                headlineText = "X-App-Token",
                supportingText = IosHeaders.appToken(device, IosDeviceUtils.nowSeconds()),
            )
            BasicListItem(
                headlineText = "User-Agent",
                supportingText = IosHeaders.userAgent(device),
            )
            BasicListItem(
                headlineText = "iOS 设备档案",
                supportingText = "${device.model} / ${device.hardwareId} / " +
                    "${device.salesModel} / iOS ${device.systemVersion}",
            )
            BasicListItem(
                headlineText = "重新生成机型档案",
                supportingText = "保留当前设备 ID，仅更新 iPhone 机型与系统版本",
                onClick = {
                    device = IosDeviceUtils.regenerate()
                    scope.launch {
                        snackbarHostState.showSnackbar("已重新生成 iOS 机型档案")
                    }
                },
            )
            BasicListItem(
                headlineText = "重新生成设备 ID",
                supportingText = "未登录生成 UUID；登录后会自动申请新的 CDID",
                onClick = {
                    device = IosDeviceUtils.resetDeviceId()
                    if (!prefs.isLogin) {
                        scope.launch {
                            snackbarHostState.showSnackbar("已生成新的匿名 UUID")
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("正在申请新的 CDID…")
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    CdidClient.fetch(
                                        IosDeviceUtils.current(),
                                        IosDeviceUtils.idfv(),
                                        IosDeviceUtils.installId(),
                                    )
                                }
                            }.onSuccess { cdid ->
                                IosDeviceUtils.setCdid(cdid)
                                refreshDevice()
                                snackbarHostState.showSnackbar("设备 ID 已更新")
                            }.onFailure { error ->
                                refreshDevice()
                                snackbarHostState.showSnackbar(
                                    "CDID 申请失败，当前保留 UUID：${error.message.orEmpty()}"
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}
