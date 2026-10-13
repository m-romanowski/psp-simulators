package dev.marcinromanowski.pspsim.kernel.http;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.spi.WireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import java.util.Optional;

public interface ASurfaceOfThisSimulator {

  boolean owns(String address);

  ObjectOrigin whatReachesIt();

  Optional<ProviderId> theProviderItsAddressNames(String address);

  Optional<WireAnswer> theAnswerTo(WireFault fault);
}
