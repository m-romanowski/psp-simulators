package dev.marcinromanowski.pspsim.provider.tranzila;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TranzilaReportController {

  static final String HOST = "/tranzila/report";

  static final String TRANSACTION = HOST + "/v1/transaction";

  private final TranzilaApi api;
  private final TranzilaDelivery delivery;

  TranzilaReportController(TranzilaApi api, TranzilaDelivery delivery) {
    this.api = api;
    this.delivery = delivery;
  }

  @PostMapping(TRANSACTION)
  Object transaction(HttpServletRequest request) {
    return delivery.delivered(api.report(TranzilaWireRequest.callOf(request)));
  }
}
