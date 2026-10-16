package dev.marcinromanowski.pspsim.buildlogic

enum class CommentDialect {
    JAVA,
    KOTLIN,
    JAVASCRIPT,
    MARKUP,
    SQL;

    companion object {

        fun of(fileName: String): CommentDialect =
            when {
                fileName.endsWith(".java") -> JAVA
                fileName.endsWith(".kts") || fileName.endsWith(".kt") -> KOTLIN
                fileName.endsWith(".js") || fileName.endsWith(".mjs") -> JAVASCRIPT
                fileName.endsWith(".html") -> MARKUP
                else -> SQL
            }
    }
}

object CommentScanner {

    private val wordsARegexMayFollow =
        setOf(
            "return",
            "typeof",
            "case",
            "in",
            "of",
            "delete",
            "void",
            "throw",
            "new",
            "instanceof",
            "do",
            "else",
            "yield",
            "await",
        )

    private const val punctuationARegexMayFollow = "(,=:[!&|?{};+-*%<>~^"

    fun commentsIn(source: String, dialect: CommentDialect): List<Pair<Int, String>> =
        when (dialect) {
            CommentDialect.MARKUP -> markupCommentsAt(source)
            CommentDialect.JAVASCRIPT -> scriptCommentsAt(source)
            else -> commentsAt(source, dialect)
        }

    fun lineOf(source: String, offset: Int): Int = 1 + source.take(offset).count { it == '\n' }

    private fun endOfQuoted(
        source: String,
        from: Int,
        quote: Char,
        escaped: Boolean,
        doubling: Boolean,
    ): Int {
        var index = from + 1
        while (index < source.length) {
            val current = source[index]
            if (escaped && current == '\\') {
                index += 2
                continue
            }
            if (current == quote) {
                if (doubling && index + 1 < source.length && source[index + 1] == quote) {
                    index += 2
                    continue
                }
                return index + 1
            }
            if (!doubling && current == '\n') return index
            index++
        }
        return source.length
    }

    private fun commentsAt(source: String, dialect: CommentDialect): List<Pair<Int, String>> {
        val sql = dialect == CommentDialect.SQL
        val java = dialect == CommentDialect.JAVA
        val found = mutableListOf<Pair<Int, String>>()
        val length = source.length
        var at = 0
        while (at < length) {
            val current = source[at]

            if (!sql && source.startsWith("\"\"\"", at)) {
                var index = at + 3
                while (index < length) {
                    if (java && source[index] == '\\') {
                        index += 2
                        continue
                    }
                    if (source.startsWith("\"\"\"", index)) {
                        index += 3
                        break
                    }
                    index++
                }
                at = minOf(index, length)
                continue
            }

            if (current == '"') {
                at = endOfQuoted(source, at, '"', !sql, false)
                continue
            }

            if (current == '\'') {
                at = endOfQuoted(source, at, '\'', !sql, sql)
                continue
            }

            if (sql && current == '$') {
                var index = at + 1
                while (
                    index < length && (source[index].isLetterOrDigit() || source[index] == '_')
                ) {
                    index++
                }
                if (index < length && source[index] == '$') {
                    val tag = source.substring(at, index + 1)
                    val close = source.indexOf(tag, index + 1)
                    at = if (close < 0) length else close + tag.length
                    continue
                }
            }

            if (source.startsWith(if (sql) "--" else "//", at)) {
                found += at to "line comment"
                val newline = source.indexOf('\n', at)
                at = if (newline < 0) length else newline
                continue
            }

            if (source.startsWith("/*", at)) {
                val javadoc = java && source.startsWith("/**", at) && !source.startsWith("/**/", at)
                found += at to if (javadoc) "javadoc comment" else "block comment"
                var index = at + 2
                if (java) {
                    val close = source.indexOf("*/", index)
                    index = if (close < 0) length else close + 2
                } else {
                    var depth = 1
                    while (index < length && depth > 0) {
                        if (source.startsWith("/*", index)) {
                            depth++
                            index += 2
                        } else if (source.startsWith("*/", index)) {
                            depth--
                            index += 2
                        } else {
                            index++
                        }
                    }
                }
                at = minOf(index, length)
                continue
            }

            at++
        }
        return found
    }

