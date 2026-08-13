package com.wildlife.feasibility.ui.screens.account

import com.wildlife.feasibility.PendingAccountLink
import com.wildlife.feasibility.VerifiedAccount
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountLinkUiStateTest {
    @Test
    fun emptyStateStartsAtUsernameEntry() {
        assertEquals(AccountLinkStage.START, AccountLinkUiState().stage)
    }

    @Test
    fun pendingLinkShowsVerificationSteps() {
        val state = AccountLinkUiState(
            pending = PendingAccountLink(42, "naturalist", "WILDLIFE-3K8P7R", 1),
        )

        assertEquals(AccountLinkStage.PENDING, state.stage)
    }

    @Test
    fun verifiedAccountTakesPrecedenceOverStalePendingData() {
        val state = AccountLinkUiState(
            pending = PendingAccountLink(42, "naturalist", "WILDLIFE-3K8P7R", 1),
            verified = VerifiedAccount(42, "naturalist", 2),
        )

        assertEquals(AccountLinkStage.VERIFIED, state.stage)
    }
}
