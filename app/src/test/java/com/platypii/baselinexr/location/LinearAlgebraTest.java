package com.platypii.baselinexr.location;

import org.junit.Test;

import static org.junit.Assert.*;

public class LinearAlgebraTest {
    private static final double EPSILON = 1e-12;

    @Test
    public void testIdentityMatrix() {
        double[][] I3 = LinearAlgebra.identity(3);
        
        assertEquals(3, I3.length);
        assertEquals(3, I3[0].length);
        
        // Check diagonal elements are 1.0
        for (int i = 0; i < 3; i++) {
            assertEquals(1.0, I3[i][i], EPSILON);
        }
        
        // Check off-diagonal elements are 0.0
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i != j) {
                    assertEquals(0.0, I3[i][j], EPSILON);
                }
            }
        }
    }

    @Test
    public void testIdentityMatrixSize1() {
        double[][] I1 = LinearAlgebra.identity(1);
        assertEquals(1, I1.length);
        assertEquals(1, I1[0].length);
        assertEquals(1.0, I1[0][0], EPSILON);
    }

    @Test
    public void testZerosMatrix() {
        double[][] Z = LinearAlgebra.zeros(3, 4);
        
        assertEquals(3, Z.length);
        assertEquals(4, Z[0].length);
        
        // Check all elements are 0.0
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 4; j++) {
                assertEquals(0.0, Z[i][j], EPSILON);
            }
        }
    }

    @Test
    public void testZerosSquareMatrix() {
        double[][] Z = LinearAlgebra.zeros(2, 2);
        assertEquals(2, Z.length);
        assertEquals(2, Z[0].length);
        
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                assertEquals(0.0, Z[i][j], EPSILON);
            }
        }
    }

    @Test
    public void testMatrixAddition() {
        double[][] A = {{1.0, 2.0}, {3.0, 4.0}};
        double[][] B = {{5.0, 6.0}, {7.0, 8.0}};
        
        double[][] C = LinearAlgebra.add(A, B);
        
        assertEquals(6.0, C[0][0], EPSILON);
        assertEquals(8.0, C[0][1], EPSILON);
        assertEquals(10.0, C[1][0], EPSILON);
        assertEquals(12.0, C[1][1], EPSILON);
    }

    @Test
    public void testMatrixAdditionWithZeros() {
        double[][] A = {{1.0, 2.0}, {3.0, 4.0}};
        double[][] Z = LinearAlgebra.zeros(2, 2);
        
        double[][] C = LinearAlgebra.add(A, Z);
        
        assertEquals(1.0, C[0][0], EPSILON);
        assertEquals(2.0, C[0][1], EPSILON);
        assertEquals(3.0, C[1][0], EPSILON);
        assertEquals(4.0, C[1][1], EPSILON);
    }

    @Test
    public void testMatrixSubtraction() {
        double[][] A = {{5.0, 7.0}, {9.0, 11.0}};
        double[][] B = {{1.0, 2.0}, {3.0, 4.0}};
        
        double[][] C = LinearAlgebra.sub(A, B);
        
        assertEquals(4.0, C[0][0], EPSILON);
        assertEquals(5.0, C[0][1], EPSILON);
        assertEquals(6.0, C[1][0], EPSILON);
        assertEquals(7.0, C[1][1], EPSILON);
    }

    @Test
    public void testMatrixSubtractionResultingInZero() {
        double[][] A = {{1.0, 2.0}, {3.0, 4.0}};
        double[][] B = {{1.0, 2.0}, {3.0, 4.0}};
        
        double[][] C = LinearAlgebra.sub(A, B);
        
        assertEquals(0.0, C[0][0], EPSILON);
        assertEquals(0.0, C[0][1], EPSILON);
        assertEquals(0.0, C[1][0], EPSILON);
        assertEquals(0.0, C[1][1], EPSILON);
    }

    @Test
    public void testMatrixMultiplication2x2() {
        double[][] A = {{1.0, 2.0}, {3.0, 4.0}};
        double[][] B = {{5.0, 6.0}, {7.0, 8.0}};
        
        double[][] C = LinearAlgebra.mul(A, B);
        
        // C[0][0] = 1*5 + 2*7 = 19
        // C[0][1] = 1*6 + 2*8 = 22
        // C[1][0] = 3*5 + 4*7 = 43
        // C[1][1] = 3*6 + 4*8 = 50
        assertEquals(19.0, C[0][0], EPSILON);
        assertEquals(22.0, C[0][1], EPSILON);
        assertEquals(43.0, C[1][0], EPSILON);
        assertEquals(50.0, C[1][1], EPSILON);
    }

    @Test
    public void testMatrixMultiplicationWithIdentity() {
        double[][] A = {{1.0, 2.0}, {3.0, 4.0}};
        double[][] I = LinearAlgebra.identity(2);
        
        double[][] C = LinearAlgebra.mul(A, I);
        
        assertEquals(1.0, C[0][0], EPSILON);
        assertEquals(2.0, C[0][1], EPSILON);
        assertEquals(3.0, C[1][0], EPSILON);
        assertEquals(4.0, C[1][1], EPSILON);
    }

    @Test
    public void testMatrixMultiplicationNonSquare() {
        double[][] A = {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}}; // 2x3
        double[][] B = {{7.0, 8.0}, {9.0, 10.0}, {11.0, 12.0}}; // 3x2
        
        double[][] C = LinearAlgebra.mul(A, B); // Should be 2x2
        
        // C[0][0] = 1*7 + 2*9 + 3*11 = 58
        // C[0][1] = 1*8 + 2*10 + 3*12 = 64
        // C[1][0] = 4*7 + 5*9 + 6*11 = 139
        // C[1][1] = 4*8 + 5*10 + 6*12 = 154
        assertEquals(58.0, C[0][0], EPSILON);
        assertEquals(64.0, C[0][1], EPSILON);
        assertEquals(139.0, C[1][0], EPSILON);
        assertEquals(154.0, C[1][1], EPSILON);
    }

    @Test
    public void testMatrixMultiplicationWithZeros() {
        double[][] A = {{1.0, 2.0}, {0.0, 4.0}};
        double[][] B = {{0.0, 6.0}, {7.0, 0.0}};
        
        double[][] C = LinearAlgebra.mul(A, B);
        
        // C[0][0] = 1*0 + 2*7 = 14
        // C[0][1] = 1*6 + 2*0 = 6
        // C[1][0] = 0*0 + 4*7 = 28
        // C[1][1] = 0*6 + 4*0 = 0
        assertEquals(14.0, C[0][0], EPSILON);
        assertEquals(6.0, C[0][1], EPSILON);
        assertEquals(28.0, C[1][0], EPSILON);
        assertEquals(0.0, C[1][1], EPSILON);
    }

    @Test
    public void testMatrixVectorMultiplication() {
        double[][] A = {{1.0, 2.0}, {3.0, 4.0}};
        double[] x = {5.0, 6.0};
        
        double[] y = LinearAlgebra.mul(A, x);
        
        // y[0] = 1*5 + 2*6 = 17
        // y[1] = 3*5 + 4*6 = 39
        assertEquals(17.0, y[0], EPSILON);
        assertEquals(39.0, y[1], EPSILON);
    }

    @Test
    public void testMatrixVectorMultiplicationNonSquare() {
        double[][] A = {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}}; // 2x3
        double[] x = {7.0, 8.0, 9.0}; // 3x1
        
        double[] y = LinearAlgebra.mul(A, x); // Should be 2x1
        
        // y[0] = 1*7 + 2*8 + 3*9 = 50
        // y[1] = 4*7 + 5*8 + 6*9 = 122
        assertEquals(50.0, y[0], EPSILON);
        assertEquals(122.0, y[1], EPSILON);
    }

    @Test
    public void testMatrixVectorMultiplicationWithZeros() {
        double[][] A = {{1.0, 0.0}, {0.0, 1.0}};
        double[] x = {5.0, 6.0};
        
        double[] y = LinearAlgebra.mul(A, x);
        
        assertEquals(5.0, y[0], EPSILON);
        assertEquals(6.0, y[1], EPSILON);
    }

    @Test
    public void testMatrixTranspose() {
        double[][] A = {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}};
        
        double[][] AT = LinearAlgebra.transpose(A);
        
        assertEquals(3, AT.length);
        assertEquals(2, AT[0].length);
        
        assertEquals(1.0, AT[0][0], EPSILON);
        assertEquals(4.0, AT[0][1], EPSILON);
        assertEquals(2.0, AT[1][0], EPSILON);
        assertEquals(5.0, AT[1][1], EPSILON);
        assertEquals(3.0, AT[2][0], EPSILON);
        assertEquals(6.0, AT[2][1], EPSILON);
    }

    @Test
    public void testTransposeSquareMatrix() {
        double[][] A = {{1.0, 2.0}, {3.0, 4.0}};
        
        double[][] AT = LinearAlgebra.transpose(A);
        
        assertEquals(1.0, AT[0][0], EPSILON);
        assertEquals(3.0, AT[0][1], EPSILON);
        assertEquals(2.0, AT[1][0], EPSILON);
        assertEquals(4.0, AT[1][1], EPSILON);
    }

    @Test
    public void testTransposeSymmetricMatrix() {
        double[][] A = {{1.0, 2.0}, {2.0, 3.0}};
        
        double[][] AT = LinearAlgebra.transpose(A);
        
        assertEquals(1.0, AT[0][0], EPSILON);
        assertEquals(2.0, AT[0][1], EPSILON);
        assertEquals(2.0, AT[1][0], EPSILON);
        assertEquals(3.0, AT[1][1], EPSILON);
    }

    @Test
    public void testMatrixInversion2x2() {
        double[][] A = {{1.0, 2.0}, {3.0, 4.0}};
        
        double[][] invA = LinearAlgebra.inverse(A);
        
        // For 2x2 matrix [[a,b],[c,d]], inverse is (1/det) * [[d,-b],[-c,a]]
        // det = 1*4 - 2*3 = -2
        // inverse = (1/-2) * [[4,-2],[-3,1]] = [[-2,1],[1.5,-0.5]]
        assertEquals(-2.0, invA[0][0], EPSILON);
        assertEquals(1.0, invA[0][1], EPSILON);
        assertEquals(1.5, invA[1][0], EPSILON);
        assertEquals(-0.5, invA[1][1], EPSILON);
    }

    @Test
    public void testMatrixInversionIdentityMatrix() {
        double[][] I = LinearAlgebra.identity(3);
        
        double[][] invI = LinearAlgebra.inverse(I);
        
        // Inverse of identity is identity
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i == j) {
                    assertEquals(1.0, invI[i][j], EPSILON);
                } else {
                    assertEquals(0.0, invI[i][j], EPSILON);
                }
            }
        }
    }

    @Test
    public void testMatrixInversionVerification() {
        double[][] A = {{2.0, 1.0}, {1.0, 1.0}};
        
        double[][] invA = LinearAlgebra.inverse(A);
        double[][] product = LinearAlgebra.mul(A, invA);
        
        // A * A^-1 should equal identity
        assertEquals(1.0, product[0][0], 1e-10);
        assertEquals(0.0, product[0][1], 1e-10);
        assertEquals(0.0, product[1][0], 1e-10);
        assertEquals(1.0, product[1][1], 1e-10);
    }

    @Test
    public void testMatrixInversion3x3() {
        double[][] A = {{1.0, 0.0, 2.0}, {-1.0, 5.0, 0.0}, {0.0, 3.0, -9.0}};
        
        double[][] invA = LinearAlgebra.inverse(A);
        double[][] product = LinearAlgebra.mul(A, invA);
        
        // Verify A * A^-1 = I
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i == j) {
                    assertEquals(1.0, product[i][j], 1e-10);
                } else {
                    assertEquals(0.0, product[i][j], 1e-10);
                }
            }
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testMatrixInversionSingularMatrix() {
        // Singular matrix (determinant = 0)
        double[][] singular = {{1.0, 2.0}, {2.0, 4.0}};
        
        LinearAlgebra.inverse(singular);
    }

    @Test
    public void testCalculateJacobian() {
        double dt = 0.1; // 100ms
        
        double[][] F = LinearAlgebra.calculateJacobian(dt);
        
        // Should be 18x18 identity matrix with specific modifications (18D state with wind)
        assertEquals(18, F.length);
        assertEquals(18, F[0].length);
        
        // Check identity elements
        for (int i = 0; i < 18; i++) {
            assertEquals(1.0, F[i][i], EPSILON);
        }
        
        // Check dp/dv elements (position derivatives w.r.t. velocity)
        assertEquals(dt, F[0][3], EPSILON); // dx/dvx
        assertEquals(dt, F[1][4], EPSILON); // dy/dvy
        assertEquals(dt, F[2][5], EPSILON); // dz/dvz
        
        // Check dv/da elements (velocity derivatives w.r.t. acceleration)
        assertEquals(dt, F[3][6], EPSILON); // dvx/dax
        assertEquals(dt, F[4][7], EPSILON); // dvy/day
        assertEquals(dt, F[5][8], EPSILON); // dvz/daz
        
        // Check that all other off-diagonal elements are zero
        for (int i = 0; i < 18; i++) {
            for (int j = 0; j < 18; j++) {
                if (i == j) continue; // Skip diagonal
                if ((i < 3 && j == i + 3) || (i >= 3 && i < 6 && j == i + 3)) {
                    continue; // Skip the dp/dv and dv/da elements we already checked
                }
                assertEquals(0.0, F[i][j], EPSILON);
            }
        }
    }

    @Test
    public void testCalculateJacobianZeroTime() {
        double[][] F = LinearAlgebra.calculateJacobian(0.0);
        
        // With dt=0, should just be 18x18 identity matrix
        for (int i = 0; i < 18; i++) {
            for (int j = 0; j < 18; j++) {
                if (i == j) {
                    assertEquals(1.0, F[i][j], EPSILON);
                } else {
                    assertEquals(0.0, F[i][j], EPSILON);
                }
            }
        }
    }

    @Test
    public void testCalculateJacobianLargeTime() {
        double dt = 1.0; // 1 second
        
        double[][] F = LinearAlgebra.calculateJacobian(dt);
        
        // Check the time-dependent elements
        assertEquals(1.0, F[0][3], EPSILON);
        assertEquals(1.0, F[1][4], EPSILON);
        assertEquals(1.0, F[2][5], EPSILON);
        assertEquals(1.0, F[3][6], EPSILON);
        assertEquals(1.0, F[4][7], EPSILON);
        assertEquals(1.0, F[5][8], EPSILON);
    }

    @Test
    public void testEdgeCasesEmptyMatrix() {
        double[][] zeros = LinearAlgebra.zeros(0, 0);
        assertEquals(0, zeros.length);
    }

    @Test
    public void testEdgeCasesSingleElement() {
        double[][] A = {{5.0}};
        double[][] B = {{3.0}};
        
        double[][] sum = LinearAlgebra.add(A, B);
        assertEquals(8.0, sum[0][0], EPSILON);
        
        double[][] product = LinearAlgebra.mul(A, B);
        assertEquals(15.0, product[0][0], EPSILON);
        
        double[][] transposed = LinearAlgebra.transpose(A);
        assertEquals(5.0, transposed[0][0], EPSILON);
        
        double[][] inverted = LinearAlgebra.inverse(A);
        assertEquals(0.2, inverted[0][0], EPSILON);
    }

    @Test
    public void testMatrixOperationsConsistency() {
        // Test that operations are consistent with mathematical properties
        double[][] A = {{1.0, 2.0}, {3.0, 4.0}};
        double[][] B = {{5.0, 6.0}, {7.0, 8.0}};
        
        // Commutativity of addition: A + B = B + A
        double[][] sumAB = LinearAlgebra.add(A, B);
        double[][] sumBA = LinearAlgebra.add(B, A);
        
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                assertEquals(sumAB[i][j], sumBA[i][j], EPSILON);
            }
        }
        
        // Transpose of transpose: (A^T)^T = A
        double[][] AT = LinearAlgebra.transpose(A);
        double[][] ATT = LinearAlgebra.transpose(AT);
        
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                assertEquals(A[i][j], ATT[i][j], EPSILON);
            }
        }
    }
}