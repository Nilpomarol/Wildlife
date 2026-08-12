package com.wildlife.feasibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountVerificationTest {
    private val pending = PendingAccountLink(
        userId = 42L,
        login = "NatureUser",
        code = "WILDLIFE-A2B3C4",
        createdAtMs = 1_000L,
    )

    @Test
    fun generatedCodesUseSafeReadableFormat() {
        assertTrue(AccountVerification.newCode().matches(Regex("WILDLIFE-[A-HJ-NP-Z2-9]{6}")))
    }

    @Test
    fun verifiesOnlyMatchingUserProfileAndExactCode() {
        val profile = PublicINaturalistProfile(42L, "natureuser", "Hello WILDLIFE-A2B3C4")
        assertTrue(AccountVerification.profileContainsCode(profile, pending))
        assertFalse(AccountVerification.profileContainsCode(profile.copy(id = 43L), pending))
        assertFalse(AccountVerification.profileContainsCode(profile.copy(description = "WILDLIFE-OTHER1"), pending))
    }

    @Test
    fun pendingCodeExpiresAfterTwentyFourHours() {
        assertFalse(AccountVerification.isExpired(pending, pending.createdAtMs + 23L * 60L * 60L * 1_000L))
        assertTrue(AccountVerification.isExpired(pending, pending.createdAtMs + 25L * 60L * 60L * 1_000L))
    }
}
