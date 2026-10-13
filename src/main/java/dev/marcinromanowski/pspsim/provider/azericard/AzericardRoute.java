package dev.marcinromanowski.pspsim.provider.azericard;

enum AzericardRoute {
  CGI_LINK {
    @Override
    boolean answers(AzericardTransactionType type) {
      return true;
    }
  },

  TOKEN_CGI_LINK {
    @Override
    boolean answers(AzericardTransactionType type) {
      return type.createsThePayment();
    }
  };

  abstract boolean answers(AzericardTransactionType type);
}
