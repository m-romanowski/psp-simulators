package dev.marcinromanowski.pspsim.kernel.smoke;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.List;
import java.util.Map;

public interface ContractCheck {

  ProviderId provider();

  String operation();

  Provenance provenance();

  Applicability applicability();

  List<ContractExpectation> expectations();

  Map<String, String> observe(CheckTransport transport);
}
