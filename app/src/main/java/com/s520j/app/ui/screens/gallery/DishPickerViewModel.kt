package com.s520j.app.ui.screens.gallery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.s520j.app.data.cloud.CloudRequestException
import com.s520j.app.data.cloud.DishHistory
import com.s520j.app.data.cloud.DishHistoryRepository
import com.s520j.app.data.cloud.DishRandomRepository
import com.s520j.app.data.media.DishHeaderStore
import com.s520j.app.data.prefs.UserPreferences
import com.s520j.app.domain.food.BuiltinDishes
import com.s520j.app.domain.food.DishCandidate
import com.s520j.app.domain.food.DishGroup
import com.s520j.app.domain.food.DishPicker
import com.s520j.app.domain.food.DishPickerState
import com.s520j.app.domain.food.DishTaxonomy
import com.s520j.app.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 「就吃这一桌」的确认进度：只有确认成功才写入云端「点过的菜」。 */
enum class ConfirmState { PENDING, SUBMITTING, CONFIRMED, FAILED }

/**
 * 今天吃啥：领域状态 + 云端抽页 + 内置兜底 + 点过的菜 + 自定义头图。
 * 取菜顺序：实时 → 云端菜谱池（服务端降级） → 内置 90 道。
 * 随机结果不写入记录，仅在用户确认这一桌时提交云端。
 * 所有网络异常都转为可见提示或降级结果，不向界面抛异常。
 */
