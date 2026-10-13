package dev.marcinromanowski.pspsim.kernel.spi;

public record AcceptedValue(String value, String whatItMeans) {

  public AcceptedValue {
    if (value.isBlank()) {
      throw new IllegalArgumentException(
          "an accepted value with no value sends nothing to the provider, so a tester who picked"
              + " it would have configured an account the provider was never told the shape of");
    }
    if (whatItMeans.isBlank()) {
      throw new IllegalArgumentException(
          "accepted value "
              + value
              + " must say what picking it does, because a form offers a tester the sentence and"
              + " never the wire value, and a provider's own wire value means nothing to someone"
              + " who has not read that provider's contract");
    }
    if (theLettersAndDigitsOf(whatItMeans).equals(theLettersAndDigitsOf(value))) {
      throw new IllegalArgumentException(
          "accepted value "
              + value
              + " offers its own wire value as the sentence a tester reads, which puts the same"
              + " jargon in a list that a tester had to guess at in a text box: say instead what"
              + " picking it makes this provider do, in the words the difference between the"
              + " values would be described in");
    }
  }

  private static String theLettersAndDigitsOf(String text) {
    StringBuilder kept = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char character = text.charAt(i);
      if (Character.isLetterOrDigit(character)) {
        kept.append(Character.toLowerCase(character));
      }
    }
    return kept.toString();
  }
}
