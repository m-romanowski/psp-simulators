package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutraMerchantDocumentTest {

  private static final TestRunId RUN = TestRunId.of("autra-merchant-document");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";

  private static final AutraMerchant MERCHANT =
      AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT, Map.of());

  private static AutraApiCall callNaming(
      Map<String, List<String>> headers, Map<String, List<String>> query) {
    return AutraWire.get(AutraApiController.PAYMENTS, headers, query);
  }

  private static AutraApiCall inTheHeader(String document) {
    return callNaming(Map.of(AutraFields.MERCHANT_DOCUMENT_HEADER, List.of(document)), Map.of());
  }

  private static AutraApiCall inTheQuery(String document) {
    return callNaming(Map.of(), AutraWire.query(AutraFields.DOCUMENT_ID, document));
  }

  @Test
  void the_header_the_vendor_prefers_is_spelled_the_way_the_vendor_spells_it() {
    assertThat(AutraFields.MERCHANT_DOCUMENT_HEADER).isEqualTo("X-Merchant-Document");
  }

  @Test
  void a_document_in_the_header_alone_is_the_one_read() {
    assertThat(AutraMerchantDocument.askedFor(inTheHeader(DOCUMENT))).contains(DOCUMENT);
  }

  @Test
  void a_document_in_the_query_alone_is_the_one_read() {
    assertThat(AutraMerchantDocument.askedFor(inTheQuery(DOCUMENT))).contains(DOCUMENT);
  }

  @Test
  void the_header_is_the_one_read_when_a_call_names_a_document_in_both_places() {
    AutraApiCall both =
        callNaming(
            Map.of(AutraFields.MERCHANT_DOCUMENT_HEADER, List.of(DOCUMENT)),
            AutraWire.query(AutraFields.DOCUMENT_ID, "11111111111"));

    assertThat(AutraMerchantDocument.askedFor(both)).contains(DOCUMENT);
  }

  @Test
  void a_call_naming_a_document_nowhere_names_none() {
    assertThat(AutraMerchantDocument.askedFor(callNaming(Map.of(), Map.of()))).isEmpty();
  }

  @Test
  void a_header_sent_blank_falls_through_to_the_query_parameter() {
    AutraApiCall blank =
        callNaming(
            Map.of(AutraFields.MERCHANT_DOCUMENT_HEADER, List.of("")),
            AutraWire.query(AutraFields.DOCUMENT_ID, DOCUMENT));

    assertThat(AutraMerchantDocument.askedFor(blank)).contains(DOCUMENT);
  }

  @Test
  void a_document_is_digits_and_no_longer_than_the_column_that_keeps_it() {
    assertThat(AutraMerchantDocument.isShapedLikeADocument("25055662000190")).isTrue();
    assertThat(AutraMerchantDocument.isShapedLikeADocument("1")).isTrue();
    assertThat(AutraMerchantDocument.isShapedLikeADocument("1".repeat(32))).isTrue();
    assertThat(AutraMerchantDocument.isShapedLikeADocument("1".repeat(33))).isFalse();
    assertThat(AutraMerchantDocument.isShapedLikeADocument("")).isFalse();
    assertThat(AutraMerchantDocument.isShapedLikeADocument("25.055.662/0001-90")).isFalse();
    assertThat(AutraMerchantDocument.isShapedLikeADocument("2505566200019O")).isFalse();
  }

  @Test
  void a_call_naming_the_merchant_nowhere_is_refused_400_missing_document_id() {
    AutraOperationResult refused =
        AutraMerchantDocument.theRefusalOf(callNaming(Map.of(), Map.of()), MERCHANT);

    assertThat(refused).isNotNull();
    assertThat(refused.body().httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(refused)).isEqualTo(AutraErrors.MISSING_DOCUMENT_ID);
    assertThat(theMessageIn(refused)).isEqualTo(AutraMerchantDocument.ONE_OF_THE_TWO_IS_REQUIRED);
  }

  @Test
  void a_document_of_the_wrong_shape_is_refused_422_invalid_document() {
    AutraOperationResult refused =
        AutraMerchantDocument.theRefusalOf(inTheQuery("25.055.662/0001-90"), MERCHANT);

    assertThat(refused).isNotNull();
    assertThat(refused.body().httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(refused)).isEqualTo(AutraErrors.INVALID_DOCUMENT);
    assertThat(theMessageIn(refused))
        .isEqualTo(AutraMerchantDocument.A_DOCUMENT_IS_DIGITS_AND_NOTHING_ELSE);
  }

  @Test
  void a_document_of_the_right_shape_naming_another_establishment_is_refused_404() {
    AutraOperationResult refused =
        AutraMerchantDocument.theRefusalOf(inTheQuery("11111111111"), MERCHANT);

    assertThat(refused).isNotNull();
    assertThat(refused.body().httpStatus()).isEqualTo(404);
    assertThat(theCodeIn(refused)).isEqualTo(AutraErrors.MERCHANT_NOT_FOUND);
    assertThat(theMessageIn(refused))
        .isEqualTo(AutraMerchantDocument.NO_MERCHANT_ANSWERS_TO_THAT_DOCUMENT);
  }

  @Test
  void the_document_this_token_was_issued_for_is_refused_by_nothing() {
    assertThat(AutraMerchantDocument.theRefusalOf(inTheQuery(DOCUMENT), MERCHANT)).isNull();
    assertThat(AutraMerchantDocument.theRefusalOf(inTheHeader(DOCUMENT), MERCHANT)).isNull();
  }

  private static String theCodeIn(AutraOperationResult result) {
    return theFirstErrorIn(result).text(AutraFields.CODE).orElseThrow();
  }

  private static String theMessageIn(AutraOperationResult result) {
    return theFirstErrorIn(result).text(AutraFields.MSG).orElseThrow();
  }

  private static AutraJson.JsonObject theFirstErrorIn(AutraOperationResult result) {
    AutraJson.JsonObject body = AutraJsonReader.objectIn(result.body().text()).orElseThrow();
    AutraJson.JsonArray errors = (AutraJson.JsonArray) body.member(AutraFields.ERRORS);
    return (AutraJson.JsonObject) errors.elements().getFirst();
  }
}
