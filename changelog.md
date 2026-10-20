# Changelog

## 2026-10-20（定时任务模块上线）

- quartz 移植为 scaffold 风格（com.scaffold.system.job 包 18 文件）：SysJob/SysJobLog 注解 SQL Mapper、ScheduleUtils/JobInvokeUtil/CronUtils、AbstractQuartzJob 双执行器；调度走 RAMJobStore 内存模式（@PostConstruct 从 sys_job 加载，QRTZ 表仅建在 scaffold_system 备集群化，实际不读）。invoke_target 白名单仅放行 job.task 两个包（com.scaffold.system.job.task. / com.scaffold.vuln.ai.task.），防页面配置任意 bean 方法调用。
- 菜单：系统监控 → 定时任务（203，monitor/job/index → Job.vue）+ 6 按钮权限（query/add/edit/remove/changeStatus/run）。前端双视图走 ListPage 的 alt-view（对齐菜单星图/部门画布的轻量切换）：任务表格视图 + 执行日志 alt 视图，页头 Segmented 按视图条件渲染新增/清空按钮；fit-viewport 固定查询区与分页；新增/编辑 Drawer 含 cron 在线校验（/monitor/cron-check）。
- AI 对话清理迁入 sys_job（jobTasks.aiChatClean，每日 02:00，默认启用）；AiChatCleanup 的 @Scheduled 移除，业务方法保留由 JobTasks 委托调用——@MapperScan 补 com.scaffold.system.job.mapper。
- 教训三连：①服务 unit 名是 scaffold-vuln.service（ruoyi-vuln 是不存在的旧记录名，systemctl 查它会误报服务停止）；②本地构建链 = pnpm@11 + Go 便携包（GOPROXY=goproxy.cn）+ 全路径 mvn.cmd（PATH 无 mvn），heredoc 写代码文件会吃转义——一律用编辑工具；③alt 视图切换空白排查：先查 .page 的 data-entrance-ready 与 alt-view 是否在 DOM（v-if 没触发是状态问题不是样式问题）。
- 已知边界：日志视图的分页由 alt 内自绘 Pagination 承担；引用机器人消息的触发（replyToMessage）尚未纳入白名单判定，出现漏唤后补。
- 后续打磨（同日）：执行日志视图补 alt-fit（panel 撑满视口、分页贴底，对齐任务列表）+ 面板刷新按钮 + 顶部筛选面板；页头新增按钮被 Drawer 的 scoped .btn-primary 撞全局类（9px 圆角+错色）——Drawer 按钮类名改 drawer-btn-*；Job 页挂载兜底设置 entrance-ready（Transition enter 钩子对该页未触发的保险）；视图选择持久化 localStorage（monitor-job-view）；全局切页动画提速 pageOut 0.18→0.1 / pageIn 0.62→0.3（out-in 串行 0.8s→0.4s，切页更跟手）。


## 2026-10-15（修复：字典样本册入场闪帧）

- 进样本册时卡片先空壳渲染可见 → 逐个字典拉数据各自重渲（闪几下）→ 全部到齐后 revealCascade 的 fromTo 起手把整面拽回 opacity:0 再逐个浮现。修复：加 is-entering 门闩（数据未就位藏住卡片，watch 每轮开头置位），revealCascade 接管后放行——入场只剩干净的级联浮现。

## 2026-10-15（真凶：quickTo('scale') 静默失败——缩放漂移根治）

- 用户探针抓到实锤：点放大后 x 平移了而 scale 纹丝不动（纯漂移），控制台栈指到 GSAP 警告「scale not eligible for reset」——`gsap.quickTo(layer, 'scale')` 不支持别名属性，静默失败：按钮缩放只有平移生效、缩放丢失 = 漂移；画布拖拽/滚轮走 gsap.set 直写正常，解释了「点画布就恢复」。修复：scale 拆 scaleX/scaleY 两个 quickTo。锚点改树中心（上一条）保留，缩放围绕树中心原地生长。

## 2026-10-15（修复：部门画布按钮缩放漂移）

- fit() 把树钉在画布上部（view.y ≥ 96），按钮缩放却锚定视口几何中心——锚点落在树下空白里，放大时树整体往左上飞、镜头钻向左下。按钮改 zoomBy：以树的当前可视中心（layer 平移 + 内容半宽高 × k）为锚，缩放原地生长不漂移；滚轮（指针锚）与适应按钮本就正常。

## 2026-10-15（验证码改算术式）

- 随机字母改算术题：a+b / a-b（减法保证非负、两数 1-9），图上画「9+7=?」居中（±7° 微旋转），Redis 存计算结果。登录占位符同步「请输入计算结果」。

## 2026-10-15（修复：看板拖拽取消回弹错位）

- 回弹落点用拖起瞬间的 origin 快照（视口坐标）——拖拽中列体一旦滚动（滚轮/触控板惯性），快照即过期，幽灵卡飞往旧坐标，视觉上卡片「置顶」。改为回弹时取占位卡 getBoundingClientRect() 实时位置；拖放成功路径本就查实时 DOM 不变。**（实测症状仍在，疑似另有成因——模板化时标记为已知问题，复现需录屏 + DOM 探针再查）**

## 2026-10-15（看板底线对齐：收掉两段内边距）

- 用户尺子实测卡片底与列表面板底差 ~18px：.board padding-bottom 6px + .board-col-body padding-bottom 12px 叠加。fit 模式下 board 底距清零（盒底 856→862 与面板同线）、列体底距 12→6px（卡片底 844→856）。

## 2026-10-15（修复：用户看板卡片溢出琥珀列 + 看板拉齐列表页）

- 用户管理看板滚动时卡片视觉溢出列背景（底部错位十几像素，成因未完全定案——静态 CSS 与隔离 harness 均无法复现）。双保险：`.board-col` 加 overflow:hidden 兜底裁切；同时按需求给看板接 alt-fit——列高改由视口约束（列区 flex 撑满 + grid-template-rows minmax(0,1fr)），退休 100dvh-300px 魔法数字（页头实际更高时即失准）。按部门/角色/状态三种分列同步受益。

## 2026-10-15（泳道气泡置顶：Teleport + fixed）

- 下翻仍是治标——气泡还关在 overflow 容器里。改为 <Teleport to="body"> + position:fixed 视口坐标（onEnter 直接用块的 viewport rect，上下翻转逻辑按视口余量判断），z-index 提到 50：任何 overflow 容器都裁不到，天然顶层。

## 2026-10-15（修复：泳道气泡被裁）

- Dashboard 泳道 fixedLanes=5 使 .swim-body 带 overflowY:auto，气泡固定向上弹（translate -100%-8px），悬停首泳道块时上半截被容器顶裁掉。修复：onEnter 检测头顶空间（<118px）时锚定块底下翻（.swim-tip.is-below → translate -50% 8px）。

## 2026-10-15（修复：柱状图峰值读数重叠与折行）

- 紧凑柱状图读数 .bar-readout 原 top:0 钉在整图顶部，高柱从标签中穿过；且绝对定位 left:50%+translate(-50%) 使 shrink-to-fit 只剩半槽宽，两位以上数字+单位折行。修复：bottom 改为按柱高动态绑定（calc(柱高% + 6px)），补 white-space:nowrap。

## 2026-10-15（验证码主题自适应）

- 暗色主题下浅色底 PNG 白块违和。CaptchaService 改透明底 ARGB 双份渲染：img（深墨字符）/imgDark（浅灰字符），响应三键 {uuid, img, imgDark}；前端两张叠放，[data-theme=dark] 切显隐，字符直接浮在 m-groove 按钮表面上，深浅主题均融合。

## 2026-10-15（安全修复：验证码明文自曝）

- CaptchaService.generate() 把明文 code 直接放进响应（前端还用 CSS 旋转字符假装是图）——验证码零防护，机器人读 JSON 即可过验证。修复：后端 AWT 渲染 PNG（浅底+干扰线+±10° 旋转，JDK 自带零依赖）返回 {uuid, img} data URI，明文只存 Redis；前端删 captcha-code 伪图改 <img> 渲染。线上验证：/api/auth/captcha 仅回 {img, uuid}。

## 2026-10-15（更名补漏：侧边栏与 AI 问候）

- shortName「漏洞管理平台」→「金缮」、subtitle「Vuln Manager」→「Kintsugi」（AppShell 侧边栏品牌位）；AI 助手问候语同步「你好，我是金缮 AI 助手」。旧品牌串全仓 grep 清零。

## 2026-10-15（平台更名：金缮 · Kintsugi）

- 平台定名「金缮 / Kintsugi」（金缮 = 以金修补裂痕，与缺口 logo、漏洞→修复闭环同构；安全圈无重名占用）。APP.name→Kintsugi（标题后缀）、index.html 静态 title→Kintsugi、登录品牌区：品牌名金缮/KINTSUGI、主标题换 Cohen《Anthem》句「万物皆有裂痕，那是光照进来的地方。」、副文案与版权位同步。四步终端联动与「资产有序 · 风险可控」签名保留。

## 2026-10-15（修复：本地过期后 401 不跳登录页）

- 场景：页面开着不放，token 到达前端过期时间后 session.token() 就地清存储并返回 null，后续请求裸奔被后端 401 拒——而 http.ts 触发跳登录的条件 `token && token === tokenProvider()` 被 null 短路，handler 不触发：只弹「未认证或登录已过期」toast、列表空、停在原地（刷新前一直僵尸态）。修复：`!token` 也触发 unauthorizedHandler；`token === current` 轮换保护保留（迟到 401 不打断新会话）。http.test.ts 补裸奔 401 回归测试（5 用例全绿）。

## 2026-10-15（字典样本册卡片全局等高）

- 同行 stretch 仍存在行间落差（没有 2 行标签卡的行整体偏矮）。改从源头归一：`swatch-tags` min-height 24→54px（统一预留两行标签高度），所有卡片自然等高；1 行标签的卡标签在预留区居中。不用 `grid-auto-rows: 1fr`——展开态大卡独占整行会把全部行撑到它的高度。stretch 保留作 3 行标签极端情况的兜底（退化为同行拉伸）。

## 2026-10-15（字典样本册卡片等高）

- 样本册网格 `align-items: start → stretch`：同行卡片以最高的为准等高（用户拍板方案）；`swatch-bar` 加 `margin-top: auto` 让色谱+底行钉在卡底，富余空间落在标签区与色谱之间。展开态占满整行不受影响。

## 2026-10-15（alt-fit 接入岗位阶梯）

- 岗位管理开 `alt-fit`：PostLadder（.ladder 纵向 flex + .ladder-stage flex:1，与溯源同款双层接法，560px 保底）与表格视图同底。alt-fit 已接入：菜单星图、角色矩阵/溯源、部门画布、岗位阶梯。

## 2026-10-15（alt-fit 接入部门画布）

- 部门管理开 `alt-fit`：OrgCanvas（.org-canvas，与星图同款接法 flex:1 + min-height 560px，平移画布 overflow:hidden 不变）与表格视图同底。alt-fit 已接入：菜单星图、角色矩阵/溯源、部门画布。

## 2026-10-15（alt-fit 接入角色管理矩阵/溯源）

- 角色管理开 `alt-fit`：权限矩阵（.matrix 卡片 flex 填满，.mx-scroll 解除 max-height 公式改 flex 撑满，内部滚动 + sticky 表头不变）与权限溯源（.trace 纵向 flex，.trace-graph 同星图接法 height:auto + flex:1 + min-height 600px）均与表格视图同底。至此 alt-fit 接入：菜单星图、角色矩阵、角色溯源。

## 2026-10-15（ListPage alt-fit：alt 视图与表格同底）

