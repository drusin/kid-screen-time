package io.github.drusin.kidscreentime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Proves that Robolectric starts (including the android-all download via the configured repo). */
@RunWith(RobolectricTestRunner::class)
class AppSmokeTest {
    @Test
    fun `Debug-Build hat das Suffix debug und den Namen Bildschirmzeit Dev`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("io.github.drusin.kidscreentime.debug", context.packageName)
        assertEquals("Bildschirmzeit Dev", context.getString(R.string.app_name))
    }
}
