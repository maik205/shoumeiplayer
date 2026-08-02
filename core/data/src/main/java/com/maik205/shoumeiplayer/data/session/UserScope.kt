package com.maik205.shoumeiplayer.data.session

/**
 * The `(serverUrl, userId)` pair that every *personal* piece of stored state hangs off.
 *
 * `LibraryCacheStore` already keys its snapshots this way, and for the same reason: a TV is shared
 * furniture. Switching profiles must hand the new viewer their own state, never the previous one's
 * — both because seeing another account's leftovers is a privacy leak (#85) and because a family
 * expects their own subtitle size, screensaver and resume points back.
 *
 * The value is used verbatim as a storage key prefix, so it must stay stable across releases:
 * changing the format silently orphans every preference already on disk. `userId` is a Jellyfin
 * GUID and `serverUrl` is normalized by [normalizeServerUrl], so the fixed `/` separators below
 * cannot be produced by either part in practice.
 */
data class UserScope(val serverUrl: String, val userId: String) {

    /**
     * Trailing `/` is load-bearing: without it the prefix for user `u1` would also match user
     * `u10`'s keys.
     */
    val storagePrefix: String = "$NAMESPACE$serverUrl/$userId/"

    companion object {
        /**
         * Marks a key as belonging to some account rather than to the device. Pre-scoping keys are
         * plain snake_case names, so nothing already on disk can be mistaken for a scoped key.
         */
        const val NAMESPACE: String = "user/"

        /** `null` when nobody is signed in — callers then fall back to device-wide storage. */
        fun of(session: Session?): UserScope? =
            session?.let { UserScope(serverUrl = it.serverUrl, userId = it.userId) }
    }
}
