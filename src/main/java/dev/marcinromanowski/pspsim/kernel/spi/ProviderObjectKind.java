package dev.marcinromanowski.pspsim.kernel.spi;

public record ProviderObjectKind(String asThisProviderNamesIt, String whatItIsInItsOwnWords) {

  static final String A_LIST_WOULD_PRINT_NOTHING_WHERE_A_READER_LOOKS_FIRST =
      "a provider module must say what kind of thing every object it lists is, in its own word for"
          + " it, because the payments list prints that word in the column a tester reads before"
          + " any other and an object without one leaves a hold, a hosted page and a Pix looking"
          + " like the same row three times over";

  static final String A_NAME_ALONE_IS_A_WORD_A_TESTER_HAS_TO_ALREADY_KNOW =
      "a provider module that names a kind of object must also say what one of them is, because"
          + " the name on its own is a word a tester either knows or does not: the object's own"
          + " screen prints this sentence under that name, and without it a reader who does not"
          + " recognise the word is back to guessing from the provider status";

  static final String A_NAME_IS_NOT_A_SENTENCE =
      " is the word a payments list prints inside one table cell, and it carries the end of a"
          + " sentence, so what was handed in is prose: the sentence about this kind of object"
          + " belongs in whatItIsInItsOwnWords, which the object's own screen prints in full";

  public ProviderObjectKind {
    if (asThisProviderNamesIt.isBlank()) {
      throw new IllegalArgumentException(A_LIST_WOULD_PRINT_NOTHING_WHERE_A_READER_LOOKS_FIRST);
    }
    if (whatItIsInItsOwnWords.isBlank()) {
      throw new IllegalArgumentException(A_NAME_ALONE_IS_A_WORD_A_TESTER_HAS_TO_ALREADY_KNOW);
    }
    if (asThisProviderNamesIt.contains(".")) {
      throw new IllegalArgumentException(asThisProviderNamesIt + A_NAME_IS_NOT_A_SENTENCE);
    }
  }
}
