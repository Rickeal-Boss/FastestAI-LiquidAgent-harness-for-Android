# W4 收口闸门清单（Gate Checklist）

> **性质**：W4 收口时**照着逐条跑**的执行清单。每条 = 一条命令 + 期望输出 + 判红后怎么办。
> **登记基线**：`HEAD a2a5fac`（`ci(w3): EXPECTED_TESTS 365 → 480`），远端 `origin/main` 同 SHA。
> **上游依据**：`docs/02-roadmap.md` §5.1/§5.2/§5.3、`.github/workflows/build.yml` 文件头约束、
> `scripts/localbuild.sh` / `scripts/fulltest.sh` / `scripts/arch-guard.sh` / `scripts/arch-guard-selftest.sh` 各文件头。
>
> **为什么要有这份东西**：闸门链的每一条都曾经以「看起来绿」的形态骗过人 ——
> `test` UP-TO-DATE 时汇总陈旧 XML（假全量）、`gradle` 的「N tests completed」在 fail-fast 下是残缺口径、
> 守卫因目录改名而**永远绿**、推送因 `0 run` 而看起来和「构建通过」一模一样。
> 这份清单的价值不在于「跑一遍」，而在于**每条都写明判据行**：没有判据行的绿不算绿。
>
> **本波闸门的边界**：只推 `main`、不打 tag。故 `release.yml` **不在本波闸门内**（它只认 `tags: v*`）。

---

## §0 基线事实（2026-10-31 实测，收口时先复核这几行是否仍成立）

| 项 | 值 | 判据来源 |
|---|---|---|
| `@Test` 真实数（严格口径） | **480** | `grep -rnE '^\s*@Test\b'`（排除 `build/.gradle/.kotlin/.git`） |
| `EXPECTED_TESTS`（build.yml） | **480** | 与上一行**双向相等** |
| 测试 XML 汇总 | `tests=480 failures=0 errors=0 skipped=0` | 59 个 `TEST-*.xml` / 11 模块全在 |
| `arch-guard.sh` | `OK=23 VIOLATION=0 SELF_FAULT=0`，exit 0 | 仓库根跑，约 150s（Windows） |
| `arch-guard-selftest.sh` | `24/24 PASS`（约 94s），exit 0 | 变异自证生效 ⇒ 守卫**还会红** |
| CI 实证（run 41215118702 @ `a2a5fac`） | **5/5 job success** | Assemble / Unit tests / Arch guard / Secret scan / Lint |
| 门禁有效性实证（run 41215067175 @ `78abace`） | 唯一失败 = `Reconcile EXPECTED_TESTS with real @Test count` | 「测试加到 480、常量仍写 365」被拦下，改常量即转绿 |

**模块分布**（严格口径，480 条）：

```
core-agent 186 | core-model 43 | core-engine 42 | feature-chat 41 | core-net 39
core-mcp 36 | core-skills 32 | core-design 31 | core-data 21 | app 8 | feature-settings 1
```

> ⚠️ **已知薄弱面**：`feature-settings` 仅 1 条（SmokeTest）、`app` 仅 8 条。
> W4 往这两处塞文件浏览页 / 工具页时**几乎没有回归网** —— 这不是闸门能兜住的，只能靠补测试。

**三态口径说明**：`arch-guard.sh` 的结果是 **OK / VIOLATION / SELF_FAULT** 三态，**没有 SKIP 态**。
「守卫被跳过」在本仓结构上不存在 —— 目录改名 / 正则写坏 / 目标文件缺失一律落 `SELF_FAULT` 判红，
而不是静默跳过。所以清单里看到 `OK=23` 就同时意味着「23 条都真的看了树」。

---

## §1 闸门链（顺序固定：localbuild → fulltest → selftest → arch-guard）

顺序不是偏好：`localbuild` 先失败能省掉后面 5 分钟；`fulltest` 依赖编译产物；
两个守卫脚本是纯 bash、与前两者无依赖，放最后是为了让**最快能失败的先失败**。

### ① 环境注入（跨会话必做，代理端口会漂移）

