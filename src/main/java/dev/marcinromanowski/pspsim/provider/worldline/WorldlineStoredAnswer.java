package dev.marcinromanowski.pspsim.provider.worldline;

import org.jspecify.annotations.Nullable;

record WorldlineStoredAnswer(
    int httpStatus, String contentType, String body, @Nullable String providerObjectId) {}
