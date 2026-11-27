package com.rickeal.liquidharness.core.design.liquid

/**
 * 玻璃节点的**图层预算**：当前尺寸是否还允许创建离屏 / 带效果的 GraphicsLayer。
 *
 * ## 真机黑块根因（W62）
 *
 * 每个玻璃节点一次绘制会建最多 5 个 `GraphicsLayer`，尺寸都按**节点自身**取：外阴影、
 * 内阴影、高光、背景录制（带 RenderEffect 的模糊 / 折射）、内容离屏层（`Offscreen`）。
 * 控件尺度（< 1600 px）没问题，但聊天里的模型气泡是**单个 LazyColumn item**：长回答
 * 正文可达数万 px（W62 真机实测 `984×48754`）。此时 `GraphicsLayer.record()` 按全高
 * 申请纹理，超过 GL 纹理 / 单层显存预算时分配失败，图层内容变成未定义 —— 真机上就是
 * **整块纯黑**（阴影层还额外扩 `radius × 4`，比节点更高）。用户报告「思考框及输出框
 * 文字一多就整块黑」即此路径；W24-01（双层玻璃 / 零尺寸进 AGSL）是另一条成因。
 *
 * ## 阈值依据
 *
 * - **单边 4096 px**：API 33+ 实测 `GL_MAX_TEXTURE_SIZE ≥ 8192`，取其一半，给
 *   RenderEffect 的外扩与厂商差异留余量；正常控件与短气泡远不触碰。
 * - **面积 4_000_000 px²**（ARGB8888 ≈ 16 MB/层）：拦「边长合法但横竖都大」的卡片，
 *   避免单片 30 MB × 5 层。
 *
 * ## 降级表现
 *
 * 超预算时**不申请图层**：阴影 / 内阴影 / 高光不画、背景不录制、内容改无 `Offscreen`
 * 的普通图层（保留圆角裁剪）。观感降级为半透明实底 —— 与用户关掉「背景模糊」的既有
 * 语义一致：**降级，不是崩溃**。
 */
internal object GlassLayerBudget {

    /** 单边上限（px）。 */
    const val MAX_DIMENSION_PX = 4096

    /** 单层面积上限（px²）。 */
    const val MAX_AREA_PX = 4_000_000L

    /**
     * 该尺寸是否仍允许建图层。
     *
     * 零 / 负尺寸返回 `true`：展开动画首帧的 `Size(0,0)` 由既有 `lensCanvasIsSafe`
     * 守卫处理，这里不重复拦（否则首帧会闪一次降级形态）。
     */
    fun allows(widthPx: Float, heightPx: Float): Boolean {
        if (!widthPx.isFinite() || !heightPx.isFinite()) return false
        if (widthPx <= 0f || heightPx <= 0f) return true
        if (widthPx > MAX_DIMENSION_PX || heightPx > MAX_DIMENSION_PX) return false
        return widthPx * heightPx <= MAX_AREA_PX
    }
}
