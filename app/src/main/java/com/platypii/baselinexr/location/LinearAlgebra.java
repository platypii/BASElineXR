package com.platypii.baselinexr.location;

/**
 * Linear algebra utility functions for matrix operations.
 * Provides static methods for basic matrix operations including
 * addition, subtraction, multiplication, transpose, and inversion.
 */
public final class LinearAlgebra {

    private LinearAlgebra() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates an n x n identity matrix.
     */
    public static double[][] identity(int n) {
        final double[][] I = new double[n][n];
        for (int i = 0; i < n; i++) I[i][i] = 1.0;
        return I;
    }

    /**
     * Creates an r x c zero matrix.
     */
    public static double[][] zeros(int r, int c) {
        return new double[r][c];
    }

    /**
     * Matrix addition: C = A + B.
     */
    public static double[][] add(double[][] A, double[][] B) {
        final int r = A.length, c = A[0].length;
        final double[][] C = new double[r][c];
        for (int i = 0; i < r; i++)
            for (int j = 0; j < c; j++)
                C[i][j] = A[i][j] + B[i][j];
        return C;
    }

    /**
     * Matrix subtraction: C = A - B.
     */
    public static double[][] sub(double[][] A, double[][] B) {
        final int r = A.length, c = A[0].length;
        final double[][] C = new double[r][c];
        for (int i = 0; i < r; i++)
            for (int j = 0; j < c; j++)
                C[i][j] = A[i][j] - B[i][j];
        return C;
    }

    /**
     * Matrix multiplication: C = A * B.
     */
    public static double[][] mul(double[][] A, double[][] B) {
        final int r = A.length, n = A[0].length, c = B[0].length;
        final double[][] C = new double[r][c];
        for (int i = 0; i < r; i++) {
            for (int k = 0; k < n; k++) {
                final double a = A[i][k];
                if (a == 0.0) continue;
                for (int j = 0; j < c; j++) C[i][j] += a * B[k][j];
            }
        }
        return C;
    }

    /**
     * Matrix-vector multiplication: y = A * x.
     */
    public static double[] mul(double[][] A, double[] x) {
        final int r = A.length, c = A[0].length;
        final double[] y = new double[r];
        for (int i = 0; i < r; i++) {
            double sum = 0.0;
            for (int j = 0; j < c; j++) sum += A[i][j] * x[j];
            y[i] = sum;
        }
        return y;
    }

    /**
     * Matrix transpose: AT = A^T.
     */
    public static double[][] transpose(double[][] A) {
        final int r = A.length, c = A[0].length;
        final double[][] AT = new double[c][r];
        for (int i = 0; i < r; i++)
            for (int j = 0; j < c; j++)
                AT[j][i] = A[i][j];
        return AT;
    }

    /**
     * Matrix inversion using Gauss-Jordan elimination with partial pivoting.
     * Sufficient for small matrices (6x6, 12x12).
     */
    public static double[][] inverse(double[][] A) {
        final int n = A.length;
        final double[][] aug = new double[n][2*n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, aug[i], 0, n);
            aug[i][n + i] = 1.0;
        }

        for (int col = 0; col < n; col++) {
            // Pivot
            int pivot = col;
            double max = Math.abs(aug[pivot][col]);
            for (int r = col + 1; r < n; r++) {
                final double v = Math.abs(aug[r][col]);
                if (v > max) { max = v; pivot = r; }
            }
            if (max < 1e-12) throw new IllegalStateException("Singular matrix");

            if (pivot != col) {
                final double[] tmp = aug[col];
                aug[col] = aug[pivot];
                aug[pivot] = tmp;
            }

            // Normalize pivot row
            final double pivotVal = aug[col][col];
            final double inv = 1.0 / pivotVal;
            for (int j = 0; j < 2*n; j++) aug[col][j] *= inv;

            // Eliminate other rows
            for (int r = 0; r < n; r++) {
                if (r == col) continue;
                final double factor = aug[r][col];
                if (factor == 0.0) continue;
                for (int j = 0; j < 2*n; j++) aug[r][j] -= factor * aug[col][j];
            }
        }

        // Extract right block
        final double[][] invA = new double[n][n];
        for (int i = 0; i < n; i++) System.arraycopy(aug[i], n, invA[i], 0, n);
        return invA;
    }

    /**
     * Jacobian for the standard integrator (linearized p,v; a persists).
     */
    public static double[][] calculateJacobian(double dt) {
        final double[][] F = identity(12);
        // dp/dv
        F[0][3] = dt;
        F[1][4] = dt;
        F[2][5] = dt;
        // dv/da
        F[3][6] = dt;
        F[4][7] = dt;
        F[5][8] = dt;
        // a, params persist (identity already)
        return F;
    }
}
