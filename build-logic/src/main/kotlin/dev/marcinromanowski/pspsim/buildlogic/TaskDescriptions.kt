package dev.marcinromanowski.pspsim.buildlogic

data class ADescription(val task: String, val line: Int, val text: String) {

    val words: Int = WORD.findAll(text).count()

    val sentences: Int = SENTENCE.split(text.trim()).count { it.isNotBlank() }

    companion object {
        private val WORD = Regex("[A-Za-z][A-Za-z']*")
        private val SENTENCE = Regex("(?<=[.!?])\\s+")
    }
}

data class ALongerEntry(val words: Int, val why: String)

object TaskDescriptions {

    const val MOST_WORDS_A_LISTING_ENTRY_READS = 45

    const val MOST_SENTENCES_A_LISTING_ENTRY_READS = 2

    const val FEWEST_WORDS_THAT_SAY_WHAT_A_TASK_DOES = 6

    const val UNNAMED = "a task this scan could not name"

    val THE_TASKS_A_LONGER_ENTRY_IS_ALLOWED: Map<String, ALongerEntry> = mapOf()

    fun anAllowanceThatNamesNoReason(allowances: Map<String, ALongerEntry>): List<String> =
        allowances.filterValues { it.why.isBlank() }.keys.toList()

    private val ASSIGNED = Regex("""\bdescription\s*=(?!=)""")

    private val SET_ANOTHER_WAY = Regex("""\b(?:setDescription\s*\(|description\s*\.\s*set\s*\()""")

    private val REGISTERED =
        Regex(
            """register(?:<[^>]*>)?\("([^"]+)"\)|named(?:<[^>]*>)?\("([^"]+)"\)""" +
                """|tasks\.(\w+)\s*\{"""
        )

    private val NOT_A_TASK_NAME =
        setOf("register", "named", "withType", "matching", "create", "all", "configureEach")

    fun inSource(source: String): List<ADescription> =
        ASSIGNED.findAll(source)
            .mapNotNull { assignment ->
                theLiteralAfter(source, assignment.range.last + 1)?.let { text ->
                    ADescription(
                        theTaskAbove(source, assignment.range.first),
                        lineOf(source, assignment.range.first),
                        text,
                    )
                }
            }
            .toList()

    fun howManyDescriptionsAreSet(source: String): Int =
        ASSIGNED.findAll(source).count() + SET_ANOTHER_WAY.findAll(source).count()

    fun howManyWereReadWhole(source: String): Int =
        ASSIGNED.findAll(source).count { assignment ->
            theWholeLiteralFollows(source, assignment.range.last + 1)
        }

    fun theMostWordsAllowed(
        task: String,
        allowances: Map<String, ALongerEntry> = THE_TASKS_A_LONGER_ENTRY_IS_ALLOWED,
    ): Int = allowances[task]?.words ?: MOST_WORDS_A_LISTING_ENTRY_READS

    fun tooLongToRead(
        described: ADescription,
        allowances: Map<String, ALongerEntry> = THE_TASKS_A_LONGER_ENTRY_IS_ALLOWED,
    ): Boolean =
        described.words > theMostWordsAllowed(described.task, allowances) ||
            described.sentences > MOST_SENTENCES_A_LISTING_ENTRY_READS

    fun saysTooLittle(described: ADescription): Boolean =
        described.words < FEWEST_WORDS_THAT_SAY_WHAT_A_TASK_DOES

    fun anAllowanceNoDescriptionNeeds(
        described: List<ADescription>,
        allowances: Map<String, ALongerEntry> = THE_TASKS_A_LONGER_ENTRY_IS_ALLOWED,
    ): List<String> =
        allowances.keys.filter { allowed ->
            described.none {
                it.task == allowed && it.words > MOST_WORDS_A_LISTING_ENTRY_READS
            }
        }

    fun lineOf(source: String, offset: Int): Int = 1 + source.take(offset).count { it == '\n' }

    private fun theTaskAbove(source: String, offset: Int): String =
        REGISTERED.findAll(source.take(offset))
            .mapNotNull { theNameIn(it.groupValues) }
            .lastOrNull() ?: UNNAMED

    private fun theNameIn(groups: List<String>): String? {
        val named = groups[1].ifEmpty { groups[2] }
        if (named.isNotEmpty()) {
            return named
        }
        val accessed = groups[3]
        return if (accessed.isEmpty() || accessed in NOT_A_TASK_NAME) null else accessed
    }

    private fun theWholeLiteralFollows(source: String, from: Int): Boolean {
        var at = from
        var expectingALiteral = true
        var read = false
        while (at < source.length) {
            val here = source[at]
            if (here.isWhitespace()) {
                at++
                continue
            }
            if (expectingALiteral) {
                val literal = theQuotedAt(source, at) ?: return false
                read = true
                at = literal.second
                expectingALiteral = false
                continue
            }
            if (here == '+') {
                at++
                expectingALiteral = true
                continue
            }
            return read
        }
        return read
    }

    private fun theLiteralAfter(source: String, from: Int): String? {
        val joined = StringBuilder()
        var at = from
        var expectingALiteral = true
        while (at < source.length) {
            val here = source[at]
            if (here.isWhitespace()) {
                at++
                continue
            }
            if (expectingALiteral) {
                val literal = theQuotedAt(source, at) ?: return joined.ifEmptyNull()
                joined.append(literal.first)
                at = literal.second
                expectingALiteral = false
                continue
            }
            if (here == '+') {
                at++
                expectingALiteral = true
                continue
            }
            return joined.ifEmptyNull()
        }
        return joined.ifEmptyNull()
    }

    private fun StringBuilder.ifEmptyNull(): String? = if (isEmpty()) null else toString()

    private fun theQuotedAt(source: String, at: Int): Pair<String, Int>? {
        if (source.startsWith(RAW, at)) {
            val closes = source.indexOf(RAW, at + RAW.length)
            if (closes < 0) {
                return null
            }
            return source.substring(at + RAW.length, closes) to closes + RAW.length
        }
        if (source[at] != '"') {
            return null
        }
        val text = StringBuilder()
        var index = at + 1
        while (index < source.length) {
            val here = source[index]
            if (here == '\\' && index + 1 < source.length) {
                text.append(source[index + 1])
                index += 2
                continue
            }
            if (here == '"') {
                return text.toString() to index + 1
            }
            if (here == '\n') {
                return null
            }
            text.append(here)
            index++
        }
        return null
    }

    private const val RAW = "\"\"\""
}