```bash
eval "$(bash scripts/localbuild.sh --env | grep '^export ')"
echo "$JAVA_HOME"; echo "$ANDROID_HOME"; echo "$GRADLE_USER_HOME"
```

**期望**：三行都指向工作区内 `_j2env/jdk/*`、`_j2env/sdk/`、`_j2env/gradle-home/` 的真实路径。

> 沙箱代理端口**跨会话漂移**（实测过 9079 → 1799 → 13516 → 10512）。
> `localbuild.sh --env` 已在运行时从 `$https_proxy` 解析端口，**不要**手工写死，
> 也不要把端口抄进任何脚本或配置。

### ② 本地编译闸门

```bash
bash scripts/localbuild.sh
```

**期望**：`BUILD SUCCESSFUL`，且 `app/build/outputs/apk/debug/app-debug.apk` 存在。
**判红**：按 `grep -A20 '^\* What went wrong'` 取 Gradle 真实失败点（栈顶多是插件初始化噪音）；
先确认是不是**既有基线**失败（对比上一个绿 run 的同一任务），再判断是否本波引入。

### ③ 全量单测闸门

```bash
bash scripts/fulltest.sh
```

**期望**：`tests=480 failures=0 errors=0`，且汇总覆盖 **11/11 模块**。
（本波若有增删测试，这里的新总数必须与 ④ 的结果、与 `EXPECTED_TESTS` 三者一致。）

**三种假象的识别与处置**：

| 现象 | 真实含义 | 处置 |
|---|---|---|
| 「N 个 test-results 目录未能清除」 | Windows 句柄锁，rm 失败 ⇒ 汇总口径不可信 | **拒绝判绿**（脚本判红是正确行为）。用 python 清 + 等 10s 再重跑 |
| 全绿但很快结束、模块数不足 11 | task UP-TO-DATE，汇总的是**上一轮陈旧 XML** | 看最早 `TEST-*.xml` 的 mtime 是否晚于本轮启动时刻 |
| `BUILD FAILED` 但用例全绿 | 可能是**既有基线**失败而非本波引入 | 先 `grep -A20 'What went wrong'`，再对比上一绿 run |

> `fulltest.sh` **不认** `GRADLE_JVM_ARGS`，只认 `GRADLE_OPTS`（gradlew 启动器读它）。
> 本地不需要手工设；CI 里由 build.yml 显式透传。

### ④ `@Test` ↔ `EXPECTED_TESTS` 双向对账（**唯一的计数依据**）

```bash
grep -rnE '^\s*@Test\b' --include=*.kt \
  --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.kotlin --exclude-dir=.git . | wc -l
```

**期望**：输出 == `.github/workflows/build.yml` 里的 `EXPECTED_TESTS`。

**何时改 `EXPECTED_TESTS`、依据是什么**：

- **任何**新增 / 删除 `@Test` 的提交，**必须在同一次提交里**改这个常量（CI 判据是双向相等，不是 `>=`）。
- 改的依据 = **上面这条命令的输出**，不是「我觉得加了几个」。
- **只许改这一个常量**，不许把判据放宽成区间 / 百分比 / `>=`。
  （旧仓守卫 #26 就是写成宽松的 `>=` 而形同虚设。）
- CI 判红时先看 job 里打印的「@Test 分布（按文件）」，两个方向的话术不同：
  - `actual > expected` ⇒ 加了测试没同步常量；
  - `actual < expected` ⇒ 测试被删 / 被注释 / 被漏扫。

**口径细节**（三者必须一致，否则「本地绿 / CI 红」变玄学）：

- 用 `^\s*@Test\b` 而非裸 `@Test` —— KDoc 里引用 `@Test` 会多算；
- 排除 `build/.gradle/.kotlin/.git` —— KSP/AGP 生成目录里的 `.kt` 会把数算大；
  该步骤在 CI 上跑于 gradle 之前（这些目录还不存在），但**本地重跑时存在**，
  所以口径必须在两种状态下都成立。

### ⑤ 架构守卫（必须**后台**跑，且必须在**仓库根**）

