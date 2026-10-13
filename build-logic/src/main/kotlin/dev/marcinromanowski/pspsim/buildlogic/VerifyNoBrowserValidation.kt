package dev.marcinromanowski.pspsim.buildlogic

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class VerifyNoBrowserValidation : DefaultTask() {

    @get:InputFiles abstract val templates: ConfigurableFileCollection

    @get:InputFiles abstract val coreSources: ConfigurableFileCollection

    @get:InputFiles abstract val screenSources: ConfigurableFileCollection

    @get:Internal abstract val projectRoot: DirectoryProperty

    @TaskAction
    fun verify() {
        val root: File = projectRoot.get().asFile
        val scanned = templates.files.sorted()
        var controls = 0

        val offences = scanned.flatMap { template ->
            val text = template.readText()
            controls += BrowserValidation.controlsIn(text).size
            BrowserValidation.findingsIn(text).map { finding ->
                "${template.toRelativeString(root)}:" +
                    "${CommentScanner.lineOf(text, finding.offset)}: ${finding.constraint} on " +
                    finding.control.split(Regex("\\s+")).joinToString(" ").take(120)
            }
        }

        val core = coreSources.files.sorted()
        val screens = screenSources.files.sorted()
        val judged =
            core.flatMap {
                theJudgementsIn(
                    it,
                    root,
                    BrowserValidation.theWaysASourceAsksWhetherAProviderPublishesAValue +
                        BrowserValidation.theWayASourceReadsTheValuesAProviderPublishes,
                )
            } +
                screens.flatMap {
                    theJudgementsIn(
                        it,
                        root,
                        BrowserValidation.theWaysASourceAsksWhetherAProviderPublishesAValue,
                    )
                }
        var offered = 0
        screens.forEach {
            offered +=
                BrowserValidation.theOnesIn(
                        it.readText(),
                        BrowserValidation.theWayASourceReadsTheValuesAProviderPublishes,
                    )
                    .size
        }

        if (offences.isNotEmpty() || judged.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "Neither a form control of this UI nor a source between a tester and a " +
                            "provider may judge what a tester typed. " +
                            "${offences.size} found in ${scanned.size} templates and " +
                            "${judged.size} in ${core.size + screens.size} sources:"
                    )
                    offences.forEach { appendLine("  - $it") }
                    judged.forEach { appendLine("  - $it") }
                    appendLine(
                        "The provider is the validator of every value this simulator takes, and " +
                            "its refusals are written for a tester to read. A control the " +
                            "browser blocks a submit on, empties a paste into or bounds takes " +
                            "one of those refusals away from the screen it was written for, so " +
                            "the UI stops covering what the control API covers."
                    )
                    appendLine(
                        "A declared list of values is what this provider publishes, which is a " +
                            "hint beside the field and the suggestions on its box; it is not a " +
                            "gate. The core asking a field whether it publishes a value made " +
                            "this simulator a second validator refusing in its own words, and " +
                            "autra had already worked around it by declaring two read fields as " +
                            "boxes to keep its own 400 INVALID_STATUS and 400 INVALID_TYPE " +
                            "reachable. A screen may read the values a provider publishes to " +
                            "offer them and may not ask whether they contain what a tester typed."
                    )
                    appendLine(
                        "Two of these hid a refusal each for a year: a Pix amount and its " +
                            "currency, which `CreatePixCheckout` refuses by name and no clicking " +
                            "tester could reach. The sweep that said there were none read " +
                            "`templates/*.html`, which is ${BrowserValidation.theTemplatesAGlobOneDirectoryDeepReads(scanned)} of " +
                            "the ${scanned.size} templates this task reads, and matched one line " +
                            "at a time, which misses every attribute standing on its own line."
                    )
                    appendLine(
                        "Let the provider refuse it. There is no exception list here on purpose: " +
                            "an exclusion in this repository silently became a tenth of the " +
                            "codebase once."
                    )
                }
            )
        }

        logger.lifecycle(
            "verifyNoBrowserValidation: $controls form controls in ${scanned.size} templates, " +
                "none carrying ${BrowserValidation.constraintsABrowserEnforces.joinToString(", ")} " +
                "and none of a type a browser judges or rewrites what was typed into, " +
                "${BrowserValidation.theTypesABrowserJudgesOrRewritesWhatWasTypedInto
                    .joinToString(", ")}; the provider refuses what a tester states. " +
                "${core.size} core and ${screens.size} screen sources, none of them asking " +
                "${BrowserValidation.theWaysASourceAsksWhetherAProviderPublishesAValue
                    .joinToString(", ")}; the values a provider publishes are read in $offered " +
                "place(s) of the screens that offer them and nowhere in the core."
        )
    }

    private fun theJudgementsIn(source: File, root: File, expressions: List<String>): List<String> {
        val text = source.readText()
        return BrowserValidation.theOnesIn(text, expressions).map {
            "${source.toRelativeString(root)}:${CommentScanner.lineOf(text, it.offset)}: " +
                "${it.expression} asks what this provider publishes about a value a tester typed"
        }
    }
}
