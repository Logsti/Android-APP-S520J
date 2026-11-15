package com.s520j.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.s520j.app.data.connectivity.NetworkStateTracker
import com.s520j.app.data.couple.CoupleEvent
import com.s520j.app.data.couple.CoupleLiveRepository
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
import com.s520j.app.data.repository.LocalResetCoordinator
import com.s520j.app.data.repository.SpaceRepository
import com.s520j.app.data.space.SpaceStateMachine
import com.s520j.app.domain.heart.ChallengeState
import com.s520j.app.domain.heart.PhotoFeed
import com.s520j.app.domain.heart.PokeAddResult
import com.s520j.app.domain.heart.PokeCatalog
import com.s520j.app.domain.heart.WishBoard
import com.s520j.app.domain.us.ReminderCounts
import com.s520j.app.domain.us.ReminderDigest
import com.s520j.app.domain.us.ReminderSetting
import com.s520j.app.ui.util.today
import com.s520j.app.util.AvatarCompressor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
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
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences,
    private val localReset: LocalResetCoordinator,
    private val contentRepository: ContentRepository,
    private val authRepository: AuthRepository,
    private val spaceRepository: SpaceRepository,
    private val notificationDao: com.s520j.app.data.local.NotificationDao,
    private val networkTracker: NetworkStateTracker,
    private val cloudAuth: com.s520j.app.data.cloud.CloudAuthRepository,
    private val cloudSpace: com.s520j.app.data.cloud.CloudSpaceRepository,
    private val coupleLive: CoupleLiveRepository,
    private val mediaRepository: com.s520j.app.data.media.MediaRepository,
    private val backupService: com.s520j.app.data.backup.BackupService,
    private val coupleNotifier: com.s520j.app.notifications.CoupleNotifier,
    private val syncRepository: com.s520j.app.data.sync.SyncRepository
) : ViewModel() {

    private val _localResetReady = MutableStateFlow(false)
    val localResetReady: StateFlow<Boolean> = _localResetReady

    init {
        viewModelScope.launch {
            localReset.ensureCompleted()
            _localResetReady.value = true
        }
    }

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

    /** 自头像上传中：UI 禁用选择按钮并显示加载。 */
    private val _isAvatarUploading = MutableStateFlow(false)
    val isAvatarUploading: StateFlow<Boolean> = _isAvatarUploading

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

    /** 对方碰一碰 / 挑战达成的应用内横幅，自动 6 秒后消失。 */
    private val _pokeBanner = MutableStateFlow<CoupleEvent?>(null)
    val pokeBanner: StateFlow<CoupleEvent?> = _pokeBanner

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

    /** 云端同步状态（8B）：空闲/同步中/离线/失败 + 最近结果文案。 */
    val syncState: StateFlow<com.s520j.app.data.sync.SyncState> = syncRepository.state

    val pendingSyncCount: StateFlow<Int> = syncRepository.pendingCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** 冲突快照列表（8B.2）：由用户在同步设置页选择保留本机还是云端。 */
    val syncConflicts: StateFlow<List<com.s520j.app.data.local.SyncConflictEntity>> =
        syncRepository.conflicts
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 用 Channel 而不是 SharedFlow：登录/注册成功瞬间界面会从 AuthScreen 切到 MainShell，
    // SharedFlow 在该窗口内没有订阅者时消息会被丢弃；Channel 会把消息缓存到下个订阅者出现。
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private suspend fun send(text: String) {
        _messages.send(UiMessage(text))
    }

    /** 协程兜底：异常转成可见失败提示，绝不让异常逃逸导致 APP 闪退。 */
    private suspend fun reportFailure(action: String, t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        val detail = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
        val text = "$action：$detail"
        _bindFailure.value = text
        send(text)
    }

    fun contents(module: ContentModule) = contentRepository.observeModule(module)

    fun allContents() = contentRepository.observeAll()

    fun recycleBin() = contentRepository.observeRecycleBin()

    fun notify(message: String) {
        viewModelScope.launch { send(message) }
    }

    // ---------- 会话与账号 ----------

    fun register(accountId: String, nickname: String, recoveryCode: String) = viewModelScope.launch {
        localReset.ensureCompleted()
        if (!networkTracker.currentConnected()) {
            send("网络不可用，请检查后重试")
            return@launch
        }
        try {
            when (val cloud = cloudAuth.register(accountId, nickname, recoveryCode)) {
                is com.s520j.app.data.cloud.CloudAuthResult.Failure -> send(cloud.message)
                is com.s520j.app.data.cloud.CloudAuthResult.Success -> {
                    when (val result = authRepository.adoptCloudAccount(
                        accountId, cloud.nickname, recoveryCode, cloud.userId
                    )) {
                        is AuthResult.Failure -> send(result.message)
                        is AuthResult.Success -> {
                            seedContent()
                            syncRepository.schedulePeriodic()
                            syncRepository.syncNow()
                            send("注册成功，账号 ID：${result.accountId}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            send("注册失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun login(accountId: String, recoveryCode: String) = viewModelScope.launch {
        localReset.ensureCompleted()
        try {
            when (val cloud = cloudAuth.login(accountId, recoveryCode)) {
                is com.s520j.app.data.cloud.CloudAuthResult.Success -> {
                    when (val result = authRepository.adoptCloudAccount(
                        accountId, cloud.nickname, recoveryCode, cloud.userId
                    )) {
                        is AuthResult.Failure -> send(result.message)
                        is AuthResult.Success -> {
                            syncRepository.schedulePeriodic()
                            syncRepository.syncNow()
                            send("欢迎回来，${result.nickname}")
                        }
                    }
                }
                is com.s520j.app.data.cloud.CloudAuthResult.Failure -> {
                    if (cloud.offline) {
                        // 断网时按本机缓存登录，恢复网络后自动同步补齐。
                        when (val local = authRepository.login(accountId, recoveryCode)) {
                            is AuthResult.Failure -> send(cloud.message)
                            is AuthResult.Success -> send("当前离线，已使用本机数据登录")
                        }
                    } else {
                        send(cloud.message)
                    }
                }
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
        if (cloudAuth.hasSession()) {
            when (val cloud = cloudAuth.changePassword(oldCode, newCode)) {
                is com.s520j.app.data.cloud.CloudAuthResult.Failure -> {
                    // 云端修改失败时不做本机修改，避免两端密码不一致。
                    val text = if (cloud.offline) "网络不可用，请联网后重试" else cloud.message
                    send(text)
                    return@launch
                }
                is com.s520j.app.data.cloud.CloudAuthResult.Success -> Unit
            }
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
        syncRepository.cancelPeriodic()
        cloudAuth.logout()
        authRepository.logout()
        send("已退出登录")
    }

    /** 设置页「立即同步」/ 下拉刷新 / 状态条点击：强制同步一次。 */
    fun syncNow() = viewModelScope.launch {
        lastSyncAttemptAt = System.currentTimeMillis()
        syncRepository.syncNow()
        cloudSpace.refreshLocal()
        cloudAuth.fetchMe()
        syncCoupleLive()
    }

    // ---------- 9.3 同步可见化：前台自动轮询 ----------

    private var foregroundSyncJob: Job? = null
    private var lastSyncAttemptAt = 0L

    /** APP 进入前台：按 20 秒节拍自动同步，失败不打扰用户。 */
    fun onAppForeground() {
        if (foregroundSyncJob?.isActive == true) return
        foregroundSyncJob = viewModelScope.launch {
            while (isActive) {
                maybeSync(force = false)
                delay(com.s520j.app.data.sync.SyncPolicy.AUTO_SYNC_INTERVAL_MS)
            }
        }
    }

    /** APP 退到后台：立即停止前台轮询，避免无谓唤醒。 */
    fun onAppBackground() {
        foregroundSyncJob?.cancel()
        foregroundSyncJob = null
    }

    /** 主页面进入时同步一次（仍受 20 秒节流保护，避免切页频繁请求）。 */
    fun syncOnEnter() = viewModelScope.launch {
        maybeSync(force = false)
    }

    private suspend fun maybeSync(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!com.s520j.app.data.sync.SyncPolicy.shouldAutoSync(lastSyncAttemptAt, now, force)) return
        lastSyncAttemptAt = now
        // syncNow() 内部已捕获异常并把结果写进 state，这里不会再抛出。
        syncRepository.syncNow(automatic = true)
        cloudSpace.refreshLocal()
        syncCoupleLive()
    }

    /**
     * 同步每日挑战与双人事件：挑战状态以云端为准，事件拉到后横幅 + 系统通知。
     * 未绑定 / 离线时静默跳过，保持本地能力可用。
     */
    private suspend fun syncCoupleLive() {
        val epochDay = today().toEpochDay()
        coupleLive.getChallenge(epochDay)?.let { cloud ->
            val local = ChallengeState.forDay(ChallengeState.decode(prefs.challengeSnapshot()), epochDay)
            val merged = local.mergeCloud(epochDay, cloud.selfDone, cloud.partnerDone)
            if (merged.encode() != local.encode()) prefs.setChallengeState(merged.encode())
        }
        val events = coupleLive.pullEvents()
        if (events.isEmpty()) return
        val newEvents = mutableListOf<CoupleEvent>()
        events.forEach { event ->
            val challengeDone = event.type == CoupleEvent.TYPE_CHALLENGE_DONE
            val who = event.fromName.ifBlank { "TA" }
            val inserted = notificationDao.insertIfAbsent(
                NotificationEntity(
                    id = event.id,
                    title = if (challengeDone) "今日挑战达成" else "$who 碰了碰你",
                    body = if (challengeDone) "你们俩今天都完成了，真好。"
                    else event.body.ifBlank { "打开看看 TA 想说什么" },
                    createdAt = if (event.createdAt > 0) event.createdAt else System.currentTimeMillis(),
                    read = false
                )
            )
            if (inserted >= 0L) {
                newEvents += event
                coupleNotifier.notifyEvent(event)
            }
        }
        // 横幅只展示真正新到的一条，重复拉取不覆盖已读也不重复提醒。
        if (newEvents.isNotEmpty()) _pokeBanner.value = newEvents.last()
    }

    fun dismissPokeBanner() {
        _pokeBanner.value = null
    }

    /** 通知权限是否还需要询问（用户拒绝过就不再重复弹）。 */
    suspend fun shouldAskNotifyPermission(): Boolean = !prefs.pokeNotifyAskedSnapshot()

    fun markNotifyPermissionAsked() = viewModelScope.launch { prefs.markPokeNotifyAsked() }

    /** 处理冲突：keepLocal=true 保留本机版本，否则保留云端版本。 */
    fun resolveConflict(conflictId: String, keepLocal: Boolean) = viewModelScope.launch {
        syncRepository.resolveConflict(conflictId, keepLocal)
        if (keepLocal) syncRepository.syncNow()
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
        localReset.ensureCompleted()
        val snapshot = prefs.sessionSnapshot()
        if (snapshot.loggedIn) {
            seedContent()
            if (!prefs.taxonomyMigratedSnapshot()) {
                contentRepository.migrateTaxonomyV2()
                prefs.markTaxonomyMigrated()
            }
            if (cloudAuth.hasSession()) {
                syncRepository.schedulePeriodic()
                syncRepository.syncNow(automatic = true)
                cloudSpace.refreshLocal()
                cloudAuth.fetchMe()
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
        val ownerId = snapshot.userId.ifBlank { "local-user" }
        val recordOwnerId = if (module == ContentModule.LETTER) existing?.ownerId ?: ownerId else ownerId
        val recordOwnerName = if (module == ContentModule.LETTER) existing?.ownerName ?: snapshot.nickname else snapshot.nickname
        val extraMap = buildMap<String, String> {
            putAll(existing?.extra ?: emptyMap())
            putAll(extra)
            if (rating > 0) put("星级", rating.toString())
            // 心愿的提出人统一落当前 userId（新数据），旧值「我」/「TA」保持原样由 WishBoard 解析。
            if (module == ContentModule.WISH && get(WishBoard.KEY_PROPOSER).isNullOrBlank()) {
                put(WishBoard.KEY_PROPOSER, ownerId)
            }
        }
        val localMedia = mediaIds ?: existing?.mediaIds ?: emptyList()
        // 新选的图片先上传拿到云端引用；已有图片复用之前的引用，避免重复上传。
        // 注意：mediaRefs 必须与 mediaIds 等长且同序，否则下次编辑 zip 复用会张冠李戴。
        val previousRefs = existing?.let { old ->
            old.mediaIds.zip(old.mediaRefs)
                .filter { (_, ref) -> ref.isNotBlank() && ref != MISSING_MEDIA_REF }
                .toMap()
        }.orEmpty()
        val refs: List<String> = if (localMedia.isEmpty()) {
            emptyList()
        } else {
            val needUpload = localMedia.filter { it !in previousRefs }
            val newRefs = if (needUpload.isEmpty()) emptyList() else mediaRepository.uploadAll(needUpload)
            val newRefMap = needUpload.zip(newRefs).toMap()
            val resolved = localMedia.map { uri -> previousRefs[uri] ?: newRefMap[uri] }
            val failed = resolved.count { it == null }
            if (failed > 0) send("$failed 张图片上传失败，其余内容已保存；可在编辑页重新保存重试")
            // 失败位置用占位符保持与 mediaIds 的索引对齐，避免下次编辑复用串图。
            resolved.map { it ?: MISSING_MEDIA_REF }
        }
        val record = ContentRecord(
            id = existing?.id ?: "rec-${UUID.randomUUID()}",
            module = module,
            spaceId = space.value?.id,
            ownerId = recordOwnerId,
            ownerName = recordOwnerName,
            visibility = visibility,
            title = title.trim(),
            content = content.trim(),
            eventDate = eventDate,
            mediaIds = localMedia,
            mediaRefs = refs,
            extra = extraMap,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            deletedAt = null,
            // 本机 version 必须始终等于「最近一次云端版本」：它是推送时的 baseVersion（乐观锁基线）。
            // 在这里自增会让首次编辑就与云端基线不符而被判冲突，导致修改永远传不上去。
            version = existing?.version ?: 0
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

    /** 心愿卡片上直接流转状态，例如「标记实现」；非法迁移由 WishBoard 拦截，不改数据。 */
    fun setWishStatus(record: ContentRecord, status: String) =
        transitWish(record, status, "已标记为$status")

    /** 答应对方提出的心愿：仅「待回应」且自己不是提出人。 */
    fun acceptWish(record: ContentRecord) =
        transitWish(record, WishBoard.ACCEPTED, "已答应，等你们一起实现")

    /** 拒绝对方提出的心愿：仅「待回应」且自己不是提出人，归入失效心愿。 */
    fun rejectWish(record: ContentRecord) =
        transitWish(record, WishBoard.REJECTED, "已拒绝，移入失效心愿")

    private fun transitWish(record: ContentRecord, status: String, doneMessage: String) =
        viewModelScope.launch {
            if (isReadOnly.value) {
                send(READ_ONLY_MESSAGE)
                return@launch
            }
            val selfId = prefs.sessionSnapshot().userId
            val updated = when (status) {
                WishBoard.ACCEPTED -> WishBoard.accept(record, selfId)
                WishBoard.REJECTED -> WishBoard.reject(record, selfId)
                WishBoard.REALIZED -> WishBoard.realize(record)
                else -> record
            }
            if (updated === record) {
                send("当前状态不支持这个操作")
                return@launch
            }
            contentRepository.save(updated)
            send(doneMessage)
            // 迁移已落库，立即同步一次让状态尽快到对方；复用 syncNow 逻辑，
            // 同时刷新 lastSyncAttemptAt，避免前台轮询紧接着再拉一次。失败不阻塞 UI。
            syncNow()
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
        _bindFailure.value = null
        try {
            when (val result = cloudSpace.create(name.trim(), intro.trim())) {
                is com.s520j.app.data.cloud.CloudSpaceResult.Failure -> {
                    _bindFailure.value = result.message
                    send(result.message)
                }
                is com.s520j.app.data.cloud.CloudSpaceResult.Success -> {
                    send("共同空间已创建，绑定码已生成")
                }
            }
        } catch (t: Throwable) {
            reportFailure("创建失败", t)
        }
    }

    /** 打开共同空间页时从云端刷新：处理对方确认、绑定成功、冻结等状态变化。 */
    fun refreshSpaceFromCloud() = viewModelScope.launch {
        try {
            cloudSpace.refreshLocal()
        } catch (t: Throwable) {
            reportFailure("刷新共同空间失败", t)
        }
    }

    /** 修改空间名称与简介：先写云端，成功后才由 refreshLocal 更新本地。 */
    fun updateSpaceInfo(name: String, intro: String) = viewModelScope.launch {
        _bindFailure.value = null
        try {
            when (val result = cloudSpace.updateInfo(name.trim(), intro.trim())) {
                is com.s520j.app.data.cloud.CloudSpaceResult.Failure -> {
                    _bindFailure.value = result.message
                    send(result.message)
                }
                is com.s520j.app.data.cloud.CloudSpaceResult.Success -> {
                    send("空间资料已更新")
                }
            }
        } catch (t: Throwable) {
            reportFailure("保存空间资料失败", t)
        }
    }

    fun shareInvite() = viewModelScope.launch {
        val current = space.value ?: return@launch
        spaceRepository.shareInvite(current, System.currentTimeMillis())
        _bindFailure.value = null
        send("已分享给对方，开始等待对方加入")
    }

    fun refreshBindCode() = viewModelScope.launch {
        try {
            when (val result = cloudSpace.refreshCode()) {
                is com.s520j.app.data.cloud.CloudSpaceResult.Failure -> {
                    _bindFailure.value = result.message
                    send(result.message)
                }
                is com.s520j.app.data.cloud.CloudSpaceResult.Success -> {
                    _bindFailure.value = null
                    send("绑定码已刷新，24 小时内有效")
                }
            }
        } catch (t: Throwable) {
            reportFailure("刷新绑定码失败", t)
        }
    }

    /** 输入对方的绑定码：云端创建绑定申请，本机进入「绑定申请中」。 */
    fun submitBindCode(code: String) = viewModelScope.launch {
        try {
            when (val result = cloudSpace.join(code)) {
                is com.s520j.app.data.cloud.CloudSpaceResult.Failure -> {
                    _bindFailure.value = result.message
                    if (result.offline) send(result.message)
                }
                is com.s520j.app.data.cloud.CloudSpaceResult.Success -> {
                    _bindFailure.value = null
                    send("已提交绑定申请，请双方各确认一次")
                }
            }
        } catch (t: Throwable) {
            reportFailure("提交绑定码失败", t)
        }
    }

    fun clearBindFailure() {
        _bindFailure.value = null
    }

    fun confirmBindAsOwner() = viewModelScope.launch {
        val current = space.value ?: return@launch
        val requestId = current.bindRequestId
        try {
            if (requestId == null) {
                // 旧版本地演示数据没有云端申请 id，退回本地确认。
                val snapshot = prefs.sessionSnapshot()
                val updated = spaceRepository.confirmBind(
                    space = current,
                    actorId = snapshot.userId,
                    partnerNickname = snapshot.partnerNickname.ifBlank { "TA" },
                    now = System.currentTimeMillis()
                )
                send(bindConfirmMessage(updated, simulated = false))
                return@launch
            }
            when (val result = cloudSpace.confirmBind(requestId)) {
                is com.s520j.app.data.cloud.CloudSpaceResult.Failure -> send(result.message)
                is com.s520j.app.data.cloud.CloudSpaceResult.Success -> {
                    _bindFailure.value = null
                    send(
                        if (result.value) "绑定成功，已和 TA 组成共同空间"
                        else "已确认绑定，等待对方确认"
                    )
                }
            }
        } catch (t: Throwable) {
            reportFailure("确认绑定失败", t)
        }
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

    /** 发起解绑：云端记录申请，由对方在空间页确认或拒绝。 */
    fun requestUnbind() = viewModelScope.launch {
        try {
            when (val result = cloudSpace.requestUnbind()) {
                is com.s520j.app.data.cloud.CloudSpaceResult.Failure -> {
                    _bindFailure.value = result.message
                    send(result.message)
                }
                is com.s520j.app.data.cloud.CloudSpaceResult.Success -> {
                    _bindFailure.value = null
                    send("已发起解绑，等待 TA 确认")
                }
            }
        } catch (t: Throwable) {
            reportFailure("发起解绑失败", t)
        }
    }

    /** 确认解绑：旧本地数据没有云端申请 id 时退回本地确认。 */
    fun confirmUnbind() = viewModelScope.launch {
        val current = space.value ?: return@launch
        val requestId = current.bindRequestId
        try {
            if (requestId == null) {
                val updated = spaceRepository.confirmUnbind(
                    space = current,
                    actorId = prefs.sessionSnapshot().userId,
                    now = System.currentTimeMillis()
                )
                send(unbindConfirmMessage(updated))
                return@launch
            }
            when (val result = cloudSpace.confirmUnbind(requestId)) {
                is com.s520j.app.data.cloud.CloudSpaceResult.Failure -> {
                    _bindFailure.value = result.message
                    send(result.message)
                }
                is com.s520j.app.data.cloud.CloudSpaceResult.Success -> {
                    _bindFailure.value = null
                    send("已确认解绑，空间进入冻结归档")
                }
            }
        } catch (t: Throwable) {
            reportFailure("确认解绑失败", t)
        }
    }

    /** 拒绝解绑：申请作废，空间保持已绑定。 */
    fun rejectUnbind() = viewModelScope.launch {
        val requestId = space.value?.bindRequestId ?: return@launch
        try {
            when (val result = cloudSpace.rejectUnbind(requestId)) {
                is com.s520j.app.data.cloud.CloudSpaceResult.Failure -> {
                    _bindFailure.value = result.message
                    send(result.message)
                }
                is com.s520j.app.data.cloud.CloudSpaceResult.Success -> {
                    _bindFailure.value = null
                    send("已拒绝解绑，空间保持已绑定")
                }
            }
        } catch (t: Throwable) {
            reportFailure("拒绝解绑失败", t)
        }
    }

    /** 撤回自己发起的解绑申请。 */
    fun cancelUnbind() = viewModelScope.launch {
        val requestId = space.value?.bindRequestId ?: return@launch
        try {
            when (val result = cloudSpace.cancelUnbind(requestId)) {
                is com.s520j.app.data.cloud.CloudSpaceResult.Failure -> {
                    _bindFailure.value = result.message
                    send(result.message)
                }
                is com.s520j.app.data.cloud.CloudSpaceResult.Success -> {
                    _bindFailure.value = null
                    send("已撤回解绑申请")
                }
            }
        } catch (t: Throwable) {
            reportFailure("撤回解绑失败", t)
        }
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

    // ---------- 备份与导出 ----------

    private val _backups = MutableStateFlow(backupService.list())
    val backups: StateFlow<List<com.s520j.app.data.backup.BackupService.BackupInfo>> = _backups

    fun refreshBackups() {
        _backups.value = backupService.list()
    }

    fun createBackup() = viewModelScope.launch {
        try {
            val info = backupService.create()
            refreshBackups()
            send("已创建备份（${info.bytes / 1024} KB）")
        } catch (t: Throwable) {
            reportFailure("备份失败", t)
        }
    }

    fun restoreBackup(name: String) = viewModelScope.launch {
        val count = backupService.restore(name)
        if (count < 0) {
            send("备份文件不可用，未做任何修改")
        } else {
            send("已从备份恢复 $count 条记录")
            refreshBackups()
        }
    }

    fun deleteBackup(name: String) = viewModelScope.launch {
        backupService.delete(name)
        refreshBackups()
        send("已删除备份")
    }

    /** 仅清理本机内容，云端与备份文件不受影响。 */
    fun clearLocalData() = viewModelScope.launch {
        if (backupService.clearLocal()) send("已清理本机内容，可从备份恢复")
        else send("清理失败，请重试")
    }

    fun exportZip(uri: Uri) = viewModelScope.launch {
        send(if (backupService.exportZip(uri)) "已导出 JSON + 图片 ZIP" else "导出失败，请重试")
    }

    private fun bindConfirmMessage(updated: SpaceEntity, simulated: Boolean): String = when {
        SpaceStateMachine.stateOf(updated.bindState) == BindState.BOUND -> "已绑定，双方账号各自独立"
        simulated -> "对方已确认（本地演示），等待我方确认"
        else -> "我方已确认，等待对方确认"
    }

    private fun unbindConfirmMessage(updated: SpaceEntity): String =
        if (SpaceStateMachine.stateOf(updated.bindState) == BindState.FROZEN) "已解绑，空间进入冻结归档"
        else "我方已确认解绑，等待对方确认"

    private companion object {
        const val READ_ONLY_MESSAGE = "空间已冻结归档，当前为只读状态，无法编辑共同内容"
        const val SIM_PARTNER_ID = "sim-partner"
        /** 图片上传失败时在 mediaRefs 中的占位符，用于保持与 mediaIds 的索引对齐。 */
        const val MISSING_MEDIA_REF = com.s520j.app.data.media.MediaRepository.MISSING_REF
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

    /**
     * 设置自头像：压缩到 ≤256px JPEG → base64 → 上传云端 → 写本机。
     * 上传成功后触发一次空间刷新，让对方在 space.get 时拉到新头像。
     */
    fun setMyAvatar(uri: String?) = viewModelScope.launch {
        if (uri.isNullOrBlank()) {
            prefs.setMyAvatar(null)
            return@launch
        }
        _isAvatarUploading.value = true
        try {
            val dataUrl = AvatarCompressor.toDataUrl(context, Uri.parse(uri))
            if (dataUrl == null) {
                send("头像处理失败，请换一张图片")
                return@launch
            }
            when (val result = cloudAuth.updateAvatar(dataUrl)) {
                is com.s520j.app.data.cloud.CloudAuthResult.Success -> {
                    prefs.setMyAvatar(dataUrl)
                    // 刷新空间，让对方能拉到新头像（昵称同理）。
                    cloudSpace.refreshLocal()
                    send("头像已更新")
                }
                is com.s520j.app.data.cloud.CloudAuthResult.Failure -> {
                    send(result.message.ifBlank { "头像上传失败" })
                }
            }
        } catch (t: Throwable) {
            reportFailure("头像上传失败", t)
        } finally {
            _isAvatarUploading.value = false
        }
    }

    /**
     * 对方头像由云端 space.get 同步写入 prefs.partnerAvatar，本机不再手动设置。
     * 保留空实现是为了兼容旧调用；新 UI 不应再调用。
     */
    @Deprecated("对方头像由云端同步，请勿手动设置", ReplaceWith(""))
    fun setPartnerAvatar(uri: String?) {}

    // ---------- 心动页：今日挑战与碰碰你 ----------

    fun rotateChallenge() = updateChallenge { it.rotate() }

    /**
     * 标记自己完成：本地先行（不可撤销）→ 云端记录；
     * 云端不可用（未绑定 / 离线）时保留本地状态并提示。
     */
    fun toggleChallengeSelf() = viewModelScope.launch {
        if (isReadOnly.value) {
            send(READ_ONLY_MESSAGE)
            return@launch
        }
        val epochDay = today().toEpochDay()
        val current = ChallengeState.forDay(ChallengeState.decode(prefs.challengeSnapshot()), epochDay)
        if (current.selfDone) return@launch
        prefs.setChallengeState(current.markSelfDone().encode())
        val cloud = coupleLive.markChallenge(epochDay)
        if (cloud == null) {
            send("已标记完成，联网且已绑定后会自动同步给 TA")
            return@launch
        }
        prefs.setChallengeState(
            ChallengeState.forDay(ChallengeState.decode(prefs.challengeSnapshot()), epochDay)
                .mergeCloud(epochDay, cloud.selfDone, cloud.partnerDone)
                .encode()
        )
        if (cloud.selfDone && cloud.partnerDone) send("今日挑战达成，真好")
    }

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

    /**
     * 发送碰碰你：优先投递到云端，由对方轮询收到横幅 / 系统通知。
     * 未绑定或离线时降级为本地未读记录，避免什么都不发生。
     */
    fun sendPoke(name: String) = viewModelScope.launch {
        if (isReadOnly.value) {
            send(READ_ONLY_MESSAGE)
            return@launch
        }
        if (coupleLive.sendPoke(name)) {
            send("已把「$name」送给 TA")
            return@launch
        }
        val partner = prefs.sessionSnapshot().partnerNickname.ifBlank { "TA" }
        notificationDao.upsert(
            NotificationEntity(
                id = "poke-${UUID.randomUUID()}",
                title = "碰碰你",
                body = "你送给 $partner 一个「$name」（未联网，仅本机可见）",
                createdAt = System.currentTimeMillis(),
                read = false
            )
        )
        send("网络不可用，本次碰一碰仅保存在本机")
    }

    fun markAllRead() = viewModelScope.launch { notificationDao.markAllRead() }

    fun markRead(id: String) = viewModelScope.launch { notificationDao.markRead(id) }
}
