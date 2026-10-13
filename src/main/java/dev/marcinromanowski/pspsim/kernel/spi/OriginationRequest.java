package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;

public record OriginationRequest(
    TestRunId testRunId,
    String accountId,
    String startedAs,
    SequencedMap<String, String> values,
    ObjectOrigin askedBy) {

  public OriginationRequest {
    if (accountId.isBlank()) {
      throw new IllegalArgumentException(
          "a payment starts on one of the accounts this test run configures, because the provider"
              + " signs the call with that account's own credentials, and this request names no"
              + " account");
    }
    if (startedAs.isBlank()) {
      throw new IllegalArgumentException(
          "a payment starts one of the ways the provider declares, and this request names none");
    }
    if (askedBy == ObjectOrigin.PROVIDER_API) {
      throw new IllegalArgumentException(
          "a client's request reaches an operation through the provider's own route, which checks"
              + " the credential and the signature that route publishes, so a request that"
              + " arrived that way is never asked for through this port");
    }
    if (askedBy == ObjectOrigin.SCHEDULED_JOB) {
      throw new IllegalArgumentException(
          "the delivery queue moves the callbacks a payment has already caused and never starts"
              + " one, so no scheduled job originates a payment through this port");
    }
    values = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(values));
  }

  public static OriginationRequest of(
      TestRunId testRunId,
      String accountId,
      String startedAs,
      Map<String, String> values,
      ObjectOrigin askedBy) {
    SequencedMap<String, String> asked = new LinkedHashMap<>();
    for (Map.Entry<String, String> value : values.entrySet()) {
      if (!value.getValue().isBlank()) {
        asked.put(value.getKey(), value.getValue().strip());
      }
    }
    return new OriginationRequest(testRunId, accountId.strip(), startedAs.strip(), asked, askedBy);
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
              + " before the core had checked that the way of starting a payment this request"
              + " names declares it as required");
    }
    return value;
  }
}
