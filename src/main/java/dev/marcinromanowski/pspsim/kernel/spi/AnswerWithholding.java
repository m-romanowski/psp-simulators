package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface AnswerWithholding {

  Map<String, List<String>> withheld(Map<String, List<String>> answered);
}
