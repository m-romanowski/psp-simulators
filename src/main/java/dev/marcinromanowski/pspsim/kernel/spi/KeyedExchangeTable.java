package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record KeyedExchangeTable(SequencedTable table, boolean namesTheKey, boolean keepsTheFault) {

  public static final String TEST_RUN = "test_run_id";

  public static final String KEY_NAME = "key_name";

  public static final String KEY_VALUE = "key_value";

  private static final List<String> THE_ANSWER =
      List.of(
          "operation",
          "fingerprint",
          "http_status",
          "content_type",
          "body",
          "provider_object_id",
          "state_changed",
          "at");

  private static final List<String> THE_FAULT = List.of("fault_kind", "fault_placement");

  public static KeyedExchangeTable underANamedKey(
      String name, String accountColumn, boolean keepsTheFault) {
    return new KeyedExchangeTable(
        new SequencedTable(name, List.of(TEST_RUN, accountColumn, KEY_NAME, KEY_VALUE)),
        true,
        keepsTheFault);
  }

  public static KeyedExchangeTable underItsOnlyKey(
      String name, String accountColumn, String keyColumn, boolean keepsTheFault) {
    return new KeyedExchangeTable(
        new SequencedTable(name, List.of(TEST_RUN, accountColumn, keyColumn)),
        false,
        keepsTheFault);
  }

  public String insert() {
    return table.recordingUnderAKey(columns());
  }

  private List<String> columns() {
    List<String> columns = new ArrayList<>(THE_ANSWER);
    if (keepsTheFault) {
      columns.addAll(THE_FAULT);
    }
    return List.copyOf(columns);
  }

  public List<@Nullable Object> parametersOf(KeyedExchangeRow row) {
    List<Object> key = keyOf(row);
    List<@Nullable Object> parameters = new ArrayList<>(key);
    for (ExchangeVerdict ran : ExchangeVerdict.THAT_RAN_THE_OPERATION) {
      parameters.add(ran.name());
    }
    parameters.add(row.afterTheRequestThatClaimedTheKey().name());
    parameters.add(row.whereNoRequestClaimedTheKey().name());
    parameters.add(row.operation());
    parameters.add(row.fingerprint());
    parameters.add(row.httpStatus());
    parameters.add(row.contentType());
    parameters.add(row.body());
    parameters.add(row.providerObjectId());
    parameters.add(row.stateChanged());
    parameters.add(row.at().atOffset(ZoneOffset.UTC));
    if (keepsTheFault) {
      AppliedFault fault = row.transportFault();
      parameters.add(fault == null ? null : fault.kind().name());
      parameters.add(fault == null ? null : fault.placement().name());
    }
    parameters.addAll(key);
    return parameters;
  }

  private List<Object> keyOf(KeyedExchangeRow row) {
    List<Object> key = new ArrayList<>();
    key.add(row.testRunId().value());
    key.add(row.accountId());
    if (namesTheKey) {
      key.add(row.keyName());
    }
    key.add(row.keyValue());
    return key;
  }
}
