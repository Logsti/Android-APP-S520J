# 共同照片、情书、通知中心、账号重置与登录简化 Spec

## Why
当前“共同照片”入口直接打开左右翻页查看器，无法先总览全部照片；情侣手写信缺少独立、可双向分类的保存与浏览入口。“换机恢复”与普通登录完全重复，手动备份的实际边界也不够清晰。通知中心目前只覆盖碰碰你与挑战达成，心愿和共同空间的重要动态缺少低打扰提醒。用户已授权一次性清空当前云端测试环境的全部账号及关联数据，以便双方重新注册；手机号验证码因暂未配置真实短信服务，本轮不实施。

## What Changes
- 拾光“共同照片 N 条记录”卡改为进入独立照片子页，全部照片以双列纵向列表展开，点击任意照片从对应位置放大查看。
- 心动“快速记录”标题操作区在随手记图标旁新增一个**仅图标**的情书入口，不显示文字、不增加卡片，原有单行 5 个快速记录入口保持不变。
- 默契页新增“一封情书”模块；添加时选择“写给 TA / 来自 TA”，默认“写给 TA”，TA 显示当前伴侣昵称。
- 情书查看全部页按日期倒序展示“日期＋方向＋图片”卡片，支持分类筛选、上下滑动和点击照片放大。
- 登录页删除“换机恢复”子选项，只保留“注册 / 登录”；普通登录继续完成云端认证、本机账号落地和自动同步。
- 数据管理保留手动备份、恢复备份和 ZIP 导出，但明确：换机主要依靠云端同步，本机备份只是内容快照，不是完整账号备份。
- 第四页“我们”的“关于 S520J”中，在“应用定位”现有说明下增加独立一句“致一周年的我们”。
- 通知中心新增对方心愿动态、共同空间动态和需要处理的账号/同步异常；新事件在 APP 联网同步后同时写入通知中心并发送 Android 系统通知。
- 通知按事件幂等去重，首次拉取的云端历史通知只归档、不推送；普通断网抖动、情书、照片和普通内容编辑不新增提醒。
- **BREAKING**：一次性清空当前 CloudBase 环境的全部账号、会话、共同空间、内容、照片、设置和同步数据；不可恢复，不做成普通 APP 功能。
- **BREAKING**：新版首次启动执行一次本地全量重置，清除旧账号、内容、outbox、游标和偏好，避免旧数据重新上传；随后进入注册/登录页。
- 手机号验证码注册、登录与找回密码本轮暂缓，不添加假验证码或不可用入口；本机偏好的云端同步也不在本轮扩张。

## Impact
- Affected specs: 拾光照片浏览、心动快速记录、默契内容模块、图片同步、通知中心、心愿与空间云函数、系统通知、登录导航、数据管理说明、账号与本地数据重置。
- Affected code: `GalleryScreen.kt`、照片导航与新照片子页、`HeartScreen.kt`、`TacitScreen.kt`、`Models.kt`、情书编辑/列表与方向领域逻辑、`S520JNavHost.kt`、`AuthScreen.kt`、`DataManagementScreen.kt`、`NotificationCenterScreen.kt`、`AboutScreen.kt`、`AppViewModel.kt`、`CoupleNotifier.kt`、通知 API/本地 DAO、CloudBase 心愿与空间处理器、本地数据库/偏好重置、CloudBase 一次性 SQL。
- 情书复用现有 `ContentRecord`、`extra`、`mediaIds/mediaRefs` 与 outbox 同步，无需新增云端内容表。
- 共同照片来源沿用现有照片墙五类模块，不混入随手记或情书。
- 心愿与空间通知复用云端现有 `notifications` 表和 `notification.list` API；当前云端没有通知生产者，本轮补充真实写事件触发点，不仅修改 UI。

## ADDED Requirements

### Requirement: 共同照片独立子页
The system SHALL provide an independent, vertically scrollable photo album page when the user taps the “共同照片” statistic card.

