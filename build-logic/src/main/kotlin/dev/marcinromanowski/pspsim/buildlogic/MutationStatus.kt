package dev.marcinromanowski.pspsim.buildlogic

enum class StatusVerdict {
    KILLED_BY_A_TEST,
    NAMED_AS_A_SURVIVOR,
    NAMED_AS_PITS_OWN_JUDGEMENT,
    NAMED_AS_A_RUN_THAT_DID_NOT_FINISH,
    RE_RUN_THEN_NAMED,
    RE_RUN_THEN_FAILED,
    FAILS_THE_BUILD,
}

enum class MutationStatus(val creditedToTheScore: Boolean, val verdict: StatusVerdict) {
    KILLED(true, StatusVerdict.KILLED_BY_A_TEST),
    SURVIVED(false, StatusVerdict.NAMED_AS_A_SURVIVOR),
    TIMED_OUT(true, StatusVerdict.RE_RUN_THEN_NAMED),
    NON_VIABLE(true, StatusVerdict.NAMED_AS_PITS_OWN_JUDGEMENT),
    MEMORY_ERROR(true, StatusVerdict.RE_RUN_THEN_NAMED),
    NOT_STARTED(false, StatusVerdict.NAMED_AS_A_RUN_THAT_DID_NOT_FINISH),
    STARTED(false, StatusVerdict.NAMED_AS_A_RUN_THAT_DID_NOT_FINISH),
    RUN_ERROR(true, StatusVerdict.RE_RUN_THEN_FAILED),
    NO_COVERAGE(false, StatusVerdict.FAILS_THE_BUILD),
    EQUIVALENT(true, StatusVerdict.NAMED_AS_PITS_OWN_JUDGEMENT);

    val accountsForAMutant: Boolean
        get() = verdict != StatusVerdict.NAMED_AS_A_RUN_THAT_DID_NOT_FINISH

    val awaitsANarrowedRerun: Boolean
        get() =
            verdict == StatusVerdict.RE_RUN_THEN_NAMED ||
                verdict == StatusVerdict.RE_RUN_THEN_FAILED

    companion object {

        fun of(status: String): MutationStatus? = entries.firstOrNull { it.name == status }

        fun stampedByAMinionExitCode(): List<String> =
            entries.filter { it.awaitsANarrowedRerun }.map { it.name }
    }
}
