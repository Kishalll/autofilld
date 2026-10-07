package dev.autofilld

import org.junit.Assert.assertTrue
import org.junit.Test

class SmokeTest {

    @Test
    fun mainActivityLivesInExpectedPackage() {
        assertTrue(MainActivity::class.java.`package`!!.name == "dev.autofilld")
    }
}
