package com.leapauto.app

import com.leapauto.app.ui.ClimateToggleGlyph
import com.leapauto.app.ui.ClimateToggleVisualSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class ClimateToggleVisualSpecTest {
    @Test
    fun switchUsesRequestedTrackThumbAndAnimationGeometry() {
        assertEquals(36, ClimateToggleVisualSpec.TRACK_WIDTH_DP)
        assertEquals(14, ClimateToggleVisualSpec.TRACK_HEIGHT_DP)
        assertEquals(20, ClimateToggleVisualSpec.THUMB_SIZE_DP)
        assertEquals(0, ClimateToggleVisualSpec.TRACK_HORIZONTAL_PADDING_DP)
        assertEquals(200, ClimateToggleVisualSpec.ANIMATION_DURATION_MS)
        assertEquals(
            16,
            ClimateToggleVisualSpec.TRACK_WIDTH_DP -
                ClimateToggleVisualSpec.TRACK_HORIZONTAL_PADDING_DP -
                ClimateToggleVisualSpec.THUMB_SIZE_DP
        )
    }

    @Test
    fun visualStateMovesThumbAndChangesStatusGlyph() {
        val off = ClimateToggleVisualSpec.state(checked = false)
        val on = ClimateToggleVisualSpec.state(checked = true)

        assertEquals(0, off.thumbOffsetDp)
        assertEquals(ClimateToggleGlyph.CROSS, off.glyph)
        assertEquals(16, on.thumbOffsetDp)
        assertEquals(ClimateToggleGlyph.CHECK, on.glyph)
    }
}
