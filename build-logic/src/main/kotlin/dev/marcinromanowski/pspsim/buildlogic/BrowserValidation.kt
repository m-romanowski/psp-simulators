package dev.marcinromanowski.pspsim.buildlogic

import java.io.File

object BrowserValidation {

    const val THE_DIRECTORY_EVERY_TEMPLATE_OF_THIS_UI_STANDS_UNDER = "templates"

    fun theTemplatesAGlobOneDirectoryDeepReads(scanned: List<File>): Int = scanned.count {
        it.parentFile?.name == THE_DIRECTORY_EVERY_TEMPLATE_OF_THIS_UI_STANDS_UNDER
    }

    data class Finding(val control: String, val offset: Int, val constraint: String)

    data class Judgement(val expression: String, val offset: Int)

    val constraintsABrowserEnforces =
        listOf("min", "max", "step", "pattern", "required", "novalidate")

    val theWaysASourceAsksWhetherAProviderPublishesAValue =
        listOf(".choice(", ".theValuesItAccepts(")

    val theWayASourceReadsTheValuesAProviderPublishes = listOf(".choices()")

    fun theOnesIn(source: String, expressions: List<String>): List<Judgement> =
        expressions
            .flatMap { expression ->
                Regex(Regex.escape(expression)).findAll(source).map {
                    Judgement(expression, it.range.first)
                }
            }
            .sortedBy { it.offset }

    private val controls =
        Regex("<(input|select|textarea|form)\\b[^>]*>", RegexOption.DOT_MATCHES_ALL)

    private val quotedValues = Regex("\"[^\"]*\"", RegexOption.DOT_MATCHES_ALL)

    val theTypesABrowserJudgesOrRewritesWhatWasTypedInto =
        listOf(
            "number",
            "range",
            "date",
            "datetime-local",
            "month",
            "week",
            "time",
            "email",
            "url",
            "color",
        )

    private val aJudgedBox =
        Regex(
            "type\\s*=\\s*\"\\s*(" +
                theTypesABrowserJudgesOrRewritesWhatWasTypedInto.joinToString("|") {
                    Regex.escape(it)
                } +
                ")\\s*\"",
            RegexOption.IGNORE_CASE,
        )

    fun controlsIn(template: String): List<String> =
        controls.findAll(template).map { it.value }.toList()

    fun findingsIn(template: String): List<Finding> =
        controls
            .findAll(template)
            .flatMap { control ->
                theConstraintsOn(control.value).map {
                    Finding(control.value, control.range.first, it)
                }
            }
            .toList()

    private fun theConstraintsOn(control: String): List<String> {
        val names = quotedValues.replace(control) { "\"\"" }
        val found = mutableListOf<String>()
        aJudgedBox.findAll(control).forEach {
            found.add("type=\"" + it.groupValues[1].trim().lowercase() + "\"")
        }
        constraintsABrowserEnforces.forEach { constraint ->
            if (Regex("(?<=[\\s\"])$constraint(\\s*=|\\s|/|>)").containsMatchIn(names)) {
                found.add(constraint)
            }
        }
        return found
    }
}
