package io.github.drusin.kidscreentime.api

/** Paths of the REST contract, shared by app and server. */
object ApiPaths {
    const val HEALTH = "/health"

    /** Kopplung (no token): redeem a Kopplungscode. */
    const val PAIRING_REDEEM = "/api/v1/pairing/redeem"

    /** Elterngerät: Eltern-Snapshot. */
    const val PARENT_SNAPSHOT = "/api/v1/parent/snapshot"

    /** Elterngerät: create a Kopplungscode (POST). */
    const val PARENT_PAIRING_CODES = "/api/v1/parent/pairing-codes"

    /**
     * Elterngerät: book a Tagesanpassung for today (POST [DailyAdjustmentRequest]). Like every Eltern-Aktion it answers
     * with the new [ParentSnapshot] (`400` for an unknown Kind or an invalid delta).
     */
    const val PARENT_DAILY_ADJUSTMENTS = "/api/v1/parent/daily-adjustments"

    /**
     * Elterngerät: take back the last Tagesanpassung of today as a Gegenbuchung (POST [UndoDailyAdjustmentRequest]).
     * Answers with the new [ParentSnapshot], `409` when there is nothing to take back.
     */
    const val PARENT_DAILY_ADJUSTMENTS_UNDO = "/api/v1/parent/daily-adjustments/undo"

    /**
     * Elterngerät: set the Wochenvorlage (POST [WeeklyTemplateRequest]). The new version is valid from tomorrow, today's
     * Tageskontingent stays. Answers with the new [ParentSnapshot] (`400` for an unknown Kind or invalid minutes).
     */
    const val PARENT_WEEKLY_TEMPLATE = "/api/v1/parent/weekly-template"

    /** Elterngerät: set the rules of a Kind, Vorwarnschwelle, Überziehungsgrenze and name (POST [RulesRequest]). Answers like every Eltern-Aktion. */
    const val PARENT_RULES = "/api/v1/parent/rules"

    /** Kindgerät: sync (POST). Sends the Outbox ([ChildSyncRequest]) and returns the complete [ChildSnapshot]. */
    const val CHILD_SYNC = "/api/v1/child/sync"

    /** Both roles: update the own push registration ([PushRegistration]) when it changes (POST, answer `204`). */
    const val PUSH_REGISTRATION = "/api/v1/push-registration"
}