- ListPage 新增 `alt-fit` prop（默认关，按页接入）：alt 视图（星图/画布等）也参与视口固定，画布 flex 填满面板、底部与表格视图同底——此前表格视图 fit 后与 alt 画布的固定公式高度（如星图 max(620px, 100dvh-236px)）出现落差。菜单管理星图首个接入（`.starmap` 在 fit 容器内 height:auto + flex:1 + min-height 620px）；部门画布/岗位阶梯/角色矩阵/溯源等其余视图保持自然高度，待逐个评估内容滚动后再接。

## 2026-10-15（列表页 fit-viewport 全量覆盖）

- 剩余 9 个列表页补 `fit-viewport`（参数配置、部门/字典/菜单/岗位/角色、代码生成），全站 11 个列表页统一为「查询区与分页固定、仅表格数据区滚动」（登录/操作日志两页用户要求保留整页滚动，已回滚）。带 alt 视图的页面（参数面板/字典书架/岗位阶梯/部门画布/菜单星图/角色矩阵）在非表格视图自动豁免，维持自然滚动。

## 2026-10-15（详情抽屉关联条目卡修复）

- 资产详情「关联漏洞」与漏洞详情「修复工单」条目卡双层背景 + 贴连修复：去掉 kv-grid 上的内联 border/radius/padding（padding 露出 kv-grid 自带的 --line 底形成灰圈，保留白底发丝卡与上方信息块同款）；条目间距失效根因是 `.drawer-stack` 类从未定义（裸 div 上 gap 无效），两处列表容器补 `display:flex; flex-direction:column; gap:10px`。附带发现：drawer-stack 全仓 6 处引用 0 处定义（Roles/OperLog 详情区靠 margin 排版未受影响），暂不动。

## 2026-10-15（AI 流式异常文案分型）

- 上游模型 503 过载（硅基流动 "Service is too busy"）时聊天框直接露出 `com.openai.errors.InternalServerException` 原始串。`AiChatController` 流式 catch 块改走 `friendlyError`：按异常串分型——503/too busy/429 → 「模型服务繁忙，请稍后重试」、401/403/api key → 「模型服务认证失败，请检查参数配置中的 API Key」、超时 → 「模型服务响应超时」、其余 → 「生成失败，请稍后重试」；原始堆栈只进服务端日志（log.warn），不透传前端。

## 2026-10-14（参数配置页：AI 连通性测试 + 知识库重建入口）

- 参数配置页 AI 分组组头加专属操作（改进自旧平台 AI 设置页的独立表单形态——scaffold 复用参数配置页零新页面）：「AI 模型服务」组加**测试连通性**按钮（一次测对话 + 向量双模型，结果行内展示 ok/提示/耗时）；「向量模型服务」组加**重建知识库**按钮 + 知识库状态小字（chunks/文件数，管理权限 vuln:ai:manage）。后端新增 /api/vuln/ai/test（EmbeddingModelProvider.testEmbedding 分型报错）。
- 组头结构调整为 settings-group-head flex 容器（SectionLabel + 右侧动作区），样式入 instruments.css。线上双模型实测：chat pong 2.4s / embedding 1024 维 0.12s。

## 2026-10-14（AI 迁移第三批：RAG 知识库）

- 自旧平台迁移 RAG 三件套（com.scaffold.vuln.ai.rag）：`KnowledgePipeline`（knowledge 目录 .md/.txt 扫描 → 400 字符切分/50 重叠 → SimpleVectorStore 向量化 JSON 持久化 + md5 增量清单）、`EmbeddingModelProvider`（ai.embedding.* 三参数装配，硅基流动 bge-m3）、`KnowledgeTools`（searchKnowledge 工具）。管理接口 /api/vuln/ai/knowledge/{rebuild,status}（权限 vuln:ai:manage）。AiModelFactory 恢复挂载 knowledgeTools + system prompt 知识库指引。部署：scaffold-vuln.service 加 WorkingDirectory=/opt/scaffold-vuln，knowledge 目录需 ubuntu 属主（服务用户）；种子文档两篇（SQL 注入/XSS 修复指南）。端到端实测：问平台 SLA → 引用知识库回答 48 小时时限与平台约定。

## 2026-10-14（AI 迁移第二批：VulnTools 业务工具）

- 新增 `VulnTools`（com.scaffold.vuln.ai）：7 个 Function Calling 工具——platformSummary（四读数总览）、listVulns/vulnStatistics、listAssets/assetStatistics、listRepairTasks/repairTaskStatistics、topRiskAssets；内部走 vuln 模块四 Mapper，返回给模型的字段精炼裁剪 + 代码值翻译中文标签，列表限 20（最大 50）并附总数说明防上下文膨胀。挂入 AiModelFactory.defaultTools。端到端实测：问「现在有多少个高危未闭合漏洞」→ 模型调用工具后回答 10（与库中数据一致）。

## 2026-10-14（AI 迁移：悬浮窗前端）

- 新增 `components/ai/AiAssistant.vue` 全局组件（挂 AppShell）：可拖动悬浮球 + 聊天弹窗，交互逻辑自旧平台 AiAssistant（896 行）照搬——fetch+ReadableStream 手动解析 SSE（EventSource 带不了 Authorization 头）、伪打字机平滑输出、marked+highlight.js 渲染 + DOMPurify 消毒、代码块复制/下载（智能文件名）、会话下拉切换、停止生成、关闭中断流式。UI 换 scaffold 珍珠玻璃组件（sparkles 图标新入图标库）。api/vuln.ts 加会话接口封装。类型检查全绿；无权限用户 SSE error 帧显示验证通过。线上已部署（index-Bup0RssR.js）。

## 2026-10-14（AI 迁移第一批：后端对话能力）

- 自旧平台（Vulnerability-Manager，1800 行 AI 模块）迁移后端对话能力至 scaffold-vuln（包 `com.scaffold.vuln.ai`）：
  - `AiModelFactory`：Spring AI 2.0.1 ChatClient 动态装配（openai-java 4.x 凭据体系），配置三参数读 sys_config（ai.model.*，走新增的 `SystemManagementService.configValueByKey`）；**去掉旧版按天缓存改为每次重建**（构建无 IO，配置变更下一问即生效，无需失效通知）；连通性测试分型报错保留
  - `AiChatController`：同步 + SSE 流式对话 + 会话管理（新建/列表/历史），服务端持久化最近 10 条上下文、会话归属校验；权限 `vuln:ai:chat`（super_admin 放行，普通角色待 V5 配菜单）
  - `AiChatService` + 两 Mapper（注解 SQL）+ `AiChatCleanup`（@Scheduled 每天清理 90 天前对话）+ V4__ai_chat.sql 两表
  - `WeatherTools`：和风天气工具迁移，fastjson2 换 Jackson；vuln pom 显式依赖 scaffold-system（读 sys_config，与旧平台 vuln→system 耦合同构）
  - 框架增强：JwtService.parseUserId + 过滤器把 userId claim 放进 Authentication details（业务层免查库取用户 ID）
  - 依赖：根 pom 加 spring-ai-bom 2.0.1，vuln 加 spring-ai-openai/client-chat（openai-java 为传递依赖）；**Jackson 包名坑**：旧平台 SB4 带 Jackson 3（tools.jackson），scaffold 为 Boot 3.5（com.fasterxml），Controller 照抄旧 import 导致启动失败一次
  - 部署验证：线上 active/health 200，两表建立（本地+线上），hejian 调 AI 接口 403 权限拦截生效；admin 对话测试待前端页面

## 2026-10-14（参数配置页：secret 行等宽 + 分组名中文化）

- 修复 secret 行输入框被眼睛按钮挤窄导致各行长短不齐：眼睛按钮改为绝对定位浮在输入框右内缘（input 加右 padding 腾位），全部行输入框等宽。分组名中文化：sys_config 八条 AI/天气配置的 config_name 改为「前缀-标题」格式（AI 模型服务 / 向量模型服务 / 天气服务），splitConfigName 自动按首连字符分组，零代码改动。均已部署。

## 2026-10-14（AI 迁移预备：API Key 脱敏两处漏点）

- AI 服务配置迁入 sys_config（复用参数配置页，键 ai.model.* / ai.embedding.* / weather.*，用户完成）。审计发现两层识别清单没跟上 `api-key`（连字符）键名：前端 `configValue.ts` 的 SECRET 正则与后端 `OperationLogAspect.isSensitive` 均只认 `apikey/api_key`——页面明文显示 + 改配置时 key 明文进 sys_oper_log。两处正则统一补 `api[-_]key`，前端部署 + 后端 jar 部署（备份 .bak-1014-secret）完成；验证六个键判定正确（三个 api-key → secret，base-url/host/name → text）。AI 主体迁移（Spring AI + 会话表 + VulnTools 重写）另行开工。

## 2026-10-14（修复：登录页四步按钮 0↔2 死跳）

- 修复 LoginBrandPanel.vue 中 `watch/onMounted/onBeforeUnmount` 整块重复两份的编辑事故：双 onMounted 导致两个同步的 3800ms 轮播定时器并存，`selected` 每 tick 净 +2，四步按钮在 0↔2（用户观感 1↔3）之间死跳，且 pickStage 只能清掉后建的定时器。删除重复块后线上验证：interval 创建次数 1，激活序列恢复 1→2→3→0 顺序循环。

## 2026-10-14（登录页品牌卡：终端联动主视觉）

- 登录页品牌卡中部留白区落地「纸面终端 × 四步流程联动」主视觉（采样 `sampling-login.html` / `sampling-login-sync.html` 两轮比选，用户定联动方向并否掉深色版与居中构图）：
  - 点击底部四步按钮（或 3.8s 自动轮播，点击后交还控制权）→ 终端逐字打出品牌化 CLI（`vulnctl assets --scan` 等，虚构命令不指向真实数据）→ 结果行 + braille spinner 活性行依次进场，颜色全部随阶段分类色令牌（`--cat-{tone}-ink`：资产天蓝/风险沙金/工单丁香/复测薄荷），光标同色。
  - 视觉：纸面终端（`--surface` 白底 + 1px 细线 + `--e1` 轻阴影）与流程条同材质；620px 宽 13.5px 等宽字；流内左对齐并入标题内容轴（否掉水平居中的构图失重）。`motionSkipped()` 降级直接落终态，≤900px 隐藏，卸载清定时器。
  - `LoginBrandPanel.vue` 单组件内聚（script 联动状态机 + scoped 样式），`selected` 既有状态直接复用。已部署线上验证。

## 2026-10-14（品牌图标：六边形缺口）

- 平台图标由「深底 + 绿点」升级为「深底 + 六边形缺口 + lime 呼吸点」（六方案采样页 `sampling-icon.html` 比选，用户选 D）：隐喻「漏洞 = 完整结构上的洞」，后续可延伸——全部漏洞闭环时补全缺口。落地三处：favicon（`index.html` 内联 SVG，16px 粗描边参数）、外壳左上角与登录页品牌区（新组件 `components/business/BrandMark.vue` 两处复用），layout.css 中孤儿样式 `.brand-mark .brand-dot` 清除。已构建部署线上并截图验证登录页 / 外壳 / favicon。

## 2026-10-14（外壳外框采样 + 审计时间修复）

