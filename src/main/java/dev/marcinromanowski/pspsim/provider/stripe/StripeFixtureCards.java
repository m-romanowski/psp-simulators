package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderFixtureRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class StripeFixtureCards {

  private StripeFixtureCards() {}

  static final String CUSTOMER_PROPERTY = "providerIds.customerId";

  static final String PAYMENT_METHOD_PROPERTY = "providerIds.paymentMethodId";

  static final String MERCHANT_PROPERTY = "merchantId";

  static final String TYPE_PROPERTY = "type";

  private static final String NO_TOKEN_OBJECT =
      "provider stripe publishes a Tokens API this simulator does not build: there is no Token"
          + " object here, so a seeded token would name nothing any route of this build reads. A"
          + " card is set up for later use through a SetupIntent instead, which this build serves"
          + " and which attaches a seeded payment method to a customer";

  static final String A_CUSTOMER_ID_MINTED_THE_WAY_THIS_SIMULATOR_MINTS_ONE =
      CreateCustomer.ID_PREFIX + "0123456789abcdef";

  static final String A_CUSTOMER_ID_IS_NOT_A_MINTED_OBJECT_ID =
      CUSTOMER_PROPERTY
          + " must not begin with "
          + CreatePaymentIntent.ID_PREFIX
          + " or "
          + CreateRefund.ID_PREFIX
          + " or "
          + CreateSetupIntent.ID_PREFIX
          + " or "
          + StripeChargeMinting.ID_PREFIX
          + " or "
          + CreateEphemeralKey.ID_PREFIX
          + ", which are the prefixes this simulator mints PaymentIntent, Refund, SetupIntent,"
          + " Charge and EphemeralKey ids under; a customer seeded under one of them would make"
          + " one providerObjectId resolve to two objects on the read surface";

  static final String A_CUSTOMER_IS_SERVED_THROUGH_AN_ACCOUNTS_OWN_KEY =
      CUSTOMER_PROPERTY
          + " names a customer no route of provider stripe could ever answer for, because"
          + " merchantId names no account this test run configures for stripe. A stripe customer"
          + " belongs to an account and is addressed with that account's own secret key: every"
          + " one of "
          + StripeApiController.CUSTOMERS
          + ", the retrieve, the update and the delete resolves the customer through the key that"
          + " authenticated the call, so a customer stored under an account no key belongs to"
          + " would sit on the object list looking readable and be answered by none of them."
          + " Configure that account first, or name one this test run already configures. The"
          + " core does not check merchantId and is right not to: which values a providerIds key"
          + " can be served with is the provider's own to decide, and this is stripe deciding it.";

  static final String A_CUSTOMER_ID_IS_AS_LONG_AS_STRIPE_CARRIES =
      CUSTOMER_PROPERTY
          + " must be at most "
          + StripeCustomer.LONGEST_CUSTOMER_ID
          + " characters, which is the width a PaymentIntent of this simulator carries its"
          + " customer in; a longer value would be seeded and could never be named on a payment";

  static final String A_PAYMENT_METHOD_ID_IS_NOT_A_MINTED_OBJECT_ID =
      PAYMENT_METHOD_PROPERTY
          + " must not begin with "
          + CreatePaymentIntent.ID_PREFIX
          + ", "
          + CreateRefund.ID_PREFIX
          + " or "
          + CreateCustomer.ID_PREFIX
          + " or "
          + CreateSetupIntent.ID_PREFIX
          + " or "
          + StripeChargeMinting.ID_PREFIX
          + " or "
          + CreateEphemeralKey.ID_PREFIX
          + ", which are the prefixes this simulator mints PaymentIntent, Refund, Customer,"
          + " SetupIntent, Charge and EphemeralKey ids under; a payment method seeded under one of"
          + " them would make one providerObjectId resolve to two objects on the read surface";

  static final String A_PAYMENT_METHOD_ID_IS_AS_LONG_AS_STRIPE_CARRIES =
      PAYMENT_METHOD_PROPERTY
          + " must be at most "
          + StripePaymentMethod.LONGEST_PAYMENT_METHOD_ID
          + " characters, which is the width a PaymentIntent of this simulator carries its"
          + " payment_method in; a longer value would be seeded and could never be named on a"
          + " payment";

  static final String A_PAYMENT_METHOD_IS_SERVED_THROUGH_AN_ACCOUNTS_OWN_KEY =
      MERCHANT_PROPERTY
          + " must name an account this test run configures for provider stripe, because a seeded"
          + " payment method is answered by "
          + StripeApiController.PAYMENT_METHODS
          + " and by the two listing routes beside it, and every one of them resolves the method"
          + " through the secret key that authenticated the call. A payment method stored against"
          + " an account no key belongs to would sit on the object list looking readable and be"
          + " answered by none of them.";

  static final String A_STRIPE_METHOD_IS_A_CARD_OR_A_WALLET =
      TYPE_PROPERTY
          + " must be "
          + PaymentMethodKind.CARD.publishedName()
          + ", which provider stripe publishes as type "
          + StripePaymentMethodType.CARD.wireValue()
          + ", or "
          + PaymentMethodKind.WALLET.publishedName()
          + ", which this simulator serves as the published type "
          + StripePaymentMethodType.PAYPAL.wireValue()
          + ". "
          + NO_TOKEN_OBJECT;

  static final ProviderPaymentMethodFields PAYMENT_METHOD_FIELDS =
      new ProviderPaymentMethodFields(
          StripeApi.STRIPE,
          List.of(
              new ConfigurableField(
                  ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                  "payment_method",
                  "The id a PaymentIntent of this account is created or confirmed with.",
                  true,
                  false,
                  List.of(),
                  StripePersonas.THE_PUBLISHED_PAYMENT_METHOD_ID),
              new ConfigurableField(
                  ProviderPaymentMethodFields.CUSTOMER_ID,
                  "customer",
                  "The id a PaymentIntent is created with. Naming one this test run holds no"
                      + " customer under records that customer against the account merchantId"
                      + " names, so it is listed and retrievable with that account's key like one"
                      + " POST /v1/customers created.",
                  false,
                  false,
                  List.of(),
                  A_CUSTOMER_ID_MINTED_THE_WAY_THIS_SIMULATOR_MINTS_ONE)),
          Map.of(ProviderPaymentMethodFields.TOKEN, NO_TOKEN_OBJECT),
          Set.of(ProviderPaymentMethodFields.CUSTOMER_ID));

  static @Nullable String customerIdOf(NewPaymentMethodFixture fixture) {
    return fixture.providerIds().get(ProviderPaymentMethodFields.CUSTOMER_ID);
  }

  static String paymentMethodIdOf(NewPaymentMethodFixture fixture) {
    return theOnlyIdAStripeMethodIsDrivenBy(
        fixture.providerIds().get(ProviderPaymentMethodFields.PAYMENT_METHOD_ID));
  }

  static String paymentMethodIdOf(PaymentMethodFixture fixture) {
    return theOnlyIdAStripeMethodIsDrivenBy(
        fixture.providerIds().get(ProviderPaymentMethodFields.PAYMENT_METHOD_ID));
  }

  private static String theOnlyIdAStripeMethodIsDrivenBy(@Nullable String paymentMethodId) {
    if (paymentMethodId == null) {
      throw new IllegalStateException(
          "a payment-method fixture of provider stripe reached the store with no"
              + " providerIds.paymentMethodId, although the seed refuses one that leaves it out");
    }
    return paymentMethodId;
  }

  static StripePaymentMethodType theTypeStripePublishesFor(NewPaymentMethodFixture fixture) {
    Optional<StripePaymentMethodType> named = StripePaymentMethodType.seededAs(fixture.type());
    if (named.isEmpty()) {
      throw new ProviderFixtureRejectedException(
          TYPE_PROPERTY, A_STRIPE_METHOD_IS_A_CARD_OR_A_WALLET);
    }
    return named.get();
  }

  static StripeAccount theAccountThatWillServeTheMethod(
      NewPaymentMethodFixture fixture, @Nullable StripeAccount account) {
    String paymentMethodId = paymentMethodIdOf(fixture);
    if (paymentMethodId.length() > StripePaymentMethod.LONGEST_PAYMENT_METHOD_ID) {
      throw new ProviderFixtureRejectedException(
          PAYMENT_METHOD_PROPERTY, A_PAYMENT_METHOD_ID_IS_AS_LONG_AS_STRIPE_CARRIES);
    }
    if (paymentMethodId.startsWith(CreatePaymentIntent.ID_PREFIX)
        || paymentMethodId.startsWith(CreateRefund.ID_PREFIX)
        || paymentMethodId.startsWith(CreateCustomer.ID_PREFIX)
        || paymentMethodId.startsWith(CreateSetupIntent.ID_PREFIX)
        || paymentMethodId.startsWith(StripeChargeMinting.ID_PREFIX)
        || paymentMethodId.startsWith(CreateEphemeralKey.ID_PREFIX)) {
      throw new ProviderFixtureRejectedException(
          PAYMENT_METHOD_PROPERTY, A_PAYMENT_METHOD_ID_IS_NOT_A_MINTED_OBJECT_ID);
    }
    String customerId = customerIdOf(fixture);
    if (customerId != null) {
      return theAccountThatWillAnswerForIt(customerId, account);
    }
    if (account == null) {
      throw new ProviderFixtureRejectedException(
          MERCHANT_PROPERTY, A_PAYMENT_METHOD_IS_SERVED_THROUGH_AN_ACCOUNTS_OWN_KEY);
    }
    return account;
  }

  static StripeAccount theAccountThatWillAnswerForIt(
      String customerId, @Nullable StripeAccount account) {
    if (customerId.length() > StripeCustomer.LONGEST_CUSTOMER_ID) {
      throw new ProviderFixtureRejectedException(
          CUSTOMER_PROPERTY, A_CUSTOMER_ID_IS_AS_LONG_AS_STRIPE_CARRIES);
    }
    if (customerId.startsWith(CreatePaymentIntent.ID_PREFIX)
        || customerId.startsWith(CreateRefund.ID_PREFIX)
        || customerId.startsWith(CreateSetupIntent.ID_PREFIX)
        || customerId.startsWith(StripeChargeMinting.ID_PREFIX)
        || customerId.startsWith(CreateEphemeralKey.ID_PREFIX)) {
      throw new ProviderFixtureRejectedException(
          CUSTOMER_PROPERTY, A_CUSTOMER_ID_IS_NOT_A_MINTED_OBJECT_ID);
    }
    if (account == null) {
      throw new ProviderFixtureRejectedException(
          CUSTOMER_PROPERTY, A_CUSTOMER_IS_SERVED_THROUGH_AN_ACCOUNTS_OWN_KEY);
    }
    return account;
  }
}
