package org.zero.wumi.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.zero.wumi.data.repo.TransactionRepository
import javax.inject.Inject

/** 撤销最近一笔：缓存 txId，撤销即软删。Activity 作用域（spec §7.2 Snackbar 撤销）。 */
@HiltViewModel
class UndoViewModel @Inject constructor(private val txRepo: TransactionRepository) : ViewModel() {
    private val _recentTxId = MutableStateFlow<String?>(null)

    /** 是否有可撤销的一笔（供 UI 决定是否展示撤销入口）。 */
    val hasUndoable: StateFlow<Boolean> =
        _recentTxId.map { it != null }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 缓存最近一笔的 txId，作为可撤销对象。 */
    fun record(id: String) { _recentTxId.value = id }

    /** 软删最近一笔并清空缓存（无缓存时为空操作，重复撤销无害）。 */
    fun undo() {
        _recentTxId.value?.let { id -> viewModelScope.launch { txRepo.softDelete(id) } }
        _recentTxId.value = null
    }
}
