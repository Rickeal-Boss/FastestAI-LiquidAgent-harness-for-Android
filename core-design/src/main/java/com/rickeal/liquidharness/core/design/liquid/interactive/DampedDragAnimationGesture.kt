package com.rickeal.liquidharness.core.design.liquid.interactive

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * [DampedDragAnimation] 的手势宿主（从 `DampedDragAnimation.kt` 的 `val modifier`
 * 初始化表达式原样外提，逐字节等价）。
 *
 * `with(this@gestureModifier)` 恢复「类内成员可直接裸名访问」的作用域，故循环体与旧代码
 * 逐字一致；`this@gestureModifier` 标签仍指向被拖实例。⚠️ 为跨文件访问，类里若干
 * `private` 成员放宽为 `internal`（非公开 API 变化）。
 */
internal fun DampedDragAnimation.gestureModifier(): Modifier = Modifier.pointerInput(this) {
    with(this@gestureModifier) {
        awaitEachGesture {
            // ⚠️ 每轮手势**最开头**就清掉让位标志（在 awaitFirstDown 之前）。
            // awaitEachGesture 的语义是一次手势 = down 到 up，up 之后才进下一轮；
            // 放在这里能保证"上一次让位过"绝不会残留到下一次手势。
            //
            // 反过来，**不要在 finally 里清**：onDragStopped 是在 finally 里执行的，
            // 它必须读到 true 才能跳过提交（否则让位后仍会双重提交，状态翻两次）。
            // 也就是说这个标志的生命周期是"本次手势"，由下一轮的开头负责收尾。
            yieldedToParentState = false
            finishedNormallyState = false
            val down = awaitFirstDown(requireUnconsumed = false)
            var previous = down.position
            // 累积位移：consume 与否按**累积量**判定，不按单帧 delta。
            // 真机点击必然带亚像素抖动，逐帧判定会把外层的 clickable / toggleable
            // 一并取消掉 —— 表现就是"点了没反应"。
            var accumulatedX = 0f
            var accumulatedY = 0f
            // 轴向**只判定一次**（latch）。不能用 running 净位移逐帧重判：
            // 来回拖动会让净位移归零、方向来回翻转，手势会在中途莫名其妙让位。
            var axisDecided = false
            // ⚠️ slop 必须在**循环外**取一次快照。
            //
            // 为什么这行是安全的：`awaitFirstDown` / `awaitEachGesture` 的 lambda 与
            // `pointerInput` 的协程在同一个 continuation 里，进入 `awaitEachGesture`
            // 那一刻仍在 composition 内，所以这里读 CompositionLocal 合法。
            //
            // 但**之后就不行了**：`awaitFirstDown` 返回后，当前 continuation 已经离开
            // composition；此后每次 `awaitPointerEvent()` 返回都会跑到下一个
            // `awaitPointerEvent()`，这中间不在 composition 内 —— 那时再读
            // `viewConfiguration` 会抛 IllegalStateException。
            // 所以循环内一律用这里的 `slop`，不许再出现 viewConfiguration。
            val slop = viewConfiguration.touchSlop
            yieldedToParentState = false
            // ⚠️ 起手门禁：必须在 `yieldedToParentState / finishedNormallyState` 复位**之后**、
            // `setPressed(true)` **之前**求值 ——
            //  · 放在复位之后：被挡下的手势不残留任何本次标志（下一轮 awaitEachGesture 干净）；
            //  · 放在 setPressed 之前：门禁不过 ⇒ 按压态从不置位 ⇒ 胶囊不会在"按非当前页签"
            //    时鼓包（pressProgress 驱动的 56→78dp 弹簧），事件也不消费 ⇒ 页签点击照常。
            // 控制流直接交回 awaitEachGesture（它会在 block 返回后 awaitAllPointersUp，
            // 等全部手指抬起才进下一轮，不会反复触发）。
            //
            // ⚠️ 用 `?.let` + `invoke`：`let` 是 inline ⇒ `return@awaitEachGesture` 非局部
            // 返回合法；`invoke` 显式传接收者，避免"带接收者 lambda"在可空属性上的
            // 智能转换 / receiver 调用歧义。
            canStartDrag?.let { gate ->
                if (!gate.invoke(this@gestureModifier, down.position)) {
                    // 门禁不过：分流看 onTap。
                    //  - onTap == null（既有调用点）：维持原语义，直接交回 awaitEachGesture。
                    //  - onTap != null（静态宿主独占命中的补救）：进**观察循环**，跟踪这根
                    //    手指到抬手，"净位移未过 slop 的完整点按"回调 onTap。
                    // ⚠️ 观察循环**只读事件、不写任何状态**：不 setPressed、不置
                    // isDraggingState、不碰 yieldedToParentState / finishedNormallyState
                    // —— isDragging 被调用方的回显门禁读取，观察分支碰它会引发回写互搏。
                    // ⚠️ 全程不 consume、不改事件走向；slop 用循环外的既有快照
                    //（上面那行 `val slop = viewConfiguration.touchSlop`），
                    // 观察循环内绝不读 viewConfiguration。
                    if (onTap != null) {
                        var tapPrevious = down.position
                        var tapAccumX = 0f
                        var tapAccumY = 0f
                        var tapCandidate = true
                        while (true) {
                            val tapEvent = awaitPointerEvent()
                            val tapChange = tapEvent.changes.firstOrNull { it.id == down.id }
                                ?: break // 事件流断（change 被移除）⇒ 无 tap，静默放弃
                            if (!tapChange.pressed) {
                                // 抬手：全程净位移未越过 slop 才算一次 tap。
                                if (tapCandidate) {
                                    onTap.invoke(this@gestureModifier, down.position)
                                }
                                break
                            }
                            val tapCurrent = tapChange.position
                            val tapDelta = tapCurrent - tapPrevious
                            tapPrevious = tapCurrent
                            if (tapDelta != Offset.Zero) {
                                // 与主循环同款 reach 公式（有符号净位移 → 逐轴 abs → 欧氏距离）。
                                tapAccumX += tapDelta.x
                                tapAccumY += tapDelta.y
                                if (Offset(abs(tapAccumX), abs(tapAccumY)).getDistance() >= slop) {
                                    // 判定为拖动意图：取消 tap 候选，但**继续观察到 up、
                                    // 不 break、不消费** —— 观察分支不改变任何事件走向。
                                    tapCandidate = false
                                }
                            }
                        }
                    }
                    return@awaitEachGesture
                }
            }
            setPressed(true)
            isDraggingState.value = true
            onDragStarted(this@gestureModifier)
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        // ⚠️ 走到这里有两种截然不同的情况，必须区分：
                        //   1. 手指**按下过又抬起** → 一次完整手势正常结束
                        //   2. 这根 change 从未真正按下（事件流断了 / change 被移除）
                        //      → 不是手势结束，不该走业务提交
                        //
                        // ⚠️ 直接置 true，**不要用 down.previousPressed**。
                        //
                        // 早期这里写成 `down.previousPressed`，并注释"恒为 true"——
                        // **两个都是错的**。源码（foundation 1.10.3 PointerEvent.kt）：
                        //   changedToDownIgnoreConsumed() = !previousPressed && pressed
                        // 而 awaitFirstDown 的循环条件正是 isChangedToDown，
                        // 所以返回的 down **必定满足 previousPressed == false**
                        // → finishedNormally 恒为 false → 业务提交被永久关闭
                        // （滑块改完不落盘、开关拨不动，只能点）。
                        //
                        // 能走到这个分支，说明我们确实看到了指针释放，即一次完整手势。
                        // 真正需要拦的是上面 `? : break` 那条路：change 被移除
                        // （事件流断了）时根本走不到这里，标志保持初始的 false。
                        finishedNormallyState = true
                        break
                    }
                    val current = change.position
                    val dragAmount = current - previous
                    previous = current
                    if (dragAmount != Offset.Zero) {
                        // ⚠️ 累加**有符号**的 dx/dy（净位移）。
                        // 不要用 abs / getDistance()：它们恒非负，累加的是"路径长度"，
                        // 来回抖动会单调累加顶过 slop → 误判为拖动 → 误消费。
                        // Compose 自己累加的也是 dragAccumulator += dragAmount（有符号 Offset）。
                        //
                        // 分轴是为了跟父级滚动共存：纵向滑动不该被本手势吃掉，
                        // 否则父级 verticalScroll 的 awaitTouchSlopOrCancellation 会放弃
                        // → **手指按在滑块上时页面滚不动**（13 个滑块全在可滚动容器里）。
                        accumulatedX += dragAmount.x
                        accumulatedY += dragAmount.y

                        // 轴向锁定：判定为纵向意图 → 让位给父级滚动。
                        //
                        // ⚠️ 阈值必须跟 Compose 对齐（同 InteractiveHighlight）：
                        //   |dy| > |dx| * tan30°(0.577)
                        // 写成 |dy| > |dx|（45°）会在 30°~45° 斜滑留残余死区。
                        //
                        // ⚠️ 必须 **break**，不能只"跳过本次 consume/onDrag"（源码依据）：
                        // verticalScroll / LazyColumn 的 startDragImmediately = false，
                        // 父级只在 **Main pass** 消费，而 Main 自下而上 —— 子级永远
                        // 先拿到未消费事件。只跳一次的话，父级开滚后子级仍会在后续帧
                        // 继续走 onDrag，表现为"一边滚页面一边改数值"。
                        // break 后 awaitEachGesture 等抬手才重启，一次解决。
                        // 代价：用户要多滑几 px 才起滚。
                        // ⚠️ 方向判定**必须先过幅度门限**，不能在第一帧就判。
                        // 起手那一两帧 accumulatedX/Y 还是亚像素噪声：
                        // dx=0.3px、dy=0.6px → 0.6 > 0.3*0.577 → 立刻 break，
                        // 整个手势作废 → 滑块/开关"有时拖不动"（随机，极难查）。
                        // Compose 也是在越过 slop 那一刻才算 gestureAngle，不是第一帧。
                        val reach = Offset(abs(accumulatedX), abs(accumulatedY)).getDistance()
                        // 轴向**只判定一次**（axisDecided latch）。
                        // 不能用 running 净位移逐帧重判：有符号累加下，来回拖两帧净位移
                        // 就归零，方向会来回翻转 —— 手势中途莫名其妙让位。
                        if (!axisDecided && reach >= slop) {
                            axisDecided = true
                            if (canYieldToParent &&
                                isVerticalDragIntent(accumulatedX, accumulatedY)
                            ) {
                                // 让位给父级滚动。置位后本手势"吞掉"后续事件。
                                yieldedToParentState = true
                                // ⚠️ 父级一接管就要取消按压，**不能等到 finally**（那要等抬手）。
                                // 否则手指按在滑块/开关上纵向滑走之后，pressProgress 会一直
                                // 保持 1：thumb 维持 1.5 倍拉伸、玻璃维持"变实"
                                // （模糊减弱 + 折射增强）贯穿整个滚动过程，直到抬手才归位。
                                // 平台标准行为是"父级接管即取消"，InteractiveHighlight
                                // （走 break 路径）也是这个行为 —— 两边必须一致，
                                // 否则同一个手势语义在两个类里表现相反。
                                setPressed(false)
                                // 让位即拖拽事实来源终止：回显门禁要放行（isDragging 复位），
                                // 否则外层状态在剩余手势里永远无法回写到控件上。
                                isDraggingState.value = false
                            }
                        }

                        // 让位后留在循环里"吞掉"后续事件：既不消费也不 onDrag，但继续等 up。
                        //
                        // 📌 机制澄清（早期这里写过一段**错误**的说明，已更正）：
                        //   曾认为「break 之后下一轮 awaitFirstDown 会立刻拿到同一根手指，
                        //   反复走 onDragStopped → 落盘 N 次」。这不成立 ——
                        //   awaitEachGesture 在 block() 返回后会调用 awaitAllPointersUp()，
                        //   正是用来防止"手势还没结束就重开一轮"的，
                        //   所以 break 之后协程会等到所有手指抬起才进下一轮。
                        //
                        //   那为什么还保留吞事件、不退回 break？因为吞事件有一个
                        //   break 做不到的好处：让 yieldedToParent 标志**活到 up 帧**。
                        //   GlassSlider 的 detectTapGestures 守卫
                        //   （`if (yieldedToParent) return@detectTapGestures`）在 up 帧读它；
                        //   若退回 break，finally 会在 up 之前就把标志清掉，跳值又会发生。
                        //
                        //   代价是按压态要提前归位 —— 由上面那次显式 setPressed(false) 负责。
                        if (!yieldedToParentState) {
                            // 横向：越过 [consumeSlopPx] 才消费。
                            // 严格大于：consumeSlopPx = 0f 时 `0 > 0` 为假，纯抖动不消费。
                            if (abs(accumulatedX) > consumeSlopPx) {
                                change.consume()
                            }
                            // ⚠️ onDrag **必须每帧无条件回调**，不能塞进上面的门控里。
                            // 累加是有符号净位移，来回拖两帧它就归零；若 onDrag 受
                            // `abs(accumulatedX) > consumeSlopPx` 门控，来回拖到中途
                            // onDrag 会彻底不再触发 → 滑块值卡住、开关拖到一半松手失效。
                            // 改动前它就是每个 move 无条件回调的，这里保持该语义。
                            onDrag(this@gestureModifier, valueAnimatable.value, dragAmount)

                            // ── 速度采样（2026-09-26 回弹恢复，上游 updateValue 同款）────
                            // 对 value（页签/进度量纲）按时间采样，目标速度 = tracker 速度
                            // ÷ valueRange 跨度（归一化成"每秒走完多少比例的量程"），
                            // 交给欠阻尼速度弹簧跟随 —— 拖动中各向异性拉伸有真实来源，
                            // 松手后速度带过冲衰减（弹性拖尾）。上游逐帧 animateTo 的
                            // 模式照搬；snapValue 让 valueAnimatable.velocity 恒 ≈0，
                            // 那条路已经没有速度可读（Wave 10 申报的取舍由此真正解决）。
                            velocityTracker.addPosition(
                                SystemClock.elapsedRealtime(),
                                Offset(valueAnimatable.value, 0f),
                            )
                            val span = valueRange.endInclusive - valueRange.start
                            val trackedVelocity = velocityTracker.calculateVelocity().x /
                                if (span > 0f) span else 1f
                            animationScope.launch {
                                velocityAnimatable.animateTo(
                                    trackedVelocity,
                                    velocityAnimationSpec,
                                )
                            }
                        }
                    }
                }
            } finally {
                // ⚠️ 与 InteractiveHighlight 同理，这两句必须在 finally 里。
                // 手势被父级抢走 / 协程被取消时，循环体之后的代码不会执行，
                // 结果有两个：pressProgress 永远卡在 1（那一行一直显示按下态）；
                // 更糟的是 onDragStopped 不触发 —— 滑块的 onValueChangeFinished
                // 永远不来，设置页改完温度**不落盘**。
                setPressed(false)
                // 速度弹簧终点归零（防御性，覆盖全部手势终点）：正常松手后消费方会在
                // onDragStopped 里 animateToValue 收敛 value，但速度弹簧的目标若不显式
                // 归零，会停在最后一次采样值上 —— layerBlock 的各向异性拉伸将永久卡住。
                // 欠阻尼弹簧从当前速度带过冲衰减到 0 = 松手的弹性拖尾（上游经
                // animateToValue 里的 velocity→0 达成同款观感，这里收口更严）。
                // 让位 / 事件流断路径同样走到这里（finally 语义），不会残留拉伸。
                animationScope.launch {
                    velocityAnimatable.animateTo(0f, velocityAnimationSpec)
                }
                // ⚠️ 顺序不能换：onDragStopped 必须**先**执行、且能读到 true，
                // 它才能据此跳过提交（让位场景）。清标志必须放在它之后。
                onDragStopped(this@gestureModifier)
                // 让位标志的生命周期 = 本次手势。这里清掉，避免跨手势残留。
                // （awaitEachGesture 开头那次复位是双保险，两者不冲突。）
                yieldedToParentState = false
                isDraggingState.value = false
            }
        }
    }
}
