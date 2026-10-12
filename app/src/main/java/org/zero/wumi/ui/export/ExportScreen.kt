package org.zero.wumi.ui.export

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 数据导出页（spec §8 v0.1）：CSV 按月导出 + JSON 全量导出，均经 SAF `CreateDocument`
 * 由用户选位置落盘（无需存储权限），成功/失败以 Snackbar 反馈。
 */
@Composable
fun ExportScreen(vm: ExportViewModel = hiltViewModel()) {
    val month by vm.month.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // SAF 选择器异步返回：点按钮时先记住所选月份，回调里按它取数，避免期间切月造成口径漂移
    var pendingCsvMonth by remember { mutableStateOf<YearMonth?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val m = pendingCsvMonth
        if (uri != null && m != null) {
            scope.launch {
                try {
                    val text = vm.csvForMonth(m)
                    snackbar.showSnackbar(
                        if (writeToUri(context, uri, text)) "已导出 ${vm.csvFileName(m)}" else "导出失败：无法写入所选位置",
                    )
                } catch (e: Exception) {
                    snackbar.showSnackbar("导出失败：${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = vm.jsonAll()
                    snackbar.showSnackbar(
                        if (writeToUri(context, uri, text)) "已导出 ${vm.jsonFileName()}" else "导出失败：无法写入所选位置",
                    )
                } catch (e: Exception) {
                    snackbar.showSnackbar("导出失败：${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 月切换行：上月 / 月份标题 / 下月（复用明细页模式）
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.prevMonth() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上月") }
                Text(month.format(DateTimeFormatter.ofPattern("yyyy年M月")), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { vm.nextMonth() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下月") }
                Spacer(Modifier.weight(1f))
            }
            ExportEntry(
                title = "导出 CSV（本月）",
                hint = "${month.year} 年 ${month.monthValue} 月的交易明细，表格软件可直接打开",
                buttonText = "选择位置并导出 CSV",
                onClick = {
                    pendingCsvMonth = month
                    csvLauncher.launch(vm.csvFileName(month))
                },
            )
            ExportEntry(
                title = "导出 JSON（全量）",
                hint = "全部账户、分类、交易（含已删除）与设置，可用于换机或再次导入",
                buttonText = "选择位置并导出 JSON",
                onClick = { jsonLauncher.launch(vm.jsonFileName()) },
            )
        }
    }
}

/** 单个导出入口：标题 + 说明 + 按钮（文件名由 ViewModel 生成，实际落盘位置由用户在 SAF 中选）。 */
@Composable
private fun ExportEntry(title: String, hint: String, buttonText: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(buttonText) }
    }
}

/** SAF URI 写入（UTF-8），IO 线程执行；失败返回 false 由调用方提示。 */
private suspend fun writeToUri(context: Context, uri: Uri, text: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: error("openOutputStream returned null")
        }.isSuccess
    }
