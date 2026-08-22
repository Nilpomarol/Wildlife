package com.wildlife.feasibility

import android.content.Context

class AccountStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun verified(): VerifiedAccount? {
        val id = preferences.getLong(KEY_VERIFIED_ID, -1L)
        val login = preferences.getString(KEY_VERIFIED_LOGIN, null)
        val verifiedAt = preferences.getLong(KEY_VERIFIED_AT, -1L)
        return if (id > 0L && !login.isNullOrBlank() && verifiedAt > 0L) {
            VerifiedAccount(id, login, verifiedAt)
        } else {
            null
        }
    }

    fun pending(): PendingAccountLink? {
        val id = preferences.getLong(KEY_PENDING_ID, -1L)
        val login = preferences.getString(KEY_PENDING_LOGIN, null)
        val code = preferences.getString(KEY_PENDING_CODE, null)
        val createdAt = preferences.getLong(KEY_PENDING_CREATED_AT, -1L)
        return if (id > 0L && !login.isNullOrBlank() && !code.isNullOrBlank() && createdAt > 0L) {
            PendingAccountLink(id, login, code, createdAt)
        } else {
            null
        }
    }

    fun savePending(link: PendingAccountLink) {
        preferences.edit()
            .putLong(KEY_PENDING_ID, link.userId)
            .putString(KEY_PENDING_LOGIN, link.login)
            .putString(KEY_PENDING_CODE, link.code)
            .putLong(KEY_PENDING_CREATED_AT, link.createdAtMs)
            .apply()
    }

    fun markVerified(link: PendingAccountLink, verifiedAtMs: Long) {
        preferences.edit()
            .putLong(KEY_VERIFIED_ID, link.userId)
            .putString(KEY_VERIFIED_LOGIN, link.login)
            .putLong(KEY_VERIFIED_AT, verifiedAtMs)
            .remove(KEY_PENDING_ID)
            .remove(KEY_PENDING_LOGIN)
            .remove(KEY_PENDING_CODE)
            .remove(KEY_PENDING_CREATED_AT)
            .apply()
    }

    fun restoreVerified(account: VerifiedAccount) {
        preferences.edit()
            .putLong(KEY_VERIFIED_ID, account.userId)
            .putString(KEY_VERIFIED_LOGIN, account.login)
            .putLong(KEY_VERIFIED_AT, account.verifiedAtMs)
            .remove(KEY_PENDING_ID).remove(KEY_PENDING_LOGIN).remove(KEY_PENDING_CODE).remove(KEY_PENDING_CREATED_AT)
            .apply()
    }

    fun clearPending() {
        preferences.edit()
            .remove(KEY_PENDING_ID)
            .remove(KEY_PENDING_LOGIN)
            .remove(KEY_PENDING_CODE)
            .remove(KEY_PENDING_CREATED_AT)
            .apply()
    }

    fun unlink(): Boolean = preferences.edit().clear().commit()

    companion object {
        internal const val PREFERENCES = "linked_inaturalist_account"
        private const val KEY_VERIFIED_ID = "verified_id"
        private const val KEY_VERIFIED_LOGIN = "verified_login"
        private const val KEY_VERIFIED_AT = "verified_at"
        private const val KEY_PENDING_ID = "pending_id"
        private const val KEY_PENDING_LOGIN = "pending_login"
        private const val KEY_PENDING_CODE = "pending_code"
        private const val KEY_PENDING_CREATED_AT = "pending_created_at"
    }
}
