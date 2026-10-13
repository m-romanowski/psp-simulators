package dev.marcinromanowski.pspsim.kernel.spi;

import static java.util.Collections.unmodifiableSequencedMap;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;

public record ProviderOriginationFields(
    ProviderId provider,
    List<DeclaredOrigination> waysToStartAPayment,
    SequencedMap<String, String> fieldsItFillsInItself) {

  public ProviderOriginationFields {
    if (waysToStartAPayment.isEmpty()) {
      throw new IllegalArgumentException(
          "provider "
              + provider.value()
              + " declares no way of starting a payment, so no payment of it could ever begin"
              + " anywhere but on its own wire, and a tester without a client application would"
              + " have nothing to test against");
    }
    Map<String, DeclaredOrigination> byName = new LinkedHashMap<>();
    for (DeclaredOrigination way : waysToStartAPayment) {
      if (byName.put(way.name(), way) != null) {
        throw new IllegalArgumentException(
            "provider "
                + provider.value()
                + " declares "
                + way.name()
                + " twice as a way of starting a payment, so one answer from a tester would"
                + " address two of them");
      }
      for (AFieldATesterFillsIn field : way.fields()) {
        if (fieldsItFillsInItself.containsKey(field.key())) {
          throw new IllegalArgumentException(
              "provider "
                  + provider.value()
                  + " asks a tester to type "
                  + field.key()
                  + " and at the same time fills it in itself, and a wizard reading both would ask"
                  + " a tester for a value that is overwritten before the provider ever sees it,"
                  + " or put a credential of the account on a screen");
        }
      }
    }
    for (Map.Entry<String, String> supplied : fieldsItFillsInItself.entrySet()) {
      if (supplied.getValue().isBlank()) {
        throw new IllegalArgumentException(
            "a provider that fills "
                + supplied.getKey()
                + " in itself must say, in its own words, where the value comes from: a blank"
                + " field reads as a value this simulator failed to record rather than as one no"
                + " tester is ever asked for");
      }
    }
    waysToStartAPayment = List.copyOf(waysToStartAPayment);
    fieldsItFillsInItself = unmodifiableSequencedMap(new LinkedHashMap<>(fieldsItFillsInItself));
  }

  public List<String> names() {
    List<String> names = new ArrayList<>();
    for (DeclaredOrigination way : waysToStartAPayment) {
      names.add(way.name());
    }
    return List.copyOf(names);
  }

  public Optional<DeclaredOrigination> wayNamed(String name) {
    for (DeclaredOrigination way : waysToStartAPayment) {
      if (way.name().equals(name)) {
        return Optional.of(way);
      }
    }
    return Optional.empty();
  }

  public String theWaysItOffers() {
    List<String> offered = new ArrayList<>();
    for (DeclaredOrigination way : waysToStartAPayment) {
      offered.add(way.label() + " (" + way.name() + ")");
    }
    return String.join(", ", offered);
  }
}
