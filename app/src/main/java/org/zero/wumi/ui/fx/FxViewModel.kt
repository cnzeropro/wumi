package org.zero.wumi.ui.fx

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.zero.wumi.data.local.entity.CurrencyEntity
import org.zero.wumi.data.repo.CurrencyRepository
import org.zero.wumi.work.applyAutoSync
import javax.inject.Inject

/** 汇率管理页 UI 状态：全部币种（CNY 基准在内）+ 自动拉取开关。 */
data class FxUiState(
    val currencies: List<CurrencyEntity> = emptyList(),
    val autoSync: Boolean = true,
)

/**
 * 汇率管理 ViewModel：订阅币种流（Room 唯一真相源），代理手改/立即更新/自动拉取开关。
 *
 * autoSync 状态：repo 无现成 Flow，VM 本地 [_autoSync] 持有——init 读一次真实开关，
 * setAutoSync 后本地同步更新并落库联动 WorkManager；开屏瞬间极快切换的极端竞态可忽略。
 * 刷新结果经 [message] 单次事件回传 Snackbar。
 */
@HiltViewModel
class FxViewModel @Inject constructor(
    private val repo: CurrencyRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _autoSync = MutableStateFlow(true)

    /** 用户是否已手动切换过开关：init 的慢读不得覆盖之后的手动选择。 */
    private var autoSyncTouched = false

    /** 自动拉取开关（UI 直读；[state].autoSync 同源）。 */
    val autoSync: StateFlow<Boolean> = _autoSync.asStateFlow()

    private val _refreshing = MutableStateFlow(false)

    /** 「立即更新」进行中：按钮禁用 + 内嵌进度圈。 */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = Channel<String>(Channel.BUFFERED)

    /** 一次性提示（刷新成功/失败），由页面 Snackbar 消费。 */
    val message: Flow<String> = _message.receiveAsFlow()

    /** 币种列表 + 开关合并态。 */
    val state: StateFlow<FxUiState> =
        combine(repo.observeAll(), _autoSync) { list, auto -> FxUiState(list, auto) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FxUiState())

    init {
        viewModelScope.launch {
            val on = repo.autoSyncEnabled()
            if (!autoSyncTouched) _autoSync.value = on
        }
    }

    /** 手改汇率；false = 非法（UI 前置校验已拦，此处兜底双保险）。 */
    suspend fun updateRate(code: String, rate: String): Boolean = repo.updateRate(code, rate)

    /** 立即拉取全部币种（阻塞 IO 已由 repo 切 IO 调度器）；结果以 Snackbar 文案发出。 */
    fun refresh(): Job = viewModelScope.launch {
        _refreshing.value = true
        try {
            repo.refreshAll().fold(
                onSuccess = { n -> _message.send("已更新 $n 个币种") },
                onFailure = { _message.send("更新失败，请检查网络") },
            )
        } finally {
            _refreshing.value = false
        }
    }

    /** 切换自动拉取：本地即时生效，落库后联动 WorkManager 入队/取消每周任务；返回写库任务可 join 等待。 */
    fun setAutoSync(enabled: Boolean): Job {
        autoSyncTouched = true
        _autoSync.value = enabled
        return viewModelScope.launch {
            repo.setAutoSync(enabled)
            applyAutoSync(context, repo)
        }
    }
}
