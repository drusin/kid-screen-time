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

    /** Kindgerät: sync (POST). Sends the Outbox ([ChildSyncRequest]) and returns the complete [ChildSnapshot]. */
    const val CHILD_SYNC = "/api/v1/child/sync"
}
