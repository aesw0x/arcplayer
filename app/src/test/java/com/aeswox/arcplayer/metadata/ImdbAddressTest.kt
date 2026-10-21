package com.aeswox.arcplayer.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two IMDb addresses, written against the replies the typeahead endpoint actually gives: a
 * scan-sized poster on its own CDN, a name with punctuation in it, and a bucket character that is not
 * always the first character of the title.
 */
class ImdbAddressTest {

    private val scan = "https://m.media-amazon.com/images/M/MV5BMDVkOGEzZDgtYWU4Yi00MDA3" +
        "-LWE4YmQtYjQxNDgwNDYxNGU4XkEyXkFqcGc@._V1_.jpg"

    @Test
    fun aNameIsBucketedByItsFirstLetterAndItsSpaceIsKept() {
        assertEquals(
            "https://v3.sg.media-imdb.com/suggestion/m/Manjummel%20Boys.json",
            imdbTypeaheadUrl("Manjummel Boys")
        )
    }

    @Test
    fun aLeadingQuoteStillBucketsUnderTheLetterBehindIt() {
        assertEquals("https://v3.sg.media-imdb.com/suggestion/a/%27Allo%20%27Allo.json",
            imdbTypeaheadUrl("'Allo 'Allo"))
    }

    @Test
    fun anAmpersandInANameCannotOpenASecondParameter() {
        assertEquals(
            "https://v3.sg.media-imdb.com/suggestion/l/Lilo%20%26%20Stitch.json",
            imdbTypeaheadUrl("Lilo & Stitch")
        )
    }

    @Test
    fun aNameWithNothingSpellableInItIsNotAskedAtAll() {
        assertNull(imdbTypeaheadUrl("   "))
        assertNull(imdbTypeaheadUrl("!!!"))
    }

    @Test
    fun theScanIsTradedForTheRenditionAmazonResizesOnTheAddress() {
        assertEquals(
            scan.replace("@._V1_.jpg", "@._V1_SX500_.jpg"),
            imdbPoster(scan)
        )
    }

    @Test
    fun onlyImdbsOwnCdnIsFetched() {
        assertNull(imdbPoster("https://example.com/poster.jpg"))
        assertNull(imdbPoster("http://m.media-amazon.com/images/M/x@._V1_.jpg"))
        assertNull(imdbPoster("null"))
        assertNull(imdbPoster(null))
    }

    @Test
    fun anAddressWithoutTheVersionMarkerIsUsedAsSent() {
        val bare = "https://m.media-amazon.com/images/M/MV5Babc.jpg"
        assertEquals(bare, imdbPoster(bare))
    }
}
