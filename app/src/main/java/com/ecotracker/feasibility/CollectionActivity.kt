package com.wildlife.feasibility

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class CollectionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSafeSystemBars()
        render()
    }

    override fun onResume() {
        super.onResume()
        if (hasWindowFocus()) render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            applySystemBarPadding()
            setBackgroundColor(COLOR_BACKGROUND)
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@CollectionActivity).apply {
                text = "My collection"
                textSize = 26f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(COLOR_FOREST)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(this@CollectionActivity).apply {
                text = "Back"
                setOnClickListener { finish() }
            })
        }, matchWidth())

        val account = AccountStore(this).verified()
        if (account == null) {
            root.addView(message("Verify your iNaturalist account before opening the collection."), matchWidth())
            setContentView(root)
            return
        }

        val store = ObservationStore(this)
        val observations = store.observations(account.userId)
        val species = CollectionProjection.species(observations)
        val summary = store.summary(account.userId)

        root.addView(TextView(this).apply {
            text = "${species.size} collection entries · ${observations.size} observations · ${summary.totalXp} XP"
            textSize = 15f
            setTextColor(COLOR_FOREST)
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = rounded(COLOR_STATUS, 14)
        }, matchWidth().apply { topMargin = dp(10); bottomMargin = dp(6) })
        root.addView(TextView(this).apply {
            val awaiting = species.count(CollectionSpecies::awaitingSpeciesIdentification)
            text = buildString {
                append("Subspecies are grouped under their parent species")
                if (awaiting > 0) append(" · $awaiting awaiting species identification")
                append(". XP is shown only for Wildlife-rewarded sightings.")
            }
            textSize = 13f
            setTextColor(COLOR_MUTED)
            setPadding(dp(2), dp(3), dp(2), dp(10))
        }, matchWidth())

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (species.isEmpty()) {
            list.addView(message("Your collection is empty. Return to the main screen and sync it."), matchWidth())
        } else {
            species.forEach { entry -> list.addView(speciesCard(entry), matchWidth().apply { bottomMargin = dp(8) }) }
        }
        root.addView(
            ScrollView(this).apply { addView(list, matchWidth()) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(root)
    }

    private fun speciesCard(entry: CollectionSpecies) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = rounded(Color.WHITE, 16, COLOR_BORDER)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://www.inaturalist.org/observations/${entry.latestObservationUuid}",
            )))
        }
        addView(TextView(this@CollectionActivity).apply {
            text = entry.label
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_FOREST)
        }, matchWidth())
        addView(TextView(this@CollectionActivity).apply {
            val sightings = if (entry.observationCount == 1) "1 observation" else "${entry.observationCount} observations"
            text = if (entry.awaitingSpeciesIdentification) {
                "$sightings · Awaiting species identification"
            } else {
                "$sightings · ${friendlyQuality(entry.bestQualityGrade)}"
            }
            textSize = 13f
            setTextColor(COLOR_MUTED)
            setPadding(0, dp(3), 0, dp(2))
        }, matchWidth())
        addView(TextView(this@CollectionActivity).apply {
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.latestObservedAtMs))
            text = buildString {
                append("Last seen $date")
                if (entry.rewardedObservationCount > 0) {
                    append(" · ${entry.rewardedObservationCount} Wildlife-rewarded")
                }
            }
            textSize = 12f
            setTextColor(if (entry.rewardedObservationCount > 0) COLOR_FOREST else COLOR_MUTED)
        }, matchWidth())
    }

    private fun friendlyQuality(value: String): String = when (value) {
        "research" -> "Research grade"
        "needs_id" -> "Needs identification"
        "casual" -> "Casual"
        else -> "Unrated"
    }

    private fun message(value: String) = TextView(this).apply {
        text = value
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(COLOR_MUTED)
        setPadding(dp(18), dp(28), dp(18), dp(28))
        background = rounded(Color.WHITE, 16, COLOR_BORDER)
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        strokeColor?.let { setStroke(dp(1), it) }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private val COLOR_BACKGROUND = Color.rgb(244, 248, 244)
        private val COLOR_FOREST = Color.rgb(26, 86, 48)
        private val COLOR_MUTED = Color.rgb(88, 103, 92)
        private val COLOR_BORDER = Color.rgb(214, 225, 216)
        private val COLOR_STATUS = Color.rgb(230, 242, 233)
    }
}
