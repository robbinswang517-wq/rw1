package com.andrerinas.openheadunit.web

import org.junit.Assert.*
import org.junit.Test

class BrowserSessionTest {
    private var time = 0L
    private val events = mutableListOf<String>()
    private fun session() = BrowserSession({ time }) { action, _, _ -> events.add(action) }

    @Test fun revokedTokenCannotSendOrCancelNewBrowserInput() {
        val s = session(); val old = s.pair(s.code)!!
        s.disconnect(); val current = s.pair(s.code)!!
        assertFalse(s.authorizedTouch(old, "down", .5, .5))
        assertTrue(s.authorizedTouch(current, "down", .5, .5))
        assertFalse(s.authorizedDisconnect(old))
        assertTrue(s.authenticate(current))
        assertTrue(s.authorizedDisconnect(current))
        assertEquals(listOf("down", "cancel"), events)
    }

    @Test fun pairsOneBrowserAndExpiresOwnership() {
        val s = session()
        assertNull(s.pair("wrong"))
        val token = s.pair(s.code)!!
        assertTrue(s.authenticate(token))
        assertFalse(s.authenticate("wrong"))
        assertNull(s.pair(s.code))
        time = 15_001
        assertFalse(s.authenticate(token))
        assertNotNull(s.pair(s.code))
    }
    @Test fun throttlesEvenCorrectCodeAfterFiveAttempts() {
        val s = session()
        repeat(5) { assertNull(s.pair("wrong")) }
        assertNull(s.pair(s.code))
        time = 30_000
        assertNotNull(s.pair(s.code))
    }
    @Test fun releasesFingerAfterBrowserDisappears() {
        val s = session()
        s.pair(s.code)
        assertTrue(s.touch("down", .1, .9))
        time = 1501
        s.expire()
        assertEquals(listOf("down", "cancel"), events)
        s.expire()
        assertEquals(2, events.size)
        assertFalse(s.touch("move", .5, .5))
    }
    @Test fun rejectsNonFiniteOutOfRangeAndInvalidSequence() {
        val s = session()
        assertFalse(s.touch("move", .5, .5))
        assertFalse(s.touch("down", Double.NaN, .5))
        assertFalse(s.touch("down", .5, Double.POSITIVE_INFINITY))
        assertFalse(s.touch("down", -.1, .5))
        assertFalse(s.touch("down", .5, 1.1))
        assertTrue(s.touch("down", .0, 1.0))
        assertFalse(s.touch("down", .5, .5))
        assertFalse(s.touch("hack", .5, .5))
        assertTrue(s.touch("move", .5, .5))
        assertTrue(s.touch("up", .5, .5))
        assertEquals(listOf("down", "move", "up"), events)
    }
    @Test fun disconnectInvalidatesCredentialsAndReleasesTouch() {
        val s = session(); val token = s.pair(s.code)!!
        s.touch("down", .5, .5)
        s.disconnect()
        assertEquals(listOf("down", "cancel"), events)
        assertFalse(s.authenticate(token))
        assertNotEquals(token, s.pair(s.code))
    }
    @Test fun framePollingKeepsSessionButNotStuckFingerAlive() {
        val s = session(); val token = s.pair(s.code)!!
        s.touch("down", .5, .5)
        time = 2000
        assertTrue(s.authenticate(token))
        assertEquals(listOf("down", "cancel"), events)
        time = 16_000
        assertTrue(s.authenticate(token))
    }
}
