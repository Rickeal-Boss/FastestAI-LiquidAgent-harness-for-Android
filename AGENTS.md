# AGENTS.md —— 本仓 agent 作业契约

> 给「未来接手本仓的 agent」看，不是人类入门文档（人看 `README.md`）。
> 本仓从 LiquidAgent（端侧优先）分叉而来，但**定位已变**：这是**纯 API 的 Android Agent Harness**，
> 端侧推理核心全部移除。凡旧仓文档里与「模型跑在设备上」绑定的判据，在本仓一律不适用。
>
> 纪律：本文件只写「能指到仓内既有机制」的规则。写不出出处的规则不写 —— 无出处的规则
> 会变成「看起来像规矩、实际无人执行」的装饰。
>
> `Verified as of 2026-10-29`（W1 开荒波）。**日期戳过期即视为可疑**：先复核再引用。

---

## 一、这个仓是什么

- **包名 / applicationId**：`com.rickeal.liquidharness`（debug 后缀 `.debug`）
- **模型在哪跑**：**云端**。本仓不含任何本地推理代码、不含模型权重、不下载模型。
- **核心资产**：Agent 内核（工具循环 / 审批闸门 / 上下文压缩 / 运行账本）、Skill 系统、MCP Client、
  多供应商远程引擎。UI 是这些能力的展示面，不是主体。
- **与旧仓的关系**：旧仓 `LiquidAgent`（`com.rickeal.agent`，Apache-2.0，同作者）是**来源**，不是依赖。
  可以搬代码（同许可），但**搬之前必须问「这条判据在 API 路径下还触发吗」** —— 不触发的就删，
  不要留成装饰。

---

## 二、三条方法论底色（都从真实事故里长出来，别当口号）

1. **证据优先**：任何论断要能指到 `文件:行` / commit / 测试输出 / 真机日志。推断可以写，必须标【推断】。
2. **证伪优先于证实**：外审报告、交接文档、上一位 agent 的结论，**都可能不成立**。
   采纳任何处方前，先 `grep` 现役树核实它描述的状态还在不在（旧仓 W87 一次证伪 4 条外审，
   全是「报告查的是它那一版的树」）。
3. **「未触发」≠「已验证通过」**：没跑到的判据写「未走 / vacuous」，不能写成通过。
   「测试全绿」也不等于「口径被验证」—— 5 个纯函数单测对「谁来行使判据」是 vacuous 的。

---

## 三、构建与验证

```bash
# 唯一本地入口（注入 JDK/SDK/GRADLE_USER_HOME，不污染系统）
./scripts/localbuild.sh                 # 默认 :app:assembleDebug
./scripts/localbuild.sh test            # 全量 JVM 单测
./scripts/localbuild.sh :app:lintDebug  # lint
./scripts/fulltest.sh                   # 单测 + 解析 TEST-*.xml 汇总
```

- **裸 `./gradlew` 会失败**（JAVA_HOME 缺失）。必须走 `scripts/localbuild.sh` 或显式注入环境。
- 本地环境：JDK 21（`_j2env/jdk/`）、Android SDK（`_j2env/sdk`）、Gradle 缓存（`_j2env/gradle-home`）。
- **沙箱出网走 MITM 代理**：JVM 需 `-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=<port>`，
  否则 PKIX 证书错误。`localbuild.sh` 已处理。
- 真机：`_j2env/sdk/platform-tools/adb.exe`（PATH 里没有 adb）。设备 OPPO PDRM00 / Android 13（SDK 33）。
  `adb install` 必须 `MSYS_NO_PATHCONV=1` 且源路径用 `D:/...`；读设备二进制用 `adb exec-out`。

### 🔴 安装前必须唤醒设备（2026-10-29 实测）

**灭屏状态下 `pm install-commit` 会永久挂起**（无输出、无退出码），`adb install` 直接返回
-1。根因不是 adb 参数，而是 ColorOS 在灭屏后把**安装服务一起冻住** —— 与已知的
「灭屏 ~10s cgroup 冻结」同源。

正确姿势：

```bash
A=_j2env/sdk/platform-tools/adb.exe
$A shell input keyevent KEYCODE_WAKEUP
MSYS_NO_PATHCONV=1 $A exec-out screencap -p > /tmp/s.png   # 确认不是全黑
# 再走 install-create / install-write / install-commit 三步
```

判据：截图非全黑 **且** 能读到桌面或应用界面。只做 `KEYCODE_WAKEUP` 不够 ——
AOD 状态下屏幕仍可能是黑的。

### 安装三步法（比 `adb install` 可诊断）

```bash
S=$(MSYS_NO_PATHCONV=1 $A shell pm install-create -r -t -S <bytes> | grep -o '\[[0-9]*\]' | tr -d '[]')
MSYS_NO_PATHCONV=1 $A shell pm install-write -S <bytes> $S base /data/local/tmp/x.apk
MSYS_NO_PATHCONV=1 $A shell pm install-commit $S     # 成功输出 "Success"
```

分步做的好处：挂起时能立刻定位是「写」还是「提交」卡住，而 `adb install` 只给一个 -1。

---

## 四、模块边界（编译能过 ≠ 架构没坏）

