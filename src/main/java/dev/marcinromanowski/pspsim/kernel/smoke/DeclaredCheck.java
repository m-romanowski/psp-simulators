package dev.marcinromanowski.pspsim.kernel.smoke;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.List;
import java.util.Map;

public record DeclaredCheck(
    ProviderId provider,
    String operation,
    Provenance provenance,
    Applicability applicability,
    List<ContractExpectation> expectations,
    DeclaredCheck.Observation observation)
    implements ContractCheck {

  public interface Observation {

    Map<String, String> of(CheckTransport transport);
  }

  public DeclaredCheck {
    expectations = List.copyOf(expectations);
  }

  public static DeclaredCheck applying(
      ProviderId provider,
      String operation,
      Provenance provenance,
      List<ContractExpectation> expectations,
      Observation observation) {
    return new DeclaredCheck(
        provider, operation, provenance, Applicability.APPLIES, expectations, observation);
  }

  public static DeclaredCheck notApplying(
      ProviderId provider, String operation, Provenance provenance, String reason) {
    return new DeclaredCheck(
        provider,
        operation,
        provenance,
        new Applicability.DoesNotApply(reason),
        List.of(),
        transport -> Map.of());
  }

  @Override
  public Map<String, String> observe(CheckTransport transport) {
    return observation.of(transport);
  }
}
