package dev.marcinromanowski.pspsim.kernel.smoke;

public sealed interface Applicability {

  record Applies() implements Applicability {}

  record DoesNotApply(String reason) implements Applicability {

    public DoesNotApply {
      if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("a check that does not apply must say why");
      }
    }
  }

  Applies APPLIES = new Applies();
}
