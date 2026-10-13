package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WhatAPayerPageMayLoad {

  public static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";

  public static final String NOTHING_BUT_THE_PAGES_OWN_STYLE =
      "default-src 'none'; style-src 'unsafe-inline'";

  public static final String CACHE_CONTROL = "Cache-Control";

  public static final String NOTHING_IS_STORED = "no-store";

  public static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

  public static final String NOTHING_IS_SNIFFED = "nosniff";

  public static final String REFERRER_POLICY = "Referrer-Policy";

  public static final String NOTHING_IS_REFERRED = "no-referrer";

  private WhatAPayerPageMayLoad() {}

  public static Map<String, String> onADocumentDeclaring(String policy) {
    Map<String, String> declared = new LinkedHashMap<>();
    declared.put(CACHE_CONTROL, NOTHING_IS_STORED);
    declared.put(CONTENT_TYPE_OPTIONS, NOTHING_IS_SNIFFED);
    declared.put(REFERRER_POLICY, NOTHING_IS_REFERRED);
    declared.put(CONTENT_SECURITY_POLICY, policy);
    return Collections.unmodifiableMap(declared);
  }

  public static Map<String, List<String>> eachOnADocumentDeclaring(String policy) {
    Map<String, List<String>> declared = new LinkedHashMap<>();
    onADocumentDeclaring(policy).forEach((name, value) -> declared.put(name, List.of(value)));
    return Collections.unmodifiableMap(declared);
  }
}