    private val wordsWhoseParenthesesARegexMayFollow = setOf("if", "while", "for", "with")

    private fun scriptCommentsAt(source: String): List<Pair<Int, String>> {
        val found = mutableListOf<Pair<Int, String>>()
        scanScript(source, 0, found, false)
        return found
    }

    private fun scanScript(
        source: String,
        from: Int,
        found: MutableList<Pair<Int, String>>,
        untilTheBraceThatClosesIt: Boolean,
    ): Int {
        val length = source.length
        var depth = 0
        var at = from
        while (at < length) {
            val current = source[at]
            when {
                current == '"' || current == '\'' -> {
                    at = endOfQuoted(source, at, current, true, false)
                    continue
                }
                current == '`' -> {
                    at = endOfTemplate(source, at, found)
                    continue
                }
                source.startsWith("//", at) -> {
                    found += at to "line comment"
                    val newline = source.indexOf('\n', at)
                    at = if (newline < 0) length else newline
                    continue
                }
                source.startsWith("/*", at) -> {
                    found += at to "block comment"
                    val close = source.indexOf("*/", at + 2)
                    at = if (close < 0) length else close + 2
                    continue
                }
                current == '/' && aRegexMayStartAfter(source, at) -> {
                    at = endOfRegex(source, at)
                    continue
                }
                untilTheBraceThatClosesIt && current == '{' -> depth++
                untilTheBraceThatClosesIt && current == '}' -> {
                    if (depth == 0) return at + 1
                    depth--
                }
            }
            at++
        }
        return length
    }

    private fun endOfTemplate(
        source: String,
        from: Int,
        found: MutableList<Pair<Int, String>>,
    ): Int {
        var index = from + 1
        while (index < source.length) {
            when {
                source[index] == '\\' -> index += 2
                source[index] == '`' -> return index + 1
                source.startsWith("${'$'}{", index) ->
                    index = scanScript(source, index + 2, found, true)
                else -> index++
            }
        }
        return source.length
    }

    private fun aRegexMayStartAfter(source: String, slash: Int): Boolean {
        var index = slash - 1
        while (index >= 0 && source[index].isWhitespace()) index--
        if (index < 0) return true
        val before = source[index]
        if ((before == '+' || before == '-') && index > 0 && source[index - 1] == before) {
            return false
        }
        if (before == ')')
            return theParenthesesFollow(source, index, wordsWhoseParenthesesARegexMayFollow)
        if (punctuationARegexMayFollow.indexOf(before) >= 0) return true
        if (!before.isLetterOrDigit() && before != '_' && before != '$') return false
        return theWordEndingAt(source, index) in wordsARegexMayFollow
    }

    private fun theParenthesesFollow(source: String, closing: Int, words: Set<String>): Boolean {
        var depth = 0
        var index = closing
        while (index >= 0) {
            when (source[index]) {
                ')' -> depth++
                '(' -> {
                    depth--
                    if (depth == 0) break
                }
            }
            index--
        }
        if (index < 0) return false
        var before = index - 1
        while (before >= 0 && source[before].isWhitespace()) before--
        if (before < 0 || !source[before].isLetter()) return false
        return theWordEndingAt(source, before) in words
    }

    private fun theWordEndingAt(source: String, end: Int): String {
        var start = end
        while (start > 0 && (source[start - 1].isLetterOrDigit() || source[start - 1] == '_')) {
            start--
        }
        return source.substring(start, end + 1)
    }

    private fun endOfRegex(source: String, from: Int): Int {
        var index = from + 1
        var inAClass = false
        while (index < source.length) {
            val current = source[index]
            when {
                current == '\\' -> {
                    index += 2
                    continue
                }
                current == '\n' -> return index
                current == '[' -> inAClass = true
                current == ']' -> inAClass = false
                current == '/' && !inAClass -> return index + 1
            }
            index++
        }
        return source.length
    }

    private fun markupCommentsAt(source: String): List<Pair<Int, String>> {
        val found = mutableListOf<Pair<Int, String>>()
        var at = source.indexOf("<!--")
        while (at >= 0) {
            found += at to "markup comment"
            at = source.indexOf("<!--", at + 4)
        }
        return found
    }
}
