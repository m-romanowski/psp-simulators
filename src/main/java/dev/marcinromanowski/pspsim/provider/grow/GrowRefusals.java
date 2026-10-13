package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;

final class GrowRefusals {

  static final String THE_FORM_WAS_REFUSED_BEFORE_ANYTHING_WAS_ADDRESSED =
      "provider grow refuses this request on the form it carries, before it looks for the"
          + " transaction or payment process the form would have named, so this refusal is about"
          + " the request and not about any object this test run holds";

  static final String THE_CREDENTIALS_REACH_NO_MERCHANT =
      "provider grow decides which merchant's objects a request may reach from the credentials the"
          + " form carries, and refuses a form whose credentials reach no merchant of this test run"
          + " without looking for an object at all";

  static final String THE_UNIQUE_IDENTIFIER_IS_ALREADY_HELD =
      "provider grow holds a charge under the unique identifier this request carries and refuses"
          + " the second one, and its published answer reports the refusal without naming the"
          + " charge that holds the identifier";

  static final String THE_GROUP_IS_ALREADY_CHARGED =
      "provider grow holds a charge under the group identifier this request carries and refuses a"
          + " second charge in that group, and its published answer reports the refusal without"
          + " naming the charge that holds the group";

  static final String NO_PROCESS_IS_EVER_ADDRESSED =
      "provider grow answers this operation with a refusal in every case and looks for no payment"
          + " process at all, so no object of this test run is ever addressed by it";

  static final String THE_PROCESS_BELONGS_TO_ANOTHER_MERCHANT =
      "this test run holds the payment process the form named, it was opened by another merchant,"
          + " and provider grow answers a lookup from the wrong merchant with the same refusal it"
          + " gives for a process it does not hold, so its published answer names none";

  static final String A_SCENARIO_DECLINED_BEFORE_A_PROCESS_WAS_STORED =
      "a scenario declined this creation, and provider grow stores a payment process only on the"
          + " answer that reports one, so this test run holds no process for the declined request"
          + " to name";

  static final String A_SCENARIO_DECLINED_BEFORE_A_TRANSACTION_WAS_STORED =
      "a scenario declined this stored-card charge, and provider grow stores a transaction only for"
          + " a charge it carried out, so this test run holds none for the declined request to"
          + " name";

  private GrowRefusals() {}

  static RefusalSubject theFormWasRefused() {
    return RefusalSubject.noObjectWasNamed(THE_FORM_WAS_REFUSED_BEFORE_ANYTHING_WAS_ADDRESSED);
  }

  static RefusalSubject theCredentialsReachNoMerchant() {
    return RefusalSubject.noObjectWasNamed(THE_CREDENTIALS_REACH_NO_MERCHANT);
  }
}