```bash
bash scripts/arch-guard.sh
```

**期望**：`OK=23  VIOLATION=0  SELF_FAULT=0`，`[arch-guard] result=PASS`，exit 0。

- **必须后台跑**：前台 120s 会被 SIGTERM 截断，得到的是**空输出**（空输出不等于通过）。
- **必须在仓库根**：在工作区根跑会扫到仓外目录，出现「全红」假象。脚本自身会 `cd` 到仓库根，
  但如果你手工复制粘贴单条 `grep`，务必确认 cwd。
- 耗时参考：Windows/Git Bash 约 150s；CI（ubuntu）更快。
- **判红后**：`VIOLATION` 是真违规（改树）；`SELF_FAULT` 是守卫自身故障（目标被改名 / 正则写坏 /
  `grep` rc≥2），**不要**为了让输出变绿去改判据。

### ⑥ 守卫自证（变异注入）

```bash
bash scripts/arch-guard-selftest.sh
```

**期望**：`selftest: 24/24 PASS`，exit 0（约 94s，同样**后台跑**）。

> ⑤ 绿只证明「守卫没报错」，⑥ 绿才证明「守卫还会报错」。
> 只有 ⑤ ⇒ 守卫可以永远绿（旧仓「目录改名 ⇒ 空守卫永远绿」）；只有 ⑥ ⇒ 没人判树。
> 两个都绿才成立。⑥ 用 `--batch` 默认模式（CI 同款）；`--per-case` 留给夜间深度验证。

### ⑦ 推送前置检查（三条全过才允许 push）

```bash
git status --short
git diff --cached --name-only | grep -E '\.log$|\.env$|\.jks$|\.keystore$|\.p12$|local\.properties$'
git log --oneline origin/main..HEAD
```

**期望**：
1. `git status --short` **无输出**（工作树干净，没有忘记 `git add` 的改动）；
2. 第二条**无输出**（无 `_*.log` 调试残留、无密钥/凭据文件、无 `local.properties`）；
3. 第三条**只含本波提交**，且每条都能对上本波的一个意图（没有夹带）。

> `.gitignore` 已覆盖 `*.log` / `*.p12` / `*.jks` / `*.keystore` / `keystore.properties` /
> `local.properties` / `*.env`。被 ignore 不等于安全 —— 密钥扫描（gitleaks + trufflehog）
> 扫的是**全历史**，`git commit --no-verify` 绕不过 CI。

### ⑧ 推送（**由主理人执行**，本清单只记姿势与判据）

```bash
# 先 fetch 再数领先 —— stale 的 origin/main 会骗人（W3 收口时实测骗到了「领先 2」）
git -c http.sslVerify=false fetch origin main
git rev-list --count origin/main..HEAD

# 真判据：dry-run。不用 git ls-remote（匿名读会假绿）
git -c http.sslVerify=false -c credential.helper= push <内联PAT的URL> main --dry-run
```

**期望**：`rev-list --count` 等于本波提交数；dry-run 输出 `main -> main`。
（回 `Everything up-to-date` 表示远端已有该 commit —— 不是失败，但也说明没东西可推。）

**本机已知坑**（都实测过，别再花时间试）：

- 裸 `git push` 会撞 `schannel: CRYPT_E_NO_REVOCATION_CHECK`（沙箱 MITM CA 吊销检查）；
  `-c http.schannelCheckRevoke=false` **无效**。
- `GIT_SSL_NO_VERIFY=true` 能过 TLS 但会假报 `Invalid username or token` —— **别用这条路径**。
- 正确姿势是 `-c http.sslVerify=false` **加上** `-c credential.helper=` 清掉 GCM 缓存，
  用内联 PAT 的 URL 推送。
- ⚠️ 内联 PAT 只出现在命令行，**绝不**写进 `.git/config`、脚本、日志或本文档。
  本机可用 `git remote -v` 事后核对 remote URL 未被改写成带 token 的形态。

### ⑨ 推送后判读（`gh` 未安装，用 GitHub API）

