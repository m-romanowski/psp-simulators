package dev.marcinromanowski.pspsim.provider.tranzila;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TranzilaApiController {

  static final String TRANSACTION = "/v1/transaction";

  static final String CREATE = TRANSACTION + "/credit_card/create";

  static final String COMPLETE = TRANSACTION + "/credit_card/3ds/complete";

  static final String TRANSACTIONS = "/v1/transactions";

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

  @PostMapping(COMPLETE)
  Object complete(HttpServletRequest request) {
    return delivery.delivered(api.complete(TranzilaWireRequest.callOf(request)));
  }

  @PostMapping(TRANSACTIONS)
  Object transactions(HttpServletRequest request) {
    return delivery.delivered(api.transactions(TranzilaWireRequest.callOf(request)));
  }

  @PostMapping(CREATE_HANDSHAKE)
  Object handshake(HttpServletRequest request) {
    return delivery.delivered(api.handshake(TranzilaWireRequest.callOf(request)));
  }
}
