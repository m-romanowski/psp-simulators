package dev.marcinromanowski.pspsim.buildlogic

data class Mutant(
    val status: String,
    val className: String,
    val method: String,
    val line: String,
    val mutator: String,
    val index: String,
) {

    val identity: List<String>
        get() = listOf(className, method, line, mutator, index)

    fun spelledOut(): String = "$className.$method(), line $line: $mutator"

    fun spelledOutWithItsStatus(): String = "$status at ${spelledOut()}"
}

object MutationReport {

    fun mutatedClasses(report: String): List<String> =
        Regex("<mutatedClass>(.*?)</mutatedClass>")
            .findAll(report)
            .map { it.groupValues[1].trim() }
            .toList()

    fun mutantsIn(report: String): List<Mutant> =
        Regex(
                "<mutation [^>]*status='([A-Za-z0-9_]+)'[^>]*>(.*?)</mutation>",
                RegexOption.DOT_MATCHES_ALL,
            )
            .findAll(report)
            .map { match ->
                val body = match.groupValues[2]
                Mutant(
                    status = match.groupValues[1],
                    className = field(body, "mutatedClass"),
                    method = field(body, "mutatedMethod"),
                    line = field(body, "lineNumber"),
                    mutator = field(body, "mutator").substringAfterLast('.'),
                    index = field(body, "index"),
                )
            }
            .toList()

    private fun field(mutation: String, tag: String): String =
        Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
            .find(mutation)
            ?.groupValues
            ?.get(1)
            ?.trim()
            .orEmpty()
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
}
