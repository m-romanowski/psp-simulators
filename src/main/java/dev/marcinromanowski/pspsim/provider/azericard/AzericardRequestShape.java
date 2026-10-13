package dev.marcinromanowski.pspsim.provider.azericard;

import java.util.List;

enum AzericardRequestShape {
  AUTHORIZATION {
    @Override
    List<AzericardRequiredField> required() {
      return AzericardFields.REQUIRED_ON_AUTHORIZATION;
    }

    @Override
    byte[] macSourceOf(AzericardForm form) {
      return AzericardMac.sourceOf(
          form.required(AzericardFields.AMOUNT),
          form.required(AzericardFields.CURRENCY),
          form.required(AzericardFields.TERMINAL),
          form.required(AzericardFields.TRTYPE),
          form.required(AzericardFields.TIMESTAMP),
          form.required(AzericardFields.NONCE),
          form.first(AzericardFields.MERCH_URL));
    }
  },

  SETTLEMENT {
    @Override
    List<AzericardRequiredField> required() {
      return AzericardFields.REQUIRED_ON_SETTLEMENT;
    }

    @Override
    byte[] macSourceOf(AzericardForm form) {
      return AzericardMac.sourceOf(
          form.required(AzericardFields.AMOUNT),
          form.required(AzericardFields.CURRENCY),
          form.required(AzericardFields.ORDER),
          form.required(AzericardFields.RRN),
          form.required(AzericardFields.INT_REF),
          form.required(AzericardFields.TERMINAL),
          form.required(AzericardFields.TRTYPE),
          form.required(AzericardFields.TIMESTAMP),
          form.required(AzericardFields.NONCE));
    }
  },

  STATUS {
    @Override
    List<AzericardRequiredField> required() {
      return AzericardFields.REQUIRED_ON_STATUS;
    }

    @Override
    byte[] macSourceOf(AzericardForm form) {
      return AzericardMac.sourceOf(
          form.required(AzericardFields.ORDER),
          form.required(AzericardFields.TERMINAL),
          form.required(AzericardFields.TRTYPE),
          form.required(AzericardFields.TIMESTAMP),
          form.required(AzericardFields.NONCE));
    }
  };

  abstract List<AzericardRequiredField> required();

  abstract byte[] macSourceOf(AzericardForm form);
}
