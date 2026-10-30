package com.rickeal.liquidharness.core.design.liquid.interactive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/*
   Copyright 2025 Kyant

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

/**
 * 带阻尼的拖拽动画：**数值**（滑块进度 / 开关 0~1）+ **按压形变**（缩放）。
 *
 * Kyant0 的 Slider / Toggle / BottomTabs 全部建立在它上面。它同时暴露：
 *  - [value] / [targetValue] / [progress]：给布局用（thumb 位置、轨道填充宽度）
 *  - [pressProgress]：给 `effects {}` 用（按压时模糊减弱、折射增强）
 *  - [scaleX] / [scaleY] / [velocity]：给 `layerBlock` 用（各向异性拉伸 —— 快速拖动时
 *    沿拖动方向被拉长、垂直方向被压扁，这就是"液态"最直观的信号）
 *  - [modifier]：手势入口，挂到需要被拖的节点上
 *
 * **为什么不用 `detectDragGestures`**：它在"按下后没有越过 touch slop 就抬手"时
 * 不保证回调 `onDragStart` / `onDragEnd`，而开关的**纯点击切换**正是走 `onDragStopped`。
 * 这里手写 `awaitEachGesture` 循环，保证按下→抬手一定会走完 onDragStarted/onDragStopped。
 *
 * 对齐 Kyant0 `catalog/utils/DampedDragAnimation`（Apache-2.0，原实现用 `context()`
 * 上下文接收者，本项目按「不开 -Xcontext-receivers」改为普通 lambda 参数）。
 */
@Stable
/**
 * 判定"纵向意图"的阈值：tan(30°) = 0.577。
 *
 * 与 foundation 1.10.3 `DragGestureNode.processAwaitTouchSlop` 的分轴规则对齐
 * （Horizontal: `angle <= 30`；Vertical: `30 < angle <= 90`，角度由
 * `atan2(x = |dx|, y = |dy|)` 得出，从 X 轴量角）。
 *
 * ⚠️ 不要"简化"成 `|dy| > |dx|`（= 45°），否则 30°~45° 斜滑留残余死区。
 */
private const val VERTICAL_INTENT_TAN30 = 0.577f

/**
 * 「本次手势是纵向意图」的判据：**累积位移** [accumulatedY] 是否压过
 * [accumulatedX] × tan30°（[VERTICAL_INTENT_TAN30]）。
 *
 * 从 [DampedDragAnimation.modifier] 的轴向锁定分支**原样外提**（逐字节等价，
 * 判据常量与比较运算符都未改），目的是让这条判据能被 JVM 单测直接覆盖 ——
 * 它是「手指按在滑块/开关上纵向滑时页面能不能滚」的唯一开关，写错的症状是
 * 真机上极难复现的「有时拖不动 / 滚动被吃掉」。
 *
 * ⚠️ 阈值不能"简化"成 `|dy| > |dx|`（45°）：30°~45° 那一段斜滑会留残余死区
 * （判成横向 → 本手势消费事件 → 父级 verticalScroll 起不来）。本函数的单测
 * 用 35° 斜滑钉住这个边界。
 */
internal fun isVerticalDragIntent(accumulatedX: Float, accumulatedY: Float): Boolean =
    abs(accumulatedY) > abs(accumulatedX) * VERTICAL_INTENT_TAN30

/**
 * 归一化进度 0~1（相对 [valueRange]），从 [DampedDragAnimation.progress] 的
 * getter **原样外提**（逐字节等价）：轨道填充宽度与 thumb 位移都按它排版，
 * 所以「量程退化（span ≤ 0）」与「越界钳制」两条分支都不能写反。
 *
 * - `span <= 0f` ⇒ 恒 0f：退化量程下除零会得到 NaN，NaN 传进布局会让整个
 *   节点消失（Compose 对 NaN 尺寸直接抛/不绘制），宁可退化成 0；
 * - 其余 ⇒ 线性映射后 `coerceIn(0f, 1f)`：拖动期 [value] 可能被外部 snap 到
 *   量程之外，不钳制会让 thumb 画到轨道外。
 */
