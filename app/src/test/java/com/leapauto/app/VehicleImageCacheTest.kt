package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VehicleImageCacheTest {

    @Test
    fun `vehicle picture meta model stores url and key correctly`() {
        val meta = VehiclePictureMeta(
            pictureKey = "test_key_123",
            shareBindUrl = "https://cdn.leapmotor.com/car/c16_white.png"
        )
        assertEquals("test_key_123", meta.pictureKey)
        assertEquals("https://cdn.leapmotor.com/car/c16_white.png", meta.shareBindUrl)
    }

    @Test
    fun `vehicle picture meta equals and copy work as expected`() {
        val meta1 = VehiclePictureMeta("key1", "https://example.com/pic1.png")
        val meta2 = meta1.copy(pictureKey = "key2")
        assertEquals("key2", meta2.pictureKey)
        assertEquals("https://example.com/pic1.png", meta2.shareBindUrl)
    }
}
