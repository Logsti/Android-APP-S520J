package com.s520j.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.s520j.app.data.local.BindState
import com.s520j.app.data.local.NotificationEntity
import com.s520j.app.data.local.SpaceEntity
import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.Visibility
import com.s520j.app.data.prefs.SessionProfile
import com.s520j.app.data.prefs.UserPreferences
import com.s520j.app.data.repository.AuthRepository
import com.s520j.app.data.repository.AuthResult
import com.s520j.app.data.repository.ContentRepository
import com.s520j.app.data.repository.SpaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val contentRepository: ContentRepository,
    private val authRepository: AuthRepository,
    private val spaceRepository: SpaceRepository,
    private val notificationDao: com.s520j.app.data.local.NotificationDao
) : ViewModel() {

    val session: StateFlow<SessionProfile> = prefs.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionProfile("", "你", "", "TA", 0L, false))

    val fontScale: StateFlow<Float> = prefs.fontScale
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

    val greeting: StateFlow<String> = prefs.greeting
        .stateIn(viewModelScope, SharingStarted.Eagerly, "今天也要好好喜欢彼此。")

    val space: StateFlow<SpaceEntity?> = spaceRepository.observeSpace()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val notifications: StateFlow<List<NotificationEntity>> = notificationDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unreadCount: StateFlow<Int> = notificationDao.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // 用 Channel 而不是 SharedFlow：登录/注册成功瞬间界面会从 AuthScreen 切到 MainShell，
    // SharedFlow 在该窗口内没有订阅者时消息会被丢弃；Channel 会把消息缓存到下个订阅者出现。
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun contents(module: ContentModule) = contentRepository.observeModule(module)

    fun allContents() = contentRepository.observeAll()

    fun recycleBin() = contentRepository.observeRecycleBin()

    fun notify(message: String) {
        viewModelScope.launch { _messages.send(message) }
    }

    // ---------- 会话与账号 ----------

    fun register(accountId: String, nickname: String, recoveryCode: String) = viewModelScope.launch {
        try {
            when (val result = authRepository.register(accountId, nickname, recoveryCode)) {
                is AuthResult.Failure -> _messages.send(result.message)
                is AuthResult.Success -> {
                    seedContent()
                    _messages.send("注册成功，账号 ID：${result.accountId}")
                }
            }
        } catch (e: Exception) {
            _messages.send("注册失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun login(accountId: String, recoveryCode: String) = viewModelScope.launch {
        try {
            when (val result = authRepository.login(accountId, recoveryCode)) {
                is AuthResult.Failure -> _messages.send(result.message)
                is AuthResult.Success -> _messages.send("欢迎回来，${result.nickname}")
            }
        } catch (e: Exception) {
            _messages.send("登录失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun changeRecoveryCode(oldCode: String, newCode: String) = viewModelScope.launch {
        when (val result = authRepository.changeRecoveryCode(oldCode, newCode)) {
            is AuthResult.Failure -> _messages.send(result.message)
            is AuthResult.Success -> _messages.send("密码已更新")
        }
    }

    fun logout() = viewModelScope.launch {
        spaceRepository.currentSpace()?.let { spaceRepository.confirmUnbind(it) }
        authRepository.logout()
        _messages.send("已退出登录")
    }

    private suspend fun seedContent() {
        val snapshot = prefs.sessionSnapshot()
        contentRepository.seedIfEmpty(snapshot.userId, snapshot.nickname)
        if (notifications.value.isEmpty()) {
            notificationDao.upsert(
                NotificationEntity(
                    id = "welcome",
                    title = "S520J 已准备好",
                    body = "把今天的小事记下来，日子会慢慢有光。",
                    createdAt = System.currentTimeMillis(),
                    read = false
                )
            )
        }
    }

    fun seedIfNeeded() = viewModelScope.launch {
        val snapshot = prefs.sessionSnapshot()
        if (snapshot.loggedIn) seedContent()
    }

    // ---------- 内容 ----------

    fun saveRecord(
        existing: ContentRecord?,
        module: ContentModule,
        title: String,
        content: String,
        visibility: Visibility,
        eventDate: Long?,
        rating: Int,
        photoType: String,
        extra: Map<String, String> = emptyMap()
    ) = viewModelScope.launch {
        if (title.isBlank()) {
            _messages.send("请填写标题")
            return@launch
        }
        val snapshot = prefs.sessionSnapshot()
        val extraMap = buildMap {
            putAll(existing?.extra ?: emptyMap())
            putAll(extra)
            if (rating > 0) put("星级", rating.toString())
            if (photoType.isNotBlank()) put("category", photoType)
        }
        val record = ContentRecord(
            id = existing?.id ?: "rec-${UUID.randomUUID()}",
            module = module,
            spaceId = "space-local",
            ownerId = snapshot.userId.ifBlank { "local-user" },
            ownerName = snapshot.nickname,
            visibility = visibility,
            title = title.trim(),
            content = content.trim(),
            eventDate = eventDate,
            mediaIds = existing?.mediaIds ?: emptyList(),
            extra = extraMap,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            deletedAt = null,
            version = (existing?.version ?: 0) + 1
        )
        contentRepository.save(record)
        _messages.send(if (existing == null) "已保存" else "已更新")
    }

    fun deleteRecord(id: String) = viewModelScope.launch {
        contentRepository.softDelete(id)
        _messages.send("已移入回收站，保留 30 天")
    }

    fun restoreRecord(id: String) = viewModelScope.launch {
        contentRepository.restore(id)
        _messages.send("已恢复")
    }

    fun purgeRecord(id: String) = viewModelScope.launch {
        contentRepository.purge(id)
        _messages.send("已彻底删除")
    }

    // ---------- 共同空间 ----------

    fun createSpace(name: String, intro: String) = viewModelScope.launch {
        val snapshot = prefs.sessionSnapshot()
        spaceRepository.createSpace(name, intro, snapshot)
        _messages.send("共同空间已创建")
    }

    fun refreshBindCode() = viewModelScope.launch {
        space.value?.let { spaceRepository.refreshBindCode(it) }
    }

    fun submitBindCode(code: String) = viewModelScope.launch {
        val snapshot = prefs.sessionSnapshot()
        val (spaceEntity, error) = spaceRepository.submitBindCode(code, snapshot.userId)
        if (error != null) _messages.send(error)
        if (spaceEntity != null && error == null) _messages.send("已提交绑定申请，请双方各确认一次")
    }

    fun confirmBind() = viewModelScope.launch {
        val current = space.value ?: return@launch
        spaceRepository.confirmBind(current, prefs.sessionSnapshot().partnerNickname.ifBlank { "TA" })
        _messages.send("已绑定")
    }

    fun requestUnbind() = viewModelScope.launch {
        space.value?.let { spaceRepository.requestUnbind(it) }
        _messages.send("已发起解绑，等待对方确认")
    }

    fun confirmUnbind() = viewModelScope.launch {
        space.value?.let { spaceRepository.confirmUnbind(it) }
        _messages.send("已解绑，空间进入冻结归档")
    }

    fun rebind() = viewModelScope.launch {
        space.value?.let { spaceRepository.rebind(it) }
        _messages.send("空间已解冻，请重新绑定")
    }

    fun bindState(): BindState? = space.value?.bindState?.let { raw ->
        BindState.entries.firstOrNull { it.name == raw }
    }

    // ---------- 设置 ----------

    fun setFontScale(value: Float) = viewModelScope.launch { prefs.setFontScale(value) }

    fun setGreeting(value: String) = viewModelScope.launch { prefs.setGreeting(value) }

    fun markAllRead() = viewModelScope.launch { notificationDao.markAllRead() }

    fun markRead(id: String) = viewModelScope.launch { notificationDao.markRead(id) }
}
