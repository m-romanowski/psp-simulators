package dev.marcinromanowski.pspsim.ui;

public record RailDestination(
    RailGroup group,
    String label,
    String path,
    String screen,
    int order,
    boolean theSelectionTravelsWithIt) {}
