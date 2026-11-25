package `in`.melo.presentation.components.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropBlurTest {

    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun red(p: Int) = (p shr 16) and 0xFF

    @Test
    fun aFlatFieldStaysFlat() {
        val texels = IntArray(16 * 16) { gray(120) }
        repeat(3) { boxBlur(texels, 16, radius = 4) }
        texels.forEach { assertEquals(gray(120), it) }
    }

    @Test
    fun aHardEdgeBecomesAGradient() {
        val size = 16
        val texels = IntArray(size * size) { i -> if (i % size < size / 2) gray(0) else gray(240) }
        boxBlur(texels, size, radius = 3)
        val row = (0 until size).map { red(texels[5 * size + it]) }
        // Far ends keep their colour, the seam turns into a monotonic ramp.
        assertEquals(0, row.first())
        assertEquals(240, row.last())
        assertTrue(row.zipWithNext().all { (a, b) -> a <= b })
        assertTrue(row[size / 2 - 1] in 1..239 && row[size / 2] in 1..239)
        // Every pixel stays opaque.
        texels.forEach { assertEquals(0xFF, (it ushr 24) and 0xFF) }
    }
}
