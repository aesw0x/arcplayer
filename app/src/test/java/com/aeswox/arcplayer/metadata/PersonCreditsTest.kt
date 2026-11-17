package com.aeswox.arcplayer.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonCreditsTest {

    private fun credit(
        id: Int,
        title: String = "Work $id",
        series: Boolean = false,
        votes: Int = 500,
        popularity: Double = 10.0,
        character: String = "Someone",
        poster: String? = "https://image.tmdb.org/t/p/w342/$id.jpg"
    ) = PersonCredit(id, series, title, 2020, poster, character, popularity, votes)

    @Test
    fun anUnvotedWorkIsNotAFilmographyEntry() {
        val ranked = PersonCredits.rank(listOf(credit(1, votes = PersonCredits.MIN_VOTES - 1), credit(2)))
        assertEquals(listOf(2), ranked.map { it.id })
    }

    @Test
    fun theVoteFloorItselfIsKept() {
        val ranked = PersonCredits.rank(listOf(credit(1, votes = PersonCredits.MIN_VOTES)))
        assertEquals(listOf(1), ranked.map { it.id })
    }

    @Test
    fun talkShowSlotsAsSelfAreDropped() {
        val ranked = PersonCredits.rank(
            listOf(
                credit(1, character = "Self"),
                credit(2, character = "himself"),
                credit(3, character = " Herself "),
                credit(4, character = "Detective")
            )
        )
        assertEquals(listOf(4), ranked.map { it.id })
    }

    @Test
    fun aWorkWithoutAPosterIsDropped() {
        val ranked = PersonCredits.rank(listOf(credit(1, poster = null), credit(2)))
        assertEquals(listOf(2), ranked.map { it.id })
    }

    @Test
    fun votesWeightPopularitySoATrendingTrailerDoesNotOutrankAKnownFilm() {
        val trending = credit(1, popularity = 60.0, votes = 12)
        val known = credit(2, popularity = 20.0, votes = 8000)
        val ranked = PersonCredits.rank(listOf(trending, known))
        assertEquals(listOf(2, 1), ranked.map { it.id })
    }

    @Test
    fun theSameWorkTwiceKeepsItsStrongestRow() {
        val ranked = PersonCredits.rank(
            listOf(credit(1, popularity = 5.0, character = "Waiter"), credit(1, popularity = 30.0, character = "Lead"))
        )
        assertEquals(1, ranked.size)
        assertEquals("Lead", ranked.single().character)
    }

    @Test
    fun aFilmAndAShowSharingANumberAreDifferentWorks() {
        val ranked = PersonCredits.rank(listOf(credit(7, series = false), credit(7, series = true)))
        assertEquals(2, ranked.size)
        assertTrue(ranked.any { it.isSeries } && ranked.any { !it.isSeries })
    }

    @Test
    fun knownForIsTheTopThreeRankedTitles() {
        val ranked = PersonCredits.knownFor(
            listOf(
                credit(1, title = "A", popularity = 1.0),
                credit(2, title = "B", popularity = 4.0),
                credit(3, title = "C", popularity = 3.0),
                credit(4, title = "D", popularity = 2.0)
            )
        )
        assertEquals(listOf("B", "C", "D"), ranked)
    }

    @Test
    fun anEmptyListRanksToNothing() {
        assertEquals(emptyList<PersonCredit>(), PersonCredits.rank(emptyList()))
        assertEquals(emptyList<String>(), PersonCredits.knownFor(emptyList()))
    }
}