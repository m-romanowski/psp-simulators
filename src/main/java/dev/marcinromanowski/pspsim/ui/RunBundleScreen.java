package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.bundle.RunBundle;
import dev.marcinromanowski.pspsim.kernel.spi.BundleAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class RunBundleScreen {

  public static final String THE_BOX_SECRETS_ARE_TYPED_INTO = "secrets";

  public static final String A_LINE_NAMES_AN_ACCOUNT_A_FIELD_AND_A_VALUE =
      "each line of secrets names one account, one field and one value as"
          + " accountId.field=value, and only a value its provider takes as a block of many lines"
          + " runs on over the lines under it, as far as the line such a block ends with; this"
          + " refusal names the line that did neither, and never what was typed on it.";

  public static final String A_BUNDLE_IS_WHAT_THIS_BOX_TAKES =
      "this box takes the document the box above produced; what was pasted is not a run bundle"
          + " this simulator can read";

  public static final String NOTHING_WAS_WRITTEN =
      "this was a dry run: the report below is what an import would do, and this test run holds"
          + " everything it held before";

  private static final Pattern THE_END_OF_A_LINE = Pattern.compile("\\R");

  private RunBundleScreen() {}

  public sealed interface Pasted {

    record Unreadable(String message) implements Pasted {}

    record Bundle(RunBundle bundle) implements Pasted {}
  }

  public sealed interface Supplied {

    record Unreadable(String message) implements Supplied {}

    record Secrets(Map<String, Map<String, String>> byAccount) implements Supplied {}
  }

  public static Pasted pastedInto(@Nullable String document, Function<String, RunBundle> reader) {
    if (document == null || document.isBlank()) {
      return new Pasted.Unreadable(A_BUNDLE_IS_WHAT_THIS_BOX_TAKES);
    }
    try {
      return new Pasted.Bundle(reader.apply(document));
    } catch (RuntimeException unreadable) {
      return new Pasted.Unreadable(A_BUNDLE_IS_WHAT_THIS_BOX_TAKES);
    }
  }

  public static Supplied theSecretsTypedInto(
      @Nullable String typed, ProviderAccountFields declared) {
    Map<String, Map<String, String>> byAccount = new LinkedHashMap<>();
    if (typed == null || typed.isBlank()) {
      return new Supplied.Secrets(Map.of());
    }
    List<String> lines = THE_END_OF_A_LINE.splitAsStream(typed).toList();
    ASecretTheseLinesName running = null;
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i).strip();
      if (line.isEmpty()) {
        continue;
      }
      if (running != null && running.isStillRunningOn()) {
        running = running.andThisLineToo(line);
        continue;
      }
      ASecretTheseLinesName named = ASecretTheseLinesName.readFrom(line, declared, i);
      if (named == null) {
        return running == null
            ? theLineItCannotRead(i)
            : theLineItCannotReadUnder(i, running.field());
      }
      if (running != null) {
        running.intoTheSecretsOf(byAccount);
      }
      running = named;
    }
    if (running != null && running.ranOnOverALineThatNeverEnded()) {
      return theBlockThatNeverEnded(running);
    }
    if (running != null) {
      running.intoTheSecretsOf(byAccount);
    }
    Map<String, Map<String, String>> copied = new LinkedHashMap<>();
    byAccount.forEach((accountId, fields) -> copied.put(accountId, Map.copyOf(fields)));
    return new Supplied.Secrets(Map.copyOf(copied));
  }

  private record ASecretTheseLinesName(
      String accountId,
      String field,
      String value,
      List<String> theLinesItMayEndWith,
      int theLineItOpenedOn,
      int linesItRanOnOver) {

    private static @Nullable ASecretTheseLinesName readFrom(
        String line, ProviderAccountFields declared, int whichLine) {
      int equals = line.indexOf('=');
      if (equals < 0) {
        return null;
      }
      String addressed = line.substring(0, equals);
      String value = line.substring(equals + 1);
      int dot = addressed.indexOf('.');
      if (dot < 0) {
        return null;
      }
      String accountId = addressed.substring(0, dot).strip();
      String field = addressed.substring(dot + 1).strip();
      if (accountId.isEmpty() || field.isEmpty() || value.isEmpty()) {
        return null;
      }
      return new ASecretTheseLinesName(
          accountId,
          field,
          value,
          declared
              .field(field)
              .filter(ConfigurableField::holdsABlockOfManyLines)
              .map(ConfigurableField::theLinesABlockMayEndWith)
              .orElseGet(List::of),
          whichLine,
          0);
    }

    private boolean isStillRunningOn() {
      return !theLinesItMayEndWith.isEmpty() && !theEndItRunsOnTo().isPresent();
    }

    private boolean ranOnOverALineThatNeverEnded() {
      return linesItRanOnOver > 0 && isStillRunningOn();
    }

    private Optional<String> theEndItRunsOnTo() {
      return theLinesItMayEndWith.stream().filter(value::contains).findFirst();
    }

    private ASecretTheseLinesName andThisLineToo(String line) {
      return new ASecretTheseLinesName(
          accountId,
          field,
          value + "\n" + line,
          theLinesItMayEndWith,
          theLineItOpenedOn,
          linesItRanOnOver + 1);
    }

    private void intoTheSecretsOf(Map<String, Map<String, String>> byAccount) {
      byAccount.computeIfAbsent(accountId, named -> new LinkedHashMap<>()).put(field, value);
    }
  }

  private static Supplied theLineItCannotReadUnder(int line, String theFieldAbove) {
    return new Supplied.Unreadable(
        THE_BOX_SECRETS_ARE_TYPED_INTO
            + " line "
            + (line + 1)
            + ": "
            + A_LINE_NAMES_AN_ACCOUNT_A_FIELD_AND_A_VALUE
            + " The value of "
            + theFieldAbove
            + " above it is not one this provider takes as a block of many lines, so nothing runs"
            + " on under it and the mistake may be in how that line names its own field.");
  }

  private static Supplied theBlockThatNeverEnded(ASecretTheseLinesName running) {
    return new Supplied.Unreadable(
        THE_BOX_SECRETS_ARE_TYPED_INTO
            + " line "
            + (running.theLineItOpenedOn() + 1)
            + ": "
            + running.field()
            + " is a value this provider takes as a block of many lines, and the lines under it"
            + " run on to the end of this box without reaching any of the lines such a block ends"
            + " with: "
            + String.join(", ", running.theLinesItMayEndWith())
            + ". Paste the whole block, the last line included.");
  }

  private static Supplied theLineItCannotRead(int line) {
    return new Supplied.Unreadable(
        THE_BOX_SECRETS_ARE_TYPED_INTO
            + " line "
            + (line + 1)
            + ": "
            + A_LINE_NAMES_AN_ACCOUNT_A_FIELD_AND_A_VALUE);
  }

  public static final String THE_BOX_TAKES_A_BLOCK = "theSecretsBoxTakesABlockOfManyLines";

  public static boolean theSecretsBoxTakesABlockOfManyLines(ProviderAccountFields declared) {
    for (ConfigurableField field : declared.fields()) {
      if (field.holdsABlockOfManyLines()) {
        return true;
      }
    }
    return false;
  }

  public static List<String> theSecretNamesOf(Supplied supplied) {
    return supplied instanceof Supplied.Secrets secrets
        ? BundleAdmission.theNamesOfTheSecretsSupplied(secrets.byAccount())
        : List.of();
  }

  public static ActionVerdict.Refused theRefusalOfABundleItDidNotTakeBack() {
    return ActionVerdict.refused(
        RefusalSubject.noObjectWasNamed(
            BundleAdmission.THE_BUNDLE_IT_ASKED_THIS_PROVIDER_TO_TAKE_BACK));
  }
}
