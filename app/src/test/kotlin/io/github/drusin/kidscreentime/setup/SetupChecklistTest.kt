package io.github.drusin.kidscreentime.setup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.drusin.kidscreentime.api.SetupItem
import io.github.drusin.kidscreentime.ui.FakeSetupChecks
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Ticket 15: the state of the Einrichtungs-Checkliste: the last check and the ticked hints (they survive a restart). */
@RunWith(RobolectricTestRunner::class)
class SetupChecklistTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val checks = FakeSetupChecks()

    @Test
    fun `refresh prüft neu und liefert die fehlenden Punkte`() {
        val checklist = SetupChecklist(checks, context)
        assertEquals(emptyList(), checklist.refresh())

        checks.missing = listOf(SetupItem.ALARM_VOLUME)
        assertEquals(listOf(SetupItem.ALARM_VOLUME), checklist.refresh())
        assertEquals(listOf(SetupItem.ALARM_VOLUME), checklist.state.value.missing)

        checks.missing = emptyList()
        checklist.refresh()
        assertEquals(emptyList(), checklist.state.value.missing)
    }

    @Test
    fun `Abgehakte Hinweise bleiben nach einem Neustart abgehakt und beeinflussen die fehlenden Punkte nicht`() {
        val checklist = SetupChecklist(checks, context)
        checklist.setHintChecked(SetupHint.DO_NOT_DISTURB_ALARMS, true)
        checklist.setHintChecked(SetupHint.NEVER_SLEEPING_APPS, true)
        checklist.setHintChecked(SetupHint.NEVER_SLEEPING_APPS, false)

        val afterRestart = SetupChecklist(checks, context)

        assertEquals(setOf(SetupHint.DO_NOT_DISTURB_ALARMS), afterRestart.state.value.checkedHints)
        assertEquals(emptyList(), afterRestart.refresh())
    }
}