- 外壳外框去重：`.app-frame` 由 10px 近黑画框（`--frame`）改为 1px `color-mix(var(--ink) 22%)` 细描边，圆角 38px 收至 30px，阴影由 `0 24px 70px .24` 减淡至 `0 18px 48px .14`；舞台 8px 留白与移动端满铺降级不变。经四方案采样页（`sampling-frame.html`）对比后用户选定 D 方案，已部署线上。
- 修复 vuln 三表审计时间为 NULL 的问题：V3 建表时 `create_time/update_time` 误写为 `DEFAULT NULL`（偏离 V1 基座 `DEFAULT CURRENT_TIMESTAMP` 惯例），INSERT 又未显式写时间，导致新建资产/漏洞/工单创建时间为空、工作台"近七天新工单"统计恒为 0。新增 `V3.1__audit_time_fix.sql`（ALTER 三表列默认值 + 补齐历史 NULL 行），V3 文件同步对齐；本地与线上库均已执行并验证（NULL 清零，`/api/vuln/statistics/summary` 的 `weekTasks` 由 0 恢复为 4）。
- 修复时间字段序列化带 T（`2026-10-14T09:40:59`）的问题：vuln 三 Mapper 的列表/详情 SELECT 对 `create_time/update_time/finish_time` 统一 `DATE_FORMAT(..., '%Y-%m-%d %H:%i:%s')`，三个 Row 类对应字段由 `LocalDateTime` 改 `String`（与 system 模块 DATE_FORMAT 惯例对齐）；`plan_time` 为 `LocalDate` 无 T 问题、`found_time` 同理，均未动。前后端一并部署线上并截图验证（细线边框 + 时间格式同框确认）。

## 2026-10-13（登录页品牌采样）

- 恢复底部资产管理、漏洞分级、修复工单、复测闭环四个按钮及独立箭头间距；点击仅切换选中样式，主视觉继续留白。空白区域恢复原插画区域的最小高度，避免移除内容后收缩；未部署。
- 按当前设计讨论暂时清空登录品牌左侧红框区域：移除风险流转卡片、分页和流程交互，保留标题、背景留白及底部品牌信息，后续再单独确定主视觉方案。采样类型检查通过，未部署。
- 调整层叠交互为固定四卡顺序：四个流程按钮与四张档案一一对应，点击任意按钮时对应卡片从最底层抽到最上层，其余卡片顺移一格，不再使用循环索引；保留四色纸面和前后层次。类型检查与生产构建通过，采样服务已启动，未部署。
- 修正相邻选择从中层抽出的路径差异：所有目标卡先短暂隐去并在后层准备，再统一抬起、前移、向下覆盖；原前卡同步退入卡叠。保留四色与空间层次，放弃整叠淡出淡入方案；快速改选取消旧时间线，以最后选择落位。仅更新本地采样，未部署。
- 路径修正验证：采样类型检查通过；浏览器核对全部 12 种有向切换的后层层级与 3D 姿态及最终选中卡，覆盖相邻、跳选、反向和 D→A；快速连续改选、动画中开启减少动态均正确落位，无运行错误，已核对终态截图。
- 层叠档案新增翻台历式切换：四张档案保留各自 DOM 与内容，后层先沿上边缘抬起，再从上向下覆盖前卡；公共 `turnStackCards`（层叠卡片翻到前层）集中复用 GSAP 时序，快速改选从当前姿态续接。减少动态偏好、尺寸变化时直接落到终态，组件卸载清理动画与监听。
- 四流程绑定固定分类色：资产档案天蓝、风险分析沙金、修复记录丁香、复测结论薄荷；卡片移动前后不换色，底部图标与选中标题同步对应颜色，复用项目深浅主题分类色令牌，纸面底色不透明以保持遮挡关系。
- 本轮验证：采样类型检查、生产构建通过；浏览器完成真实 3D 翻动、四配色、卡片节点身份、快速反向改选、键盘切换、动画中开启减少动态、三档窗口调整和手机预览返回共 15 项检查；核对深浅主题截图，无运行错误、控制台警告或业务接口调用。仅更新采样与新增公共动效方法，未部署。
- 用户选定「层叠档案」方向：完整采样页以独立的 `samples/LoginBrandArchive.vue` 替换盾牌与连线主视觉，三层档案使用统一主题材质；底部四个流程切换资产档案、风险分析、修复记录和复测结论，复用公共内容进场动效。后两层标题完整露出，随容器高度调整卡片密度。
- 层叠档案验证：采样 Vue 类型检查通过；核对浅色、深色、手机与 901–1920px 桌面布局，1440×840 和 1366×768 一屏展示，三层标题无遮挡。四流程点击、键盘切换、快速连续切换及减少动态偏好正常，无运行错误、控制台警告或业务接口调用。
- 正式接入约束：保留线上登录框现有入场、指针倾斜 / 光效、提交反馈和登录成功动效，仅替换左侧品牌内容；当前仍为独立采样，未更改生产 `Login.vue` 或部署。
- 跟进流程栏间距：箭头从按钮内的绝对定位改为独立网格列，居中放在两个选项之间；桌面箭头两侧各留 13.5px，窄屏保留 5.5px，避免与选中卡片边缘粘连。浏览器核对浅色 / 深色截图及 901–1920px 宽度，按钮和文字无溢出，点击与键盘切换正常。
- 新增 `scaffold-vue/samples/login-brand.html` 和 `LoginBrandSample.vue`：以珍珠玻璃盾牌、资产节点及「看清资产风险，推动漏洞闭环」文案展示漏洞平台品牌，提供资产管理 / 漏洞分级 / 修复工单 / 复测闭环四个可交互流程。
- 复用项目主题、图标、表单控件及公共 GSAP 动效；支持浅色 / 深色、手机预览、键盘流程切换、动效暂停与减少动态偏好。验证码和登录仅作本地演示，不调用业务接口；独立 HTML 入口，不接入正式登录路由。
- 验证：采样 Vue 类型检查通过；浏览器核对桌面、深色及手机截图，验证 1920 / 1440 / 1366 / 1024 / 900 / 390 / 320px 宽度无横向溢出，桌面与笔记本高度一屏展示；流程点击与键盘切换、配色、模拟验证码、模拟登录、动效暂停及减少动态响应正常，无运行错误或控制台警告。

## 2026-10-13（修复列表 / 看板切换空白）

- 根因：`ListPage` 切换固定列表与看板时，Vue 更新根元素的动态类，覆盖了动效直接添加的进场标记；新挂载的查询、列表或看板节点被初始隐藏样式设为透明，因此刷新后才恢复。
- 页面与外壳统一改用独立的 `data-entrance-ready`（动效显示状态）属性，保留现有进场效果，避免布局切换与导航折叠覆盖显示状态；同步公共显现节点与工作台卡片的样式选择器。
- 新增 `scripts/view-switch-regression.cjs` 浏览器回归，检测元素及祖先的实际透明度，覆盖列表 / 看板往返、快速切换、初始保存视图、刷新与进场期间切换、导航折叠以及不同尺寸和动效偏好。修复前的构建已稳定复现：首次切到看板后，页面标题和看板透明度均为 0。
- 更正上一条用户列表验证的范围：原检查仅验证切换后的节点存在与布局尺寸，未验证透明度，不能证明切换后内容实际可见；本次回归补上这一缺口。
- 验证：类型检查与生产构建、8 组实际透明度与视图切换回归、原用户列表 7 组固定布局回归通过；另验证工作台普通 / 减少动态两种模式下卡片实际可见。列表 / 看板截图已核对，接口使用隔离 GET 夹具，无运行错误或控制台警告。
- 2026-10-13 15:43（北京时间）部署至 `www.liaoym.cloud`：发布前备份 `/home/ubuntu/scaffold-vuln-backups/users-switch-20261013-154151-before.tar.gz`，先同步资源再原子替换入口。公网 40 个文件与本地构建 SHA-256 一致，4 个业务路由返回新入口，nginx / scaffold-vuln 为 active、健康状态 UP；线上实际资源配合隔离接口重复 8 组切换可见性回归，全部通过，并核对切换后截图。未写入线上业务数据，本次未执行 Git 提交。

## 2026-10-13（用户列表固定布局）

- 用户管理的列表视图接入公共 `ListPage.fitViewport`（填满可视工作区），复用查询区、表头、分页固定及数据区内部滚动能力；批量操作在列表工具栏展示，看板保留原有布局。
- 扩展公共左树右表布局：桌面两栏填满同一工作区，部门搜索框固定，部门节点独立滚动；限制网格行的最小尺寸，避免较多部门撑高整页。沿用窄屏 / 低窗口自然滚动回退。
- 验证：类型检查与生产构建通过；Chrome 使用隔离 GET 接口夹具完成 7 组桌面 / 笔记本 / 临界窗口 / 深色 / 动效 / 手机 / 低窗口场景，覆盖 50 条用户数据、45 个部门、横纵滚动、部门筛选、空结果、分页、批量操作、看板往返及新增弹窗开关，无运行错误或控制台警告。已核对桌面及临界窗口截图，`git diff --check` 通过。
- 2026-10-13 15:22（北京时间）按用户要求部署至 `www.liaoym.cloud`：发布前备份 `/home/ubuntu/scaffold-vuln-backups/users-viewport-20261013-152139-before.tar.gz`，先同步资源再原子替换入口，保留旧散列资源。公网 40 个文件与本地发布包 SHA-256 全部一致，用户与三个漏洞业务列表路由返回新入口；nginx / scaffold-vuln 为 active，后端健康检查 UP。使用线上实际资源和隔离 GET 接口夹具重复上述 7 组浏览器检查，全部通过；未写入线上业务数据，本次未执行 Git 提交。

## 2026-10-13（漏洞业务列表固定布局）

- 公共 `ListPage` 新增 `fitViewport`（填满可视工作区），资产管理、漏洞管理、修复工单统一启用：视口至少 1024×700px 时，标题、查询区、列表工具栏和分页保持可见，仅表格数据区滚动；搜索字段按可用宽度伸缩。窄屏与低窗口回退为自然页面滚动，保留全部筛选项和分页的可达性。
- 公共 `DataTable` 新增 `scrollable`（可滚动数据区），保持吸顶表头与右侧操作列的正确层级，支持键盘滚动；查询、翻页和刷新后回到首行，保留横向位置。固定布局列表的批量操作移至列表工具栏，避免遮挡分页，沿用原 `#batch` 插槽。
- 公共进场动效跳过空的卡片 / 显现节点集合，消除列表页的空动画目标警告。同步设计规范与项目地图。
- 验证：21 项前端单元测试、类型检查与生产构建通过；Chrome 使用隔离 GET 接口夹具验证三个实际 Vue 页面，共 33 组尺寸 / 深色 / 动效场景，覆盖 50 条数据内部滚动、吸顶层级、最后一页、空结果、查询重置、批量操作、键盘滚动与窄屏回退，无运行错误或控制台警告。已核对桌面、临界窗口及深色截图，`git diff --check` 通过。
- 2026-10-13 11:54（北京时间）按用户要求部署至 `www.liaoym.cloud`，代码提交 `3ad414d`。发布前备份 `/home/ubuntu/scaffold-vuln-backups/list-viewport-20261013-115233-before.tar.gz`；先同步资源，再原子替换入口并保留旧散列资源。公网 40 个文件与发布包 SHA-256 全部一致，资产 / 漏洞 / 修复工单三个路由返回新入口，nginx 与 scaffold-vuln 为 active，后端健康检查 UP。使用线上实际静态资源与隔离 GET 接口夹具重复上述 33 组浏览器场景，全部通过，无运行错误或控制台警告，未写入线上业务数据。

## 2026-10-13（安全态势与采样页对齐）

