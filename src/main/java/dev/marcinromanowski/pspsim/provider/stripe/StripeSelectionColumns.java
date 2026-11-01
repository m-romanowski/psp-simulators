package dev.marcinromanowski.pspsim.provider.stripe;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

final class StripeSelectionColumns {

  static final String NAMES =
      "automatic_payment_methods_enabled, automatic_payment_methods_allow_redirects,"
          + " allowed_payment_method_types, excluded_payment_method_types,"
          + " payment_method_configuration, payment_method_types";

  static final String PLACEHOLDERS = "?, ?, ?, ?, ?, ?";

  static final String ASSIGNMENTS =
      "automatic_payment_methods_enabled = ?, automatic_payment_methods_allow_redirects = ?,"
          + " allowed_payment_method_types = ?, excluded_payment_method_types = ?,"
          + " payment_method_configuration = ?, payment_method_types = ?";

  private StripeSelectionColumns() {}

  static JdbcClient.StatementSpec bound(
      JdbcClient.StatementSpec statement, StripeSelection selection) {
    StripeRedirects redirects = selection.allowRedirects();
    return statement
        .param(selection.automatic())
        .param(redirects == null ? null : redirects.wireValue())
        .param(arrayOf(selection.allowed()))
        .param(arrayOf(selection.excluded()))
        .param(selection.configuration())
        .param(arrayOf(selection.offeredWireValues()));
  }

  static StripeSelection read(ResultSet rs) throws SQLException {
    String redirects = rs.getString("automatic_payment_methods_allow_redirects");
    List<StripePaymentMethodType> offered = new ArrayList<>();
    for (String value : listOf(rs.getArray("payment_method_types"))) {
      offered.add(StripePaymentMethodType.of(value).orElseThrow());
    }
    return new StripeSelection(
        rs.getObject("automatic_payment_methods_enabled", Boolean.class),
        redirects == null ? null : StripeRedirects.of(redirects).orElseThrow(),
        nullableListOf(rs.getArray("allowed_payment_method_types")),
        nullableListOf(rs.getArray("excluded_payment_method_types")),
        rs.getString("payment_method_configuration"),
        offered);
  }

  private static String @Nullable [] arrayOf(@Nullable List<String> values) {
    return values == null ? null : values.toArray(String[]::new);
  }

  private static @Nullable List<String> nullableListOf(@Nullable Array array) throws SQLException {
    return array == null ? null : listOf(array);
  }

  private static List<String> listOf(Array array) throws SQLException {
    return List.of((String[]) array.getArray());
  }
}
