package dev.marcinromanowski.pspsim.buildlogic

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BrowserValidationTest {

    private fun constraints(template: String): List<String> =
        BrowserValidation.findingsIn(template).map { it.constraint }

    @Test
    fun `a constraint standing on its own line is found, which a line at a time is not`() {
        val onItsOwnLine =
            """
            <input
              type="radio"
              name="outcome"
              th:value="${'$'}{met.outcome}"
              required
            />
            """

        assertEquals(listOf("required"), constraints(onItsOwnLine))
        assertEquals(
            emptyList<String>(),
            onItsOwnLine.lines().filter { Regex("<input[^>]*required").containsMatchIn(it) },
        )
    }

    @Test
    fun `every constraint a browser enforces is found, wherever it stands in the element`() {
        assertEquals(
            listOf("type=\"number\"", "min", "max", "step", "pattern", "required"),
            constraints(
                """<input type="number" min="0" max="9" step="1" pattern="[0-9]+" required />"""
            ),
        )
        assertEquals(listOf("novalidate"), constraints("""<form class="form" novalidate>"""))
    }

    @Test
    fun `a box whose type makes a browser judge or rewrite what was typed is found as well`() {
        BrowserValidation.theTypesABrowserJudgesOrRewritesWhatWasTypedInto.forEach { judged ->
            assertEquals(
                listOf("""type="$judged""""),
                constraints("""<input type="$judged" name="from" />"""),
                "a control of type $judged empties or rewrites a value a tester typed before the" +
                    " provider it is sent to ever reads it, so the refusal that provider publishes" +
                    " for a malformed value is unreachable from the screen",
            )
        }
    }

    @Test
    fun `a box a tester types any text into is judged by nothing in the browser`() {
        listOf("text", "hidden", "submit", "checkbox", "radio", "password", "search", "tel")
            .forEach { plain ->
                assertEquals(
                    emptyList<String>(),
                    constraints("""<input type="$plain" name="from" />"""),
                    "a control of type $plain carries what was typed to the provider unchanged",
                )
            }
    }

    @Test
    fun `a plain box a tester types anything into carries none of them`() {
        assertEquals(
            emptyList<String>(),
            constraints(
                """<input id="amount" type="text" inputmode="numeric" name="field.amount" />"""
            ),
        )
    }

    @Test
    fun `the word in a template expression or a label is no attribute of the control`() {
        assertEquals(
            emptyList<String>(),
            constraints("""<input th:if="${'$'}{field.required}" th:value="required" />"""),
        )
        assertEquals(
            emptyList<String>(),
            constraints("""<span th:if="${'$'}{field.required}" class="tag">required</span>"""),
        )
    }

    @Test
    fun `a name a constraint is only the tail of is not that constraint`() {
        assertEquals(
            emptyList<String>(),
            constraints(
                """<input data-min="1" th:attr="aria-required=null" name="notrequired" />"""
            ),
        )
    }

    @Test
    fun `a core source asking whether a provider publishes a tester's value is found`() {
        val aSecondValidator =
            """
            private static void refuseAValueThisFieldDoesNotAccept(
                OriginationField field, String typed) {
              if (field.choices().isEmpty() || field.choice(typed).isPresent()) {
                return;
              }
              throw new IllegalArgumentException(field.theValuesItAccepts());
            }
            """

        assertEquals(
            listOf(".choices()", ".choice(", ".theValuesItAccepts("),
            BrowserValidation.theOnesIn(
                    aSecondValidator,
                    BrowserValidation.theWaysASourceAsksWhetherAProviderPublishesAValue +
                        BrowserValidation.theWayASourceReadsTheValuesAProviderPublishes,
                )
                .map { it.expression },
        )
    }

    @Test
    fun `a screen reading the values a provider publishes to offer them asks nothing of them`() {
        val offering = """for (OriginationChoice choice : declared.choices()) { offered.add(…); }"""

        assertEquals(
            emptyList<String>(),
            BrowserValidation.theOnesIn(
                    offering,
                    BrowserValidation.theWaysASourceAsksWhetherAProviderPublishesAValue,
                )
                .map { it.expression },
        )
        assertEquals(
            listOf(".choices()"),
            BrowserValidation.theOnesIn(
                    offering,
                    BrowserValidation.theWayASourceReadsTheValuesAProviderPublishes,
                )
                .map { it.expression },
        )
    }

    @Test
    fun `a method of that name declared rather than called on a field is not a call on one`() {
        val theTypeThatDeclaresIt =
            """
            public String theValuesItAccepts() {
              return String.join(", ", offered);
            }
            """

        assertEquals(
            emptyList<String>(),
            BrowserValidation.theOnesIn(
                    theTypeThatDeclaresIt,
                    BrowserValidation.theWaysASourceAsksWhetherAProviderPublishesAValue +
                        BrowserValidation.theWayASourceReadsTheValuesAProviderPublishes,
                )
                .map { it.expression },
        )
    }

    @Test
    fun `the controls it counts are the ones a tester answers, so a sweep names its denominator`() {
        val template =
            """
            <form class="form"><div>
              <input type="text" name="a" /><select name="b"><option>x</option></select>
              <textarea name="c"></textarea><span>d</span>
            </div></form>
            """

        assertEquals(4, BrowserValidation.controlsIn(template).size)
        assertEquals(emptyList<String>(), constraints(template))
    }

    @Test
    fun `the templates a glob one directory deep reads are counted, not remembered`() {
        val templates =
            File(
                "src/main/resources/${BrowserValidation.THE_DIRECTORY_EVERY_TEMPLATE_OF_THIS_UI_STANDS_UNDER}"
            )
        val scanned =
            listOf(
                File(templates, "accounts.html"),
                File(templates, "objects.html"),
                File(File(templates, "payer"), "card.html"),
            )

        assertEquals(2, BrowserValidation.theTemplatesAGlobOneDirectoryDeepReads(scanned))
        assertEquals(3, scanned.size)
    }
}
