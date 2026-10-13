package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.List;

@FunctionalInterface
public interface TheValuesARunHasAlreadyProduced {

  List<AValueAFieldOffers> ofTheField(String accountId, String fieldKey);
}
