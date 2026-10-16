package dev.marcinromanowski.pspsim.provider.tranzila;

enum TranzilaInsert {
  STORED,

  ID_TAKEN,

  CLOSED_BY_ANOTHER_REQUEST_FIRST;

  static TranzilaInsert ofTheRowsWritten(int written, int holdingTheId) {
    if (written == 1) {
      return STORED;
    }
    return holdingTheId > 0 ? ID_TAKEN : CLOSED_BY_ANOTHER_REQUEST_FIRST;
  }
}
