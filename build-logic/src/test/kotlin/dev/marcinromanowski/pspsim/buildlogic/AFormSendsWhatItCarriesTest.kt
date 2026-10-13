package dev.marcinromanowski.pspsim.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AFormSendsWhatItCarriesTest {

    private fun addresses(template: String): List<String> =
        AFormSendsWhatItCarries.findingsIn(template).map { it.address }

    @Test
    fun `a get form naming a value only in the address it submits to is found`() {
        assertEquals(
            listOf("@{/ui/reads/ask(provider=\${read.provider},testRunId=\${read.testRunId})}"),
            addresses(
                """
                <form
                  class="form"
                  method="get"
                  th:action="@{/ui/reads/ask(provider=${'$'}{read.provider},testRunId=${'$'}{read.testRunId})}"
                >
                  <input type="hidden" name="accountId" th:value="${'$'}{read.accountId}" />
                </form>
                """
            ),
        )
    }

    @Test
    fun `a get form carrying every value as a field of its own is left alone`() {
        val carryingThemAsFields =
            """
            <form class="form" method="get" th:action="@{/ui/reads/ask}">
              <input type="hidden" name="provider" th:value="${'$'}{read.provider}" />
              <input type="hidden" name="testRunId" th:value="${'$'}{read.testRunId}" />
            </form>
            """

        assertEquals(emptyList<String>(), addresses(carryingThemAsFields))
        assertEquals(1, AFormSendsWhatItCarries.theGetFormsIn(carryingThemAsFields).size)
    }

    @Test
    fun `a path variable an address binds is not a value the query carries`() {
        assertEquals(
            emptyList<String>(),
            addresses(
                """
                <form method="get"
                  th:action="@{/ui/x/{provider}/{screen}(provider=${'$'}{provider},screen=${'$'}{screen})}">
                </form>
                """
            ),
        )
        assertEquals(
            listOf("testRunId"),
            AFormSendsWhatItCarries.theValuesAnAddressCarriesOnlyInItsQuery(
                "@{/ui/x/{provider}(provider=grow,testRunId=a-run)}"
            ),
        )
    }

    @Test
    fun `a form naming no method at all is submitted as a query by a browser`() {
        val namingNone =
            """<form class="form" th:action="@{/ui/reads/ask(provider=grow)}"></form>"""

        assertEquals(true, AFormSendsWhatItCarries.itIsAGetForm(namingNone))
        assertEquals(listOf("@{/ui/reads/ask(provider=grow)}"), addresses(namingNone))
    }

    @Test
    fun `an attribute whose name ends in method is not the method of the form`() {
        assertEquals(
            true,
            AFormSendsWhatItCarries.itIsAGetForm(
                """<form data-method="post" th:action="@{/ui/reads/ask}"></form>"""
            ),
        )
    }

    @Test
    fun `a form submitted as a body keeps the query of the address it submits to`() {
        val posted =
            """<form method="post" th:action="@{/ui/objects/act(provider=${'$'}{provider})}">"""

        assertEquals(emptyList<String>(), addresses(posted))
        assertEquals(emptyList<String>(), AFormSendsWhatItCarries.theGetFormsIn(posted))
        assertEquals(1, AFormSendsWhatItCarries.formsIn(posted).size)
    }

    @Test
    fun `a literal question mark on the address of a get form is found as well`() {
        assertEquals(
            listOf("/ui/reads/ask?provider=grow"),
            addresses("""<form method="get" action="/ui/reads/ask?provider=grow">"""),
        )
    }

    @Test
    fun `a get form written over several lines with its method last is still a get form`() {
        assertEquals(
            listOf("@{/ui/reads/ask(provider=grow)}"),
            addresses(
                """
                <form
                  class="form"
                  th:action="@{/ui/reads/ask(provider=grow)}"
                  method="get"
                >
                """
            ),
        )
    }

    @Test
    fun `an address naming no value at all is left alone whatever the method`() {
        assertEquals(
            emptyList<String>(),
            addresses("""<form method="get" th:action="@{/ui/reads}">"""),
        )
    }
}
