package dev.marcinromanowski.pspsim.kernel.http;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.ThisSimulatorsOwnAddresses;
import dev.marcinromanowski.pspsim.kernel.audit.AFailureNothingAnswered;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.audit.TheCausesOfAFailure;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.WhatAProviderAnswersOffItsRoutes;
import dev.marcinromanowski.pspsim.kernel.spi.WireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WhoeverWasAsked {

  static final String WHAT_THE_SERVER_LOG_SAYS =
      "{} {} answered {} with no body: no provider of this build claims this address and no part"
          + " of this simulator owns it, so there is nobody whose words it could answer in";

  private static final Logger NOBODY_CLAIMED_THE_ADDRESS =
      LoggerFactory.getLogger(WhoeverWasAsked.class);

  private final WhatAProviderAnswersOffItsRoutes providersAnswer;
  private final ProviderRegistry providers;
  private final List<ASurfaceOfThisSimulator> surfaces;
  private final ActionTrail trail;

  public WhoeverWasAsked(
      WhatAProviderAnswersOffItsRoutes providersAnswer,
      ProviderRegistry providers,
      List<ASurfaceOfThisSimulator> surfaces,
      ActionTrail trail) {
    this.providersAnswer = providersAnswer;
    this.providers = providers;
    this.surfaces = List.copyOf(surfaces);
    this.trail = trail;
  }

  public Optional<WireAnswer> answers(AFailedRequest asked) {
    Optional<WireAnswer> inAProvidersWords =
        providersAnswer.to(asked.fault(), asked.address(), asked.statusNothingElseChose());
    if (inAProvidersWords.isPresent()) {
      return inAProvidersWords;
    }
    for (ASurfaceOfThisSimulator surface : surfaces) {
      if (!surface.owns(asked.address())) {
        continue;
      }
      Optional<WireAnswer> inItsOwnWords = surface.theAnswerTo(asked.fault());
      if (inItsOwnWords.isPresent()) {
        return inItsOwnWords;
      }
    }
    if (!nobodyClaims(asked.address())) {
      return Optional.empty();
    }
    return Optional.of(inNobodysWords(asked));
  }

  private static WireAnswer inNobodysWords(AFailedRequest asked) {
    NOBODY_CLAIMED_THE_ADDRESS.info(
        WHAT_THE_SERVER_LOG_SAYS, asked.method(), asked.address(), asked.statusNothingElseChose());
    return WireAnswer.withNothingInIt(asked.statusNothingElseChose());
  }

  private boolean nobodyClaims(String address) {
    if (ThisSimulatorsOwnAddresses.thisSimulatorAnswersIt(address)) {
      return false;
    }
    for (ASurfaceOfThisSimulator surface : surfaces) {
      if (surface.owns(address)) {
        return false;
      }
    }
    return true;
  }

  public void record(AFailedRequest asked) {
    if (asked.fault() != WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED) {
      return;
    }
    Optional<ProviderId> claiming = theProviderClaiming(asked.address());
    if (claiming.isPresent()) {
      recordUnder(asked, claiming.get(), ObjectOrigin.PROVIDER_API);
      return;
    }
    for (ASurfaceOfThisSimulator surface : surfaces) {
      if (!surface.owns(asked.address())) {
        continue;
      }
      Optional<ProviderId> named = surface.theProviderItsAddressNames(asked.address());
      if (named.isPresent()) {
        recordUnder(asked, named.get(), surface.whatReachesIt());
        return;
      }
    }
  }

  private void recordUnder(AFailedRequest asked, ProviderId provider, ObjectOrigin origin) {
    trail.record(
        new AFailureNothingAnswered(
            provider,
            TestRunId.theRunARowAboutThisRequestBelongsTo(asked.theRunItNamed(), TestRunId.UNKNOWN),
            origin,
            asked.method(),
            asked.address(),
            asked.statusNothingElseChose(),
            TheCausesOfAFailure.of(asked.error())));
  }

  private Optional<ProviderId> theProviderClaiming(String address) {
    for (SimulatorProvider provider : providers.modules()) {
      if (provider.wireRoutes().covers(address)) {
        return Optional.of(provider.id());
      }
    }
    return Optional.empty();
  }
}
