package dev.marcinromanowski.pspsim.buildlogic

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class VerifyEveryFormSendsWhatItCarries : DefaultTask() {

    @get:InputFiles abstract val templates: ConfigurableFileCollection

    @get:Internal abstract val projectRoot: DirectoryProperty

    @TaskAction
    fun verify() {
        val root: File = projectRoot.get().asFile
        val scanned = templates.files.sorted()
        var forms = 0
        var getForms = 0

        val offences = scanned.flatMap { template ->
            val text = template.readText()
            forms += AFormSendsWhatItCarries.formsIn(text).size
            getForms += AFormSendsWhatItCarries.theGetFormsIn(text).size
            AFormSendsWhatItCarries.findingsIn(text).map { finding ->
                "${template.toRelativeString(root)}:" +
                    "${CommentScanner.lineOf(text, finding.offset)}: submits to " +
                    finding.address
            }
        }

        if (offences.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "A form a tester submits by clicking must carry every value the route " +
                            "it reaches needs as a field of its own. ${offences.size} of the " +
                            "$getForms get form(s) in ${scanned.size} templates name a value " +
                            "only in the address they submit to:"
                    )
                    offences.forEach { appendLine("  - $it") }
                    appendLine(WHY_A_BROWSER_DROPS_WHAT_ONLY_THE_ACTION_CARRIES)
                    appendLine(
                        "This hid a whole screen once: the Look a payment up screen named the " +
                            "provider and the test run only in the address its second step " +
                            "submitted to, so no read of any provider could be asked by " +
                            "clicking at all. The screen answered that the request named no " +
                            "provider, and the suite that walked that screen had built the " +
                            "address itself instead of submitting the form, so it never saw it."
                    )
                    appendLine(
                        "Name the value in a hidden input beside the fields a tester fills in."
                    )
                }
            )
        }

        logger.lifecycle(
            "verifyEveryFormSendsWhatItCarries: $forms form(s) in ${scanned.size} templates, " +
                "$getForms of them submitted by a browser as a query, none of which names a " +
                "value only in the address it submits to; " +
                AFormSendsWhatItCarries.WHY_A_BROWSER_DROPS_WHAT_ONLY_THE_ACTION_CARRIES
        )
    }

    private companion object {

        const val WHY_A_BROWSER_DROPS_WHAT_ONLY_THE_ACTION_CARRIES =
            AFormSendsWhatItCarries.WHY_A_BROWSER_DROPS_WHAT_ONLY_THE_ACTION_CARRIES
    }
}
