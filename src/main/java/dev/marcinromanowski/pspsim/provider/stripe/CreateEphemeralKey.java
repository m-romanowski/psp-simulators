package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;

final class CreateEphemeralKey {

  static final int ID_ATTEMPTS = 5;

  static final String ID_PREFIX = "ephkey_sim_";

  static final int ID_LENGTH = 16;

  static final Duration THE_HOUR_READ_OFF_ONE_PUBLISHED_SAMPLE = Duration.ofHours(1);

  private final StripeEphemeralKeys keys;
  private final StripeCustomers customers;
  private final IdGenerator ids;

  CreateEphemeralKey(StripeEphemeralKeys keys, StripeCustomers customers, IdGenerator ids) {
    this.keys = keys;
    this.customers = customers;
    this.ids = ids;
  }

  StripeOperationResult handle(
      StripeForm form, TestRunId testRunId, StripeAccount account, Instant now) {
    String customerId = form.first(StripeFields.CUSTOMER);
    if (customerId == null) {
      return StripeOperationResult.refusedBeforeExecuting(
          StripeRefusals.theRequestWasRefused(), StripeErrors.noResourceToMintAKeyFor());
    }
    if (StripeCustomerLookup.of(customers, testRunId, account, customerId).isEmpty()) {
      return StripeOperationResult.refusedBeforeExecuting(
          RefusalSubject.noObjectAnsweredTo(StripeFields.CUSTOMER),
          StripeErrors.resourceMissing(StripeFields.CUSTOMER));
    }
    for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
      String ephemeralKeyId = ID_PREFIX + ids.hex(ID_LENGTH);
      StripeEphemeralKey key =
          new StripeEphemeralKey(
              testRunId,
              ephemeralKeyId,
              account.accountId(),
              customerId,
              StripeEphemeralKeySecret.drawnApartFromTheKeysOwnId(ids),
              now,
              now.plus(THE_HOUR_READ_OFF_ONE_PUBLISHED_SAMPLE));
      if (keys.insertIfAbsent(key)) {
        return StripeOperationResult.succeeded(ephemeralKeyId, StripeEphemeralKeyView.of(key));
      }
    }
    throw new IllegalStateException(
        "no Stripe ephemeral key id could be claimed in " + ID_ATTEMPTS + " attempts");
  }
}
