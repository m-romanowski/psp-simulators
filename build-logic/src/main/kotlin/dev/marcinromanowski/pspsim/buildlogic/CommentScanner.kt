package dev.marcinromanowski.pspsim.buildlogic

enum class CommentDialect {
    JAVA,
    KOTLIN,
    MARKUP,
    SQL;

    companion object {

        fun of(fileName: String): CommentDialect =
            when {
                fileName.endsWith(".java") -> JAVA
                fileName.endsWith(".kts") || fileName.endsWith(".kt") -> KOTLIN
                fileName.endsWith(".html") -> MARKUP
                else -> SQL
            }
    }
}

object CommentScanner {

    fun commentsIn(source: String, dialect: CommentDialect): List<Pair<Int, String>> =
        if (dialect == CommentDialect.MARKUP) markupCommentsAt(source)
        else commentsAt(source, dialect)

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