- 数值移入柱体并相对柱顶定位，距柱顶 7px；复用公共 `BarChart` 新增的可选择列模式，点击和键盘选择同步高亮与月份读数，默认选择数据中的最后一个月。保留原紧凑模式并恢复工作台每日操作量图表的组件导入。
- 按已批准采样恢复盾牌标题、浅色入口按钮、标题区留白、2×2 指标比例和 172px 绘图区；按卡片内容宽度切换上下布局，长时间序列在图表内滚动，零值不画虚假高度。日期范围来自数据，支持跨年，刻度与柱高共用一致比例。
- 验证：21 项前端单元测试、类型检查与生产构建通过；Chrome 实际 Vue 页面使用隔离接口夹具完成 1440 / 1920 / 1000 / 390 / 320 宽、深色、零值、空数据、跨年、12 个月共 10 种场景，柱顶间距、点击与键盘反馈、旧操作量图表、卡片无溢出检查通过，无浏览器运行错误。已核对桌面、手机、深色截图；不涉及业务数据写入。
- 2026-10-13 10:53（北京时间）部署至 `www.liaoym.cloud`：备份 `/home/ubuntu/scaffold-vuln-backups/security-parity-20261013-1050-before.tar.gz`，先同步资源再原子替换入口，保留旧散列资源；公网入口、`index-DSWOfZL_.css`、`index-BXDxDsFx.js`、`Dashboard-A2gBb003.js` 与构建包逐一比对一致，nginx / scaffold-vuln 为 active，健康检查 UP。再以公网实际静态资源、隔离 API 夹具执行上述 10 种浏览器场景，全部通过；未使用真实账号或写入线上业务数据。

## 2026-10-13（安全态势布局采样）

- 新增 `samples/security-overview.html` 静态采样页，复用现有珍珠玻璃主题样式，展示 2×2 指标与月度柱状图布局，提供原版结构对照、深浅主题、手机宽度预览与月份选择。数据仅取自用户截图，不接入业务接口。
- 按采样方案改造首页安全态势卡：四项指标改为 2×2，月度趋势改为带刻度和当前月份高亮的柱状图，业务入口移到标题区；前端构建通过并部署至 `www.liaoym.cloud`。发布前备份至 `/home/ubuntu/scaffold-vuln-backups/security-board-1791856926406-before.tar.gz`，公网资源与本地构建产物一致，后端健康检查为 UP。

## 2026-10-13（工作台最近登录卡片等高修复）

- 跟进底部留白：最近登录卡片改为纵向弹性布局，列表从 176px 基准高度伸展至卡片剩余空间，保留正常底部内边距与内部滚动；窄屏独立成行时保持紧凑高度。
- 移除今日操作泳道与最近登录卡片的 `align-self: start` 覆盖，恢复公共网格同一行卡片自动等高；保留登录列表内部滚动和窄屏单列布局。
- 验证：前端类型检查与生产构建通过，`git diff --check` 通过；未进行浏览器视觉复验。
- 2026-10-13 09:12（北京时间）按用户要求部署到 `www.liaoym.cloud`：沿用已验证构建产物，先更新静态资源再原子替换入口 HTML，保留旧资源；发布前备份至 `/home/ubuntu/scaffold-vuln-backups/dashboard-align-1791853864167-before.tar.gz`。公网入口、样式和工作台脚本的 SHA-256 与本地一致，后端健康检查为 UP。
- 跟进部署（2026-10-13 09:17，北京时间）：最近登录列表弹性填满卡片剩余高度，已发布至 `www.liaoym.cloud`；桌面 / 手机布局检查通过，发布前备份至 `/home/ubuntu/scaffold-vuln-backups/dashboard-fill-1791854196112-before.tar.gz`，公网入口、样式、工作台脚本逐一比对一致，后端健康检查为 UP。

## 2026-10-12（漏洞管理业务模块 · 阶段 4 部署上线 www.liaoym.cloud）

- 部署方式：本地构建直传（无 CI）——后端 `mvn package` 出 fat jar scp 至 /opt/scaffold-vuln/，前端 `npm run build` 出 dist 至 /var/www/vuln；systemd 服务 `deploy/scaffold-vuln.service`（端口 6061，-Xmx320m，独立 JWT_SECRET 环境变量）。
- 数据迁移：服务器 `V4__real_migrate.sql`（deploy/ 留档）——账号体系从 vuln_manager(RuoYi) 重建：6 个真实用户（密码哈希直搬，登录密码不变）、部门「老九门专业团队」、角色保留 super_admin + 新增 vuln_member（授权漏洞全菜单，lym 与 admin 同为超管）；test 演示账号不迁；业务三表跨库原样搬（资产 25 / 漏洞 37 / 工单 9），用户 id 沿用旧库故 handler/verifier 无需映射，test 名下 5 条工单归 admin。
- nginx：ruoyi-vuln.conf 改写（旧版备份 .bak-1012）——root 换 /var/www/vuln，新增 /api/ 原样透传 6061，/prod-api/ 与 /profile/ 一律 404；80 段 certbot + 301 保留。
- 旧服务下线：ruoyi-vuln stop + disable（jar 与 service 文件原地保留，回滚 = nginx 还原备份 + systemctl start ruoyi-vuln）；可用内存 558Mi → 852Mi。
- 探测脚本联动（portal-design 仓库 e1a07cf）：www 探测改 /api/health，vuln 色谱直连库改 scaffold_system。
- 组件修正：TextInput 的 type 扩展 date / datetime-local / time / month / url / search（HTML 原生类型，业务表单日期控件所需）。
- 验证：公网 https://www.liaoym.cloud 前端 200、/api/health UP、验证码接口 200；探测 www up 18ms，vuln 色谱数据从新库聚合正常（high 16 / med 13 / low 8 / assets 25）。

## 2026-10-12（漏洞管理业务模块 · 阶段 2 工作台安全态势）

