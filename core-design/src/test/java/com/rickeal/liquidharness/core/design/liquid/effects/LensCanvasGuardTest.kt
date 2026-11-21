package com.rickeal.liquidharness.core.design.liquid.effects

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * W41（W24-01）：折射着色器的零尺寸守卫。
 *
 * `expandVertically` 首帧会给出 0 高度；若把 `Size(0,0)` 送进 AGSL 的 `normalize`，
 * 得到 NaN 采样坐标 ⇒ 展开区域可能整块黑/花屏。这里只钉纯函数判据，像素结论以真机为准。
 */
class LensCanvasGuardTest {

    @Test
    fun zeroAndNonFiniteSizesAreRejected() {
        assertFalse(lensCanvasIsSafe(0f), "零高度首帧必须退化成纯模糊")
        assertFalse(lensCanvasIsSafe(-1f), "负尺寸不是有效画布")
        assertFalse(lensCanvasIsSafe(Float.NaN), "NaN 不得进 shader uniform")
        assertFalse(lensCanvasIsSafe(Float.POSITIVE_INFINITY))
    }

    @Test
    fun finitePositiveSizeIsAccepted() {
        assertTrue(lensCanvasIsSafe(1f), "1px 是有效画布，守卫不得过宽")
        assertTrue(lensCanvasIsSafe(200f))
    }

    /**
     * W42（P1-5）：协作用例 —— 上面的纯函数自测**删掉生产调用点仍全绿**（空转判别力），
     * 本条钉住「守卫确实被调用、且在 shader uniform 写入之前」。
     *
     * JVM 无 Compose / AGSL 运行时，无法在单测里真的构造 `Size(0,0)` 走渲染分支；
     * 按本仓 arch-guard 的静态判据手法（`AgentRunnerTest.runnerHasNoMoreThanOneRunInFlight`
     * 同款：读源码文本断言形态）钉调用点。删 `Lens.kt` 的 `if (!lensCanvasIsSafe(...)) return`
     * ⇒ 本条立即 RED；把调用挪到 `applyRuntimeShader` 之后也 RED。
     */
    @Test
    fun guardCallSitePrecedesRefractionShader() {
        val source = java.io.File(
            "src/main/java/com/rickeal/liquidharness/core/design/liquid/effects/Lens.kt"
        ).readText()
        val guard = source.indexOf("if (!lensCanvasIsSafe(size.minDimension)) return")
        val shader = source.indexOf("renderEffect = renderEffect.applyRuntimeShader")
        assertTrue(guard >= 0, "Lens.kt 必须调用零尺寸守卫；删调用点后纯函数用例仍绿、本条必红")
        assertTrue(shader >= 0, "锚点自检：shader 应用点必须存在，否则本用例失去判别力")
        assertTrue(guard < shader, "守卫必须在 shader uniform 写入之前，否则 NaN 已进 AGSL")
    }
}
