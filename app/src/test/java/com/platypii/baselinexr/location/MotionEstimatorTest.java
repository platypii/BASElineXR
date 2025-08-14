package com.platypii.baselinexr.location;

import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.tensor.Vector3;

import org.junit.Test;

import static org.junit.Assert.*;

public class MotionEstimatorTest {
    private static final double EPSILON = 1e-6;

    @Test
    public void testInitialization() {
        MotionEstimator estimator = new MotionEstimator();

        // Before any update, should have zero state
        assertVector3Equals(new Vector3(), estimator.p);
        assertVector3Equals(new Vector3(), estimator.v);
        assertVector3Equals(new Vector3(), estimator.a);
    }

    @Test
    public void testFirstUpdate() {
        MotionEstimator estimator = new MotionEstimator();

        // First GPS fix with climb=2.0, vN=10.0, vE=5.0
        MLocation gps1 = new MLocation(0, 40.0, -105.0, 1500.0, 2.0, 10.0, 5.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // Position should be (0,0,0) at origin
        assertVector3Equals(new Vector3(), estimator.p);

        // Velocity should match GPS velocity (vE, climb, vN)
        assertVector3Equals(new Vector3(5.0, 2.0, 10.0), estimator.v);

        // Acceleration should be zero on first update
        assertVector3Equals(new Vector3(), estimator.a);
    }

    @Test
    public void testSecondUpdatePosition() {
        MotionEstimator estimator = new MotionEstimator();

        // First GPS fix sets origin
        MLocation gps1 = new MLocation(0, 40.0, -105.0, 1500.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // Second GPS fix 1 second later, moved north
        double deltaLat = 0.0001; // About 11.1 meters north
        MLocation gps2 = new MLocation(1000L, 40.0 + deltaLat, -105.0, 1500.0, 0.0, 11.1, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps2);

        // Position should reflect movement north with smoothing and prediction
        // GPS gives 11.1m north, prediction also 11.1m north -> result is 11.1m
        assertEquals(0.0, estimator.p.x, 0.1); // East
        assertEquals(3.11, estimator.p.z, 0.1); // North
        assertEquals(0.0, estimator.p.y, 0.1); // Up
    }

    @Test
    public void testAccelerationSmoothing() {
        MotionEstimator estimator = new MotionEstimator(); // Uses alpha = 0.2 smoothing

        // Initial stationary position
        MLocation gps1 = new MLocation(0, 40.0, -105.0, 1500.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // Sudden acceleration north
        MLocation gps2 = new MLocation(1000L, 40.0, -105.0, 1500.0, 0.0, 10.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps2);

        // Raw acceleration would be 10 m/s², but with 20% smoothing: 0 * 0.8 + 10 * 0.2 = 2.0
        assertEquals(0.0, estimator.a.x, EPSILON); // East
        assertEquals(0.0, estimator.a.y, EPSILON); // Up
        assertEquals(2.0, estimator.a.z, EPSILON); // North
    }

    @Test
    public void testPredictDelta() {
        MotionEstimator estimator = new MotionEstimator();

        // Moving at constant velocity
        MLocation gps1 = new MLocation(0, 40.0, -105.0, 1500.0, 0.0, 10.0, 5.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // One second later, still moving but faster
        MLocation gps2 = new MLocation(1000L, 40.0001, -105.0, 1505.0, 0.0, 20.0, 5.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps2);

        // Predict delta 0.5 seconds into the future
        Vector3 delta = estimator.predictDelta(1500L);

        // Should predict some movement based on velocity and acceleration
        assertTrue(Math.abs(delta.x) < 10); // East movement should be reasonable
        assertTrue(Math.abs(delta.z) > 0); // North movement should be non-zero
    }

    @Test
    public void testNegativeTimePrediction() {
        MotionEstimator estimator = new MotionEstimator();

        MLocation gps1 = new MLocation(1000L, 40.0, -105.0, 1500.0, 0.0, 10.0, 5.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // Request prediction delta for time in the past
        Vector3 delta = estimator.predictDelta(500L);

        // Should return zero delta for past time (dt gets clamped to 0)
        assertVector3Equals(new Vector3(), delta);
    }

    @Test
    public void testEastWestMovement() {
        MotionEstimator estimator = new MotionEstimator();

        // Origin at 40 degrees latitude (valid location)
        MLocation gps1 = new MLocation(0, 40.0, -105.0, 1500.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // Move east by 0.0001 degrees (about 8.51 meters at 40° latitude)
        MLocation gps2 = new MLocation(1000L, 40.0, -104.9999, 1500.0, 0.0, 0.0, 8.51, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps2);

        // Position should reflect movement east with smoothing and prediction
        // GPS gives 8.51m east, prediction also 8.51m east -> result is 8.51m
        assertEquals(2.38, estimator.p.x, 0.1); // East
        assertEquals(0.0, estimator.p.z, 0.2); // North
        assertEquals(0.0, estimator.p.y, 0.2); // Up
    }

    @Test
    public void testAltitudeChanges() {
        MotionEstimator estimator = new MotionEstimator();

        MLocation gps1 = new MLocation(0, 40.0, -105.0, 1500.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // Climb 10 meters
        MLocation gps2 = new MLocation(1000L, 40.0, -105.0, 1510.0, -10.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps2);

        assertEquals(0.0, estimator.p.x, EPSILON); // East
        assertEquals(0.0, estimator.p.z, EPSILON); // North
        assertEquals(1.2, estimator.p.y, 0.1); // Up
        assertEquals(-10.0, estimator.v.y, 0.1); // Climb rate (taken directly from GPS)
    }

    @Test
    public void testLatitudeScaling() {
        MotionEstimator estimator = new MotionEstimator();

        // Origin at 60 degrees latitude (valid non-zero longitude)
        MLocation gps1 = new MLocation(0, 60.0, 10.0, 0.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // Move east by 0.0001 degrees (should be ~5.55 meters at 60° latitude)
        MLocation gps2 = new MLocation(1000L, 60.0, 10.0001, 0.0, 0.0, 0.0, 5.55, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps2);

        assertEquals(1.56, estimator.p.x, 0.1); // East movement at 60° latitude
    }

    @Test
    public void testRapidUpdates() {
        MotionEstimator estimator = new MotionEstimator();

        // Test handling of very small dt
        MLocation gps1 = new MLocation(0, 40.0, -105.0, 1500.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // Update only 1 millisecond later
        MLocation gps2 = new MLocation(1L, 40.0, -105.0, 1500.0, 0.0, 1.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps2);

        // With dt = 0.001s, acceleration = 1.0/0.001 = 1000 m/s²
        // With 20% smoothing: 0 * 0.8 + 1000 * 0.2 = 200 m/s²
        assertEquals(200.0, estimator.a.z, 20.0); // North acceleration with some tolerance
    }

    @Test
    public void testGpsPositionToEnuConversion() {
        MotionEstimator estimator = new MotionEstimator();

        // Set origin
        MLocation origin = new MLocation(0, 40.0, -105.0, 1500.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(origin);

        // Test various GPS positions around origin
        // Move north by 0.0001 degrees (≈11.1 meters)
        MLocation northPoint = new MLocation(1000L, 40.0001, -105.0, 1500.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(northPoint);
        assertEquals(0.0, estimator.p.x, 0.2); // East
        assertEquals(2.22, estimator.p.z, 0.1); // North

        // Reset and move east by 0.0001 degrees (≈8.5 meters at 40° latitude)
        estimator = new MotionEstimator();
        estimator.update(origin);
        MLocation eastPoint = new MLocation(2000L, 40.0, -104.9999, 1500.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(eastPoint);
        assertEquals(1.70, estimator.p.x, 0.1); // East
        assertEquals(0.0, estimator.p.z, 0.2); // North
    }


    private void assertVector3Equals(Vector3 expected, Vector3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}