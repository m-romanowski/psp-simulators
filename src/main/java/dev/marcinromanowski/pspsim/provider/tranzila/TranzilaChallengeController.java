package dev.marcinromanowski.pspsim.provider.tranzila;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TranzilaChallengeController {

  private final TranzilaThreeDSecureChallenge challenge;
  private final TranzilaDelivery delivery;

  TranzilaChallengeController(TranzilaThreeDSecureChallenge challenge, TranzilaDelivery delivery) {
    this.challenge = challenge;
    this.delivery = delivery;
  }

  @GetMapping(TranzilaChallengePage.ROUTE)
  Object shown(
      @PathVariable(TranzilaChallengePage.TRACK) String trackId, HttpServletRequest request) {
    return delivery.delivered(challenge.show(TranzilaWireRequest.pageCallOf(request), trackId));
  }

  @PostMapping(TranzilaChallengePage.ROUTE)
  Object answered(
      @PathVariable(TranzilaChallengePage.TRACK) String trackId, HttpServletRequest request) {
    return delivery.delivered(challenge.answer(TranzilaWireRequest.pageCallOf(request), trackId));
  }
}
