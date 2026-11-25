package io.github.drusin.kidscreentime.ui

import androidx.annotation.StringRes
import io.github.drusin.kidscreentime.R
import io.github.drusin.kidscreentime.api.SetupItem

/** The short name of a Checklisten-Punkt, shared by the Kindgerät (Checkliste, Banner) and the Elterngerät (Status-Karte). */
@StringRes
internal fun SetupItem.labelRes(): Int = when (this) {
    SetupItem.EXACT_ALARMS -> R.string.setup_item_exact_alarms
    SetupItem.FULL_SCREEN_INTENT -> R.string.setup_item_full_screen_intent
    SetupItem.NOTIFICATIONS -> R.string.setup_item_notifications
    SetupItem.BATTERY -> R.string.setup_item_battery
    SetupItem.ALARM_VOLUME -> R.string.setup_item_alarm_volume
    SetupItem.PUSH_DISTRIBUTOR -> R.string.setup_item_push_distributor
}

@StringRes
internal fun SetupItem.hintRes(): Int = when (this) {
    SetupItem.EXACT_ALARMS -> R.string.setup_item_exact_alarms_hint
    SetupItem.FULL_SCREEN_INTENT -> R.string.setup_item_full_screen_intent_hint
    SetupItem.NOTIFICATIONS -> R.string.setup_item_notifications_hint
    SetupItem.BATTERY -> R.string.setup_item_battery_hint
    SetupItem.ALARM_VOLUME -> R.string.setup_item_alarm_volume_hint
    SetupItem.PUSH_DISTRIBUTOR -> R.string.setup_item_push_distributor_hint
}
