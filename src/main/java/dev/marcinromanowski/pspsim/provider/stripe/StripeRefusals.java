package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import org.jspecify.annotations.Nullable;

final class StripeRefusals {

  static final String THE_REQUEST_WAS_REFUSED_BEFORE_ANYTHING_WAS_ADDRESSED =
      "provider stripe refuses this request on the parameters it carries, before the endpoint"
          + " reaches the object those parameters would have named, so this refusal is about the"
          + " request and not about any object this test run holds";

  static final String THE_KEY_REACHES_NO_ACCOUNT =
      "provider stripe decides which account's objects a request may reach from the secret key it"
          + " presents, and refuses a request whose key reaches no account of this test run before"
          + " it addresses an object at all";

  static final String THE_SHORT_LIVED_KEY_IS_REFUSED =
      "provider stripe refuses this request on the ephemeral key it presents rather than on any"
          + " object, because an ephemeral key decides which resource a request may reach before"
          + " the request reaches one";

  static final String THE_IDEMPOTENCY_KEY_IS_ALREADY_CLAIMED =
      "provider stripe holds an earlier request under the idempotency key this one repeats and"
          + " answers the repeat from the key alone, so its published answer names no object even"
          + " though this test run holds the request that claimed the key";

  static final String THE_METHOD_IS_DETACHED_AND_NO_INTENT_EXISTS_YET =
      "this test run holds the payment method this request names and provider stripe publishes it"
          + " as detached and unusable, and the endpoint refuses before it mints the PaymentIntent"
          + " that would have carried the refusal";

  static final String AN_EARLIER_ANSWER_IS_REPLAYED =
      "provider stripe replays under this idempotency key the answer it stored for the first"
          + " request, and its store keeps the object that answer named but not the words the"
          + " first refusal used when it named none";

  private StripeRefusals() {}

  static RefusalSubject theRequestWasRefused() {
    return RefusalSubject.noObjectWasNamed(THE_REQUEST_WAS_REFUSED_BEFORE_ANYTHING_WAS_ADDRESSED);
  }

  static RefusalSubject theKeyReachesNoAccount() {
    return RefusalSubject.noObjectWasNamed(THE_KEY_REACHES_NO_ACCOUNT);
  }

  static RefusalSubject theShortLivedKeyIsRefused() {
    return RefusalSubject.noObjectWasNamed(THE_SHORT_LIVED_KEY_IS_REFUSED);
  }

  static RefusalSubject theAnswerItStoredFor(@Nullable String providerObjectId) {
    return providerObjectId == null
        ? RefusalSubject.noObjectWasNamed(AN_EARLIER_ANSWER_IS_REPLAYED)
        : RefusalSubject.theObject(providerObjectId);
  }
}
