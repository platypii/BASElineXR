package com.platypii.baselinexr;

public record MiniMapOptions(
    double latMin,
    double latMax,
    double lngMin,
    double lngMax,
    int drawableResource
) {}