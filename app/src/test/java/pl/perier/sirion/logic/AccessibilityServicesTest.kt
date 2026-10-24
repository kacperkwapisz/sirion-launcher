package pl.perier.sirion.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityServicesTest {
    private val ours = "pl.perier.sirion/pl.perier.sirion.ui.VolumePopup"

    @Test
    fun addsOursToAnEmptyList() {
        assertEquals(ours, AccessibilityServices.withVolumePopup(null))
        assertEquals(ours, AccessibilityServices.withVolumePopup("null"))
        assertEquals(ours, AccessibilityServices.withVolumePopup(""))
    }

    @Test
    fun keepsOtherServicesAndDropsTheOldVolumeKeys() {
        val current = "com.other/.Service:pl.perier.sirion/pl.perier.sirion.ui.VolumeKeys"
        assertEquals("com.other/.Service:$ours", AccessibilityServices.withVolumePopup(current))
    }

    @Test
    fun leavesAListThatAlreadyHasOursAlone() {
        assertEquals("com.other/.Service:$ours", AccessibilityServices.withVolumePopup("com.other/.Service:$ours"))
    }
}
