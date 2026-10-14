package dev.marcinromanowski.pspsim.provider.tranzila;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TranzilaApiController {

  static final String TRANSACTION = "/v1/transaction";

  static final String CREATE = TRANSACTION + "/credit_card/create";

  static final String VERSION_2 = "/v2";

  static final String HANDSHAKE = VERSION_2 + "/handshake";

  static final String CREATE_HANDSHAKE = HANDSHAKE + "/create";

  private final TranzilaApi api;
  private final TranzilaDelivery delivery;

  TranzilaApiController(TranzilaApi api, TranzilaDelivery delivery) {
    this.api = api;
    this.delivery = delivery;
  }

  @PostMapping(CREATE)
  Object create(HttpServletRequest request) {
    return delivery.delivered(api.create(TranzilaWireRequest.callOf(request)));
  }

  @PostMapping(CREATE_HANDSHAKE)
  Object handshake(HttpServletRequest request) {
    return delivery.delivered(api.handshake(TranzilaWireRequest.callOf(request)));
  }
}
