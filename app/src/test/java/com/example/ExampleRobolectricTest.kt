package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ControllerPreferencesRepository
import com.example.hardware.BluetoothHidManager
import com.example.model.LayoutPreset
import com.example.model.PsButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `verify app name and USB HID Gamepad descriptor`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NeonPad", appName)
        assertTrue(BluetoothHidManager.GAMEPAD_HID_DESCRIPTOR.isNotEmpty())
        assertEquals(0x05.toByte(), BluetoothHidManager.GAMEPAD_HID_DESCRIPTOR[0])
    }

    @Test
    fun `verify custom button mapping and layout preset persistence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ControllerPreferencesRepository(context)

        repo.saveButtonMapping(PsButton.CROSS, 5, "Jump / Boost")
        val loadedMappings = repo.loadButtonMappings()
        assertEquals(5, loadedMappings[PsButton.CROSS]?.mappedHidBit)
        assertEquals("Jump / Boost", loadedMappings[PsButton.CROSS]?.mappedPcKey)

        val transforms = repo.applyPresetTransforms(LayoutPreset.FPS_COMPETITIVE)
        assertEquals(LayoutPreset.FPS_COMPETITIVE, repo.loadActivePreset())
        assertTrue(transforms.isNotEmpty())
    }
}
