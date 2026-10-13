package dev.marcinromanowski.pspsim.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommentScannerTest {

    private fun kinds(source: String, dialect: CommentDialect): List<String> =
        CommentScanner.commentsIn(source, dialect).map { it.second }

    @Test
    fun `a dialect is read from the file name, and a kotlin source is kotlin`() {
        assertEquals(CommentDialect.JAVA, CommentDialect.of("Thing.java"))
        assertEquals(CommentDialect.KOTLIN, CommentDialect.of("build.gradle.kts"))
        assertEquals(CommentDialect.KOTLIN, CommentDialect.of("PitInvocation.kt"))
        assertEquals(CommentDialect.MARKUP, CommentDialect.of("payments.html"))
        assertEquals(CommentDialect.SQL, CommentDialect.of("V1__thing.sql"))
    }

    @Test
    fun `a line comment, a block comment and a javadoc are told apart in java`() {
        assertEquals(
            listOf("line comment", "block comment", "javadoc comment"),
            kinds("int a = 1; // one\n/* two */\n/** three */\n", CommentDialect.JAVA),
        )
    }

    @Test
    fun `a slash-star-slash is a block comment and not a javadoc`() {
        assertEquals(listOf("block comment"), kinds("/**/\n", CommentDialect.JAVA))
    }

    @Test
    fun `two slashes inside a string literal are not a comment`() {
        assertEquals(
            emptyList<String>(),
            kinds("""String url = "https://example.test/x";""", CommentDialect.JAVA),
        )
    }

    @Test
    fun `two slashes inside a text block are not a comment`() {
        assertEquals(
            emptyList<String>(),
            kinds("String s = \"\"\"\n  // not a comment\n  \"\"\";\n", CommentDialect.JAVA),
        )
    }

    @Test
    fun `a kotlin block comment nests, so the code after it is still scanned`() {
        assertEquals(
            listOf("block comment", "line comment"),
            kinds("/* outer /* inner */ still */\nval a = 1 // after\n", CommentDialect.KOTLIN),
        )
    }

    @Test
    fun `sql comments open with two dashes and a slash-star comment is a block`() {
        assertEquals(
            listOf("line comment", "block comment"),
            kinds("select 1; -- one\n/* two */\n", CommentDialect.SQL),
        )
    }

    @Test
    fun `two slashes in sql are not a comment and a doubled quote closes nothing`() {
        assertEquals(
            emptyList<String>(),
            kinds("select 'it''s // fine' from t;\n", CommentDialect.SQL),
        )
    }

    @Test
    fun `a dollar-quoted sql body hides what would otherwise be a comment`() {
        assertEquals(
            emptyList<String>(),
            kinds("do \$body\$ -- hidden\n\$body\$;\n", CommentDialect.SQL),
        )
    }

    @Test
    fun `a markup comment is found wherever its four characters stand`() {
        assertEquals(
            listOf("markup comment", "markup comment"),
            kinds("<p>a</p><!-- one --><div><!-- two --></div>", CommentDialect.MARKUP),
        )
    }

    @Test
    fun `the line of an offence counts the newlines before it`() {
        val source = "a\nb\n// here\n"
        val (offset, kind) = CommentScanner.commentsIn(source, CommentDialect.KOTLIN).single()
        assertEquals("line comment", kind)
        assertEquals(3, CommentScanner.lineOf(source, offset))
    }
}
