package dev.marcinromanowski.pspsim.provider.autra;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class AutraMerchantDocument {

  private AutraMerchantDocument() {}

  static final String ONE_OF_THE_TWO_IS_REQUIRED =
      "provider autra names the merchant on this read by the header "
          + AutraFields.MERCHANT_DOCUMENT_HEADER
          + ", which it calls \"Preferido: mantem o documento fora da URL e dos logs de acesso\","
          + " or by the query parameter "
          + AutraFields.DOCUMENT_ID
          + ", and publishes that \"um dos dois (header ou documentId) e obrigatorio\". This call"
          + " carries neither";

  static final String A_DOCUMENT_IS_DIGITS_AND_NOTHING_ELSE =
      "provider autra publishes the merchant document as \"CPF ou CNPJ, sem mascara\" and answers"
          + " 422 "
          + AutraErrors.INVALID_DOCUMENT
          + " to a value that is not one. This simulator takes a document of "
          + AutraMerchant.A_DOCUMENT.saidInWords()
          + ", which is the one shape an account of it is configured with, its store width"
          + " included. It does not run the CPF and CNPJ check digits the vendor runs, because an"
          + " account of this simulator is configured with any digits-only document and a"
          + " check-digit rule here would refuse the very merchants the run holds";

  static final String NO_MERCHANT_ANSWERS_TO_THAT_DOCUMENT =
      "the merchant document names no establishment this access token was issued for";

  static Optional<String> askedFor(AutraApiCall call) {
    Optional<String> preferred = call.header(AutraFields.MERCHANT_DOCUMENT_HEADER);
    return preferred.isPresent() ? preferred : call.queryParameter(AutraFields.DOCUMENT_ID);
  }

  static boolean isShapedLikeADocument(String value) {
    return AutraMerchant.A_DOCUMENT.admits(value);
  }

  static @Nullable AutraOperationResult theRefusalOf(AutraApiCall call, AutraMerchant merchant) {
    Optional<String> asked = askedFor(call);
    if (asked.isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.MISSING_DOCUMENT_ID, ONE_OF_THE_TWO_IS_REQUIRED);
    }
    String document = asked.get();
    if (!isShapedLikeADocument(document)) {
      return AutraAnswers.refusedDocument(A_DOCUMENT_IS_DIGITS_AND_NOTHING_ELSE);
    }
    if (!merchant.documentId().equals(document)) {
      return AutraAnswers.noSuchMerchantOnARead(NO_MERCHANT_ANSWERS_TO_THAT_DOCUMENT);
    }
    return null;
  }
}
