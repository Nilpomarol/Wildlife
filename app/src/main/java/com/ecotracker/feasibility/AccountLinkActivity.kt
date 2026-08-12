package com.wildlife.feasibility

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class AccountLinkActivity : Activity() {
    private lateinit var accountStore: AccountStore
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSafeSystemBars()
        accountStore = AccountStore(this)
        render()
    }

    private fun render(message: String? = null) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            setBackgroundColor(COLOR_BACKGROUND)
        }
        content.addView(TextView(this).apply {
            text = "Link iNaturalist"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_FOREST)
        })
        content.addView(TextView(this).apply {
            text = "Verify that the public iNaturalist account belongs to you—without OAuth or a password."
            textSize = 14f
            setTextColor(COLOR_MUTED)
            setPadding(0, dp(4), 0, dp(16))
        })

        message?.let {
            content.addView(statusCard(it), matchWidth().apply { bottomMargin = dp(12) })
        }

        val verified = accountStore.verified()
        val pending = accountStore.pending()
        when {
            verified != null -> renderVerified(content, verified)
            pending != null -> renderPending(content, pending)
            else -> renderStart(content)
        }
        setContentView(ScrollView(this).apply {
            addView(content, matchWidth())
            applySystemBarPadding()
        })
    }

    private fun renderStart(content: LinearLayout) {
        content.addView(stepTitle("1 · Enter your exact username"))
        val username = EditText(this).apply {
            hint = "iNaturalist username"
            setSingleLine(true)
            setText(intent.getStringExtra(EXTRA_USERNAME).orEmpty())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(Color.WHITE, strokeColor = COLOR_BORDER)
        }
        content.addView(username, matchWidth())
        content.addView(primaryButton("Generate verification code") {
            beginVerification(username.text.toString().trim())
        }, matchWidth().apply { topMargin = dp(8) })
        content.addView(TextView(this).apply {
            text = "Wildlife first resolves the username to its permanent numeric iNaturalist ID."
            textSize = 13f
            setTextColor(COLOR_MUTED)
            setPadding(0, dp(10), 0, 0)
        })
    }

    private fun renderPending(content: LinearLayout, pending: PendingAccountLink) {
        content.addView(stepTitle("1 · Account found"))
        content.addView(card("${pending.login}\niNaturalist user ID #${pending.userId}"))

        content.addView(stepTitle("2 · Add this code to your profile"))
        content.addView(TextView(this).apply {
            text = pending.code
            textSize = 24f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_FOREST)
            setTextIsSelectable(true)
            setPadding(dp(12), dp(18), dp(12), dp(18))
            background = roundedBackground(Color.WHITE, strokeColor = COLOR_BORDER)
        }, matchWidth())
        content.addView(button("Copy code") { copyCode(pending.code) }, matchWidth())
        content.addView(TextView(this).apply {
            text = "Open iNaturalist profile settings, paste the code into your profile description, and save. You can remove it after verification. The code expires after 24 hours."
            textSize = 14f
            setTextColor(COLOR_MUTED)
            setPadding(0, dp(10), 0, dp(8))
        })
        content.addView(button("Open iNaturalist profile settings", ::openProfileSettings), matchWidth())

        content.addView(stepTitle("3 · Check the public profile"))
        content.addView(primaryButton("Check verification", ::checkVerification), matchWidth())
        content.addView(button("Start again with another username") {
            accountStore.clearPending()
            render()
        }, matchWidth())
    }

    private fun renderVerified(content: LinearLayout, account: VerifiedAccount) {
        content.addView(TextView(this).apply {
            text = "✓ Verified account"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_FOREST)
        })
        content.addView(card("${account.login}\niNaturalist user ID #${account.userId}"))
        content.addView(TextView(this).apply {
            text = "Wildlife will only fetch and match observations belonging to this immutable user ID. You may now remove the temporary code from your iNaturalist profile."
            textSize = 14f
            setTextColor(COLOR_MUTED)
            setPadding(0, dp(12), 0, dp(8))
        })
        content.addView(primaryButton("Done") { finish() }, matchWidth())
        content.addView(button("Open public profile") {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.inaturalist.org/people/${account.login}")),
            )
        }, matchWidth())
        content.addView(dangerButton("Unlink account") { confirmUnlink(account.login) }, matchWidth())
    }

    private fun beginVerification(username: String) {
        if (username.isBlank() || loading) {
            if (username.isBlank()) render("Enter your exact iNaturalist username.")
            return
        }
        loading = true
        render("Resolving the exact public iNaturalist username…")
        thread(name = "inat-account-resolve") {
            runCatching {
                val user = INaturalistClient().resolveExactUser(username)
                PendingAccountLink(
                    userId = user.id,
                    login = user.login,
                    code = AccountVerification.newCode(),
                    createdAtMs = System.currentTimeMillis(),
                )
            }.onSuccess { pending ->
                accountStore.savePending(pending)
                runOnUiThread {
                    loading = false
                    render("Account found. Complete the two steps below.")
                }
            }.onFailure { error ->
                runOnUiThread {
                    loading = false
                    render("Could not start verification: ${error.message}")
                }
            }
        }
    }

    private fun checkVerification() {
        val pending = accountStore.pending() ?: return
        if (loading) return
        if (AccountVerification.isExpired(pending, System.currentTimeMillis())) {
            accountStore.clearPending()
            render("The verification code expired. Generate a new one.")
            return
        }
        loading = true
        render("Checking the public iNaturalist profile…")
        thread(name = "inat-account-check") {
            runCatching { INaturalistClient().publicUserProfile(pending.userId) }
                .onSuccess { profile ->
                    runOnUiThread {
                        loading = false
                        if (AccountVerification.profileContainsCode(profile, pending)) {
                            accountStore.markVerified(pending, System.currentTimeMillis())
                            setResult(RESULT_OK)
                            render("Verified successfully.")
                        } else {
                            render("Code not found yet. Save it in the iNaturalist profile description, wait briefly, then check again.")
                        }
                    }
                }
                .onFailure { error ->
                    runOnUiThread {
                        loading = false
                        render("Verification check failed: ${error.message}")
                    }
                }
        }
    }

    private fun copyCode(code: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Wildlife verification code", code))
        render("Code copied. Paste it into your iNaturalist profile description.")
    }

    private fun openProfileSettings() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.inaturalist.org/users/edit")))
    }

    private fun confirmUnlink(login: String) {
        AlertDialog.Builder(this)
            .setTitle("Unlink $login?")
            .setMessage("Pending observations will stop checking until an account is verified again. No iNaturalist data will be changed.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Unlink") { _, _ ->
                accountStore.unlink()
                setResult(RESULT_OK)
                render("Account unlinked.")
            }
            .show()
    }

    private fun card(value: String) = TextView(this).apply {
        text = value
        textSize = 16f
        setTextColor(COLOR_FOREST)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = roundedBackground(Color.WHITE, strokeColor = COLOR_BORDER)
    }

    private fun statusCard(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(COLOR_FOREST)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = roundedBackground(COLOR_STATUS)
    }

    private fun stepTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(COLOR_FOREST)
        setPadding(0, dp(12), 0, dp(7))
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(COLOR_FOREST)
        backgroundTintList = ColorStateList.valueOf(COLOR_BUTTON)
        setOnClickListener { action() }
    }

    private fun primaryButton(label: String, action: () -> Unit) = button(label, action).apply {
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        backgroundTintList = ColorStateList.valueOf(COLOR_FOREST)
    }

    private fun dangerButton(label: String, action: () -> Unit) = button(label, action).apply {
        setTextColor(COLOR_DANGER)
        backgroundTintList = ColorStateList.valueOf(COLOR_DANGER_BACKGROUND)
    }

    private fun roundedBackground(color: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(12).toFloat()
        strokeColor?.let { setStroke(dp(1), it) }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val EXTRA_USERNAME = "username"
        private val COLOR_BACKGROUND = Color.rgb(244, 248, 244)
        private val COLOR_FOREST = Color.rgb(26, 86, 48)
        private val COLOR_MUTED = Color.rgb(88, 103, 92)
        private val COLOR_BORDER = Color.rgb(214, 225, 216)
        private val COLOR_BUTTON = Color.rgb(226, 239, 228)
        private val COLOR_STATUS = Color.rgb(230, 242, 233)
        private val COLOR_DANGER = Color.rgb(156, 45, 45)
        private val COLOR_DANGER_BACKGROUND = Color.rgb(252, 235, 235)
    }
}
