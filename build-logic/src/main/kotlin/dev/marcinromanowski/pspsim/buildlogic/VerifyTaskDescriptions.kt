package dev.marcinromanowski.pspsim.buildlogic

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class VerifyTaskDescriptions : DefaultTask() {

    @get:InputFiles abstract val buildScripts: ConfigurableFileCollection

    @get:Internal abstract val projectRoot: DirectoryProperty

    @TaskAction
    fun verify() {
        val root: File = projectRoot.get().asFile
        val scripts = buildScripts.files.sorted()

        val described = mutableListOf<Pair<String, ADescription>>()
        val unread = mutableListOf<String>()
        for (script in scripts) {
            val source = script.readText()
            val where = script.toRelativeString(root)
            described += TaskDescriptions.inSource(source).map { where to it }
            val set = TaskDescriptions.howManyDescriptionsAreSet(source)
            val whole = TaskDescriptions.howManyWereReadWhole(source)
            if (set != whole) {
                unread += "$where: ${set - whole} of $set description(s) set here"
            }
        }

        if (described.isEmpty()) {
            throw GradleException(
                "verifyTaskDescriptions read no description at all from " +
                    "${scripts.size} build script(s), which is a broken scan rather than a " +
                    "build whose tasks describe themselves in nothing."
            )
        }

        val tooLong = described.filter { TaskDescriptions.tooLongToRead(it.second) }
        val tooShort = described.filter { TaskDescriptions.saysTooLittle(it.second) }
        val unnamed = described.filter { it.second.task == TaskDescriptions.UNNAMED }
        val spare = TaskDescriptions.anAllowanceNoDescriptionNeeds(described.map { it.second })
        val unreasoned =
            TaskDescriptions.anAllowanceThatNamesNoReason(
                TaskDescriptions.THE_TASKS_A_LONGER_ENTRY_IS_ALLOWED
            )

        if (
            tooLong.isNotEmpty() ||
                tooShort.isNotEmpty() ||
                unnamed.isNotEmpty() ||
                unread.isNotEmpty() ||
                spare.isNotEmpty() ||
                unreasoned.isNotEmpty()
        ) {
            throw GradleException(
                buildString {
                    appendLine(THE_RULE)
                    tooLong.forEach { (where, it) ->
                        appendLine(
                            "  - $where:${it.line} ${it.task}: ${it.words} words in " +
                                "${it.sentences} sentence(s), against " +
                                "${TaskDescriptions.theMostWordsAllowed(it.task)} and " +
                                "${TaskDescriptions.MOST_SENTENCES_A_LISTING_ENTRY_READS}"
                        )
                    }
                    tooShort.forEach { (where, it) ->
                        appendLine(
                            "  - $where:${it.line} ${it.task}: ${it.words} words says too " +
                                "little for a reader to tell what the task does"
                        )
                    }
                    unnamed.forEach { (where, it) ->
                        appendLine(
                            "  - $where:${it.line} names no task above it, so this scan cannot " +
                                "say what it describes"
                        )
                    }
                    unread.forEach {
                        appendLine(
                            "  - $it could not be read as a literal, so its length is unmeasured"
                        )
                    }
                    unreasoned.forEach {
                        appendLine("  - $it is allowed a longer entry and names no reason for it")
                    }
                    spare.forEach {
                        appendLine(
                            "  - $it is allowed a longer entry and does not use it, so the " +
                                "allowance says something untrue of this build"
                        )
                    }
                    appendLine(
                        "A reason a reader needs in order not to work around a rule belongs in " +
                            "the failure that rule throws. A decision belongs in doc/ as the " +
                            "fact it settled. A task that genuinely needs more words is " +
                            "registered in `THE_TASKS_A_LONGER_ENTRY_IS_ALLOWED` with the " +
                            "reason, which widens it for that task alone."
                    )
                }
            )
        }

        logger.lifecycle(
            "verifyTaskDescriptions: ${described.size} task descriptions across " +
                "${scripts.size} build scripts, each between " +
                "${TaskDescriptions.FEWEST_WORDS_THAT_SAY_WHAT_A_TASK_DOES} and " +
                "${TaskDescriptions.MOST_WORDS_A_LISTING_ENTRY_READS} words and at most " +
                "${TaskDescriptions.MOST_SENTENCES_A_LISTING_ENTRY_READS} sentences, " +
                "${TaskDescriptions.THE_TASKS_A_LONGER_ENTRY_IS_ALLOWED.size} of them " +
                "registered for more; the longest is ${described.maxOf { it.second.words }} " +
                "words and every one of them was read whole."
        )
    }

    companion object {
        val THE_RULE =
            "A task description is the one line `./gradlew tasks` prints beside the task, so " +
                "it says what the task does in no more words than a reader of that listing " +
                "reads and no fewer than tell them. These do not:"
    }
}