internal fun dampedDragProgress(value: Float, valueRange: ClosedFloatingPointRange<Float>): Float {
    val span = valueRange.endInclusive - valueRange.start
    return if (span <= 0f) {
        0f
    } else {
        ((value - valueRange.start) / span).coerceIn(0f, 1f)
    }
}

class DampedDragAnimation(
    internal val animationScope: CoroutineScope,
    initialValue: Float,
    internal val valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    private val initialScale: Float,
    private val pressedScale: Float,
    internal val onDragStarted: DampedDragAnimation.() -> Unit,
    internal val onDragStopped: DampedDragAnimation.() -> Unit,
    internal val onDrag: DampedDragAnimation.(value: Float, dragAmount: Offset) -> Unit,
    /**
     * 只有**累积主轴位移**超过它才 `consume()` 事件。
     *
     * ⚠️ 累积的是 `abs(dragAmount.x)`（主轴），**不是**欧氏距离 —— 纵向滑动不会累积，
     * 因此纵向永远不消费，父级 `verticalScroll` 才能正常接管滚动。
     *
     * **当前全仓 4 个调用点，全部显式传正值 8dp**（主轴均为 x，暂无纵向控件）：
     *  - `GlassSegmented`（分段指示器拖动）
     *  - `GlassSlider`（滑块拖动）
     *  - `LiquidBottomTabs`（底栏页签拖动）
     *  - `GlassSwitch`（开关，其局部变量名为 `touchSlopPx`）
     *
     * **为什么现在都必须传正值**：`GlassSegmented` / `GlassSlider` / `LiquidBottomTabs`
     * 三处的手势宿主在 Wave 10 Phase 2d/2e 都从"被平移的元素节点"迁到了**静态**覆盖层
     * （为分离坐标反馈），命中路径随之从"元素自身 bounds"扩成"整条宿主" ⇒ 若仍用默认
     * `0f`（"横向一动就消费"），真机点击 / 点轨道跳转必然带亚像素抖动，1px 就被 consume
     * 取消 ⇒ **"点了没反应"**。`GlassSwitch` 同理（它外层挂了 `toggleable`）。过了 slop
     * 才消费，才能把"点击"与"拖动"这两条路径干净地分开。
     *
     * 默认 `0f` 仅为历史默认值，**当前无任何调用点使用**（保留它是为了不动既有构造签名）。
     * **若将来新增纵向控件（如竖向 slider），这个累加必须参数化**，否则纵向拖动会失效。
     */
    internal val consumeSlopPx: Float = 0f,
    /**
     * 是否允许"纵向意图 → 让位给父级滚动"（2026-09-24 新增开关）。
     *
     * 让位锁（tan30° 分轴 + 一次判定终生让位）是为**可滚动容器里的横向控件**
     * （设置页的滑块/开关）设计的；判定为纵向后本手势永久静默。对**没有纵向
     * 滚动父级**的控件（底栏 LiquidBottomTabs——它在 MainShell 的 Column 里，
     * 上下都没有 verticalScroll），让位锁是纯害：用户拖页签时手指的自然弧线
     * 会让 net-vertical 越过阈值 → 拖拽**中途冻结**（"拉越远越偏移"的直接来源
     * 之一）。这类调用点必须传 false。
     */
    internal val canYieldToParent: Boolean = true,
    /**
     * 可选起手门禁：**带接收者**的 lambda，入参是**手势宿主节点的局部坐标**按下点，
     * 返回 true 才允许本次手势起手。
     *
     * ⚠️ 为什么带接收者（`DampedDragAnimation.(Offset) -> Boolean`，Wave 10 Phase 2d）：
     * 调用点需要按**被拖元素的实时位置**判定抓取区（页签 = 胶囊实时 `value`、滑块 =
     * thumb 实时 `progress`）。带接收者后门禁里可直接读 `value` / `progress` —— 既
     * **不需要**在 `remember{}` 之后用 holder 回填实例（那是"组合期写 MutableState"的
     * 反模式），也**没有**"首帧实例未就绪"分支（恒可用）。若用无接收者的
     * `(Offset) -> Boolean`，调用方只能退而求其次读"内部选中态"（动画窗口内会偏半格）。
     *
     * ⚠️ 用途：把「手势宿主」从被平移的胶囊 / thumb 节点迁到**静态**覆盖层后，手势区会从
     * "元素自身 bounds"扩成"整条宿主"。为了让行为**零变化**，调用方传入逐轴复刻原抓取区
     * 的门禁（**宿主矩形 ∩ 元素实时矩形**）。
     *
     * 返回 false 时：**不** `setPressed`、**不**置 [isDragging]、**不**走
     * [onDragStarted] / [onDragStopped]，且**不消费**事件。后续分流看 [onTap]：
     *  - [onTap] == null（GlassSlider / GlassSwitch 等既有调用点）：维持原语义，直接
     *    `return@awaitEachGesture`，本次手势到此为止（外层 `clickable` /
     *    `toggleable` 照常生效）；
     *  - [onTap] != null（静态宿主独占命中的调用点）：进**观察循环**，跟踪这根手指
     *    到抬手，按 [onTap] 的语义决定是否回调点按。
     * 门禁在 `awaitFirstDown` 之后、`setPressed(true)` 之前求值，因此被挡下的按下
     * 对控件**完全无副作用**。
     *
     * 默认 `null` = 不设门禁（开关等既有行为完全不变）。
     */
    internal val canStartDrag: (DampedDragAnimation.(Offset) -> Boolean)? = null,
    /**
     * 可选点按观察通道（2026-09-26 新增）：**门禁不过**时的补救路径。
     *
     * ⚠️ 背景（为什么需要它）：compose-ui 1.10.3 `InnerNodeCoordinator.hitTestChild`
     * 的兄弟命中语义 —— 兄弟节点按 z 序**逆序**命中，顶部兄弟命中后若不与兄弟共享
     * 指针输入（默认），**下层兄弟全部不再参与命中**。当手势宿主是 `matchParentSize`
     * 覆盖整条的**静态**顶层节点（Wave 10 Phase 2d/2e 的坐标反馈修复）时，宿主下层
     * 的 `clickable`（底栏页签 / 分段项）收不到任何指针事件 —— 真机表现为
     * **"点击死亡"**（点击无响应，只能拖动换页）。这类调用点传 [onTap]，由本类的
     * 手势循环代为观察点按。
     *
     * 语义（**只在门禁不过时**走；门禁通过路径完全不经过这里）：
     *  - 观察手势至抬手：净位移（与主循环同款 reach 公式
     *    `Offset(abs(accumulatedX), abs(accumulatedY)).getDistance()`，有符号净位移
     *    逐轴取 abs 再求欧氏距离 —— 不是路径长度，来回抖动不会顶过阈值）未越过
     *    touchSlop（循环外快照 `slop`，观察循环内**绝不**读 `viewConfiguration`）
     *    且事件流未断（tracked change 一直在）⇒ 以**宿主局部坐标**的按下点
     *    `down.position` 回调；
     *  - 净位移越过 slop ⇒ 判定为拖动意图、tap 取消，但仍**继续观察到 up、不
     *    break、不消费** —— 观察分支只回答"是不是 tap"这一个问题，不改变任何
     *    事件走向；
     *  - **全程零副作用**：不消费任何事件、不 `setPressed`、不置 [isDragging]、
     *    不碰 [yieldedToParent] / [finishedNormally] 任何状态字段 —— [isDragging]
     *    被调用方的回显门禁（`LaunchedEffect(selectedIndex)`）读取，观察分支碰它
     *    会引发回写互搏。
     *
     * 入参与 [canStartDrag] 同一坐标系（宿主局部坐标），调用方按同一套几何常量
     * 换算目标项序号（LiquidBottomTabs 需 TabPad 边界守卫 —— Kotlin `toInt()` 向零
     * 截断，`(-0.5).toInt() == 0`，否则左 pad 带会被静默映射到第 0 格）。
     *
     * 默认 `null` = 既有调用点（GlassSlider / GlassSwitch）零变化。
     */
    internal val onTap: (DampedDragAnimation.(Offset) -> Unit)? = null,
) {

    internal val valueAnimatable = Animatable(initialValue, visibilityThreshold)
    private val pressAnimatable = Animatable(0f)

    // ── 回弹恢复（2026-09-26，对齐上游 Kyant0 catalog/utils/DampedDragAnimation）────
    // 上游的 scale 是**独立的欠阻尼弹簧**（scaleX dampingRatio=0.6 / scaleY=0.7、
    // stiffness=250），不是 pressProgress 的纯函数 —— 按下微过冲、松手**回弹数拍**
    //（iOS Liquid Glass 的标志性 squash & stretch）。本仓移植时把 scale 写成了
    // `initialScale + (pressedScale - initialScale) * pressProgress`，pressProgress
    // 又是 NoBouncy 临界阻尼 ⇒ 回弹整条丢失（用户以上游 demo 录屏对比实锤）。
    // 规格逐字取自上游（0.6/0.7/250），initialThreshold 上游用 0.001f。
    private val scaleXAnimatable = Animatable(initialScale, 0.001f)
    private val scaleYAnimatable = Animatable(initialScale, 0.001f)

    /**
     * scale 弹簧规格（上游逐字）：欠阻尼 = 松手回弹的来源。
     * X 轴 0.6 / Y 轴 0.7：松手时横向先弹过头再收，纵向略收窄，两轴相位差
     * 产生"果冻"观感。**不要**改成 NoBouncy —— 那正是被丢掉的回弹。
     */
    private val scaleXAnimationSpec =
        spring(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f)
    private val scaleYAnimationSpec =
        spring(dampingRatio = 0.7f, stiffness = 250f, visibilityThreshold = 0.001f)

    // 上游同款：独立的速度动画（spring(0.5f, 300f)，欠阻尼），由 VelocityTracker
    // 对 **value**（页签序号量纲）逐帧采样驱动，而不是直接读 valueAnimatable.velocity
    // —— 拖动路径是 snapValue（瞬时到位，Animatable.velocity 恒 ≈0），直接读它
    // 等于拖动中没有任何拉伸（Wave 10 已申报的取舍）。逐帧 animateTo 一个
    // 欠阻尼弹簧：拖动中真实跟手拉伸 + 松手后速度带过冲衰减 = 上游的弹性拖尾。
    internal val velocityAnimatable = Animatable(0f, 5f)
    internal val velocityAnimationSpec =
        spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.01f)
    internal val velocityTracker = VelocityTracker()

    // 刻意不用 `by` 委托：MutableFloatState 的
    // getValue/setValue 是 androidx.compose.runtime 的扩展运算符，必须显式 import 才生效，
    // 漏了 import 会报 "Type 'MutableFloatState' has no method 'getValue(...)'"（CI 实测踩过）。
    // 此处直接持有 state 并手写 get/set，且一律走**原语访问器 `.floatValue`**：它是
    // MutableFloatState 的成员（非扩展、无需 import）；而桥接的 `.value` 会装箱
    //（lint 报 `AutoboxingStateValueProperty`）。既免了装箱，也不多一个隐式依赖。
    private val targetValueState = mutableFloatStateOf(initialValue)

    /** 当前目标值（手指/外部状态想去的地方）。拖拽增量基于它计算，避免累积漂移。 */
    var targetValue: Float
        get() = targetValueState.floatValue
        private set(value) { targetValueState.floatValue = value }

    /** 当前实际值（弹簧跟随 [targetValue]，所以会"慢半拍"——这就是阻尼）。 */
    val value: Float get() = valueAnimatable.value

    /**
     * 当前速度（value 量纲 / s，来自独立速度弹簧——见 [velocityAnimatable] 注释）。
     * 用于各向异性拉伸。
     */
    val velocity: Float get() = velocityAnimatable.value

    /** 按压进度 0~1。 */
    val pressProgress: Float get() = pressAnimatable.value

    /**
     * 本次手势是否已**让位给父级滚动**（判定为纵向意图后 break 退出）。
     *
     * ⚠️ 必须在让位分支里置真，调用方的 `onDragStopped` 要据此**跳过提交**：
     * 让位意味着我们**没有消费**事件，于是外层的 `toggleable` / `clickable` 不会
     * 因"事件被消费"而取消按压 —— 抬手时它仍会走一次 `onValueChange`。
     * 如果此时 `onDragStopped` 也提交（`draggedX≈0` 会落进"纯点击"分支），
     * 就会**提交两次 = 状态翻两次 = 看起来完全没反应**。
     * 这正是 P0「点了没反应」从另一条路复活，务必用这个标志挡住。
     *
     * 典型场景：设置页按在开关上纵向滑（想滚列表）→ 滚完抬手，开关不能突然翻转。
     */
    val yieldedToParent: Boolean get() = yieldedToParentState

    internal var yieldedToParentState = false

    /**
     * 本次手势是否是**完整走完**的：`true` = 手指按下过并抬起（正常结束）；
     * `false` = 事件流断了（change 被移除 / 这根手指从未真正按下），
     * 此时 [onDragStopped] 里不应再走业务提交。
     *
     * 判据是 `down.previousPressed`，不是 `change.isConsumed` —— 见赋值处的说明。
     */
    val finishedNormally: Boolean get() = finishedNormallyState

    internal var finishedNormallyState = false

    /**
     * 手势是否进行中（按下 → 抬起/让位/断流）。
     *
     * **可观测**（mutableStateOf）—— 调用方用它做**回显门禁**：拖动期间手势是
     * 数值的唯一事实来源，外部状态（导航回压 / StateFlow 回流）此刻写入只会
     * 与 `snapValue` 打架（2026-09-24 真机录屏实证的"胶囊两端乱飘"振荡）。
     */
    val isDragging: Boolean get() = isDraggingState.value

    internal val isDraggingState = mutableStateOf(false)

    /**
     * 归一化进度 0~1（相对 [valueRange]）。轨道填充宽度、thumb 位移都用它。
     *
     * 判定式已外提为 [dampedDragProgress]（纯函数，可被 JVM 单测覆盖），
     * 这里只负责把 [value] / [valueRange] 喂进去 —— 语义与外提前逐字节一致。
     */
    val progress: Float
        get() = dampedDragProgress(value, valueRange)

    /**
     * 按下时放大的 X 缩放（[initialScale] → [pressedScale]）。
     * 2026-09-26 起读**独立欠阻尼弹簧**（不再随 pressProgress 线性走）——
     * 按下微过冲、松手回弹，见 [scaleXAnimatable] 处注释。
     */
    val scaleX: Float get() = scaleXAnimatable.value

    /** 同 [scaleX]，Y 轴（阻尼比 0.7，回弹相位与 X 略异 = 果冻感）。 */
    val scaleY: Float get() = scaleYAnimatable.value

    /**
     * 手势入口。必须挂在**需要被拖的节点**上，且顺序上放在 `drawBackdrop(...)` **之后**
     * （Kyant0 原序）—— 这样它是玻璃节点的子节点，绘制会被玻璃形状裁剪。
     */
    val modifier: Modifier = gestureModifier()

    /**
     * 外部状态变化 → 同步目标值（弹簧跟随）。
     * 拖拽期间每帧都会被调用；`animateTo` 会自动取消上一条未完成的动画，不会打架。
     */
    fun updateValue(value: Float) {
        val coerced = value.coerceIn(valueRange)
        targetValue = coerced
        animationScope.launch {
            valueAnimatable.animateTo(
                targetValue = coerced,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
        }
    }

    /**
     * 直接把值 animate 过去（点轨道跳转 / 外部选中态变化时用）。
     *
     * [animationSpec] 默认 `spring()` —— 与历史行为一致（同一刚度 / 阻尼），既有调用点
     * 不受影响。页签点击切换显式传入 `LiquidMotion.TabSwitch`（2026-09-24 起与上游
     * 对齐的临界阻尼快弹簧，见其 KDoc 的真机证据）。
     *
     * ⚠️ 拖动松手收敛（`onDragStopped`）刻意**不传** spec，保持默认的快收敛手感。
     */
    fun animateToValue(value: Float, animationSpec: AnimationSpec<Float> = spring()) {
        val coerced = value.coerceIn(valueRange)
        targetValue = coerced
        animationScope.launch { valueAnimatable.animateTo(coerced, animationSpec) }
    }

    /**
     * **立即到位**（`snapTo`，不走弹簧）。拖动期间专用。
     *
     * 为什么拖动不能用弹簧：thumb 位置完全由 [valueAnimatable] 驱动，若拖动期间
     * 每帧都 `animateTo` 重启弹簧，弹簧永远追不上每帧更新的目标 → 永不收敛 →
     * thumb 恒定滞后于手指（"不跟手"）。拖动要求的是**瞬时相等**，不是缓动。
     *
     * `snapTo` 与 `animateTo` 共用同一个 mutation 锁：每次调用会取消在途动画，
     * 所以逐帧调用是安全且预期的（旧目标直接作废）。
     *
     * ⚠️ 派发方式必须是 [CoroutineStart.UNDISPATCHED]，**不是**默认的 `launch`：
     * `animationScope` 来自 `rememberCoroutineScope()`，其 context 是
     * `AndroidUiDispatcher.Main + MonotonicFrameClock`，默认 `launch` 是**派发**的
     * —— 协程体要等下一个 frame 的 dispatch 阶段才跑 ⇒ 拖动期渲染读到的 [value]
     * 恒滞后 ≥1 帧（真机上表现为"胶囊/滑块慢半拍"）。`UNDISPATCHED` 让协程体在
     * **首个挂起点之前同步执行**：`snapTo` 在没有在途动画竞争时会同步把 value 写完，
     * 当帧即生效、严格跟手。
     *
     * 📌 与真机录屏里那次"胶囊在 1.6↔3.2 页签间以 ≈5.9Hz 振荡"**不是一回事** ——
     * **修正（Wave 10 Phase 2d）**：早前曾断言那次振荡是"双写者互搏、已由 `bf2bed4`
     * 的回显门禁切断"，这是**错的**。2026-09-25 15:01 录屏（tip `53656a5`，已含
     * `bf2bed4`）里振荡仍在，真因是**坐标反馈**（手势宿主与被平移节点是同一个 ⇒ 一阶
     * 反馈，斜率 ≈0.489）叠加**本处的派发延迟**（`v_{t+1} = v_{t-1}` 二周期）。坐标反馈
     * 由 `LiquidBottomTabs` 的 R1（手势宿主迁静态节点）修复；本处只负责消掉派发延迟那半边。
     *
     * ⚠️ **影响面申报（Wave 10）**：`snapValue` 是全仓共享 API，改动 `launch` →
     * `UNDISPATCHED` 会同时影响**全部 3 个调用点**，且它们**都在拖动路径**上：
     *  - `GlassSegmented.kt:217`（分段控件拖动换项）
     * `GlassSlider.kt:286`（滑块拖动）
     * `LiquidBottomTabs.kt:366`（底栏页签拖动换页）
     * ⇒ 本次顺带改变了**已验收的滑块 / 分段跟手行为**。方向为**正向**（值同步生效、
     * 更跟手、无回归风险），但属"申报外的行为变更"，真机验收**必须回归滑块与分段**。
     * 📌 `animateToValue` / `updateValue` **未动**（保持默认 `launch`）：它们是非拖动
     * 路径（1 帧启动延迟不可感知），且被 `GlassSwitch` 的弹簧路径消费，改了会波及开关。
     *
     * ⚠️ 只做"值立即到位"这一件事 —— 不碰手势循环 / consume 门控 / 轴向锁定，
     * 那三块是真机验过的稳定性所在，别为手感顺手改它们。
     */
    fun snapValue(value: Float) {
        val coerced = value.coerceIn(valueRange)
        targetValue = coerced
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            valueAnimatable.snapTo(coerced)
        }
    }

    /**
     * **弹簧跟随**（2026-09-26 果冻回弹恢复，对齐上游 `updateValue`）：拖动期专用。
     *
     * ⚠️ 与 [snapValue] 的分工：[snapValue] 是**瞬时到位**（回显门禁 / 外部状态强同步
     * 用，值必须是权威的）；本函数是**弹簧追随**（手指路径专用）。
     *
     * ## 为什么拖动期必须用弹簧而不是瞬时
     *
     * 上游 Kyant0 `catalog/utils/DampedDragAnimation` 的 `updateValue` 走的是
     * `valueAnimatable.animateTo(target, spring(1f, 1000f, visibilityThreshold))` ——
     * 手指每帧把弹簧目标挪一格，值**追着手指跑**：快速拖动时胶囊略微滞后、松手后
     * 带着惯性收敛 —— 这就是**果冻般的弹动位移**（squash & stretch 之外的另一半）。
     *
     * 本仓 Wave 10 为了根治"不跟手"把拖动期改成 `snapValue`（瞬时），位移变成**刚性**：
     * 手指到哪胶囊到哪、零滞后零拖尾 —— 跟手是跟手了，但果冻位移没了（用户以上游
     * demo 录屏对比实锤）。
     *
     * ⚠️ 现在改回弹簧是**安全**的：当年"不跟手"的真因是**坐标反馈**（手势宿主与被
     * 平移节点同体，斜率 0.489），已由"宿主迁静态节点"根治；Wave 10 顺带改的
     * `UNDISPATCHED`（消派发延迟）这里**保留**，所以是「有弹性的跟手」而不是
     * 「慢半拍的跟手」—— 与当年的故障不是一回事，KDoc 留证据。
     *
     * 规格逐字取上游：dampingRatio=1f（临界阻尼，不过冲）、stiffness=1000f。
     * 每帧 animateTo 会取消上一条在途动画并从**当前值**继续追新目标（与上游同款，
     * 不是累加漂移）。
     */
    fun followValue(value: Float) {
        val coerced = value.coerceIn(valueRange)
        targetValue = coerced
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            valueAnimatable.animateTo(
                targetValue = coerced,
                animationSpec = spring(
                    dampingRatio = 1f,
                    stiffness = 1000f,
                    visibilityThreshold = 0.001f,
                ),
            )
        }
    }

    /**
     * 按压 / 释放的三路弹簧：pressProgress（临界阻尼，NoBouncy 与上游一致）+
     * scaleX / scaleY（欠阻尼 0.6/0.7 —— 回弹来源）。
     *
     * ⚠️ 影响面（2026-09-26 回弹恢复申报）：scaleX/scaleY 从 pressProgress 的纯函数
     * 改为独立弹簧，消费方 = LiquidBottomTabs 胶囊 layerBlock、GlassSlider thumb、
     * GlassSwitch thumb —— 三者的按压/松手都会获得上游同款的微过冲与回弹；
     * pressProgress 本身（effects 的模糊/折射/阴影）仍走原 NoBouncy 路径，观感不变。
     */
    internal fun setPressed(pressed: Boolean) {
        // 按下即重置速度采样（上游 press() 同款）：上一段手势的速度不得污染本次。
        velocityTracker.resetTracking()
        animationScope.launch {
            launch {
                pressAnimatable.animateTo(
                    targetValue = if (pressed) 1f else 0f,
                    // 上游 pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
                    //（临界阻尼、高刚度 —— 与 value 弹簧同规格）。本仓旧值
                    // NoBouncy/StiffnessMedium(1500) 阻尼比一致但刚度偏高，对齐上游。
                    animationSpec = spring(
                        dampingRatio = 1f,
                        stiffness = 1000f,
                        visibilityThreshold = 0.001f,
                    )
                )
            }
            launch {
                scaleXAnimatable.animateTo(
                    targetValue = if (pressed) pressedScale else initialScale,
                    animationSpec = scaleXAnimationSpec
                )
            }
            launch {
                scaleYAnimatable.animateTo(
                    targetValue = if (pressed) pressedScale else initialScale,
                    animationSpec = scaleYAnimationSpec
                )
            }
        }
    }
}