#### Scenario: 展开全部照片
- **WHEN** 用户点击拾光页“共同照片 N 条记录”卡
- **THEN** 进入带返回按钮的共同照片子页，以双列网格展开照片墙来源记录中的全部图片，不再受单条记录最多 4 张的展示截断影响
- **AND** 记录按现有照片墙时间倒序排列，同一记录内保持原图片顺序，使用记录 ID＋图片序号作为稳定标识

#### Scenario: 从指定照片放大
- **WHEN** 用户点击子页中的任意照片
- **THEN** 打开现有图片查看器并定位到被点击照片，可双指缩放、拖动，也可继续左右翻页

#### Scenario: 空照片库
- **WHEN** 当前没有任何共同照片
- **THEN** 子页显示明确空状态，不出现空白页或崩溃

### Requirement: 一封情书入口与记录
The system SHALL provide a couple-shared LETTER module in the TACIT group, with an icon-only creation entry beside 随手记 and a module entry in 默契页.

#### Scenario: 从心动页添加
- **WHEN** 用户点击“快速记录”标题右侧的信件图标
- **THEN** 打开一封情书编辑页，入口与随手记一样仅显示图标，不显示“情书”文字或新增卡片
- **AND** 原有单行 5 个快速记录入口及随手记入口均保持可用

#### Scenario: 保存情书
- **WHEN** 用户保存情书
- **THEN** 记录沿用随手记的标题、正文、日期、图片与同步规则，保存为 LETTER 模块并归属默契页
- **AND** 情书不进入“最近的美好”、拾光照片墙或共同照片子页

### Requirement: 情书方向与双方视角
The system SHALL store the creator-selected direction and derive each viewer’s label from that viewer’s perspective.

#### Scenario: 添加时选择方向
- **WHEN** 用户新增情书
- **THEN** 默认选择“写给 伴侣昵称”，也可切换为“来自 伴侣昵称”；未绑定时使用“TA”兜底

#### Scenario: 双方查看同一封信
- **WHEN** 创建者选择“写给 TA”并由对方同步查看
- **THEN** 创建者看到“写给 伴侣昵称”，对方看到“来自 创建者昵称”
- **AND** 创建者选择“来自 TA”时双方显示反向转换，编辑记录不得改变原始创建者或意外翻转存储方向

### Requirement: 情书查看全部页
The system SHALL provide a dedicated, vertically scrollable letter list with direction filters and photo cards.

#### Scenario: 浏览与筛选
- **WHEN** 用户在默契页“一封情书”中点击“查看全部”
- **THEN** 进入专用子页，可筛选“全部 / 写给 TA / 来自 TA”，按信件日期倒序展示全部记录
- **AND** 每条记录以卡片显示日期、视角方向和该条全部图片，图片沿用 1/2/3/4 张宫格布局

#### Scenario: 放大情书照片
- **WHEN** 用户点击某封情书的任意照片
- **THEN** 从该照片打开多图查看器，可缩放并继续浏览这封信的其它照片

### Requirement: 普通登录承担云端恢复
The system SHALL use the existing cloud-first normal login flow for reinstall and device changes, without a separate restore tab.

#### Scenario: 换机或重装后登录
- **WHEN** 用户使用原账号 ID 和密码在普通登录页成功登录
- **THEN** 沿用现有逻辑完成云端认证、本机账号落地与同步，自动恢复已云同步的昵称、头像、共同空间、内容记录和已上传图片
- **AND** 登录页不出现“换机恢复”子选项

#### Scenario: 明确本地偏好边界
- **WHEN** 用户在新设备恢复账号
- **THEN** 字体缩放、问候语、自定义碰碰你、提醒设置、默认可见范围等本机偏好按当前实现使用默认值，不宣称这些偏好已云恢复

### Requirement: 手动备份边界清晰
The system SHALL retain local content backups and ZIP export while accurately describing their scope.

#### Scenario: 查看数据管理
- **WHEN** 用户进入数据管理页
- **THEN** 页面明确提示换机/重装以普通登录后的云端同步为主
- **AND** 明确本机手动备份仅包含内容记录 JSON，不包含图片文件、账号、密码、共同空间或设置，且卸载 APP 后本机备份会删除
- **AND** ZIP 导出继续包含内容 JSON 与图片，定位为离线留存，不暗示存在 ZIP 导入恢复能力

