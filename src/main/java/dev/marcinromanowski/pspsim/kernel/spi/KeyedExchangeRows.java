package dev.marcinromanowski.pspsim.kernel.spi;

public interface KeyedExchangeRows {

  int recordedAtTheNextFreeSequence(KeyedExchangeRow row);
}
