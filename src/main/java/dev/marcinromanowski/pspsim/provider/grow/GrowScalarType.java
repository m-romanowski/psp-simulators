package dev.marcinromanowski.pspsim.provider.grow;

enum GrowScalarType {
  INTEGER {
    @Override
    GrowJson of(long value) {
      return GrowJson.number(value);
    }
  },
  STRING {
    @Override
    GrowJson of(long value) {
      return GrowJson.text(Long.toString(value));
    }
  };

  abstract GrowJson of(long value);
}
