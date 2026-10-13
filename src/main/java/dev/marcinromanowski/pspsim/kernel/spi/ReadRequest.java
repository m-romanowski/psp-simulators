package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;

public record ReadRequest(
    TestRunId testRunId,
    String accountId,
    String readNamed,
    SequencedMap<String, String> values,
    ObjectOrigin askedBy) {

  public ReadRequest {
    if (accountId.isBlank()) {
      throw new IllegalArgumentException(
          "a read is asked on one of the accounts this test run configures, because the provider"
              + " signs the call with that account's own credentials and answers only what that"
              + " account may see, and this request names no account");
    }
    if (readNamed.isBlank()) {
      throw new IllegalArgumentException(
          "a read answers one of the questions the provider declares, and this request names"
              + " none");
    }
    if (askedBy == ObjectOrigin.PROVIDER_API) {
      throw new IllegalArgumentException(
          "a client's request reaches a read through the provider's own route, which checks the"
              + " credential and the signature that route publishes, so a request that arrived"
              + " that way is never asked for through this port");
    }
    if (askedBy == ObjectOrigin.SCHEDULED_JOB) {
      throw new IllegalArgumentException(
          "the delivery queue moves the callbacks a payment has already caused and asks the"
              + " provider nothing about one, so no scheduled job performs a read through this"
              + " port");
    }
    values = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(values));
  }

  public static ReadRequest of(
      TestRunId testRunId,
      String accountId,
      String readNamed,
      Map<String, String> values,
      ObjectOrigin askedBy) {
    SequencedMap<String, String> asked = new LinkedHashMap<>();
    for (Map.Entry<String, String> value : values.entrySet()) {
      if (!value.getValue().isBlank()) {
        asked.put(value.getKey(), value.getValue().strip());
      }
    }
    return new ReadRequest(testRunId, accountId.strip(), readNamed.strip(), asked, askedBy);
  }

  public Optional<String> value(String key) {
    return Optional.ofNullable(values.get(key));
  }

  public String required(String key) {
    String value = values.get(key);
    if (value == null) {
      throw new IllegalStateException(
          "read "
              + key
              + " before the core had checked that the read this request names declares it as"
              + " required");
    }
    return value;
  }
}
