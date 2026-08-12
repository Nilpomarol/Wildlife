package com.wildlife.feasibility

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class UserObservationsActivity : Activity() {
    private lateinit var usernameInput: EditText
    private lateinit var statusText: TextView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var loadMoreButton: Button

    private var resolvedUser: INaturalistUser? = null
    private var nextIdAbove: Long? = null
    private var loadedCount = 0
    private var totalResults = 0
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSafeSystemBars()
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            applySystemBarPadding()
            setBackgroundColor(Color.rgb(245, 249, 245))
        }
        root.addView(TextView(this).apply {
            text = "Public iNaturalist observations"
            textSize = 24f
            setTextColor(Color.rgb(16, 64, 32))
        })
        root.addView(TextView(this).apply {
            text = "Read-only, no OAuth. Only public data for the exact resolved user ID is shown."
            setPadding(0, dp(4), 0, dp(8))
        })
        usernameInput = EditText(this).apply {
            hint = "Exact iNaturalist username"
            setSingleLine(true)
            setText(intent.getStringExtra(EXTRA_USERNAME).orEmpty())
        }
        root.addView(usernameInput, matchWidth())
        root.addView(Button(this).apply {
            text = "Fetch observations"
            setOnClickListener { startFetch() }
        }, matchWidth())
        statusText = TextView(this).apply {
            text = "Enter a username to test public retrieval."
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(statusText, matchWidth())
        loadMoreButton = Button(this).apply {
            text = "Load next 100"
            visibility = View.GONE
            setOnClickListener { loadPage() }
        }
        root.addView(loadMoreButton, matchWidth())
        resultsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(
            ScrollView(this).apply { addView(resultsContainer, matchWidth()) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(root)
    }

    private fun startFetch() {
        val username = usernameInput.text.toString().trim()
        if (username.isBlank() || loading) return
        resolvedUser = null
        nextIdAbove = null
        loadedCount = 0
        totalResults = 0
        resultsContainer.removeAllViews()
        loadMoreButton.visibility = View.GONE
        loading = true
        statusText.text = "Resolving exact username…"
        thread(name = "inat-user-resolve") {
            runCatching { INaturalistClient().resolveExactUser(username) }
                .onSuccess { user ->
                    resolvedUser = user
                    runOnUiThread {
                        loading = false
                        statusText.text = "Resolved ${user.login} to immutable ID #${user.id}."
                        loadPage()
                    }
                }
                .onFailure { error -> showFailure(error) }
        }
    }

    private fun loadPage() {
        val user = resolvedUser ?: return
        if (loading) return
        loading = true
        loadMoreButton.isEnabled = false
        statusText.text = "Fetching public observations for user ID #${user.id}…"
        val cursor = nextIdAbove
        thread(name = "inat-observation-page") {
            runCatching { INaturalistClient().publicObservations(user.id, cursor, PAGE_SIZE) }
                .onSuccess { page ->
                    runOnUiThread {
                        page.observations.forEach(::addObservation)
                        loadedCount += page.observations.size
                        if (cursor == null) totalResults = page.totalResults
                        nextIdAbove = page.observations.lastOrNull()?.id
                        val hasMore = page.observations.size == PAGE_SIZE && loadedCount < totalResults
                        statusText.text =
                            "${user.login} (#${user.id}): loaded $loadedCount of $totalResults public observations."
                        loadMoreButton.visibility = if (hasMore) View.VISIBLE else View.GONE
                        loadMoreButton.isEnabled = true
                        loading = false
                    }
                }
                .onFailure { error -> showFailure(error) }
        }
    }

    private fun addObservation(observation: PublicObservation) {
        resultsContainer.addView(TextView(this).apply {
            text = "${observation.label}\n${observation.observedOn} · ${observation.qualityGrade} · #${observation.id}"
            textSize = 14f
            setTextColor(Color.rgb(20, 65, 110))
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setBackgroundColor(Color.WHITE)
            setOnClickListener {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.inaturalist.org/observations/${observation.uuid}"),
                    ),
                )
            }
        }, matchWidth().apply { bottomMargin = dp(6) })
    }

    private fun showFailure(error: Throwable) {
        runOnUiThread {
            loading = false
            loadMoreButton.isEnabled = true
            statusText.text = "Fetch failed: ${error.message}"
        }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val EXTRA_USERNAME = "username"
        private const val PAGE_SIZE = 100
    }
}
