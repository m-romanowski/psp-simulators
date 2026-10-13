"""Name what failed in the run log, so nobody has to download an artifact to find out."""

import os
import pathlib
import sys
import xml.etree.ElementTree as ElementTree

CAP = 40


def annotate(text, file=None, line=None):
    where = ""
    if file:
        where = " file=" + file + ("," + "line=" + str(line) if line else "")
    sys.stdout.write("::error" + where + "::" + text.replace("\n", "%0A") + "\n")


def checkstyle(root, found):
    for report in sorted(root.glob("build/reports/checkstyle/*.xml")):
        for element in ElementTree.parse(report).getroot().iter("file"):
            name = element.get("name", "")
            relative = name[len(str(root)) + 1 :] if name.startswith(str(root)) else name
            for error in element.iter("error"):
                rule = (error.get("source") or "").rsplit(".", 1)[-1]
                found.append(
                    (
                        "checkstyle",
                        relative + ":" + (error.get("line") or "?"),
                        (error.get("message") or "") + " [" + rule + "]",
                        relative,
                        error.get("line"),
                    )
                )


def tests(root, found):
    for results in sorted(root.glob("**/build/test-results/*/TEST-*.xml")):
        suite = ElementTree.parse(results).getroot()
        for case in suite.iter("testcase"):
            for outcome in list(case.iter("failure")) + list(case.iter("error")):
                head = (outcome.get("message") or outcome.get("type") or "").strip()
                found.append(
                    (
                        "test",
                        (case.get("classname") or "?") + "." + (case.get("name") or "?"),
                        head.splitlines()[0] if head else "no message",
                        None,
                        None,
                    )
                )


def mutants(root, found):
    report = root / "build/reports/pitest/mutations.xml"
    if not report.is_file():
        return
    counts = {}
    for mutation in ElementTree.parse(report).getroot().iter("mutation"):
        status = mutation.get("status") or "UNKNOWN"
        counts[status] = counts.get(status, 0) + 1
        if status in ("SURVIVED", "NO_COVERAGE"):
            where = mutation.findtext("mutatedClass", "?")
            method = mutation.findtext("mutatedMethod", "?")
            line = mutation.findtext("lineNumber", "?")
            mutator = (mutation.findtext("mutator", "?") or "?").rsplit(".", 1)[-1]
            found.append(
                (
                    "mutant " + status.lower(),
                    where + "." + method + " line " + line,
                    mutator,
                    None,
                    None,
                )
            )
    if counts:
        sys.stdout.write(
            "pitest status breakdown: "
            + ", ".join(k + "=" + str(v) for k, v in sorted(counts.items()))
            + "\n"
        )


def main():
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    found = []
    for gather in (checkstyle, tests, mutants):
        try:
            gather(root, found)
        except ElementTree.ParseError as unparsable:
            sys.stdout.write("a report under " + str(root) + " is unparsable: " + str(unparsable) + "\n")
    if not found:
        sys.stdout.write(
            "No checkstyle violation, failed test or surviving mutant is recorded in any report."
            " The failure is in the Gradle output above.\n"
        )
        return
    lines = []
    for kind, where, detail, file, line in found[:CAP]:
        annotate(kind + ": " + where + " - " + detail, file, line)
        lines.append("- `" + kind + "` " + where + " - " + detail)
    if len(found) > CAP:
        lines.append("- ... and " + str(len(found) - CAP) + " more")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    body = "### What failed\n\n" + "\n".join(lines) + "\n"
    if summary:
        with open(summary, "a", encoding="utf-8") as sink:
            sink.write(body)
    else:
        sys.stdout.write(body)


main()
