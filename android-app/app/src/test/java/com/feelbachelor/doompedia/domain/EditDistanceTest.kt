package com.feelbachelor.doompedia.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditDistanceTest {
    @Test
    fun editDistanceAtMostOne_acceptsZeroOrOneEdit() {
        assertTrue(editDistanceAtMostOne("ada", "ada"))
        assertTrue(editDistanceAtMostOne("grace", "grsce"))
        assertTrue(editDistanceAtMostOne("rome", "rom"))
    }

    @Test
    fun editDistanceAtMostOne_rejectsTwoEdits() {
        assertFalse(editDistanceAtMostOne("science", "silence"))
    }
}
