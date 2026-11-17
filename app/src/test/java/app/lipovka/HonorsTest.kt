package app.lipovka

import app.lipovka.domain.Honors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The run of days on the round: kept through a day still under way, broken by a missed one, the best kept. */
class HonorsTest {
    @Test fun runEndingToday() {
        val s = Honors.streak(setOf(8L, 9L, 10L), today = 10)
        assertEquals(3, s.now); assertEquals(3, s.best); assertTrue(s.today)
    }

    @Test fun todayNotYetDoneKeepsYesterdaysRun() {
        val s = Honors.streak(setOf(8L, 9L), today = 10)
        assertEquals(2, s.now); assertFalse(s.today)
    }

    @Test fun missedDayStartsOverButBestStays() {
        val s = Honors.streak(setOf(1L, 2L, 3L, 4L, 5L, 8L, 9L), today = 10)
        assertEquals(2, s.now); assertEquals(5, s.best)
        assertEquals(0, Honors.streak(setOf(1L, 2L), today = 10).now)
        assertEquals(0, Honors.streak(emptySet(), today = 10).best)
    }
}
