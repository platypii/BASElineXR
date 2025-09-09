package com.platypii.baselinexr.location;

public record WSEParams(double kl, double kd, double roll) {

    public double ld() {
        if (kd == 0.0) return Double.NaN;
        return kl / kd;
    }

    public double[] ss(){
        double[] ss = new double[2];
        double denom = Math.pow(kl * kl + kd * kd, 0.75);
        ss[0] = kl / denom;
        ss[1] = kd / denom;
        return ss;
    }

}