### Requirement: 一次性云端与本机全量重置
The system SHALL provide a one-time operational reset for the currently configured CloudBase environment and a guarded local reset on upgraded clients.

#### Scenario: 云端全量清空
- **WHEN** 实施人员确认 `local.properties` 的目标环境与当前 APP 一致并执行重置脚本
- **THEN** 通过单条原子 `TRUNCATE` 清空 `users`、`auth_sessions`、`spaces`、`space_members`、`bind_codes`、`bind_requests`、`contents`、`media_assets`、`media_blobs`、`recycle_bin`、`conflict_snapshots`、`sync_logs`、`backup_jobs`、`settings`、`notifications`、`challenge_days`、`couple_events`
- **AND** 执行后验证上述表记录数均为 0，不使用多语句伪事务，不在 APP 暴露重置按钮

#### Scenario: 本地首次升级重置
- **WHEN** 新版在设备上首次启动且本地重置标记不存在
- **THEN** 在任何同步、种子数据或注册操作前清理 Room 全表、DataStore 登录态与本地偏好、outbox 和同步游标，再写入仅本机保存的完成标记
- **AND** 后续重启不重复清理，旧记录不得重新上传，用户可重新注册并绑定共同空间

### Requirement: 心愿与共同空间定向通知
The system SHALL create server-side, recipient-specific notifications for actual wish and shared-space events performed by the partner.

#### Scenario: 对方心愿动态
- **WHEN** 云端成功接收对方提出心愿，或将心愿状态改为答应、拒绝、标记实现
- **THEN** 为当前用户生成一条包含对方昵称和具体动态的通知，操作者本人不收到自己操作的提醒
- **AND** 仅修改心愿标题、正文或其它非状态字段不生成通知，以服务端实际接收到的最终状态变化为准

#### Scenario: 共同空间动态
- **WHEN** 对方提交绑定请求、完成绑定、发起解除绑定，或确认解除并使空间冻结
- **THEN** 为需要知晓或处理该事件的另一方生成对应通知，操作者本人由当前操作界面直接反馈结果
- **AND** 绑定申请与解除申请明确提示需要处理，绑定成功与空间冻结明确提示当前状态

#### Scenario: 云端事件幂等
- **WHEN** 同一内容版本、绑定请求或空间状态事件因重试被重复处理
- **THEN** 使用由事件对象、状态版本和接收者组成的稳定云端通知 ID 保证每位接收者只有一条对应通知

### Requirement: 可操作的账号与同步异常提醒
The system SHALL create local actionable alerts for persistent account or synchronization failures without notifying transient network noise.

#### Scenario: 账号或权限必须处理
- **WHEN** 云端明确返回登录失效、账号不可用或无权限继续同步
- **THEN** 通知中心生成重新登录或检查账号权限的提醒，并按同一新事件发送系统通知；空间冻结只使用共同空间动态通知，不再重复生成本地异常提醒

#### Scenario: 持续同步失败
- **WHEN** 同一账号连续 3 次自动同步失败且仍有本地内容等待上传
- **THEN** 生成一条检查网络或同步状态的提醒；单次断网、超时或随后立即恢复的失败不生成通知
- **AND** 同步恢复前相同失败签名不重复提醒，恢复后再发生新的持续失败可以再次提醒

### Requirement: 通知拉取、已读与系统触达
The system SHALL merge cloud notifications into the existing local notification center and emit Android system notifications only for newly observed events after a per-device baseline.

#### Scenario: 新事件同时进入两处
- **WHEN** APP 在前台或已有计划任务中联网同步并拉取到基线之后产生的云端新通知
- **THEN** 以同一事件 ID、标题和正文写入本地通知中心为未读，并发送一条 Android 系统通知
- **AND** 无系统通知权限时仍保留应用内未读记录，不伪造系统发送成功状态

