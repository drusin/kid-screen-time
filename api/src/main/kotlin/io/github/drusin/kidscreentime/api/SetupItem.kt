package io.github.drusin.kidscreentime.api

import kotlinx.serialization.Serializable

/**
 * A prüfbarer Punkt of the Einrichtungs-Checkliste of the Kindgerät: a system setting the Alarm needs. The Kindgerät
 * checks them at every app start and sync and reports the missing ones; the Elterngeräte see them in the Status-Karte.
 * (Not prüfbare Punkte, "Nie schlafende Apps" and "Alarme bei Nicht stören", exist only as hints at the Kindgerät.)
 */
@Serializable
enum class SetupItem { EXACT_ALARMS, FULL_SCREEN_INTENT, NOTIFICATIONS, BATTERY, ALARM_VOLUME, PUSH_DISTRIBUTOR }
