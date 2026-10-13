package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class ListPaymentMethods {

  private final StripePaymentMethods methods;
  private final StripeCustomers customers;

  ListPaymentMethods(StripePaymentMethods methods, StripeCustomers customers) {
    this.methods = methods;
    this.customers = customers;
  }

  StripeOperationResult everyMethod(StripeForm query, TestRunId testRunId, StripeAccount account) {
    return listed(
        query,
        testRunId,
        account,
        query.first(StripeFields.CUSTOMER),
        StripeApiController.PAYMENT_METHODS);
  }

  StripeOperationResult theMethodsOf(
      StripeForm query, TestRunId testRunId, StripeAccount account, String customerId) {
    Optional<StripeCustomer> addressed =
        StripeCustomerLookup.of(customers, testRunId, account, customerId);
    if (addressed.isEmpty()) {
      return StripeOperationResult.refusedBeforeExecuting(
          RefusalSubject.noObjectAnsweredTo(StripeFields.CUSTOMER),
          StripeErrors.resourceMissing(StripeFields.CUSTOMER));
    }
    return listed(
        query,
        testRunId,
        account,
        customerId,
        StripeApiController.CUSTOMERS + "/" + customerId + StripeApiController.OF_A_CUSTOMER);
  }

  private StripeOperationResult listed(
      StripeForm query,
      TestRunId testRunId,
      StripeAccount account,
      @Nullable String customerId,
      String url) {
    String declaredType = query.first(StripeFields.TYPE);
    if (declaredType == null) {
      return answered(methods.of(testRunId, account.accountId(), customerId, null), url);
    }
    Optional<StripePaymentMethodType> named = StripePaymentMethodType.of(declaredType);
    if (named.isEmpty()) {
      return answered(List.of(), url);
    }
    return answered(methods.of(testRunId, account.accountId(), customerId, named.get()), url);
  }

  private static StripeOperationResult answered(List<StripePaymentMethod> found, String url) {
    return StripeOperationResult.answered(StripePaymentMethodView.listOf(found, url));
  }
}