```bash
# 1) 取最新 run 的 id 与结论
curl -sk "https://api.github.com/repos/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs?branch=main&per_page=1"

# 2) 取该 run 的 job 级结论
curl -sk "https://api.github.com/repos/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/<id>/jobs"
```

**期望**：`Build` 工作流的 **5 个 job 全 `success`**：
`Assemble Debug (JDK 21)` / `Unit tests` / `Arch guard (23 guards + mutation selftest)` /
`Secret scan (gitleaks + trufflehog)` / `Lint (baseline gate)`。

**判读规则**：

- 任一 job 非 `success` 时，打印该 job 里 `steps[].conclusion` 非 `success` 的**步骤名**即可定位
  （W3 的 `78abace` 就是这样一眼定位到 `Reconcile EXPECTED_TESTS with real @Test count`）。
- **绿判据行**（不是「job 是绿的」）：
  - `Unit tests` 日志出现 `@Test 对账通过：<N> 个`（N 与本波 ④ 的结果一致）；
  - `Arch guard` 日志出现 `OK=23  VIOLATION=0  SELF_FAULT=0` 与 `selftest: 24/24 PASS`；
  - `Assemble Debug` 出现 `BUILD SUCCESSFUL`。
- **`Lint` 的 job 颜色不可信**：必须读 `lint-reports-` artifact 的真实 issue 集
  （lint 步骤历史上带过 `continue-on-error`，「CI 双绿」不等于 lint 通过）。
  baseline 只许缩不许涨 —— regen 把新问题冻进 baseline = 僵尸豁免。

---

## §2 本波不在闸门内的事（写在这里，避免收口时被误当成漏项）

| 项 | 为什么不跑 | 什么时候才需要 |
|---|---|---|
| `release.yml` | 只认 `push.tags: v*` 与 `workflow_dispatch`；本波不推 tag ⇒ **0 run 是预期** | 打 tag 发版时。改它之前先本地 `actionlint`（YAML 语法 GitHub 侧会校验，但**步骤语义**不会） |
| `paths-ignore` 覆盖的纯文档提交 | `docs/**` 与 `**/*.md` 被忽略 ⇒ 纯文档推送 0 run | 无需处理；但**别把「必须验证的清单」只放在 `.md` 里**，它不会触发任何闸门 |
| 真机验收（OPPO PDRM00 / A13） | 与 CI 无关 | 见 W4-D。注意本机 adb 默认 5037 被占，要用 `-P 15037` |
| 新增第 12 个模块 | 本波不新增 | 若新增，必须同时改 4 处：`settings.gradle.kts` include / `arch-guard.sh` 的 `MODULES` 数组（G9 三向相等）/ G6 边界表 / `EXPECTED_TESTS`。漏一处直接红 |

---

## §3 一页速查

```
① eval "$(bash scripts/localbuild.sh --env | grep '^export ')"   → 三路径在 _j2env 下
② bash scripts/localbuild.sh                                     → BUILD SUCCESSFUL + apk 存在
③ bash scripts/fulltest.sh                                       → tests=N failures=0 errors=0，11/11 模块
④ grep -rnE '^\s*@Test\b' ... | wc -l                            → == build.yml EXPECTED_TESTS
⑤ bash scripts/arch-guard.sh          （后台 ~150s）             → OK=23 VIOLATION=0 SELF_FAULT=0
⑥ bash scripts/arch-guard-selftest.sh （后台 ~94s）              → 24/24 PASS
⑦ git status --short 空 / 无 .log·密钥文件 / 提交只含本波
⑧ git -c http.sslVerify=false fetch origin main && rev-list --count → 先 fetch 再数
   git -c http.sslVerify=false -c credential.helper= push <PAT-URL> main --dry-run → main -> main
⑨ curl -sk .../actions/runs?branch=main  → 5 job 全 success + 三条绿判据行（lint 看 artifact）
```

**任一条没有「判据行」就不要往下走。** 本清单存在的唯一目的，是让「绿」这件事有证据，
而不是让流程看起来跑完了。
