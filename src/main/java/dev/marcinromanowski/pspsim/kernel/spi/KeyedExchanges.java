package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.RecordedKeyedRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KeyedExchanges {

  private KeyedExchanges() {}

  public static KeyedExchangePage pageOf(
      ProviderKeyMechanisms mechanisms, TestRunId testRunId, List<RecordedKeyedRequest> recorded) {
    Map<UnderOneKey, List<RecordedKeyedRequest>> grouped = new LinkedHashMap<>();
    for (RecordedKeyedRequest row : recorded) {
      grouped
          .computeIfAbsent(
              new UnderOneKey(row.accountId(), row.keyName(), row.keyValue()),
              key -> new ArrayList<>())
          .add(row);
    }
    List<KeyedExchange> exchanges = new ArrayList<>();
    for (Map.Entry<UnderOneKey, List<RecordedKeyedRequest>> group : grouped.entrySet()) {
      exchanges.add(exchangeOf(mechanisms, testRunId, group.getKey(), group.getValue()));
    }
    exchanges.sort(Comparator.comparingLong(KeyedExchange::arrival).reversed());
    return new KeyedExchangePage(exchanges);
  }

  private static KeyedExchange exchangeOf(
      ProviderKeyMechanisms mechanisms,
      TestRunId testRunId,
      UnderOneKey key,
      List<RecordedKeyedRequest> group) {
    List<RecordedKeyedRequest> ordered = new ArrayList<>(group);
    ordered.sort(Comparator.comparingInt(RecordedKeyedRequest::sequence));
    refuseAHeldClaimTheKeyCannotHave(mechanisms, key, ordered);
    List<KeyedRequest> requests = new ArrayList<>();
    for (RecordedKeyedRequest row : ordered) {
      requests.add(row.asRequest());
    }
    return new KeyedExchange(
        mechanisms.provider(),
        testRunId,
        key.accountId(),
        key.keyName(),
        mechanisms.roleOf(key.keyName()),
        key.keyValue(),
        ordered.get(0).arrival(),
        requests);
  }

  private static void refuseAHeldClaimTheKeyCannotHave(
      ProviderKeyMechanisms mechanisms, UnderOneKey key, List<RecordedKeyedRequest> ordered) {
    KeyClaim claim = mechanisms.claimOf(key.keyName());
    if (claim.canBeHeld()) {
      return;
    }
    for (RecordedKeyedRequest row : ordered) {
      if (row.verdict() == ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD) {
        throw new IllegalStateException(
            "provider "
                + mechanisms.provider().value()
                + " recorded request "
                + row.sequence()
                + " under key "
                + key.keyName()
                + " as refused because that key was still held, and it declares that no request"
                + " can ever find this key held: "
                + claim.describedAs());
      }
    }
  }

  private record UnderOneKey(String accountId, String keyName, String keyValue) {}
}
