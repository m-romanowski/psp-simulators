package dev.marcinromanowski.pspsim.kernel.smoke;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.List;

public interface CheckRunAccounts {

  List<String> configuredFor(ProviderId provider);
}
