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
}
