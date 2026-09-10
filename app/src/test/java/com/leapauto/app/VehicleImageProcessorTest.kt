package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleImageProcessorTest {

    @Test
    fun `computeAlphaBounds returns null when all pixels are transparent`() {
        val width = 100
        val height = 50
        val bounds = VehicleImageProcessor.computeAlphaBounds(width, height) { _, _ -> 0 }
        assertNull(bounds)
    }

    @Test
    fun `computeAlphaBounds returns null when dimensions are non-positive`() {
        assertNull(VehicleImageProcessor.computeAlphaBounds(0, 50) { _, _ -> 255 })
        assertNull(VehicleImageProcessor.computeAlphaBounds(100, 0) { _, _ -> 255 })
        assertNull(VehicleImageProcessor.computeAlphaBounds(-1, -1) { _, _ -> 255 })
    }

    @Test
    fun `computeAlphaBounds correctly bounds non-transparent pixels`() {
        val width = 100
        val height = 80

        // Non-transparent box between x in [20, 79], y in [10, 59]
        val bounds = VehicleImageProcessor.computeAlphaBounds(width, height) { x, y ->
            if (x in 20..79 && y in 10..59) 255 else 0
        }

        assertNotNull(bounds)
        assertEquals(20, bounds!!.left)
        assertEquals(10, bounds.top)
        assertEquals(79, bounds.right)
        assertEquals(59, bounds.bottom)
        assertEquals(60, bounds.width)
        assertEquals(50, bounds.height)
    }

    @Test
    fun `computeAlphaBounds respects alpha threshold`() {
        val width = 50
        val height = 50

        // Pixels with alpha = 10 (below default threshold 15) vs alpha = 20 (above threshold)
        val bounds = VehicleImageProcessor.computeAlphaBounds(width, height, threshold = 15) { x, y ->
            when {
                x == 5 && y == 5 -> 10 // Should be ignored
                x in 15..25 && y in 15..25 -> 20 // Should be detected
                else -> 0
            }
        }

        assertNotNull(bounds)
        assertEquals(15, bounds!!.left)
        assertEquals(15, bounds.top)
        assertEquals(25, bounds.right)
        assertEquals(25, bounds.bottom)
    }

    @Test
    fun `computeAlphaBounds correctly detects single corner pixel`() {
        val width = 64
        val height = 64

        val bounds = VehicleImageProcessor.computeAlphaBounds(width, height) { x, y ->
            if (x == 63 && y == 63) 255 else 0
        }

        assertNotNull(bounds)
        assertEquals(63, bounds!!.left)
        assertEquals(63, bounds.top)
        assertEquals(63, bounds.right)
        assertEquals(63, bounds.bottom)
        assertEquals(1, bounds.width)
        assertEquals(1, bounds.height)
    }

    @Test
    fun `computePlacement scales vehicle within target constraints and centers horizontally`() {
        val canvasW = 960
        val canvasH = 417
        val carW = 500
        val carH = 200

        val placement = VehicleImageProcessor.computePlacement(carW, carH, canvasW, canvasH)

        assertTrue(placement.carWidth <= canvasW * 0.90f + 1)
        assertTrue(placement.carHeight <= canvasH * 0.82f + 1)
        assertEquals((canvasW - placement.carWidth) / 2f, placement.carLeft, 0.01f)
        assertEquals(canvasW * 0.5f, placement.shadowCenterX, 0.01f)
        assertTrue(placement.shadowCenterY > placement.carTop)
        assertTrue(placement.shadowRadiusX > 0f)
        assertTrue(placement.shadowRadiusY > 0f)
    }

    @Test
    fun `computePlacement handles tall aspect ratio vehicle safely`() {
        val canvasW = 960
        val canvasH = 417
        val carW = 200
        val carH = 600

        val placement = VehicleImageProcessor.computePlacement(carW, carH, canvasW, canvasH)

        assertTrue(placement.carHeight <= canvasH * 0.82f + 1)
        assertEquals((canvasW - placement.carWidth) / 2f, placement.carLeft, 0.01f)
    }

    @Test
    fun `computePlacement handles wide aspect ratio vehicle safely`() {
        val canvasW = 960
        val canvasH = 417
        val carW = 1200
        val carH = 300

        val placement = VehicleImageProcessor.computePlacement(carW, carH, canvasW, canvasH)

        assertTrue(placement.carWidth <= canvasW * 0.90f + 1)
        assertEquals((canvasW - placement.carWidth) / 2f, placement.carLeft, 0.01f)
    }

    @Test
    fun `hasTransparency returns false for opaque image`() {
        val isTransparent = VehicleImageProcessor.hasTransparency(100, 100) { _, _ -> 255 }
        assertFalse(isTransparent)
    }

    @Test
    fun `hasTransparency returns true for image with substantial transparent background`() {
        // Center 40x40 is vehicle (opaque), rest is transparent
        val isTransparent = VehicleImageProcessor.hasTransparency(100, 100) { x, y ->
            if (x in 30..70 && y in 30..70) 255 else 0
        }
        assertTrue(isTransparent)
    }

    @Test
    fun `calculateInSampleSize calculates correct downsample power of 2`() {
        // Smaller than target -> sample size 1
        assertEquals(1, VehicleImageProcessor.calculateInSampleSize(1000, 800, 1920, 1920))

        // Moderate size -> sample size 1
        assertEquals(1, VehicleImageProcessor.calculateInSampleSize(1920, 1080, 1920, 1920))

        // Large photo (4K: 3840x2160) -> half is 1920x1080 -> sample size 1 or 2
        val sample4k = VehicleImageProcessor.calculateInSampleSize(4000, 3000, 1920, 1920)
        assertEquals(2, sample4k)

        // Very large photo (8000x6000) -> sample size 4
        val sample8k = VehicleImageProcessor.calculateInSampleSize(8000, 6000, 1920, 1920)
        assertEquals(4, sample8k)
    }

    @Test
    fun `computeAlphaBounds ignores pixels exactly equal to threshold`() {
        val bounds = VehicleImageProcessor.computeAlphaBounds(20, 20, threshold = 15) { _, _ -> 15 }
        assertNull(bounds)
    }

    @Test
    fun `computePlacement handles edge case with zero or negative car size safely`() {
        val placement = VehicleImageProcessor.computePlacement(0, -5, 960, 417)
        assertTrue(placement.carWidth >= 1)
        assertTrue(placement.carHeight >= 1)
        assertTrue(placement.carLeft >= 0f)
    }
}
