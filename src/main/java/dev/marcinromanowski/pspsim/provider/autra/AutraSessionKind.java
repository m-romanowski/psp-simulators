package dev.marcinromanowski.pspsim.provider.autra;

enum AutraSessionKind {
  CARD {
    @Override
    String operation() {
      return AutraOperations.INITIALIZE;
    }

    @Override
    String storyKeyOf(String documentId) {
      return AutraStoryKey.ofSession(documentId);
    }

    @Override
    boolean answersWithACode() {
      return false;
    }
  },
  PIX {
    @Override
    String operation() {
      return AutraOperations.PIX_INITIALIZE;
    }

    @Override
    String storyKeyOf(String documentId) {
      return AutraStoryKey.ofPixSession(documentId);
    }

    @Override
    boolean answersWithACode() {
      return true;
    }
  };

  abstract String operation();

  abstract String storyKeyOf(String documentId);

  abstract boolean answersWithACode();
}
