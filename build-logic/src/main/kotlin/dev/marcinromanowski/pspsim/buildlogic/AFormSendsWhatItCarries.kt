package dev.marcinromanowski.pspsim.buildlogic

object AFormSendsWhatItCarries {

    const val WHY_A_BROWSER_DROPS_WHAT_ONLY_THE_ACTION_CARRIES =
        "a browser submitting a form whose method is get replaces the query of the address it " +
            "submits to with the fields of the form itself, so a parameter named only in that " +
            "address never leaves the screen and the route is reached without it"

    data class Finding(val form: String, val offset: Int, val address: String)

    private val forms = Regex("<form\\b[^>]*>", RegexOption.DOT_MATCHES_ALL)

    private val theMethodItNames =
        Regex("(?<![-\\w:])method\\s*=\\s*\"\\s*([a-zA-Z]+)\\s*\"", RegexOption.DOT_MATCHES_ALL)

    private val theAddressItSubmitsTo =
        Regex("(?:th:)?action\\s*=\\s*\"([^\"]*)\"", RegexOption.DOT_MATCHES_ALL)

    private val theArgumentsOnAnAddress = Regex("\\(([^)]*)\\)", RegexOption.DOT_MATCHES_ALL)

    private val thePlaceholdersInAnAddress = Regex("\\{([^{}]+)}")

    private const val THE_METHOD_A_FORM_NAMING_NONE_IS_SUBMITTED_WITH = "get"

    fun formsIn(template: String): List<String> = forms.findAll(template).map { it.value }.toList()

    fun theGetFormsIn(template: String): List<String> =
        formsIn(template).filter { itIsAGetForm(it) }

    fun itIsAGetForm(form: String): Boolean =
        (theMethodItNames.find(form)?.groupValues?.get(1)
                ?: THE_METHOD_A_FORM_NAMING_NONE_IS_SUBMITTED_WITH)
            .lowercase() == THE_METHOD_A_FORM_NAMING_NONE_IS_SUBMITTED_WITH

    fun theValuesAnAddressCarriesOnlyInItsQuery(address: String): List<String> {
        val boundToThePath =
            thePlaceholdersInAnAddress.findAll(address).map { it.groupValues[1].trim() }.toSet()
        val named =
            theArgumentsOnAnAddress
                .findAll(address)
                .flatMap { arguments -> arguments.groupValues[1].split(",").asSequence() }
                .map { it.substringBefore("=").trim() }
                .filter { it.isNotEmpty() && it !in boundToThePath }
                .toList()
        return if (address.contains("?")) named + address.substringAfter("?") else named
    }

    fun findingsIn(template: String): List<Finding> =
        forms
            .findAll(template)
            .filter { itIsAGetForm(it.value) }
            .mapNotNull { form ->
                theAddressItSubmitsTo
                    .find(form.value)
                    ?.groupValues
                    ?.get(1)
                    ?.takeIf { theValuesAnAddressCarriesOnlyInItsQuery(it).isNotEmpty() }
                    ?.let { Finding(form.value, form.range.first, it) }
            }
            .toList()
}
