package dev.marcinromanowski.pspsim.kernel.spi;

public final class ObjectActionControl {

  private ObjectActionControl() {}

  public static ObjectActionOutcome perform(SimulatorProvider module, ObjectActionRequest request) {
    ObjectActions declared = module.objectActions();
    if (!declared.offers(request.action())) {
      return ObjectActionOutcome.notOffered(declared, request.action());
    }
    return module.act(request);
  }
}
