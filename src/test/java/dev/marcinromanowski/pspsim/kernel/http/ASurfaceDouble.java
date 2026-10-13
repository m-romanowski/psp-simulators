package dev.marcinromanowski.pspsim.kernel.http;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.spi.WireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class ASurfaceDouble implements ASurfaceOfThisSimulator {

  static final byte[] ITS_OWN_BYTES =
      "{\"message\":\"in my own words\"}".getBytes(StandardCharsets.UTF_8);

  static final int WHAT_NOTHING_DECIDED = 500;

  private final String namespace;
  private final List<WireFault> faultsItWasAskedAbout = new ArrayList<>();
  private @Nullable ProviderId named;
  private boolean publishesAnAnswer = true;

  ASurfaceDouble(String namespace) {
    this.namespace = namespace;
  }

  ASurfaceDouble namingTheProvider(@Nullable ProviderId provider) {
    this.named = provider;
    return this;
  }

  ASurfaceDouble publishingNoAnswerForAnything() {
    this.publishesAnAnswer = false;
    return this;
  }

  List<WireFault> theFaultsItWasAskedAbout() {
    return List.copyOf(faultsItWasAskedAbout);
  }

  @Override
  public boolean owns(String address) {
    return address.equals(namespace) || address.startsWith(namespace + "/");
  }

  @Override
  public ObjectOrigin whatReachesIt() {
    return ObjectOrigin.CONTROL_API;
  }

  @Override
  public Optional<ProviderId> theProviderItsAddressNames(String address) {
    return Optional.ofNullable(named);
  }

  @Override
  public Optional<WireAnswer> theAnswerTo(WireFault fault) {
    faultsItWasAskedAbout.add(fault);
    if (!publishesAnAnswer) {
      return Optional.empty();
    }
    return Optional.of(new WireAnswer(WHAT_NOTHING_DECIDED, "application/json", ITS_OWN_BYTES));
  }
}
