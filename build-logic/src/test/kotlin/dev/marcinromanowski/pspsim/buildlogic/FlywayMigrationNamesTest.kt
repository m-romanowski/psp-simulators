package dev.marcinromanowski.pspsim.buildlogic

import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlywayMigrationNamesTest {

    private fun components(rawVersion: String): List<BigInteger> =
        FlywayMigrationNames.componentsFlywayComparesBy(rawVersion)
            ?: throw AssertionError(
                "Flyway reads " + rawVersion + " as a version, and this did not"
            )

    private fun versionOf(fileName: String): FlywayMigrationName.Versioned {
        val read = FlywayMigrationNames.read(fileName)
        assertTrue(
            read is FlywayMigrationName.Versioned,
            fileName + " is a versioned migration Flyway applies",
        )
        return read as FlywayMigrationName.Versioned
    }

    private fun refusalFor(fileName: String): String {
        val read = FlywayMigrationNames.read(fileName)
        assertTrue(
            read is FlywayMigrationName.NotAppliedByFlyway,
            fileName + " is a file Flyway will not apply",
        )
        return (read as FlywayMigrationName.NotAppliedByFlyway).reason
    }

    @Test
    fun `a leading zero counts for nothing, so V1 and V01 are one version`() {
        assertEquals(components("1"), components("01"))
        assertEquals(components("1"), components("001"))
        assertEquals(versionOf("V1__a.sql").components, versionOf("V01__b.sql").components)
    }

    @Test
    fun `an underscore in a version is a component separator, so V1_1 and V1-1 are one version`() {
        assertEquals(components("1.1"), components("1_1"))
        assertEquals(components("2.1.3"), components("2_1_3"))
        assertEquals(components("1.2.3"), components("1.2_3"))
    }

    @Test
    fun `trailing zero components fall away, so V1 and V1-0 and V1-0-0 are one version`() {
        assertEquals(components("1"), components("1.0"))
        assertEquals(components("1"), components("1.0.0"))
        assertEquals(components("1"), components("1_0"))
        assertEquals(components("1.2.3"), components("1.2.3.0"))
    }

    @Test
    fun `a zero version survives the stripping of trailing zeroes`() {
        assertEquals(listOf(BigInteger.ZERO), components("0"))
        assertEquals(listOf(BigInteger.ZERO), components("0.0"))
        assertEquals(listOf(BigInteger.ZERO), components("00"))
    }

    @Test
    fun `a zero inside a version is not trailing and still counts`() {
        assertEquals(
            listOf(BigInteger.ONE, BigInteger.ZERO, BigInteger.TWO),
            components("1.0.2"),
        )
        assertNotEquals(components("1.0.2"), components("1"))
    }

    @Test
    fun `a component is a whole number, not a decimal, so V1-1 is not V1-10`() {
        assertNotEquals(components("1.1"), components("1.10"))
        assertNotEquals(components("1.2"), components("1.20"))
    }

    @Test
    fun `a version too large for a long is still compared component by component`() {
        assertEquals(components("20240101120000000000"), components("020240101120000000000"))
        assertNotEquals(components("20240101120000000000"), components("20240101120000000001"))
    }

    @Test
    fun `a version Flyway refuses to read is refused here rather than guessed at`() {
        assertNull(FlywayMigrationNames.componentsFlywayComparesBy("1a"))
        assertNull(FlywayMigrationNames.componentsFlywayComparesBy("1."))
        assertNull(FlywayMigrationNames.componentsFlywayComparesBy(".1"))
        assertNull(FlywayMigrationNames.componentsFlywayComparesBy("1..1"))
        assertNull(FlywayMigrationNames.componentsFlywayComparesBy(""))
    }

    @Test
    fun `a versioned migration carries the raw version it spells and the version Flyway reads`() {
        val read = versionOf("V01_2__grow_refund.sql")
        assertEquals("01_2", read.rawVersion)
        assertEquals(listOf(BigInteger.ONE, BigInteger.TWO), read.components)
        assertEquals("1.2", FlywayMigrationNames.asOneVersion(read.components))
    }

    @Test
    fun `only the first double underscore separates a version from a description`() {
        assertEquals("74", versionOf("V74__webhook__delivery.sql").rawVersion)
    }

    @Test
    fun `a repeatable migration is applied by Flyway and claims no version`() {
        assertTrue(
            FlywayMigrationNames.read("R__report_view.sql") is FlywayMigrationName.Repeatable
        )
    }

    @Test
    fun `a single underscore where Flyway wants two leaves a migration Flyway never applies`() {
        assertEquals(
            "it carries no __ between its version and its description",
            refusalFor("V75_webhook_retry.sql"),
        )
    }

    @Test
    fun `a name Flyway cannot read a version out of is reported with that version`() {
        assertEquals(
            "Flyway reads a version as numbers separated by . or _, and 75a is not one",
            refusalFor("V75a__webhook_retry.sql"),
        )
    }

    @Test
    fun `a versioned prefix with no version at all is a file Flyway never applies`() {
        assertEquals("it names no version between V and __", refusalFor("V__baseline.sql"))
    }

    @Test
    fun `a repeatable prefix that names a version is a file Flyway never applies`() {
        assertEquals(
            "a repeatable migration carries no version, and this one puts 3 before the separator",
            refusalFor("R3__report_view.sql"),
        )
    }

    @Test
    fun `a prefix this build does not configure is a file Flyway never applies`() {
        assertEquals(
            "it opens with neither V nor R, the two prefixes this build leaves at their defaults",
            refusalFor("U12__undo_scenario_rule.sql"),
        )
        assertTrue(refusalFor("v12__lowercase.sql").startsWith("it opens with neither V nor R"))
        assertTrue(refusalFor("afterMigrate__seed.sql").startsWith("it opens with neither V nor R"))
    }

    @Test
    fun `a file that is not sql at all is named as one Flyway never offers to a resolver`() {
        assertTrue(refusalFor("notes.md").startsWith("its name does not end in .sql"))
        assertTrue(refusalFor("V12__scenario_rule.sql.orig").startsWith("its name does not end"))
    }

    @Test
    fun `Flyway strips the sql suffix whatever its case, and so does this`() {
        assertEquals("12", versionOf("V12__scenario_rule.SQL").rawVersion)
    }
}
