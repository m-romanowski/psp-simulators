package com.example.shop;

public sealed interface CaptureOutcome {

  record Captured() implements CaptureOutcome {}

  record Refused(String code) implements CaptureOutcome {}
}
