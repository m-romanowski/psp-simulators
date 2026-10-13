package dev.marcinromanowski.pspsim.kernel.ids;

public interface IdGenerator {

  String hex(int length);

  String digits(int length);
}
