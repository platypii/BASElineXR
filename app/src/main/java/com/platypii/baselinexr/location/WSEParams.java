package com.platypii.baselinexr.location;

public record WSEParams(double kl, double kd, double roll) {

    public double ld() {
        if (kd == 0.0) return Double.NaN;
        return kl / kd;
    }
}
