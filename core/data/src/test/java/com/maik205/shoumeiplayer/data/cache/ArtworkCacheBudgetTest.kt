package com.maik205.shoumeiplayer.data.cache

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkCacheBudgetTest {
    @Test
    fun `low memory devices receive smaller artwork budgets`() {
        val budget = resolveArtworkCacheBudget(isLowRamDevice = true, memoryClassMiB = 1024)

        assertEquals(0.10, budget.memoryCachePercent, 0.0)
        assertEquals(100L * 1024 * 1024, budget.maximumDiskCacheBytes)
    }

    @Test
    fun `large devices retain the normal artwork budget`() {
        val budget = resolveArtworkCacheBudget(isLowRamDevice = false, memoryClassMiB = 512)

        assertEquals(0.20, budget.memoryCachePercent, 0.0)
        assertEquals(250L * 1024 * 1024, budget.maximumDiskCacheBytes)
    }
}
