package dev.marcinromanowski.pspsim.buildlogic

object MutationVerdicts {

    fun statusesWithNoVerdictHere(mutants: List<Mutant>): List<String> =
        mutants.map(Mutant::status).filter { MutationStatus.of(it) == null }.distinct().sorted()

    fun with(mutants: List<Mutant>, verdict: StatusVerdict): List<Mutant> = mutants.filter {
        MutationStatus.of(it.status)?.verdict == verdict
    }

    fun awaitingANarrowedRerun(mutants: List<Mutant>): List<Mutant> = mutants.filter {
        MutationStatus.of(it.status)?.awaitsANarrowedRerun == true
    }

    fun statusesOf(mutants: List<Mutant>): List<String> =
        mutants.map(Mutant::status).distinct().sorted()

    fun unaccountedForByTheRerun(full: List<Mutant>, narrowed: List<Mutant>): List<Mutant> {
        val accounted =
            narrowed
                .filter { MutationStatus.of(it.status)?.accountsForAMutant == true }
                .map(Mutant::identity)
                .toSet()
        return awaitingANarrowedRerun(full).filterNot { accounted.contains(it.identity) }
    }

    fun stillAwaitingAVerdict(full: List<Mutant>, narrowed: List<Mutant>): List<Mutant> =
        heldPairs(full, narrowed).map { (_, reRun) -> reRun }

    fun thoseNoRerunCanExcuse(full: List<Mutant>, narrowed: List<Mutant>): List<Mutant> =
        heldPairs(full, narrowed)
            .filter { (fullRun, reRun) -> refuses(fullRun) && refuses(reRun) }
            .map { (_, reRun) -> reRun }

    fun thoseAFirstHiccupExplains(full: List<Mutant>, narrowed: List<Mutant>): List<Mutant> =
        heldPairs(full, narrowed)
            .filter { (fullRun, reRun) -> refuses(reRun) && !refuses(fullRun) }
            .map { (_, reRun) -> reRun }

    fun thoseAStandingLimitExplains(full: List<Mutant>, narrowed: List<Mutant>): List<Mutant> =
        heldPairs(full, narrowed).map { (_, reRun) -> reRun }.filter { !refuses(it) }

    fun asTheFullRunSawThem(full: List<Mutant>, listed: List<Mutant>): List<Mutant> {
        val identities = listed.map(Mutant::identity).toSet()
        return full.filter { identities.contains(it.identity) }
    }

    private fun heldPairs(full: List<Mutant>, narrowed: List<Mutant>): List<Pair<Mutant, Mutant>> {
        val reRun = awaitingANarrowedRerun(narrowed).associateBy(Mutant::identity)
        return awaitingANarrowedRerun(full).mapNotNull { fullRun ->
            reRun[fullRun.identity]?.let { fullRun to it }
        }
    }

    private fun refuses(mutant: Mutant): Boolean =
        MutationStatus.of(mutant.status)?.verdict == StatusVerdict.RE_RUN_THEN_FAILED
}
