package com.platypii.baselinexr.location;

import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.tensor.Vector3;

import org.junit.Test;

import static org.junit.Assert.*;

public class MotionEstimatorTest {
    private static final double EPSILON = 1e-6;

    @Test
    public void testInitialization() {
        MotionEstimator estimator = new MotionEstimator(0.5);

        // Before any update, predict should return zero state
        MotionEstimator.State state = estimator.predict(0);
        assertVector3Equals(new Vector3(), state.position());
        assertVector3Equals(new Vector3(), state.velocity());
        assertVector3Equals(new Vector3(), state.acceleration());
    }

    @Test
    public void testFirstUpdate() {
        MotionEstimator estimator = new MotionEstimator(0.5);

        // First GPS fix with climb=2.0, vN=10.0, vE=5.0
        MLocation gps1 = new MLocation(0, 40.0, -105.0, 1500.0, 2.0, 10.0, 5.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // Position should be (0,0,0) at origin
        MotionEstimator.State state = estimator.predict(0);
        assertVector3Equals(new Vector3(), state.position());

        // Velocity should match GPS velocity (vE, vN, -climb)
        assertVector3Equals(new Vector3(5.0, 10.0, -2.0), state.velocity());

        // Acceleration should be zero on first update
        assertVector3Equals(new Vector3(), state.acceleration());
    }

    @Test
    public void testSecondUpdatePosition() {
        MotionEstimator estimator = new MotionEstimator(0.5);

        // First GPS fix sets origin
        MLocation gps1 = new MLocation(0, 40.0, -105.0, 1500.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // Second GPS fix 1 second later, moved north
        double deltaLat = 0.0001; // About 11.1 meters north
        MLocation gps2 = new MLocation(1000L, 40.0 + deltaLat, -105.0, 1500.0, 0.0, 11.1, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps2);

        MotionEstimator.State state = estimator.predict(1000L);

        // Position should reflect movement north
        assertEquals(0.0, state.position().x, 0.1); // East
        assertEquals(11.1, state.position().y, 0.1); // North
        assertEquals(0.0, state.position().z, 0.1); // Up
    }

    @Test
    public void testAccelerationSmoothing() {
        MotionEstimator estimator = new MotionEstimator(0.3); // 30% smoothing

        // Initial stationary position
        MLocation gps1 = new MLocation(0, 40.0, -105.0, 1500.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // Sudden acceleration north
        MLocation gps2 = new MLocation(1000L, 40.0, -105.0, 1500.0, 0.0, 10.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps2);

        MotionEstimator.State state = estimator.predict(1000L);

        // Raw acceleration would be 10 m/s², but with 30% smoothing: 0 * 0.7 + 10 * 0.3 = 3.0
        assertEquals(0.0, state.acceleration().x, EPSILON);
        assertEquals(3.0, state.acceleration().y, EPSILON);
        assertEquals(0.0, state.acceleration().z, EPSILON);
    }

    @Test
    public void testPredictionExtrapolation() {
        MotionEstimator estimator = new MotionEstimator(1.0); // No smoothing

        // Moving at constant velocity
        MLocation gps1 = new MLocation(0, 40.0, -105.0, 1500.0, 0.0, 10.0, 5.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // One second later, still moving but faster
        MLocation gps2 = new MLocation(1000L, 40.0001, -105.0, 1505.0, 0.0, 20.0, 5.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps2);

        // Predict 0.5 seconds into the future
        MotionEstimator.State future = estimator.predict(1500L);

        // With constant acceleration of 10 m/s² north:
        // pos = p + v*t + 0.5*a*t²
        // vel = v + a*t
        assertEquals(5.0, future.velocity().x, EPSILON); // East velocity (unchanged)
        assertEquals(25.0, future.velocity().y, EPSILON); // North velocity (20 + 10*0.5)
        assertEquals(0.0, future.velocity().z, EPSILON); // Up velocity (no climb)
    }

    @Test
    public void testNegativeTimePrediction() {
        MotionEstimator estimator = new MotionEstimator(0.5);

        MLocation gps1 = new MLocation(1000L, 40.0, -105.0, 1500.0, 0.0, 10.0, 5.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // Request prediction for time in the past
        MotionEstimator.State past = estimator.predict(500L);

        // Should clamp to current state (dt = 0)
        assertVector3Equals(new Vector3(), past.position());
        assertVector3Equals(new Vector3(5.0, 10.0, 0.0), past.velocity());
    }

    @Test
    public void testEastWestMovement() {
        MotionEstimator estimator = new MotionEstimator(0.5);

        // Origin at 40 degrees latitude (valid location)
        MLocation gps1 = new MLocation(0, 40.0, -105.0, 1500.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // Move east by 0.0001 degrees (about 8.51 meters at 40° latitude)
        MLocation gps2 = new MLocation(1000L, 40.0, -104.9999, 1500.0, 0.0, 0.0, 8.51, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps2);

        MotionEstimator.State state = estimator.predict(1000L);
        assertEquals(8.51, state.position().x, 0.1); // East
        assertEquals(0.0, state.position().y, 0.1); // North
        assertEquals(0.0, state.position().z, 0.1); // Up
    }

    @Test
    public void testAltitudeChanges() {
        MotionEstimator estimator = new MotionEstimator(0.5);

        MLocation gps1 = new MLocation(0, 40.0, -105.0, 1500.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // Climb 10 meters
        MLocation gps2 = new MLocation(1000L, 40.0, -105.0, 1510.0, -10.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps2);

        MotionEstimator.State state = estimator.predict(1000L);
        assertEquals(0.0, state.position().x, EPSILON);
        assertEquals(0.0, state.position().y, EPSILON);
        assertEquals(10.0, state.position().z, EPSILON); // Up is positive
        assertEquals(10.0, state.velocity().z, EPSILON); // Climb rate (inverted from GPS)
    }

    @Test
    public void testLatitudeScaling() {
        MotionEstimator estimator = new MotionEstimator(0.5);

        // Origin at 60 degrees latitude (valid non-zero longitude)
        MLocation gps1 = new MLocation(0, 60.0, 10.0, 0.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // Move east by 0.0001 degrees (should be ~5.55 meters at 60° latitude)
        MLocation gps2 = new MLocation(1000L, 60.0, 10.0001, 0.0, 0.0, 0.0, 5.55, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps2);

        MotionEstimator.State state = estimator.predict(1000L);
        assertEquals(5.55, state.position().x, 0.1); // East movement scaled by cos(lat)
    }

    @Test
    public void testRapidUpdates() {
        MotionEstimator estimator = new MotionEstimator(0.5);

        // Test handling of very small dt
        MLocation gps1 = new MLocation(0, 40.0, -105.0, 1500.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps1);

        // Update only 1 millisecond later
        MLocation gps2 = new MLocation(1L, 40.0, -105.0, 1500.0, 0.0, 1.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(gps2);

        MotionEstimator.State state = estimator.predict(1L);

        // With dt = 0.001s, acceleration = 1.0/0.001 = 1000 m/s²
        // With 50% smoothing: 0 * 0.5 + 1000 * 0.5 = 500 m/s²
        assertEquals(500.0, state.acceleration().y, 1.0);
    }

    @Test
    public void testGpsPositionToEnuConversion() {
        MotionEstimator estimator = new MotionEstimator(0.5);

        // Set origin
        MLocation origin = new MLocation(0, 40.0, -105.0, 1500.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(origin);

        // Test various GPS positions around origin
        // Earth radius = 6371000 meters
        // 1 degree = π/180 radians
        // deltaLat in radians = deltaLat_degrees * π/180
        // north meters = deltaLat_radians * EARTH_RADIUS

        // Move north by 0.0001 degrees (≈11.1 meters)
        MLocation northPoint = new MLocation(1000L, 40.0001, -105.0, 1500.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(northPoint);
        MotionEstimator.State northState = estimator.predict(1000L);
        assertEquals(0.0, northState.position().x, 0.2);
        assertEquals(11.1, northState.position().y, 0.2);

        // Move east by 0.0001 degrees (≈8.5 meters at 40° latitude)
        MLocation eastPoint = new MLocation(2000L, 40.0, -104.9999, 1500.0, 0.0, 0.0, 0.0, 1.0f, 1.0f, 1.0f, 1.0f, 10, 12);
        estimator.update(eastPoint);
        MotionEstimator.State eastState = estimator.predict(2000L);
        assertEquals(8.5, eastState.position().x, 0.2);
        assertEquals(0.0, eastState.position().y, 0.2);
    }


    private void assertVector3Equals(Vector3 expected, Vector3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}