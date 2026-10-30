package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ControllerProfile
import com.example.model.GamepadState
import com.example.model.SensitivityCurve
import com.example.model.XboxButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `read app_name and verify userscript bundle asset exists`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("xCloud Pad", appName)

        val script = context.assets.open("userscript/xcloud_pad_bundle.js")
            .bufferedReader()
            .use { it.readText() }
        assertTrue(script.contains("__xcloudPadUpdateGamepad"))
        assertTrue(script.contains("navigator.getGamepads"))
        assertTrue(script.contains("gamepadconnected"))
    }

    @Test
    fun `verify standard 17-button state and deadzone curve`() {
        var state = GamepadState()
        state = state.withButton(XboxButton.A, true).withButton(XboxButton.XBOX, true)
        assertTrue(state.isPressed(XboxButton.A))
        assertTrue(state.isPressed(XboxButton.XBOX))
        assertFalse(state.isPressed(XboxButton.B))

        val profile = ControllerProfile(
            deadzone = 0.10f,
            sensitivity = 1.0f,
            sensitivityCurve = SensitivityCurve.LINEAR
        )
        // Inside deadzone -> (0f, 0f)
        val (dx0, dy0) = profile.processStickVector(0.05f, 0.02f)
        assertEquals(0f, dx0, 0.001f)
        assertEquals(0f, dy0, 0.001f)

        // Full deflection -> (1f, 0f)
        val (dx1, _) = profile.processStickVector(1.0f, 0.0f)
        assertEquals(1.0f, dx1, 0.01f)
    }
}