- 后端统计域：`VulnStatisticsMapper` / `VulnStatisticsService` / `VulnStatisticsController`（/api/vuln/statistics/*，只读聚合）：summary 指标（总资产 / 未闭合漏洞=新发现+修复中 / 高风险未闭合 / 本周新工单）、风险等级分布、按月发现趋势、高危资产 TOP5（按高危漏洞计数）。权限复用 `vuln:vulnerability:list`。
- 工作台改造：`Dashboard.vue` 新增「安全态势」置顶区块——4 张 Metric 指标卡（database/alert/ban/board 图标）+ 漏洞发现趋势 TrendChart（tone=blue）+ 风险等级分布 BarChart（低/中/高补零对齐）+ 高危资产 TOP5（复用 share-list 占比条样式），全部复用 bento 栅格零新增样式；无 `vuln:vulnerability:list` 权限时整块隐藏，统计接口失败静默降级；副标题随权限切换。
- 数据迁移佐证：E2E 与样板数据清理后，工作台态势数据随库实时聚合。
- 验证：后端 package 通过；前端 Vite 热更编译通过（浏览器端待重启后端后验收）。

## 2026-10-12（漏洞管理业务模块 · 阶段 1 全量迁移完成）

- 漏洞域后端：`VulnVulnerabilityRow` / `VulnerabilityWriteRequest` / `VulnVulnerabilityMapper`（LEFT JOIN 资产带 IP/名称展示列，同名列全部带 v./a. 前缀）/ `VulnVulnerabilityService`（CVE 正则校验、发现日期不晚于今天、删除前锁行校验无工单、归属变更时新旧资产都重算风险）/ `VulnVulnerabilityController`（/api/vuln/vulnerabilities）。
- 工单域后端：`VulnRepairTaskRow`（三表 JOIN + sys_user 两次取昵称）/ `TaskWriteRequest` / `TaskActionRequest` / `VulnRepairTaskMapper` / `VulnRepairTaskService` / `VulnRepairTaskController`。状态机 0→1→2→{3|1}→4 走语义化动作端点（accept/fix/verify/close），接单与提交修复限负责人本人或有编辑权限，复测禁止负责人本人（复测独立性），验证意见必填，提交修复清空上轮验证意见与验证人，实际完成时间在状态 2/3 必填。
- 状态联动：每次工单变化后按聚合（全待处理→新发现；全关闭→已关闭；有待/处理中→修复中；有已修复/已验证→已修复）重算漏洞状态，并联动资产风险（未关闭漏洞最高风险，无则归低）；漏洞归属变化时新旧资产都重算。
- 跨模块解耦：不依赖 scaffold-system，工单校验负责人/验证人、登录名→用户 ID 均直查 sys_user（注解 SQL）。
- 菜单放开：V3 脚本解除 402/403 段注释并重放（漏洞管理页 alert 图标、修复工单页 board 图标，各 3 个按钮），权限码 vuln:vulnerability:* / vuln:repair:*。
- 前端：`api/vuln.ts` 扩展（vulnerabilities + repairTasks + 四个动作端点）、`views/vuln/Vulns.vue`（资产下拉、状态列由工单自动计算）、`views/vuln/Repairs.vue`（UserSelect 选负责人、按当前状态渲染接单/修复/复测/关闭快捷按钮、复测弹窗 Segmented 通过/打回切换）、COMPONENT_MAP 登记。
- 详情三级穿透：复用底座 Drawer 组件——资产详情抽屉（kv 网格 + 关联漏洞子卡）、漏洞详情抽屉（+ 修复工单子卡，含状态/优先级/验证意见留痕），眼睛按钮触发，origin-from 从行生长。
- 工作台泳道两处小改：`Swimlane` 新增 `fixedLanes`（固定可视行数，超出内部滚动）与 `showHiddenHint`（截断提示开关），Dashboard 固定 5 行并关闭提示——修正行数多时卡片高度跳变与溢出。
- 验证：编译通过；E2E 接口实测——状态机全路径与顺序校验、自复测拒绝、验证意见必填、打回→再修复→复测通过→关闭闭环、漏洞状态与资产风险全程联动（close 后 2/2 → 3/0）、删除保护链；浏览器端三个菜单 + 抽屉交互可用。sys_admin 角色经 SQL 授权漏洞菜单用于双账号复测（界面可配置）。

## 2026-10-12（漏洞管理业务模块接入 · 阶段 0 样板间）

- 新增 Maven 业务模块 `scaffold-vuln`（迁移自 RuoYi 版漏洞平台 Vulnerability-Manager）：根 pom `modules` 与 `dependencyManagement` 登记、`scaffold-admin` 显式依赖打包、启动类 `@MapperScan` 增加 `com.scaffold.vuln.mapper`。
- 新增迁移脚本 `scaffold-system/sql/V3__vuln_module.sql`：业务表 `vuln_asset` / `vuln_vulnerability` / `vuln_repair_task`（含四审计字段与索引，risk_level / vuln_status 为自动计算列）、菜单（漏洞管理 dir + 资产管理 menu + 3 按钮，sort 重排为工作台 1 / 漏洞管理 2 / 系统管理 3 / 监控 4 / 工具 5）、业务字典 5 组 18 条（tone 对齐前端 Tag 现有四档）。
- 资产域完整闭环作为范式样板：`VulnAssetRow` / `AssetWriteRequest` / `VulnAssetMapper`（注解 SQL + `<script>` 动态条件 + 手写分页两条 SQL）/ `VulnAssetService`（IPv4 校验、新建强制 riskLevel='0'、删除前 FOR UPDATE 锁行并校验无关联漏洞、CSV 导出）/ `VulnAssetController`（`/api/vuln/assets` RESTful，`@RequiresPermission` + `@OperLog`）。
- 前端新增 `api/vuln.ts`（`crudPage` + 导出）、`views/vuln/Assets.vue`（ListPage + DictSelect + FormDialog + PermissionButton 全复用）、`COMPONENT_MAP` 登记 `vuln/asset/index`；菜单/权限码约定 `vuln:asset:list/add/edit/remove`，导出与详情复用 list。
- 漏洞管理页与修复工单页菜单 SQL 预留注释，阶段 1 放开；AI 助手与 harmonyOS 演示页确认不迁移。
- 验证：后端 `mvn package` 通过；接口实测登录 → 新增 → 列表 → 详情 → 修改 → 删除全 200，非法 IP / 空必填 / 删不存在均 400 返回中文业务消息；未认证请求 401。

## 2026-10-11（MIT 开源发布）

- 以当前代码建立独立的公开版本快照，采用 MIT License；第三方依赖保留各自许可，README 单独说明 GSAP 许可。
- 开源内容保留前后端源码、测试、初始化 SQL、锁文件、必要开发脚本及当前架构文档；排除宣传视频、旧规划、编辑器配置、依赖、构建产物、缓存、日志、备份和私密配置。
- JWT 密钥改为必填环境变量 `JWT_SECRET`，启动时校验 UTF-8 编码至少 32 字节且不能全为空白；新增无需数据库和 Redis 的密钥边界与签名验证测试。
- 初始化示例电话留空，示例邮箱改用 `example.com`；清理公开文档中的本机绝对路径和已排除目录入口，保留历史日志与人工备注。
- 发布整理仅作用于独立副本，原项目工作区及本地提交历史保留。
- 验证：独立副本 `npm ci`、21 项前端单元测试、含类型检查的前端构建、8 项后端公共模块与 JWT 单元测试、完整后端编译打包均通过；核对必要源码、文档链接和排除规则，敏感信息模式扫描未发现问题。本次未执行数据库集成测试或浏览器业务回归，未操作实际数据库。

## 2026-10-11（宣传视频 v2：45 秒卡点重制）

- 重新设计为约 45 秒的版本：开场用"普通表格 → 高光快剪"制造反差，按 120 BPM 节拍网格剪辑，每个动效都推近并跟拍，深色模式切换放在 drop 上；口播改用 `zh-CN-YunjianNeural`，并改写成短句。
- 清晰度：录屏视口改为 1440×810、DPR 3，加 `--force-device-scale-factor=3` 后 screencast 输出真实的 4320×2430 帧（不加时 headless 只输出 CSS 尺寸）。`clock.js` 把页面时钟和 CSS 动画同步放慢 10 倍再抓帧，内容帧率约 70fps。用 Pillow 亚像素裁切加 Lanczos 渲染推拉镜头，最终只做一次 H.264 CRF 14 编码。
- 声音：`music.py` 用 numpy 合成 BGM 和 8 种音效（无外部采样）；口播出现时 BGM 自动压低，两遍 loudnorm 到 -14 LUFS。
- 竖版改为深色画布：上方是段落标题，中间是 4:5 主画面，并单独设计一套镜头轨迹，不再用模糊背景铺底。
- 产出：`out/v2/` 下横版 2560×1440 和 1920×1080、竖版 1440×2560 和 1080×1920，均为 60fps、47 秒；另有 SRT 字幕、横竖封面、音频预览。v1 脚本保存在 本地历史宣传素材目录，v1 成片保留不动。
- 验证：横版 1440p 码率 12.7 Mbps；响度 -14.1 LUFS，峰值 -1.2 dBFS。在同一界面区域按 1:1 截图对比 1080p 的 v1 与 v2，v2 的文字和图形明显更清晰。抽帧核对了取景、字卡和字幕的时间与位置。

## 2026-10-11（宣传视频清晰度重制）

- 录屏由 JPEG 88 抓帧改为原生 1920×1080 PNG 抓帧，取消将 1080p 图像放大成 2880×1620 的伪高分辨率步骤；素材使用 RGB 无损编码，以毫秒时间基保留动作时序、按记录时长封口并转换为 30fps，在关闭页面前等待抓帧确认完成。
- 合成的中间片段改为 RGB 无损编码，最终只做一次 H.264、CRF 14 压缩，并统一 BT.709 色彩与有限范围；竖版主体采用轻微锐化，上下背景沿用原稿的虚化效果。取景按素材实际尺寸折算，兼容原素材。
- 增加素材与成片目录参数；本次新素材写入 `_clips_hd/`，高清横竖成片、字幕和封面使用 `_hd` 后缀，保留原口播、剪辑场景与原文件；同步录制说明及项目地图。
- 验证：9 个场景完成实际页面录制；横版 1920×1080、竖版 1080×1920，均为 30fps、2158 帧、71.94 秒，两版全片解码通过；核对关键场景的文字、取景和字幕，口播音频与字幕内容和原版一致。片段按累计帧数对齐，避免逐段时长舍入产生累计偏移。

## 2026-10-11（修复：浮层内滑块首帧错位）

- 分段选择器、侧栏选中块、命令面板高亮、代码生成步骤条共用的 `slideThumb` 在祖先处于缩放过渡（弹窗 / 命令面板从触发点展开、页面进场）时量到的是缩放后的包围盒，首次打开菜单编辑弹窗时「菜单类型」滑块偏小错位，切换一次才纠正；现按祖先当前缩放比折算回布局像素。`Segmented` 另监听各选项尺寸（字体加载完成只改选项宽度时也重新对位）。
- 表单字段内的分段选择器不再被纵向 flex 拉伸成整行空槽（`.field > .segmented` 按内容定宽）；部门画布适配视口改用布局尺寸，不受页面进场缩放影响。

## 2026-10-11（图谱舞台回归珍珠玻璃）

- 撤销暗场舞台：删除 `.stage-scope` 深色令牌，改为浅 / 深主题各一套 `--graph-*` RGB 三元组；舞台底改为与外壳一致的浅底点阵 + 环境光，深色只随外观设置切换。
- 视觉隐喻替换：中心光球改为珍珠球（珠光渐变、淡虹彩、缓慢扫描的刻度环与青柠卫星点）；程序化水母神经元改为玻璃刻度表盘，刻度着色比例为真实读数（已分配角色的用户、有持有人的角色、已授权页面）；叠加发光、粒子迸发改为实色细线、18% 光环脉冲与开场细环涟漪。
- 溯源全景：条目改为围绕表盘的整齐刻度弧与放射细线，跨类授权线只在悬停聚焦时出现，名称沿放射方向排开；条目过多时不画名称。台阶、分析栏、问答坞、工具条全部回到珍珠玻璃材质。

## 2026-10-11（设计审查与第三轮重构：暗场舞台、全站中文化、数据口径修正）

- 暗场舞台（已于同日撤销，见上条）：`tokens.css` 新增 `.stage-scope`（舞台底、暗玻璃、`--glow-1~5` 发光色，并在舞台内重新声明文字 / 描边 / 面板令牌，分析栏与问答坞自动换肤）；新增 `composables/neural.ts` Canvas 绘制件（光球、程序化神经元、发光丝、脉冲、迸发、真实数据背景网络、轨道、台阶光柱、地平线）；`RelationGraph` 新增 `variant="stage"`、`network` / `networkFocus` / `networkAlpha` / `orbits`、`slab` 台阶节点与 `hover` 事件、「重播 / 暂停」，删除原随机三角网背景；可点小目标不再漂移。
- 权限溯源重做为三层下钻（全景 → 模块 → 实体），背景网络为全部用户 / 角色 / 菜单真实点位与授权线；菜单星图重做为「导航星系」（三圈刻度轨道、分支扇出、角色 → 页面 → 按钮链路，侧栏预览改为占位不覆盖）；岗位阶梯重做为「能量阶梯」（对角线铺满不裁切、光柱与地平线、脊线脉冲、成员触须，删除右侧大空面板；窄屏横向滚动）。
- 全站文案中文化：`SectionLabel` 去掉英文眉标与编号，`PageHeader` / `ListPage` 去掉 `eyebrow`；工作台日期、读数标签、用户看板（最近登录 / 从未登录）、部门读数、登录页、命令面板、矩阵、泳道等英文标签改为中文；参数面板 `light / dark / auto` 显示为浅色 / 深色 / 跟随系统（取值不变）。`.eyebrow` 改为中文小标签样式。
- 工作台：删除布局重叠且与常用入口重复的「模块网络」卡；数据权限气泡改为横向占比条（删除 `BubblePack`）；删除页头与侧块重复的规格铭牌。操作日志 / 登录日志删除与概览读数重复的页头铭牌。
- 稀疏数据：`TrendChart` 非零点 ≤ 3 时改画茎叶点并标数值；`Swimlane` 默认聚焦到有操作的时段（至少 6 小时），可切全天；`DeltaPill` 新增 `fresh`「新增」态。
- 表格：操作列吸附右侧，用户表在 1440 宽下操作列不再被挤出可视区。
- 后端口径修正：登录成功写入 `sys_user.last_login_time`（最近登录时间，此前从未写入导致看板恒显示「从未登录」）；岗位 / 角色在岗人数与工作台数据权限分布排除已删除用户；`/dashboard/summary` 的 `stats[].fresh`（上一周期为 0、无法计算环比）新增。`AuthApiTest` 补最近登录时间断言。
- 验证：前端 `npm run test:unit` 21 个用例、`npm run build`（含 vue-tsc）通过；后端 `mvn -pl scaffold-admin -am test` 46 个集成测试通过；Playwright 1440 / 390 宽、浅色 / 深色、减少动态效果截图核对三个舞台与工作台、日志、用户页；舞台页无头实测满帧、最差帧约 10ms。

## 2026-10-11（神经网络升级：全模块差异化视图与全局炫技）

> 分工说明：Phase 0–6 公共底座与前六个模块由 Claude 完成；共享视口抽取、Phase 7 岗位阶梯、Phase 8 全局炫技点及文档/验证由 Devin（SWE）完成，条目分别以【Claude】/【SWE】标注。

- 【Claude】公共底座：`composables/motion.ts` 扩展 `spring`、`createFrameLoop`、`revealCascade`、`typeOut`、`bezierAt`、`firstTimeInSession`；新增 `RelationGraph`（BFS 逐层蔓延入场、贝塞尔连线脉冲、扇形触须、悬停邻接高亮、拖拽平移与缩放）、`InsightPanel`、`AskDock` 与本地规则问答引擎 `ask.ts`；`instruments.css` 补 `.rg-*`、`.insight-*`、`.heat-*` 样式；令牌新增 `--thread`、`--thread-active`、`--pulse-halo`、`--node-bg`（浅深两套）。
- 【Claude】权限溯源：`GET /api/system/permissions/graph`（权限 `system:role:list`）返回用户、角色、菜单及用户-角色、角色-菜单两组关联；角色页新增「溯源」视图（三能力岛环绕开场、选中角色扇形展开用户与菜单、`InsightPanel` 读数下钻、底部问答坞），用户页行操作带 `?trace=user:ID` 跳转聚焦。
- 【Claude】登录热力与异常：`GET /api/monitor/login-logs/heatmap?days=`（权限 `monitor:loginlog:list`）返回 7×24 `cells` 与连续失败 `anomalies`；登录日志「02 趋势」可切热力格，对角线波纹点亮、悬停十字高亮、失败占比斜线，「03 异常」卡片点按联动明细筛选。
- 【Claude】参数设置面板：参数页默认「面板」视图（可切表格），按键名前缀分组、按值推断控件行内编辑，改动累积到底部浮条显示「原值 → 新值」一次提交，失败项保留标红。
- 【Claude】菜单星图与导航预览：菜单页新增「星图」（目录 / 菜单 / 按钮三环 radial 布局，按钮显示权限码），有角色权限时叠加角色环，孤立菜单与缺权限码按钮红描边；左侧固定导航预览实时渲染当前树，隐藏 / 停用项虚化。
- 【Claude】代码生成流水线与字典样本册：生成抽屉改三步流水线（表信息 → 字段映射贝塞尔连线 → 代码逐行级联预览）；字典页新增「样本册」视图，真实 `DictTag` 平铺，点卡片 Flip 原地展开编辑（label / tone / 排序 / 默认项金标）。
- 【SWE】共享视口：抽取 `composables/viewport.ts`（`useViewportPan`），组织画布与关系图复用同一套拖拽平移、Ctrl/⌘ + 滚轮缩放、pointer capture 与 reset 逻辑，移除两处重复实现。
- 【SWE】岗位职级阶梯：用户分页、统计与导出接口新增可选 `postId` 参数（经 `sys_user_post` 关联过滤，沿用 `@DataScope` 数据权限）；新增 `PostLadder` 组件，岗位页增加「阶梯」视图——按 `sort` 排成上升台阶，`TickRing` 显示在岗人数，点卡片右侧扇形展开在岗成员触须。
- 【SWE】命令面板「问系统」：输入 `?` 切换问答模式，展示预设问题芯片，提交后打字机 + 答案卡级联，卡片芯片可跳转对应页面；`ask.ts` 新增参数意图（开关参数、登录验证码是否开启、敏感参数值），走 `system:config:list` 权限过滤与 `configs.list` 数据；参数页支持 `?keyword=` 定位。
- 【SWE】登录神经网络背景：新增 `NeuralNet.vue`（Canvas 节点 = 能力模块，连线脉冲往返、节点缓慢呼吸），置于登录页左侧深色画布底层；登录成功时中心迸发短划并收束到对勾，页面隐藏、离屏、reduced-motion 时暂停或静态呈现。
- 【SWE】工作台开场与模块网络：本会话首次进入工作台，卡片从中心弹簧飞出依次落位；新增「模块网络」小卡（用户 / 角色 / 菜单 / 部门节点 + 关联脉冲，点节点跳转对应页面）；`/dashboard/summary` 的 `stats` 新增 `menus`（菜单项总数）与 `depts`（部门数）两项。
- 【SWE】细化动效：部门画布选中节点时根 → 当前节点路径连线高亮并沿曲线走脉冲；用户看板拖拽成功落位与取消回飞改用阻尼弹簧；操作日志详情抽屉与角色授权抽屉内容接入 `revealCascade`（约 70–90ms 级联，reduced-motion 静态呈现）。
- 【SWE】验证：前端 `npm run test:unit` 21 个用例通过，`npm run build`（含 vue-tsc）通过；后端 `mvn -pl scaffold-admin -am test` 45 个集成测试通过（含用户 `postId` 岗位过滤、权限图、登录热力与新增 summary 字段断言）。浏览器实测：登录页网络呼吸与成功迸发、工作台开场弹簧入场与模块网络节点跳转、岗位阶梯台阶与成员触须（含深色）、部门画布选中路径脉冲、命令面板 `?` 问答（预设芯片 + 三类参数意图应答 + 芯片跳转）、操作日志 / 角色抽屉级联、375px 无横向溢出、reduced-motion 静态呈现，全程无控制台错误。后端测试需本地 MySQL 与 Redis，连接信息走 `DB_*` / `REDIS_*` 环境变量。

## 2026-10-11（UI 精致化：仪器化语汇与六种版式）

- 新增仪器化语汇：等宽眉标与编号、显示级读数（逐位滚动、上标单位）、深色规格铭牌、环比胶囊、刻度尺、斜线填充、悬停名片；令牌补充等宽字体、显示字号、刻度、铭牌、5 枚只表示类别的分类色与画布环境光，样式新增 `instruments.css` 一层。
- 新增自绘图表：趋势图（切换时间档时折线形变、准星读数平滑跟随、失败序列与区间斜线）、柱状图、60 刻度成功率环、气泡占比、时间轴泳道（实时「现在」线、失败与慢操作标记）。
- 工作台改为主次布局：登录脉搏主卡（总量、环比、峰值 / 均值）+ 在线与接口侧块 + 近 7 天操作量 / 成功率环 / 数据权限气泡 + 当日操作泳道（有日志权限时）+ 最近登录与常用入口。
- 操作日志、登录日志改为横带分层（概览 → 趋势 / 泳道 → 明细）；点击泳道块按模块过滤明细。部门管理新增组织架构画布（平移缩放、连线先到节点后亮、四角浮动面板）；用户管理新增看板（按部门 / 角色 / 状态分列，按状态可拖拽启停，提供键盘「移动到…」）；角色管理新增菜单 × 角色权限矩阵（十字高亮、级联勾选、底部浮条一次提交，内置角色只读）。视图偏好按页面记在本机。
- 登录页改为全幅深色画布：实时时钟刻度、服务健康与延迟、能力面板；登录卡随指针轻倾，登录成功走「收拢 → 对勾圆环 → 进入」三拍；进入外壳的导航被取消时恢复登录按钮。
- 浮层与外壳：弹窗从触发点展开、列表行生长成详情抽屉并收回原行、批量操作改为底部深色玻璃浮条、命令面板分组为最近访问 / 页面 / 用户 / 动作并以玻璃滑块指示焦点、提示改为可悬停展开的牌堆（悬停暂停计时、倒计时细环）、主按钮涟漪、内容滚动后顶栏凝结、表格中文短字段不再逐字折行。
- 后端新增只读接口：`GET /api/monitor/oper-logs/stats`、`GET /api/monitor/oper-logs/timeline`、`GET /api/monitor/login-logs/stats`（`MonitorStatsService` / `MonitorStatsMapper`）；工作台汇总新增 `trendPrev`（上一周期登录数）与 `dailyOps`（近 7 天按日操作量）。不改表结构。
- 同步 `DESIGN.md`（七层样式、令牌、版式、能量语态表）、`PROJECT_MAP.md`、两份架构 / 接口文档与 UI 方案文档。
- 验证：后端在新建的独立校验库 `scaffold_verify` 上 `scaffold-admin` 41 个集成测试通过（含新增 `MonitorStatsApiTest` 4 个及工作台字段断言）；前端 15 个单测、类型检查与构建通过；会话回归脚本两项通过。浏览器在 1440 / 375 / 320 px 与深色下检查工作台、两个日志页、部门画布、用户看板、角色矩阵、登录页，11 个页面 320 px 无横向溢出、无 Vue 警告；实测泳道、看板拖拽停用与恢复、矩阵授权保存与还原、命令面板用户搜索跳转、行展开抽屉、提示牌堆展开。测试中的临时改动均已还原。浏览器面板处于后台时 rAF 不运行，入场长帧未能实测。

## 2026-10-10（通用后台脚手架整理与验收）

- 移除建筑、工作流模块及 Flowable 依赖，后端保留公共基础、框架、系统管理、代码生成和启动模块；数据库首次初始化收敛为 `V1__init_schema.sql`、`V2__seed_data.sql`。
- 前端采用 Vue 3、GSAP 与自有珍珠玻璃组件，移除 Element Plus 和 three；统一主题、表格密度、列表模板、查询、表单、权限按钮、字典及组织选择组件，重写登录、工作台和 10 个管理页面。
- 修复切换账号时旧路由初始化、旧菜单响应、慢退出请求及旧账号 401 覆盖新会话的问题；退出同步清除路由、权限及字典/部门缓存，未分配菜单的账号显示明确落点。
- 登录 token 增加独立标识，避免同秒重新登录生成相同 token；停用或已删除角色不再授予菜单和权限。用户页面改为按用户操作权限获取必要选项，业务字典选项与管理接口权限分开。
- 超级管理员自动获取全部菜单与权限；用户按部门筛选包含下级部门；工作台 7 天与 30 天趋势包含当天；代码预览返回全部文件，生成菜单 SQL 与当前字段及手动主键结构一致。
- 数据库、Redis 连接读取环境变量；MySQL 时区参数通过连接属性传递，避免 `+08:00` 被 URL 解码为无效值，应用连接与初始化工具统一设置数据库会话时区。
- 修复页面切换空白、减少动态效果与后台标签页关闭浮层的问题；浮层按打开顺序处理层级、焦点和 Escape，下拉层级跟随所在弹窗；长内容抽屉保持底部按钮可见，正文独立滚动。
- 新建根目录 `README.md`、`PROJECT_MAP.md`，重写 `DESIGN.md`、前端说明及三份架构/接口文档，更新 `AGENTS.md`；保留历史规划和日志，移除历史文档中的实际数据库密码，忽略本地缓存、日志和私有环境文件。
- 验证：41 个后端测试通过并完成打包，15 个前端测试通过，类型检查与构建通过。真实浏览器完成会话竞态与低权限验证、字典/菜单增改删与导出、生成器导入/配置/7 文件预览/ZIP 下载；检查 11 页在 320/375px、深色模式及减少动态效果下的显示，以及主题跟随、密度持久化、菜单搜索和嵌套浮层键盘操作。生成菜单 SQL 在集成测试中实际执行并回滚。
- 验证使用独立本地校验库。按确认删除四份旧报告/需求文档，并重建 `scaffold_system`；初始化数据移除不存在的业务表生成配置，生成器从空列表开始。代码生成输出仍是开发骨架，业务 CRUD 和页面接入需在后续项目中实现。

## 2026-06-08 (侧栏动态菜单修复)

- 修复前端侧栏动态菜单跳转：后端返回的 `/system/role`、`/system/dept` 等路径型路由不再被当作 Vue Router `name` 处理，避免点击左侧菜单时报 `No match for {"name":"/system/role"}`。
- 新增导航工具单元测试，覆盖静态菜单路由名和动态菜单路径两种跳转来源。

## 2026-06-07 (最终验收修复)

- 修复后端集成测试登录夹具：新增 `TestAuthClient`，统一按真实契约先调用 `GET /api/auth/captcha` 获取 `captchaUuid` 和验证码，再提交 `POST /api/auth/login`。
- 更新认证、系统管理、监控日志、工作流、代码生成等测试中的旧登录写法，避免验证码校验拦截导致验收测试无法覆盖真实业务接口。
- 更新 `scaffold-vue/README.md`，将“纯前端 Mock、未接后端接口”的历史说明改为当前真实接口接入状态，并补充 `VITE_API_PROXY_TARGET` 代理配置说明。
- 完成最终验收验证：`mvn test` 通过 42 个后端测试，`mvn package -DskipTests` 打包成功，`npm run test:unit` 通过 18 个前端单元测试，`npm run build` 通过；本地启动后端 `18081` 和前端 `5178`，经前端 `/api` 代理完成验证码、登录、当前用户和工作台摘要真实接口冒烟；`admin-redesign` 独立原型 `npm run build` 通过。

## 2026-06-06 (Flowable 工作流闭环)

- 接入真实 Flowable 基础运行时：`scaffold-admin` 显式声明 `flowable-spring-boot-starter-process`，dev 配置开启 Flowable schema 自动更新和 schema 更新锁，并关闭异步执行器。
- 新增 `V3__flowable_workflow.sql`：扩展 `wf_model_meta.bpmn_xml`、`deployment_id`、`process_definition_id`、`version`，新增 `wf_approval_record` 审批记录表，并补充工作流业务索引。
- 重构 `scaffold-workflow`：新增 `WorkflowStatus`、`WorkflowRequests`、`BpmnTemplateService`，模型部署改为 Flowable BPMN 部署，流程定义从部署后的 `processDefinitionId` 绑定。
- 强化工作流完整性和权限：BPMN XML 强制校验可执行 `process id` 与 `modelKey` 一致；审批接口校验当前用户必须是任务处理人、候选用户或候选组成员；撤回动作仅允许流程发起人执行。
- 兼容旧已部署模型：定义列表会自动为 `deployed=1` 但缺少 `process_definition_id` 的旧模型补部署默认 BPMN，并回填 Flowable 部署 ID、流程定义 ID 和版本号。
- 新增流程实例闭环接口：`POST /api/workflow/instances/start` 发起实例、`GET /api/workflow/instances/{processInstanceId}/trace` 查询实例轨迹；待办改为按当前用户和角色候选组读取 Flowable task，审批后完成/终止流程并写入 `wf_approval_record`。
- 流程定义和待办接口改为从 BPMN XML 解析真实节点名称；流程轨迹在完成态补充“归档”结束节点，避免轨迹停留在最后一个人工审批节点。
- 保留旧演示数据兼容：没有 Flowable 历史实例的历史 `wf_business` 待办仍可展示、审批和查看静态轨迹，避免初始化种子数据失效。
- 前端流程模型页新增 BPMN XML 编辑和默认模板生成；流程定义页新增发起流程弹窗并展示真实 BPMN 节点；待办卡片和审批抽屉进度条使用后端返回的真实 BPMN 节点；审批抽屉优先按 `processInstanceId` 加载真实实例轨迹；API 类型支持字符串任务 ID。
- 工作流前端列表改为后端 keyword 查询和分页：流程模型、流程定义、我的待办、我的已办均具备查询、重置、分页、导出；待办页新增 loading 状态，避免接口加载时误显示空任务。
- 优化工作流 UI：隐藏非发起人的“撤回”动作，提交按钮增加提交中禁用，移除侧边栏固定待办角标，修复长业务编号/流程 key/审批意见可能撑破卡片或抽屉的问题。
- 待办接口和前端类型补充 `businessKey` 业务编号，待办页本地搜索同步支持业务编号，避免后端搜索命中后被前端二次过滤隐藏。
- Vite 开发代理新增 `VITE_API_PROXY_TARGET` 环境变量覆盖能力，默认仍指向 `http://localhost:8080`，便于本地端口冲突时验证当前后端实例。
- 更新 `docs/backend-architecture.md` 和 `docs/frontend-backend-integration.md`，补充 Flowable 架构状态、接口和字段中文说明。
- 完成验证：`mvn -pl scaffold-admin -am test` 通过 42 个后端测试；`npm run test:unit` 通过 18 个前端单元测试；`npm run build` 通过；浏览器在 `http://127.0.0.1:5177` 完成流程模型 BPMN 模板、流程定义发起弹窗、待办实例轨迹和审批提交冒烟，当前页面无 console error/warn，UI 烟测数据已清理。

## 2026-06-06 (性能与健壮性修复)

- 修复前端 Vite 代理端口：`vite.config.ts` 代理目标从 `localhost:18080` 改为 `localhost:8080`，与后端实际监听端口一致。
- 修复前端 `http.ts` import 顺序：将 `authSession` 的 import 语句移到模块顶部，修正不规范的 import 位置。
- 修复后端监控日志删除/清空权限标识错误：操作日志和登录日志的 DELETE 接口从错误的 `list` 权限改为正确的 `remove` 权限，防止只有查看权限的用户删除日志。
- 移除安全配置中 `/api/test/**` 的 `permitAll` 放行规则，生产环境不再暴露测试端点；将测试安全配置移入 `TestSecurityConfig` 仅在测试时生效。
- 增强全局异常处理器：新增 `HttpMessageNotReadableException`（JSON 格式错误返回 400）、`MethodArgumentTypeMismatchException`（参数类型错误返回 400）、`IllegalArgumentException`、`HttpRequestMethodNotSupportedException`（返回 405）、`NoResourceFoundException`（返回 404）和通用 `Exception`（返回 500 并记录日志）处理，防止堆栈信息泄露。
- 修复 JWT 解析异常静默丢弃问题：添加 debug 级别日志记录失败的 token 解析。
- 修复 DashboardService 登录趋势统计的潜在 NPE：对数据库返回的 `hourValue` 和 `total` 增加 null 检查。
- 为前端流程模型部署/删除、流程定义挂起/激活、待办审批、代码生成配置保存等异步操作补充 try-catch 错误处理，防止 API 异常导致页面无反馈。
- 继续清理前端残留演示交互：工作台、流程模型、流程定义、我的已办导出改为统一 CSV 下载；工作台待办“审批”跳转到真实待办页，不再伪造审批成功。
- 新增剪贴板工具 `copyText` 并接入代码生成预览弹窗，“复制当前”改为调用浏览器 Clipboard API，失败时给出明确错误提示。
- 参数配置和字典管理的“刷新缓存”类假提示改为真实重新加载列表；登录页“忘记密码”空链接改为“联系管理员重置密码”静态提示。
- 修复待办页流程进度 Vue prop 告警：新增 `normalizeFlowStep` 将后端返回的当前节点名称映射为进度索引，`FlowSteps` 支持数字和节点名称两类输入。
- 修复部门管理删除后的前端状态残留：新增 `removeDeptFromTree` 工具并接入部门删除流程，删除成功后立即从本地部门树剔除软删节点并切回父级/根部门，避免页面继续显示已删除部门。
- 修复监控日志前端关键词过滤健壮性：新增 `includesKeyword` 工具，登录日志过滤兼容空用户名/IP，操作日志过滤兼容空字段并纳入请求参数，避免 Vue update 阶段因 `null.includes` 报错。
- 修复移动端后台外壳样式：小屏侧栏强制图标化，隐藏顶栏搜索和用户姓名，收敛工作台/待办卡片、筛选框和流程进度条的固定宽度，消除移动视口内横向溢出。
- 优化字典管理行操作可见性：字典类型编辑/删除按钮在选中行、悬停和键盘聚焦时保持可操作，避免行操作完全依赖 `display:none` 的 hover 状态。
- 修复操作日志 CSV 导出契约：导出列新增“请求参数”，确保按 `request_params` 命中的日志导出后仍能看到筛选命中字段，并新增回归测试覆盖。
- 完成验证：`npm run test:unit` 通过 16 个前端单元测试，`npm run build` 通过，`mvn -pl scaffold-admin -am test` 通过 35 个后端测试；浏览器抽样验证工作台导出/待办跳转、流程模型/流程定义/我的已办导出、代码生成预览复制和待办页流程进度无当前页面 warn/error。
- 完成浏览器路由冒烟：在 `http://localhost:5175` 逐页打开工作台、系统管理、系统监控、工作流程和代码生成共 15 个主要路由，核心标题均加载成功，当前页面无 warn/error；移动 390px 视口复验工作台和我的待办，`.app-content` 无水平滚动。
- 完成真实浏览器写操作联调：在 `http://localhost:5175` 通过页面完成参数配置新增/编辑/删除、岗位管理新增/编辑/删除、流程模型新建/配置/部署/删除；浏览器控制台无来自当前前端的 warn/error，数据库核查 `sys_config`、`wf_model_meta`、`wf_business` 无测试残留，`sys_post` 无 `deleted = 0` 的有效测试残留。
- 扩展真实浏览器全功能联调：继续覆盖用户新增/编辑/重置密码/删除、角色新增/编辑/权限保存/删除、菜单新增/编辑/显示切换/删除、部门新增/编辑/删除、字典类型和字典数据新增/编辑/删除、操作日志和登录日志筛选/详情/导出/批量删除、代码生成导入表/配置保存/预览复制/下载、流程定义流程图/挂起/激活/导出、待办审批和已办导出；所有测试数据清理后无有效 `ui_smoke` 残留。
- 完成真实 API 写操作烟测：从配置解析 `scaffold_system` 数据库连接并查表结构后，通过 8080 实例完成认证、部门、岗位、角色、用户、菜单、字典、参数、流程模型、流程定义、临时待办审批、操作日志和登录日志共 12 类写操作验证，10 项测试数据清理成功，数据库无活跃 `smoke_` 残留。

## 2026-06-06

- 补齐监控日志写能力：新增操作日志/登录日志按 ID 删除和清空接口，前端批量删除、清空改为真实后端调用，并新增 `MonitorLogWriteApiTest`。
- 补齐监控日志导出：新增 `GET /api/monitor/oper-logs/export` 和 `GET /api/monitor/login-logs/export` CSV 下载接口，前端操作日志/登录日志导出按钮改为真实文件下载，并扩展 `MonitorLogWriteApiTest` 与前端 API 映射测试。
- 补齐用户管理操作接口：新增 `GET /api/system/users/export`、`PUT /api/system/users/{id}/status` 和 `PUT /api/system/users/{id}/password`，前端用户导出、批量启停、重置密码改为真实后端调用，并扩展 `SystemWriteApiTest` 与前端 API 映射测试。
- 修复角色删除校验：角色关联的用户已被软删除时，不再阻塞角色删除，避免角色管理中已无有效用户仍提示“角色已分配用户”。
- 新增统一数据权限能力：增加 `@DataScope` 注解、`DataScopeAspect` 切面、`DataScopeCriteria` 上下文和 `DataScopeResolver` 解析接口，系统模块按当前用户角色合并 `ALL`、`DEPT`、`DEPT_AND_CHILD`、`SELF`、`CUSTOM` 数据范围，并接入用户列表和用户 CSV 导出。
- 新增统一操作日志能力：增加 `@OperLog` 注解、`OperationLogAspect` 切面、`OperationLogRecorder` 记录接口和 JDBC 写入实现，覆盖系统管理、账号改密、工作流和代码生成写接口；操作日志请求参数自动脱敏 `password`、`token`、`secret`、`authorization` 等敏感字段，并支持按请求地址和请求参数检索。
- 补齐代码生成数据库表导入：新增 `GET /api/tool/generator/importable-tables` 和 `POST /api/tool/generator/import`，通过 `information_schema` 导入 `gen_table` 与 `gen_table_column`，前端导入弹窗改为真实接口，并新增 `GeneratorImportApiTest`。
- 补齐代码生成 zip 下载：新增 `GET /api/tool/generator/tables/{id}/download`，生成 Entity、Mapper、Service、Controller、前端 API、Vue 列表页和菜单 SQL 文件压缩包；前端“生成/下载全部”改为真实下载。
- 补齐流程模型写能力：新增模型创建、编辑、删除接口，前端流程模型页新增真实新建/配置/删除弹窗交互，并新增 `WorkflowModelWriteApiTest`。
- 新增当前用户修改密码接口 `PUT /api/auth/password`，校验 `oldPassword` 后加密写入 `sys_user.password`；顶部栏修改密码弹窗改为真实接口，成功后退出重新登录，并扩展 `AuthApiTest` 与前端 API 映射测试。
- 清理顶部栏和登录页残留演示交互：搜索改为真实路由跳转，应用按钮改为快捷入口，通知读取工作台真实待办，个人中心展示当前登录用户信息，登录页文案改为测试账号提示。
- 补齐前端通用 CSV 导出工具：新增 `toCsv`/`downloadCsv`，将角色、岗位、菜单、部门、字典数据、参数配置和代码生成列表的假导出提示改为真实 CSV 文件下载，并新增 CSV 转义单元测试。
- 将 `scaffold-vue/src/data/index.ts` 从历史 Mock 数据文件收敛为类型、侧边栏导航和头像色板定义，页面数据源均走 API。
- 更新 `docs/frontend-backend-integration.md`、`docs/backend-architecture.md` 和后端开发计划状态，补充新增接口字段中文说明。
- 完成验证：`npm run test:unit` 通过 9 个前端单元测试，`npm run build` 通过，`mvn -pl scaffold-admin -am test` 通过 34 个后端测试，`mvn -pl scaffold-admin -am package -DskipTests` 打包成功，并在 `http://127.0.0.1:18080` 完成登录、工作台、工作流、生成器导入候选、生成器 zip 下载、数据权限过滤、软删用户后角色删除、改密错误校验、用户停用登录拦截、用户重置密码登录、用户 CSV 导出、操作日志自动写入与敏感字段脱敏真实接口烟测。

## 2026-06-05

- 初始化 `scaffold-system` 后端 Maven 多模块工程，新增 `scaffold-common`、`scaffold-framework`、`scaffold-system`、`scaffold-generator`、`scaffold-workflow`、`scaffold-admin` 模块。
- 新增 `ScaffoldAdminApplication`、`GET /api/health` 健康检查接口、统一响应模型 `R`、分页模型、业务异常、全局异常处理和 traceId 过滤器。
- 为后端基础能力新增 JUnit 测试，覆盖统一响应、分页参数、分页返回、健康检查、traceId 和业务异常响应，并完成 Maven 测试、打包和本地 jar 启动验证。
- 新增 早期后端开发计划（历史资料），基于现有文档和 `scaffold-vue` 前端 Demo 反推 `scaffold-system` 后端工程结构、数据库、接口、模块开发顺序和验收计划。
- 新增 `scaffold_system` MySQL 数据库初始化脚本 `V1__init_schema.sql` 和 `V2__seed_system_data.sql`，创建系统用户、角色、菜单、部门、岗位、字典、参数、日志、代码生成和工作流元数据等基础表，并写入 BCrypt 密码种子数据。
- 为 `scaffold-admin` dev 环境补充 MySQL 与 Redis 配置，完成表结构、审计字段、种子数据行数和用户数据的数据库校验。
- 实现后端认证接口：`POST /api/auth/login`、`GET /api/auth/me`、`GET /api/auth/routes`、`POST /api/auth/logout`，支持 JWT、Redis token 会话、登录/登出日志和动态菜单路由。
- 新增 `TokenStore` 并让 JWT 过滤器校验 Redis 会话状态，保证登出后的旧 token 无法继续访问受保护接口。
- 扩展认证集成测试，覆盖登录成功、登录失败、未带 token、动态路由、登出和登出后 token 失效；完成 Maven 测试、打包和真实 HTTP 验证。
- 新增系统管理只读接口：用户分页、角色分页、菜单树、部门树、岗位分页、字典类型分页、字典数据、参数配置分页，以及操作日志/登录日志分页查询。
- 新增 `SystemManagementMapper`、`SystemManagementService`、`SystemManagementController` 和 `MonitorController`，通过 SQL 聚合返回前端需要的 `deptName`、`postName`、`roleName`、`userCount` 等展示字段。
- 开启 MyBatis `call-setters-on-nulls`，保证 Map 响应中 `lastLogin` 等空值字段仍保留，稳定前端接口字段契约。
- 新增 `SystemReadApiTest` 集成测试，覆盖系统管理只读接口的分页、树结构、字典、配置和日志响应，并完成 Maven 测试、打包和真实 HTTP 抽样验证。
- 将 dev 环境 `com.scaffold` 日志级别从 `debug` 调整为 `info`，减少接口运行时 SQL 调试日志刷屏。
- 优化 `admin-redesign` 事项详情交互，将全屏模糊遮罩小弹窗改为非阻断右侧详情抽屉，保留主页面可见性并补充事项摘要、处理上下文和流转记录。
- 补全 `admin-redesign` 静态页面所有可见按钮的前端交互：全局搜索、环境切换、用户菜单、顶部导航、数据刷新、台账筛选、事项详情、通过/驳回、侧栏查看全部、审计中心与审计详情。
- 为 `admin-redesign` 新增命令搜索弹层、顶部下拉菜单、详情/列表弹窗和 Toast 反馈，保证按钮点击均有可见结果。
- 新建 `admin-redesign` 独立前端原型目录，从零创建 React + Vite 静态后台管理页面，不复用现有 `scaffold-vue` 代码。
- 按用户确认的高级发散方向实现 `Editorial Control Desk` 风格后台首页，包含顶部编辑式导航、指标条、审批与权限台账、权限风险、流程积压、生成器状态和实时审计日志。
- 为 `admin-redesign` 增加基础交互：导航选中、台账筛选、行选中联动审计事项、刷新时间更新。
- 新增 `admin-redesign/design-qa.md`，记录构建、浏览器可访问性树、视觉方向和交互验证结果。
- 按用户要求删除 `admin-redesign` 前端改造项目及其全部文件，停止对应 Vite 本地服务；保留原始静态 HTML 目录和当前项目其他文件。
- 新增 `admin-redesign` 前端项目，将 本地原型目录 的静态 HTML/CSS/JS 原型转换为 Vue 3 + Vite + TypeScript 架构。
- 将静态原型样式迁入 `admin-redesign/src/styles/prototype`，保留原型视觉语言并通过 Vue 页面复用。
- 新增 `admin-redesign/src/api`、`components`、`directives`、`layouts`、`mock`、`router`、`stores`、`types`、`utils`、`views` 等规划目录。
- 将原型布局转换为 `AdminLayout.vue`，将登录、工作台、系统管理、监控、工作流和代码生成页面按业务域拆分到 `views`。
- 新增 `BaseButton`、`BaseTable`、`SearchForm`、`FormDialog`、`StatusTag`、`PermissionButton`、`DictSelect` 等统一组件。
- 新增 `docs/admin-redesign-architecture.md`，说明静态原型资源到 Vue 规划架构的转换关系和关键字段中文含义。
- 修正 `admin-redesign` 的复刻精度：将图标体系替换为原型自定义 SVG path，按原型结构重写登录页、工作台和用户管理页，并将用户 mock 数据扩展为接近原型的 56 条。
- 为满足一比一复刻要求，新增 `admin-redesign/public/exact-prototype`，完整复制 本地原型目录 静态原型资源，并将 `admin-redesign` 默认入口切换为该原型基准渲染。
- 将 `admin-redesign` 默认入口切回真实 Vue 渲染，按原 HTML 原型的 class、布局和内容结构重做登录页、后台布局、工作台、用户、角色、菜单、部门、字典、参数、日志、工作流和代码生成等页面。
- 新增 早期产品技术规划（历史资料），规划 Spring Boot + Vue 后台管理脚手架的产品定位、技术栈、架构、核心模块、数据库原则、接口原则和迭代路线。
- 新增 `docs/prd.md`，整理后台管理脚手架的用户角色、核心流程、功能需求、页面规划、非功能需求和 MVP 验收标准。
- 新增 `scaffold-vue` 前端 Mock Demo，包含 Dashboard、RBAC + 数据权限管理、Flowable 审批中心等核心页面。
- 新增 早期前端 Demo 实现计划（历史资料），记录前端 Demo 实现计划。
- 将 `scaffold-vue` 前端 Demo 的菜单、页面标题、按钮、表格、工作流和占位模块等用户可见文案统一调整为中文。
- 补齐 `scaffold-vue` 前端 Mock 页面：用户管理、菜单管理、部门管理、字典管理、代码生成、流程模型、流程定义、审计日志、系统设置。
- 为前端 Demo 增加本地点击交互：筛选、重置、新增、编辑、状态切换、角色分配、代码预览、流程部署、审批操作、日志导出和系统设置保存。
- 新增 `scaffold-vue/src/utils/exportCsv.ts` 通用 CSV 导出工具，并为所有表格页面补充导出入口。
- 在产品技术规划和 PRD 中补充通用表格能力，明确所有列表页默认支持导出当前筛选结果。
- 新增项目级 `AGENTS.md`，将统一组件复用、统一设计语言、前后端通用能力复用定义为必须遵守的开发准则。
- 在产品技术规划中补充强制开发准则，明确新增功能优先复用或扩展通用能力。
- 补齐 `admin-redesign` 岗位管理页面、导航、路由、权限标识和 mock 数据，并按静态原型修正岗位数据、表格选择列、排序图标、分页控件与标题栏布局，修正系统管理侧边栏菜单顺序不一致的问题。
- 按静态原型重做 `admin-redesign` 菜单管理页，将扁平表格改为菜单树结构，补齐展开/收起、树形缩进、目录/菜单/按钮标签、显示开关和图标操作列，并按原型菜单树数据恢复可见行顺序。
- 启动逐页逐组件视觉审计，批量生成 Vue 与静态原型截图和 DOM 摘要；按审计结果重做部门管理、操作日志与登录日志的可见结构、文案、表头、筛选区、数据和分页控件。
- 按静态原型区分流程模型与流程定义、我的待办与我的已办页面，补齐流程定义表头、数据、状态与操作按钮，以及我的已办表格、审批结果和分页结构。
- 修正我的已办页多余筛选卡，保持与静态原型一致的单表格卡片搜索结构。
- 机械导出静态原型 `data.jsx` 为 `prototypeData.ts`，让用户、角色、部门、菜单、岗位、字典、参数和代码生成列表复用原型同源数据，避免可见内容漂移。
- 将流程模型和待办/已办页面切换为 `prototypeData` 同源流程数据，修正模型、待办卡片和代码生成等页面的可见数据差异。
- 为参数配置页补齐与原型一致的分页条和每页条数选择器。
- 为 `scaffold-system` 新增后端权限注解能力：`@RequiresPermission`、权限解析器、AOP 拦截器和 403 统一异常响应，实现后端接口级权限校验。
- 补齐系统管理写接口：用户、角色、菜单、部门、岗位、字典类型、字典数据和参数配置的新增、修改、删除接口，复用统一响应、统一分页、JWT 会话和权限注解。
- 为系统管理写接口新增集成测试 `SystemWriteApiTest` 和 `SystemCatalogWriteApiTest`，覆盖低权限用户拒绝访问、用户/岗位 CRUD、角色/部门/菜单/字典/参数配置 CRUD。
- 完成 `scaffold-system` 后端全量验证：`mvn -pl scaffold-admin -am test` 通过 18 个测试，`mvn -pl scaffold-admin -am package` 打包成功，并在 18080 端口完成真实 HTTP 登录、权限拒绝、角色 CRUD 和参数配置 CRUD 冒烟。
- 为 `scaffold-vue` 新增真实后端 API 客户端、认证 API、会话存储工具和系统管理 API 封装，新增 `npm run test:api` Node 原生测试，覆盖统一响应、Bearer token、业务错误、认证接口和系统管理端点映射。
- 将 `scaffold-vue` 登录页从本地 Mock 登录改为真实后端登录、当前用户拉取和退出登录，Vite dev server 新增 `/api` 到 `localhost:18080` 的代理。
- 将 `scaffold-vue` 用户、角色、菜单、部门、岗位、字典、参数配置页面接入真实后端读写接口，并用 Playwright 真实验证用户、角色、菜单、部门、岗位、字典、参数配置 CRUD。
- 将 `scaffold-vue` 操作日志和登录日志页面接入真实后端只读分页接口，并完成浏览器访问验证。
- 新增 `docs/frontend-backend-integration.md`，记录已接入页面、接口字段中文说明和仍使用 Mock 的后续模块。
- 新增 Dashboard 后端真实接口 `GET /api/dashboard/summary`，返回统计卡、24 小时趋势、待办和最近动态。
- 将工作流代码落入 `scaffold-workflow` 模块，新增流程模型、流程定义、待办、已办、审批和审批记录接口，并补充集成测试。
- 将代码生成器代码落入 `scaffold-generator` 模块，新增生成表、字段、预览、配置保存和生成确认接口，并补充集成测试。
- 扩展 `V2__seed_system_data.sql`，补充 `wf_model_meta`、`wf_business`、`gen_table`、`gen_table_column` 示例数据，保证新库初始化后工作流和代码生成页面有真实数据。
- 新增 `scaffold-vue/src/api/portal.ts` 和 API 映射测试，将工作台、工作流、代码生成器页面从前端 Mock 切换为真实接口。
- 新增 `docs/backend-architecture.md`，说明后端 Maven 模块、包类类型、业务边界和主要字段中文含义。
- 完成验证：`npm run test:api` 通过 8 个前端 API 测试，`npm run build` 通过，`mvn -pl scaffold-admin -am test` 通过 21 个后端测试，并完成真实后端与 Vite `/api` 代理接口联调。
