package com.platypii.baselinexr.location;

public class NMEAException extends Exception {
    public NMEAException(String msg) {
        super(msg);
    }

    NMEAException(String msg, Exception e) {
        super(msg, e);
    }
}
