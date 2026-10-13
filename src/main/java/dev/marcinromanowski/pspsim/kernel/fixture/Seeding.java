package dev.marcinromanowski.pspsim.kernel.fixture;

public sealed interface Seeding {

  record Seeded(PaymentMethodFixture fixture) implements Seeding {}

  record Claimed(String property, String value) implements Seeding {

    public String message() {
      return property
          + " "
          + value
          + " is already claimed by a payment-method fixture of this provider and test run, and two"
          + " fixtures answering to one name would make which one a request used a matter of"
          + " arrival order. A key that names the owner a method belongs to rather than the method"
          + " itself is the one place this simulator lets many fixtures name one value, and the"
          + " provider declares which of its keys that is.";
    }
  }
}
