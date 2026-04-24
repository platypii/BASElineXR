package com.platypii.baselinexr.util.tensor;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.util.Numbers;

import java.util.Locale;

/**
 * Represents a 3D vector with x, y, z components
 */
public class Vector3 {

    public double x = 0;
    public double y = 0;
    public double z = 0;

    public Vector3() {
        // Default constructor with zero values
    }

    public Vector3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Return true iff all numbers are real
     */
    public boolean isReal() {
        return Numbers.isReal(x) && Numbers.isReal(y) && Numbers.isReal(z);
    }

    public void set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Vector addition
     */
    @NonNull
    public Vector3 plus(@NonNull Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    /**
     * Vector subtraction
     */
    @NonNull
    public Vector3 minus(@NonNull Vector3 other) {
        return new Vector3(x - other.x, y - other.y, z - other.z);
    }

    /**
     * Scalar multiplication
     */
    @NonNull
    public Vector3 mul(double scalar) {
        return new Vector3(x * scalar, y * scalar, z * scalar);
    }

    /**
     * Scalar division
     */
    @NonNull
    public Vector3 div(double scalar) {
        return new Vector3(x / scalar, y / scalar, z / scalar);
    }

    /**
     * Dot product
     */
    public double dot(@NonNull Vector3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    /**
     * Vector magnitude
     */
    public double magnitude() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * Normalize vector to unit length
     */
    @NonNull
    public Vector3 normalize() {
        double mag = magnitude();
        if (mag == 0) return new Vector3();
        return div(mag);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.US, "[%f, %f, %f]", x, y, z);
    }
}