package com.wildlife.feasibility.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeciesCardMediaTest {
    @Test
    fun `card media keeps personal then stored reference then remote repair order`() {
        val model = SpeciesCardModel(
            key = "taxon:1",
            label = "Robin",
            supportingText = "Erithacus rubecula",
            photoUrl = "https://personal.test/photo.jpg",
            photoKind = SpeciesCardPhotoKind.PERSONAL,
            photoFallbackUrl = "file:///stored/reference.jpg",
            photoFallbackKind = SpeciesCardPhotoKind.REFERENCE,
            photoFallbackAttribution = "Author / CC BY",
            additionalPhotoFallbacks = listOf(
                SpeciesCardPhotoCandidate(
                    "https://remote.test/reference.jpg",
                    SpeciesCardPhotoKind.REFERENCE,
                    "Author / CC BY",
                ),
            ),
        )

        assertEquals(
            listOf(
                "https://personal.test/photo.jpg",
                "file:///stored/reference.jpg",
                "https://remote.test/reference.jpg",
            ),
            model.orderedPhotoCandidates().map(SpeciesCardPhotoCandidate::url),
        )
    }

    @Test
    fun `duplicate repair URLs are not requested twice`() {
        val model = SpeciesCardModel(
            key = "taxon:1",
            label = "Robin",
            supportingText = "Erithacus rubecula",
            photoUrl = "https://example.test/photo.jpg",
            photoFallbackUrl = "https://example.test/photo.jpg",
        )

        assertEquals(1, model.orderedPhotoCandidates().size)
    }
}