#### Scenario: 首次拉取不刷历史提醒
- **WHEN** 某台设备上的某个账号首次成功拉取云端通知
- **THEN** 当时已经存在的云端通知仅作为已读历史导入通知中心，不增加未读角标、不发送系统通知，并建立该设备与账号的通知基线
- **AND** 基线之后新到达的事件才按新事件提醒，避免升级、换机或重新登录时批量推送历史消息

#### Scenario: 重试和已读状态
- **WHEN** 重复拉取、重启 APP 或再次同步同一事件
- **THEN** 不重复生成应用内记录或系统通知，云端拉取不得覆盖本机已读状态
- **AND** 本轮已读状态仍仅保存在本机，不宣称跨设备同步已读

#### Scenario: 保留现有提醒
- **WHEN** 用户查看通知中心
- **THEN** 现有碰碰你、挑战达成和今日到期提醒继续工作，说明文案补充心愿、空间与异常提醒及“联网同步后触达”的边界

## MODIFIED Requirements

### Requirement: 拾光共同照片入口
共同照片卡不再直接打开全屏左右翻页查看器，而是先进入可总览全部照片的独立子页；放大查看是子页内的二级交互。

### Requirement: 默契模块列表
默契页新增“一封情书”，其“查看全部”使用情书专用照片卡片页；其它模块仍沿用现有通用列表，不做无关重构。

### Requirement: 登录页信息架构
登录页只保留注册与登录两个子选项；普通登录文案说明成功后会自动恢复云端数据，不再把同一登录逻辑重复包装成“换机恢复”。

### Requirement: 通知中心信息架构
通知中心继续复用现有时间倒序列表、未读数、单条已读和全部已读，不为本轮通知增加无关页面或路由。新增通知必须来自真实云端事件或明确的本机异常终态，不通过扫描全部历史内容猜测新消息。

### Requirement: 关于页周年文案
“关于 S520J”的“应用定位”保留现有共同生活记录本说明，并在其后增加独立一句“致一周年的我们”；只调整该处文案，不改变关于页其它内容与布局结构。

#### Scenario: 查看应用定位
- **WHEN** 用户从第四页“我们”进入“关于 S520J”并查看“应用定位”
- **THEN** 可以在现有应用定位说明下看到完整文案“致一周年的我们”

## DEFERRED Requirements

### Requirement: 手机号验证码认证
**Reason**: 用户确认本轮暂缓，且当前没有真实短信签名、模板与服务端凭据。
**Boundary**: 不修改认证数据结构，不展示手机号/验证码入口，不实现固定验证码或模拟发送。后续接入腾讯云短信时另行立项，费用由对应腾讯云账号按控制台规则支付。

### Requirement: 本机偏好云端同步
**Reason**: 用户选择保留并澄清手动备份，本轮不扩大云端设置同步范围。
**Boundary**: 字体、提醒、默认可见范围等本机偏好仍只存本机；手动备份也不覆盖这些偏好。

### Requirement: 真正的后台远程推送
**Reason**: 用户选择的是“APP 同步到事件后同时系统通知”，不是新增 FCM 或厂商推送服务。
**Boundary**: APP 完全关闭且没有同步任务执行时，不保证事件及时到达；本轮不宣传实时远程推送，不新增推送 SDK。

### Requirement: 情书、照片与普通内容更新通知
**Reason**: 双方高频记录若逐条提醒会过度打扰，用户已确认仅新增心愿动态、空间与异常。
**Boundary**: 新增情书、上传照片、修改随手记或其它普通内容不生成新增类型通知，现有碰碰你与挑战达成不受影响。

## REMOVED Requirements

### Requirement: 共同照片卡直接打开左右翻页查看器
**Reason**: 直接进入查看器缺少全部照片总览和纵向浏览能力。
**Migration**: 原有多图查看器保留为照片子页和情书照片卡片的二级放大组件。

### Requirement: 独立“换机恢复”子选项
**Reason**: 其按钮与普通登录均调用同一个 `vm.login`，没有独立恢复逻辑，属于重复入口。
**Migration**: 用户换机或重装后直接使用普通登录，现有云端认证、本机账号落地与自动同步流程不变。
