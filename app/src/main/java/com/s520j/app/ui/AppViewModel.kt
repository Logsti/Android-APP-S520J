package com.s520j.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.s520j.app.data.connectivity.NetworkStateTracker
import com.s520j.app.data.export.ExportBuilder
import com.s520j.app.data.local.BindState
import com.s520j.app.data.local.NotificationEntity
import com.s520j.app.data.local.SpaceEntity
import com.s520j.app.data.model.ContentModule
import com.s520j.app.data.model.ContentRecord
import com.s520j.app.data.model.ModuleGroup
import com.s520j.app.data.model.Visibility
import com.s520j.app.data.prefs.SessionProfile
import com.s520j.app.data.prefs.UserPreferences
import com.s520j.app.data.repository.AuthRepository
import com.s520j.app.data.repository.AuthResult
import com.s520j.app.data.repository.ContentRepository
import com.s520j.app.data.repository.SpaceRepository
import com.s520j.app.data.space.SpaceStateMachine
import com.s520j.app.domain.heart.ChallengeState
import com.s520j.app.domain.heart.PhotoFeed
import com.s520j.app.domain.heart.PokeAddResult
import com.s520j.app.domain.heart.PokeCatalog
import com.s520j.app.domain.us.ReminderCounts
import com.s520j.app.domain.us.ReminderDigest
import com.s520j.app.domain.us.ReminderSetting
import com.s520j.app.ui.util.today
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 顶栏提示：文本 + 可选动作（用于保存后「去看看」直达）。 */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val targetModule: ContentModule? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val contentRepository: ContentRepository,
    private val authRepository: AuthRepository,
    private val spaceRepository: SpaceRepository,
    private val notificationDao: com.s520j.app.data.local.NotificationDao,
    private val networkTracker: NetworkStateTracker
) : ViewModel() {

    val session: StateFlow<SessionProfile> = prefs.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionProfile("", "你", "", "TA", 0L, false))

    val fontScale: StateFlow<Float> = prefs.fontScale
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

    /** 本机提醒偏好：时间 + 三类开关（V1 仅本机一份）。 */
    val reminder: StateFlow<ReminderSetting> = prefs.reminderSetting
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReminderSetting())

    /** 新内容默认可见范围，默认仅自己可见。 */
    val defaultVisibility: StateFlow<Visibility> = prefs.defaultVisibility
        .stateIn(viewModelScope, SharingStarted.Eagerly, Visibility.PRIVATE)

    /** 双方头像图片 URI（为空时显示昵称首字）。 */
    val myAvatar: StateFlow<String?> = prefs.myAvatar
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val partnerAvatar: StateFlow<String?> = prefs.partnerAvatar
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 进入 APP 时按当天日期与本机提醒开关汇总待提醒项。 */
    val todayReminders: StateFlow<ReminderCounts> = combine(
        contentRepository.observeAll(),
        prefs.reminderSetting
    ) { records, setting ->
        ReminderDigest.counts(records, setting, today().toEpochDay())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReminderCounts())

    val greeting: StateFlow<String> = prefs.greeting
        .stateIn(viewModelScope, SharingStarted.Eagerly, "今天也要好好吃饭，好好想你。")

    /** 今日心动挑战：按天重置，换一换偏移与双方确认均持久化。 */
    val challenge: StateFlow<ChallengeState> = prefs.challengeState
        .map { ChallengeState.forDay(ChallengeState.decode(it), today().toEpochDay()) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ChallengeState.forDay(null, today().toEpochDay())
        )

    /** 自定义碰碰你动作（默认动作由领域层拼接）。 */
    val customPokes: StateFlow<List<String>> = prefs.customPokes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val space: StateFlow<SpaceEntity?> = spaceRepository.observeSpace()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 冻结归档时为只读；无空间时保持可编辑。 */
    val isReadOnly: StateFlow<Boolean> = spaceRepository.observeSpace()
        .map { spaceRepository.isReadOnly(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _bindFailure = MutableStateFlow<String?>(null)

    /** 本次登录内最近一次绑定失败原因（可清除）。 */
    val bindFailure: StateFlow<String?> = _bindFailure

    val notifications: StateFlow<List<NotificationEntity>> = notificationDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unreadCount: StateFlow<Int> = notificationDao.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // 用 Channel 而不是 SharedFlow：登录/注册成功瞬间界面会从 AuthScreen 切到 MainShell，
    // SharedFlow 在该窗口内没有订阅者时消息会被丢弃；Channel 会把消息缓存到下个订阅者出现。
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private suspend fun send(text: String) {
        _messages.send(UiMessage(text))
    }

    fun contents(module: ContentModule) = contentRepository.observeModule(module)

    fun allContents() = contentRepository.observeAll()

    fun recycleBin() = contentRepository.observeRecycleBin()

    fun notify(message: String) {
        viewModelScope.launch { send(message) }
    }

    // ---------- 会话与账号 ----------

    fun register(accountId: String, nickname: String, recoveryCode: String) = viewModelScope.launch {
        if (!networkTracker.currentConnected()) {
            send("网络不可用，请检查后重试")
            return@launch
        }
        try {
            when (val result = authRepository.register(accountId, nickname, recoveryCode)) {
                is AuthResult.Failure -> send(result.message)
                is AuthResult.Success -> {
                    seedContent()
                    send("注册成功，账号 ID：${result.accountId}")
                }
            }
        } catch (e: Exception) {
            send("注册失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun login(accountId: String, recoveryCode: String) = viewModelScope.launch {
        if (!networkTracker.currentConnected()) {
            send("网络不可用，请检查后重试")
            return@launch
        }
        try {
            when (val result = authRepository.login(accountId, recoveryCode)) {
                is AuthResult.Failure -> send(result.message)
                is AuthResult.Success -> send("欢迎回来，${result.nickname}")
            }
        } catch (e: Exception) {
            send("登录失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun changeRecoveryCode(oldCode: String, newCode: String) = viewModelScope.launch {
        if (oldCode == newCode) {
            send("新旧密码不能相同")
            return@launch
        }
        when (val result = authRepository.changeRecoveryCode(oldCode, newCode)) {
            is AuthResult.Failure -> send(result.message)
            is AuthResult.Success -> send("密码已更新")
        }
    }

    fun logout() = viewModelScope.launch {
        spaceRepository.currentSpace()?.let {
            spaceRepository.freeze(it, System.currentTimeMillis())
        }
        authRepository.logout()
        send("已退出登录")
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
        if (snapshot.loggedIn) {
            seedContent()
            if (!prefs.taxonomyMigratedSnapshot()) {
                contentRepository.migrateTaxonomyV2()
                prefs.markTaxonomyMigrated()
            }
        }
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
        mediaIds: List<String>? = null,
        extra: Map<String, String> = emptyMap()
    ) = viewModelScope.launch {
        if (isReadOnly.value) {
            send(READ_ONLY_MESSAGE)
            return@launch
        }
        if (title.isBlank()) {
            send("请填写标题")
            return@launch
        }
        val snapshot = prefs.sessionSnapshot()
        val extraMap = buildMap {
            putAll(existing?.extra ?: emptyMap())
            putAll(extra)
            if (rating > 0) put("星级", rating.toString())
        }
        val record = ContentRecord(
            id = existing?.id ?: "rec-${UUID.randomUUID()}",
            module = module,
            spaceId = space.value?.id,
            ownerId = snapshot.userId.ifBlank { "local-user" },
            ownerName = snapshot.nickname,
            visibility = visibility,
            title = title.trim(),
            content = content.trim(),
            eventDate = eventDate,
            mediaIds = mediaIds ?: existing?.mediaIds ?: emptyList(),
            extra = extraMap,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            deletedAt = null,
            version = (existing?.version ?: 0) + 1
        )
        contentRepository.save(record)
        if (existing == null) {
            _messages.send(
                UiMessage(
                    text = receiptOf(record),
                    actionLabel = "去看看",
                    targetModule = record.module
                )
            )
        } else {
            send("已更新")
        }
    }

    /** 保存回执：与分类常量同源，明确落到哪个页面。 */
    private fun receiptOf(record: ContentRecord): String {
        val page = record.module.group.label
        val category = PhotoFeed.categoryOf(record)
            ?.takeIf { record.module.group == ModuleGroup.GALLERY }
        return "已记入 $page · ${category ?: record.module.label}"
    }

    /** 心愿卡片上直接流转状态，例如「标记实现」。 */
    fun setWishStatus(record: ContentRecord, status: String) = viewModelScope.launch {
        if (isReadOnly.value) {
            send(READ_ONLY_MESSAGE)
            return@launch
        }
        contentRepository.save(com.s520j.app.domain.heart.WishBoard.withStatus(record, status))
        send("已标记为$status")
    }

    fun deleteRecord(id: String) = viewModelScope.launch {
        if (isReadOnly.value) {
            send(READ_ONLY_MESSAGE)
            return@launch
        }
        contentRepository.softDelete(id)
        send("已移入回收站，保留 30 天")
    }

    fun restoreRecord(id: String) = viewModelScope.launch {
        if (isReadOnly.value) {
            send(READ_ONLY_MESSAGE)
            return@launch
        }
        contentRepository.restore(id)
        send("已恢复")
    }

    fun purgeRecord(id: String) = viewModelScope.launch {
        if (isReadOnly.value) {
            send(READ_ONLY_MESSAGE)
            return@launch
        }
        contentRepository.purge(id)
        send("已彻底删除")
    }

    // ---------- 共同空间 ----------

    fun createSpace(name: String, intro: String) = viewModelScope.launch {
        val snapshot = prefs.sessionSnapshot()
        spaceRepository.createSpace(name, intro, snapshot)
        _bindFailure.value = null
        send("共同空间已创建")
    }

    fun shareInvite() = viewModelScope.launch {
        val current = space.value ?: return@launch
        spaceRepository.shareInvite(current, System.currentTimeMillis())
        _bindFailure.value = null
        send("已分享给对方，开始等待对方加入")
    }

    fun refreshBindCode() = viewModelScope.launch {
        val current = space.value ?: return@launch
        spaceRepository.refreshBindCode(current, System.currentTimeMillis())
        _bindFailure.value = null
        send("绑定码已刷新，24 小时内有效")
    }

    // Mock 阶段：单机只有一份本地库，输入绑定码一律按“模拟对方提交”处理，
    // 这样创建者本人才能完成「我方确认 + 对方确认」两次操作；真实跨设备加入在云端阶段实现。
    fun submitBindCode(code: String) = viewModelScope.launch {
        val createdAt = System.currentTimeMillis()
        val result = spaceRepository.submitBindCode(
            code = code,
            applicantId = SIM_PARTNER_ID,
            applicantName = "TA",
            now = createdAt
        )
        _bindFailure.value = result.error
        if (result.error == null) {
            send("已提交绑定申请，请双方各确认一次")
        }
    }

    fun clearBindFailure() {
        _bindFailure.value = null
    }

    fun confirmBindAsOwner() = viewModelScope.launch {
        val current = space.value ?: return@launch
        val snapshot = prefs.sessionSnapshot()
        val updated = spaceRepository.confirmBind(
            space = current,
            actorId = snapshot.userId,
            partnerNickname = snapshot.partnerNickname.ifBlank { "TA" },
            now = System.currentTimeMillis()
        )
        send(bindConfirmMessage(updated, simulated = false))
    }

    fun confirmBindAsPartner() = viewModelScope.launch {
        val current = space.value ?: return@launch
        val snapshot = prefs.sessionSnapshot()
        val updated = spaceRepository.confirmBind(
            space = current,
            actorId = current.partnerUserId ?: SIM_PARTNER_ID,
            partnerNickname = snapshot.partnerNickname.ifBlank { "TA" },
            now = System.currentTimeMillis()
        )
        send(bindConfirmMessage(updated, simulated = true))
    }

    fun requestUnbind() = viewModelScope.launch {
        val current = space.value ?: return@launch
        spaceRepository.requestUnbind(
            space = current,
            requesterId = prefs.sessionSnapshot().userId,
            now = System.currentTimeMillis()
        )
        send("已发起解绑，等待双方各确认一次")
    }

    fun confirmUnbindAsOwner() = viewModelScope.launch {
        val current = space.value ?: return@launch
        val updated = spaceRepository.confirmUnbind(
            space = current,
            actorId = prefs.sessionSnapshot().userId,
            now = System.currentTimeMillis()
        )
        send(unbindConfirmMessage(updated, simulated = false))
    }

    fun confirmUnbindAsPartner() = viewModelScope.launch {
        val current = space.value ?: return@launch
        val updated = spaceRepository.confirmUnbind(
            space = current,
            actorId = current.partnerUserId ?: SIM_PARTNER_ID,
            now = System.currentTimeMillis()
        )
        send(unbindConfirmMessage(updated, simulated = true))
    }

    fun rebind() = viewModelScope.launch {
        val current = space.value ?: return@launch
        spaceRepository.rebind(current, System.currentTimeMillis())
        _bindFailure.value = null
        send("空间已解冻，历史内容保留")
    }

    /** 导出 JSON：合并正常内容与回收站内容，按 id 去重后回调到主线程。 */
    fun exportJson(onReady: (String) -> Unit) = viewModelScope.launch {
        val merged = (contentRepository.observeAll().first() + contentRepository.observeRecycleBin().first())
            .distinctBy { it.id }
        val json = ExportBuilder.build(
            space = space.value,
            session = prefs.sessionSnapshot(),
            records = merged,
            exportedAt = System.currentTimeMillis()
        )
        onReady(json)
    }

    fun bindState(): BindState? = SpaceStateMachine.stateOf(space.value?.bindState)

    private fun bindConfirmMessage(updated: SpaceEntity, simulated: Boolean): String = when {
        SpaceStateMachine.stateOf(updated.bindState) == BindState.BOUND -> "已绑定，双方账号各自独立"
        simulated -> "对方已确认（本地演示），等待我方确认"
        else -> "我方已确认，等待对方确认"
    }

    private fun unbindConfirmMessage(updated: SpaceEntity, simulated: Boolean): String = when {
        SpaceStateMachine.stateOf(updated.bindState) == BindState.FROZEN -> "已解绑，空间进入冻结归档"
        simulated -> "对方已确认解绑（本地演示），等待我方确认"
        else -> "我方已确认解绑，等待对方确认"
    }

    private companion object {
        const val READ_ONLY_MESSAGE = "空间已冻结归档，当前为只读状态，无法编辑共同内容"
        const val SIM_PARTNER_ID = "sim-partner"
    }

    // ---------- 设置 ----------

    fun setFontScale(value: Float) = viewModelScope.launch { prefs.setFontScale(value) }

    fun setGreeting(value: String) = viewModelScope.launch { prefs.setGreeting(value) }

    /** 更新本机提醒偏好。 */
    fun setReminder(setting: ReminderSetting) = viewModelScope.launch {
        prefs.setReminderSetting(setting)
    }

    fun setDefaultVisibility(value: Visibility) = viewModelScope.launch {
        prefs.setDefaultVisibility(value)
    }

    fun setMyAvatar(uri: String?) = viewModelScope.launch {
        prefs.setMyAvatar(uri)
    }

    fun setPartnerAvatar(uri: String?) = viewModelScope.launch {
        prefs.setPartnerAvatar(uri)
    }

    // ---------- 心动页：今日挑战与碰碰你 ----------

    fun rotateChallenge() = updateChallenge { it.rotate() }

    fun toggleChallengeSelf() = updateChallenge { it.toggleSelf() }

    fun toggleChallengePartner() = updateChallenge { it.togglePartner() }

    private fun updateChallenge(transform: (ChallengeState) -> ChallengeState) = viewModelScope.launch {
        if (isReadOnly.value) {
            send(READ_ONLY_MESSAGE)
            return@launch
        }
        val epochDay = today().toEpochDay()
        val current = ChallengeState.forDay(ChallengeState.decode(prefs.challengeSnapshot()), epochDay)
        prefs.setChallengeState(transform(current).encode())
    }

    fun addCustomPoke(name: String) = viewModelScope.launch {
        val existing = prefs.customPokesSnapshot()
        when (PokeCatalog.validate(existing, name)) {
            PokeAddResult.EMPTY -> send("请填写动作名称")
            PokeAddResult.TOO_LONG -> send("动作名称最多 ${PokeCatalog.MAX_NAME_LENGTH} 个字")
            PokeAddResult.DUPLICATE -> send("这个动作已经有了")
            PokeAddResult.OK -> {
                prefs.setCustomPokes(PokeCatalog.add(existing, name))
                send("已添加「${name.trim()}」")
            }
        }
    }

    fun removeCustomPoke(name: String) = viewModelScope.launch {
        prefs.setCustomPokes(PokeCatalog.remove(prefs.customPokesSnapshot(), name))
        send("已删除「$name」")
    }

    /** 发送碰碰你：写入一条未读互动通知，供通知中心查看。 */
    fun sendPoke(name: String) = viewModelScope.launch {
        val snapshot = prefs.sessionSnapshot()
        val partner = snapshot.partnerNickname.ifBlank { "TA" }
        notificationDao.upsert(
            NotificationEntity(
                id = "poke-${UUID.randomUUID()}",
                title = "碰碰你",
                body = "你送给 $partner 一个「$name」",
                createdAt = System.currentTimeMillis(),
                read = false
            )
        )
        send("已发送「$name」")
    }

    fun markAllRead() = viewModelScope.launch { notificationDao.markAllRead() }

    fun markRead(id: String) = viewModelScope.launch { notificationDao.markRead(id) }
}
