package dev.marcinromanowski.pspsim.provider.tranzila;

enum TranzilaInsert {
  STORED,

  ID_TAKEN,

  CLOSED_BY_ANOTHER_REQUEST_FIRST;

  static TranzilaInsert ofTheRowsWritten(int written) {
    return written == 1 ? STORED : ID_TAKEN;
  }
}