| 模块 | 职责 | 禁止 |
|---|---|---|
| `:core-model` | 纯数据契约 | 依赖 Android API、依赖任何其他本仓模块 |
| `:core-net` | HTTP/SSE、供应商适配 | 依赖 `core-agent` |
| `:core-agent` | 内核：循环/工具/审批/压缩 | 依赖 `core-data`、依赖 `feature-*`、依赖 UI |
| `:core-skills` | SKILL.md 解析与目录 | 反向被 `core-agent` 依赖实现（只经接口） |
| `:core-mcp` | MCP Client | 同上 |
| `:core-data` | 持久化（端点/密钥/会话） | 依赖 `feature-*` |
| `:core-design` | 设计系统 | 依赖业务模块 |
| `:feature-*` | 界面 + ViewModel | 互相依赖 |
| `:app` | 壳、DI 装配 | 承载业务逻辑 |

- **DI 只走 `AppContainer`**（手写），不引入 Hilt/Dagger/Koin。
- **禁止依赖**：KSP / Room / Retrofit / Coil / 任何注解处理器 / 任何 JitPack 库。
  （OkHttp 允许；JSON 用 kotlinx.serialization。）
- 模块级 `repositories {}` 一律禁止（`FAIL_ON_PROJECT_REPOS`）。

---

## 五、内核纪律

1. **单文件行数预算**：`core-agent` 下单文件 **≤600 行**（旧仓 `AgentRunner` 长到 2838 行、
   守卫上限 2900、余量 62 —— 接一个新能力就踩线，这是新仓从第一天就要避免的死结）。
   超了就拆，不要调上限。
2. **新增能力前先问落点**：循环 / 工具执行 / 提示词组装 / 事件发射 / 压缩，各有归属文件。
   往 `AgentRunner` 里加分支是最贵的写法。
3. **事件序列改动必须先枚举路径矩阵**：`ToolResultReceived` / `ToolSkipped` 在 UI 侧
   **只更新不新建**（提前 return 必须自己保证轨迹创建点）；`ApprovalRequested` **无去重**。
4. **`runCatching` 不能兜协程内 IO**：吞 `CancellationException` 会让 Job 从 cancelled 变 completed。
   挂起点必须 `catch (c: CancellationException) { throw c }` 前置。纯阻塞 IO 不适用。
5. **取消语义**：SSE 是冷流，但 `stop()` 是显式调用，必须主动 `call.cancel()` ——
   否则 `readLine()` 会阻塞到 readTimeout，用户点停止毫无反应。
6. **「有内容但无 finish_reason」= 被截断**，不是正常结束。必须补终帧并留证据，
   否则半截回答会被当完整回答落库。

---

## 六、安全与密钥

- **API Key 绝不落明文**：Keystore 派生密钥 + AES-GCM 加密后再落盘；绝不进 APK、绝不进日志。
- **五处泄漏面都要兜**：用户输入 / 模型输出 / 工具参数 / HTTP 头 / 截图上屏。
  上屏风险 > 落盘风险（会进截图与 bug report）。
- **日志出口先脱敏再判 400**：`?k=x` → `?k=***`，只替换值保留参数名。
- 仓库**绝不含** `*.p12` / `*.jks` / `keystore.properties` / `*.env`。
- CI 的四个签名 secret 是 `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`；
  PKCS12 下 `KEY_PASSWORD` 必须等于 `KEYSTORE_PASSWORD`。

---

## 七、测试与门禁

- **JUnit4**：测试方法返回 `Unit`（Kotlin 里写 `fun x()`），**不要**写表达式体返回非 Unit。
- 裸 `assertFailsWith {}` 收尾会让整个类 initializationError、全部用例不跑 —— 必须放在 `@Test` 内且断言其后无裸调用。
- **空跑不算通过**：`tests=0` 必须判红。`fulltest` 报「test-results 清不掉」是 Windows 句柄锁，
  拒绝判绿是正确行为。
- 纯逻辑（解析器 / 评分 / 压缩阈值 / SSE 帧）必须放可 JVM 单测的位置，不要埋进 Android 类。
- 静态守卫判据必须钉**行首代码形态**（`^[[:space:]]*fn\(`），裸子串匹配会被自家注释判成违规。
- **告警的量级本身就是判据**：全仓逐条告警 = 没有告警。已知例外显式登记带理由，只让**新**异常判红。

---

## 八、CI

- 双工作流：`build.yml`（零密钥，push main + PR 通配 + dispatch）、`release.yml`（tag `v*` + dispatch，带签名）。
- **新建/切换分支第一件事 = 核对 `build.yml` 的 `on.push.branches`**。不在列表 ⇒ 推送 **0 run**，
  而 0 run 看起来和「构建通过」一模一样（旧仓有两个波次因此从未触发 CI）。
- 纯文档提交被 `paths-ignore` 挡住 = 既定口径，不是绕过门禁。
- 发布链只在 tag 上硬要求签名；分支/dispatch 缺 secret 时降级 debug 包并显式 `::notice::`。

---

## 九、提交

- 身份：`-c user.name="Rickeal-Boss" -c user.email="263587600+Rickeal-Boss@users.noreply.github.com"`。
- **PAT 只以 URL inline 一次性使用，绝不落盘**；push 后 `git remote set-url` 回干净 URL。
- push 真判据 = `git push --dry-run` 成功，不是 `git remote -v` 看起来对。
- 中文长 commit message 用 `git commit -F <file>`，不要塞进 `-m`。