@HiltViewModel
class DishPickerViewModel @Inject constructor(
    private val random: DishRandomRepository,
    private val history: DishHistoryRepository,
    private val prefs: UserPreferences,
    private val headerStore: DishHeaderStore
) : ViewModel() {

    data class UiState(
        val picker: DishPickerState = DishPicker.initialState(),
        val categories: Map<DishGroup, List<String>> = DishTaxonomy.fallbackCategories,
        val history: DishHistory = DishHistory(),
        val confirmState: ConfirmState = ConfirmState.PENDING,
        val loading: Boolean = false,
        val swappingName: String? = null,
        /** 加载失败且降级也凑不出一桌时的可重试文案。 */
        val error: String? = null,
        val offline: Boolean = false,
        val headerPath: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            // 开关是本机偏好；记录只从云端拉取，本机不缓存。
            val preferNew = prefs.dishPreferNewSnapshot()
            _state.update {
                it.copy(
                    picker = it.picker.copy(preferNew = preferNew),
                    history = DishHistory(preferNew = preferNew)
                )
            }
            val remote = history.refresh() ?: return@launch
            _state.update {
                it.copy(history = remote, picker = it.picker.copy(preferNew = remote.preferNew))
            }
        }
        viewModelScope.launch {
            prefs.dishHeaderPath.collect { path -> _state.update { it.copy(headerPath = path) } }
        }
    }

    fun setCount(group: DishGroup, count: Int) {
        _state.update { it.copy(picker = DishPicker.updateCount(it.picker, group, count), error = null) }
    }

    fun setSubCategory(group: DishGroup, subCategory: String) {
        _state.update { it.copy(picker = DishPicker.updateSub(it.picker, group, subCategory), error = null) }
    }

    /** 随机点菜 / 换一桌：四组份数 + 组内筛选 + 最近点过。 */
    fun pick() = viewModelScope.launch {
        val current = _state.value.picker
        val invalid = DishPicker.validation(current)
        if (invalid != null) {
            toast(invalid)
            return@launch
        }
        if (_state.value.loading) return@launch
        _state.update { it.copy(loading = true, error = null) }
        val recent = _state.value.history.recentNames()
        try {
            val result = random.pick(DishPicker.request(current, recent = recent))
            val next = DishPicker.applyRemote(current, result)
            _state.update {
                it.copy(
                    picker = next,
                    loading = false,
                    offline = false,
                    confirmState = ConfirmState.PENDING
                )
            }
            if (next.table.isEmpty()) {
                _state.update { it.copy(error = "这次没有抽到菜，换个条件再试试") }
            } else {
                next.note?.let { toast(it) }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            fallback(current, e)
        }
    }

    /** 换一桌：按同一条件重抽，尽量避开当前这桌。 */
    fun newTable() = viewModelScope.launch {
        _state.update {
            it.copy(picker = DishPicker.newTable(it.picker), confirmState = ConfirmState.PENDING)
        }
        pick()
    }

    /** 单道换菜：优先本次候选池，不足才补抽 1 次。 */
    fun swap(target: DishCandidate) = viewModelScope.launch {
        val current = _state.value.picker
        if (_state.value.swappingName != null) return@launch
        val pooled = DishPicker.swapFromPool(current, target)
        if (pooled != null) {
            _state.update { it.copy(picker = pooled, confirmState = ConfirmState.PENDING) }
            return@launch
        }
        _state.update { it.copy(swappingName = target.name) }
        try {
            val result = random.pick(DishPicker.swapRequest(current, target, _state.value.history.recentNames()))
            val swapped = DishPicker.applySwapResult(current, target, result.items)
            if (swapped == null) {
                toast("暂时没有更多可换的菜")
            } else {
                _state.update { it.copy(picker = swapped, confirmState = ConfirmState.PENDING) }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val local = BuiltinDishes.all.filter { it.group == target.group }
            val swapped = DishPicker.applySwapResult(current, target, local)
            if (swapped != null) {
                _state.update { it.copy(picker = swapped, confirmState = ConfirmState.PENDING) }
                toast("网络不可用，已换成内置菜品")
            } else {
                toast("暂时没有更多可换的菜")
            }
        } finally {
            _state.update { it.copy(swappingName = null) }
        }
    }

    fun togglePreferNew() = viewModelScope.launch {
        val next = !_state.value.picker.preferNew
        _state.update {
            it.copy(
                picker = it.picker.copy(preferNew = next),
                history = it.history.copy(preferNew = next)
            )
        }
        history.setPreferNew(next)
    }

    /** 确认这一桌：只写云端，失败可再点一次重试。 */
    fun confirmTable() = viewModelScope.launch {
        val current = _state.value
        if (current.confirmState == ConfirmState.SUBMITTING ||
            current.confirmState == ConfirmState.CONFIRMED
        ) {
            return@launch
        }
        if (current.picker.table.isEmpty()) return@launch
        _state.update { it.copy(confirmState = ConfirmState.SUBMITTING) }
        val confirmed = history.record(current.picker.table)
        if (confirmed == null) {
            _state.update { it.copy(confirmState = ConfirmState.FAILED) }
            toast("这次没记上，点重试补记")
        } else {
            _state.update { it.copy(history = confirmed, confirmState = ConfirmState.CONFIRMED) }
            toast("已记入点过的菜")
        }
    }

    fun clearHistory() = viewModelScope.launch {
        val cleared = history.clear()
        if (!cleared) {
            toast("清空失败，请检查网络后重试")
            return@launch
        }
        _state.update { it.copy(history = it.history.copy(picked = emptyList())) }
        toast("已清空点过的菜")
    }

    fun retry() = pick()

    fun setHeader(uri: Uri) = viewModelScope.launch {
        val path = headerStore.save(uri)
        if (path.isNullOrBlank()) {
            toast("图片处理失败，请换一张")
            return@launch
        }
        prefs.setDishHeaderPath(path)
        toast("头图已更新")
    }

    fun resetHeader() = viewModelScope.launch {
        headerStore.deleteCustom()
        prefs.setDishHeaderPath(null)
        toast("已恢复默认头图")
    }

    /** 云端失败：云端池不可用，降级到内置 90 道凑一桌，并标注离线/降级。 */
    private suspend fun fallback(current: DishPickerState, cause: Throwable) {
        val offline = cause is IOException && cause !is CloudRequestException
        val candidates = BuiltinDishes.all.filter { current.countOf(it.group) > 0 }
        val local = DishPicker.fillLocal(current, candidates, offline = offline)
        val message = when {
            cause is CloudRequestException && cause.code == 401 -> "登录已失效，请重新登录"
            offline -> "网络不可用，已用内置菜品凑一桌"
            else -> "云端暂时不可用，已用内置菜品凑一桌"
        }
        _state.update {
            it.copy(
                picker = local,
                loading = false,
                offline = offline,
                confirmState = ConfirmState.PENDING,
                error = if (local.table.isEmpty()) message else null
            )
        }
        if (local.table.isEmpty()) {
            toast(message)
        } else {
            toast(local.note ?: message)
        }
    }

    private suspend fun toast(text: String) {
        if (text.isBlank()) return
        _messages.send(UiMessage(text))
    }
}
