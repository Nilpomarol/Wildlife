package com.wildlife.feasibility

import java.security.SecureRandom

object AccountVerification {
    private const val PREFIX = "WILDLIFE-"
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val CODE_LENGTH = 6
    private const val EXPIRY_MS = 24L * 60L * 60L * 1_000L
    private val random = SecureRandom()

    fun newCode(): String = buildString {
        append(PREFIX)
        repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    fun isExpired(link: PendingAccountLink, nowMs: Long): Boolean =
        nowMs - link.createdAtMs > EXPIRY_MS

    fun profileContainsCode(profile: PublicINaturalistProfile, link: PendingAccountLink): Boolean =
        profile.id == link.userId &&
            profile.login.equals(link.login, ignoreCase = true) &&
            profile.description.orEmpty().contains(link.code, ignoreCase = false)
}
