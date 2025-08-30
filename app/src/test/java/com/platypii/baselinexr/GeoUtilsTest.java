package com.platypii.baselinexr;

import static org.junit.Assert.*;

import com.meta.spatial.core.Vector3;
import com.platypii.baselinexr.measurements.LatLngAlt;

import org.junit.Test;

public class GeoUtilsTest {

    private static final double DELTA = 0.01; // 1cm precision for distance tests
    private static final double LAT_LNG_DELTA = 0.000001; // ~0.1m precision for lat/lng tests

    @Test
    public void testCalculateOffset_samePoint() {
        LatLngAlt point = new LatLngAlt(46.5197, 7.9969, 1000.0); // Eiger coordinates
        Vector3 offset = GeoUtils.calculateOffset(point, point);

        assertEquals(0.0, offset.getX(), DELTA);
        assertEquals(0.0, offset.getY(), DELTA);
        assertEquals(0.0, offset.getZ(), DELTA);
    }

    @Test
    public void testCalculateOffset_northMovement() {
        LatLngAlt from = new LatLngAlt(46.5197, 7.9969, 1000.0);
        LatLngAlt to = new LatLngAlt(46.5207, 7.9969, 1000.0); // Move 0.001 degrees north

        Vector3 offset = GeoUtils.calculateOffset(from, to);

        assertEquals(0.0, offset.getX(), DELTA); // No east movement
        assertEquals(0.0, offset.getY(), DELTA); // No altitude change
        assertTrue(offset.getZ() > 100.0 && offset.getZ() < 120.0); // ~111m per degree
    }

    @Test
    public void testCalculateOffset_eastMovement() {
        LatLngAlt from = new LatLngAlt(46.5197, 7.9969, 1000.0);
        LatLngAlt to = new LatLngAlt(46.5197, 8.0069, 1000.0); // Move 0.01 degrees east

        Vector3 offset = GeoUtils.calculateOffset(from, to);

        assertTrue(offset.getX() > 600.0 && offset.getX() < 800.0); // East movement with latitude correction
        assertEquals(0.0, offset.getY(), DELTA); // No altitude change
        assertEquals(0.0, offset.getZ(), DELTA); // No north movement
    }

    @Test
    public void testCalculateOffset_altitudeChange() {
        LatLngAlt from = new LatLngAlt(46.5197, 7.9969, 1000.0);
        LatLngAlt to = new LatLngAlt(46.5197, 7.9969, 1500.0); // Move up 500m

        Vector3 offset = GeoUtils.calculateOffset(from, to);

        assertEquals(0.0, offset.getX(), DELTA); // No east movement
        assertEquals(500.0, offset.getY(), DELTA); // 500m altitude increase
        assertEquals(0.0, offset.getZ(), DELTA); // No north movement
    }

    @Test
    public void testCalculateOffset_negativeAltitudeChange() {
        LatLngAlt from = new LatLngAlt(46.5197, 7.9969, 1500.0);
        LatLngAlt to = new LatLngAlt(46.5197, 7.9969, 1000.0); // Move down 500m

        Vector3 offset = GeoUtils.calculateOffset(from, to);

        assertEquals(0.0, offset.getX(), DELTA); // No east movement
        assertEquals(-500.0, offset.getY(), DELTA); // 500m altitude decrease
        assertEquals(0.0, offset.getZ(), DELTA); // No north movement
    }

    @Test
    public void testCalculateOffset_combinedMovement() {
        LatLngAlt from = new LatLngAlt(46.5197, 7.9969, 1000.0);
        LatLngAlt to = new LatLngAlt(46.5207, 8.0069, 1500.0); // North, east, and up

        Vector3 offset = GeoUtils.calculateOffset(from, to);

        assertTrue(offset.getX() > 600.0); // East movement
        assertEquals(500.0, offset.getY(), DELTA); // 500m altitude increase
        assertTrue(offset.getZ() > 100.0); // North movement
    }

    @Test
    public void testCalculateOffset_longDistance() {
        LatLngAlt zurich = new LatLngAlt(47.3769, 8.5417, 400.0);
        LatLngAlt bern = new LatLngAlt(46.9481, 7.4474, 540.0);

        Vector3 offset = GeoUtils.calculateOffset(zurich, bern);

        assertTrue(offset.getX() < -50000.0); // West movement (negative east)
        assertEquals(140.0, offset.getY(), DELTA); // Altitude difference
        assertTrue(offset.getZ() < -40000.0); // South movement (negative north)
    }

    @Test
    public void testApplyOffset_samePoint() {
        LatLngAlt original = new LatLngAlt(46.5197, 7.9969, 1000.0);
        Vector3 zeroOffset = new Vector3(0.0f, 0.0f, 0.0f);

        LatLngAlt result = GeoUtils.applyOffset(original, zeroOffset);

        assertEquals(original.lat, result.lat, LAT_LNG_DELTA);
        assertEquals(original.lng, result.lng, LAT_LNG_DELTA);
        assertEquals(original.alt, result.alt, DELTA);
    }

    @Test
    public void testApplyOffset_eastMovement() {
        LatLngAlt original = new LatLngAlt(46.5197, 7.9969, 1000.0);
        Vector3 eastOffset = new Vector3(1000.0f, 0.0f, 0.0f); // 1km east

        LatLngAlt result = GeoUtils.applyOffset(original, eastOffset);

        assertEquals(original.lat, result.lat, LAT_LNG_DELTA);
        assertTrue(result.lng > original.lng); // Should move east
        assertEquals(original.alt, result.alt, DELTA);
    }

    @Test
    public void testApplyOffset_northMovement() {
        LatLngAlt original = new LatLngAlt(46.5197, 7.9969, 1000.0);
        Vector3 northOffset = new Vector3(0.0f, 0.0f, 1000.0f); // 1km north

        LatLngAlt result = GeoUtils.applyOffset(original, northOffset);

        assertTrue(result.lat > original.lat); // Should move north
        assertEquals(original.lng, result.lng, LAT_LNG_DELTA);
        assertEquals(original.alt, result.alt, DELTA);
    }

    @Test
    public void testApplyOffset_altitudeChange() {
        LatLngAlt original = new LatLngAlt(46.5197, 7.9969, 1000.0);
        Vector3 upOffset = new Vector3(0.0f, 500.0f, 0.0f); // 500m up

        LatLngAlt result = GeoUtils.applyOffset(original, upOffset);

        assertEquals(original.lat, result.lat, LAT_LNG_DELTA);
        assertEquals(original.lng, result.lng, LAT_LNG_DELTA);
        assertEquals(1500.0, result.alt, DELTA);
    }

    @Test
    public void testApplyOffset_multipleOffsets() {
        LatLngAlt original = new LatLngAlt(46.5197, 7.9969, 1000.0);
        Vector3 offset1 = new Vector3(500.0f, 100.0f, 300.0f);
        Vector3 offset2 = new Vector3(200.0f, 50.0f, 100.0f);

        LatLngAlt result = GeoUtils.applyOffset(original, offset1, offset2);

        assertTrue(result.lat > original.lat); // Should move north
        assertTrue(result.lng > original.lng); // Should move east
        assertEquals(1150.0, result.alt, DELTA); // 1000 + 100 + 50
    }

    @Test
    public void testRoundTrip_calculateAndApply() {
        LatLngAlt start = new LatLngAlt(46.5197, 7.9969, 1000.0);
        LatLngAlt target = new LatLngAlt(46.5207, 8.0069, 1500.0);

        Vector3 offset = GeoUtils.calculateOffset(start, target);
        LatLngAlt result = GeoUtils.applyOffset(start, offset);

        assertEquals(target.lat, result.lat, LAT_LNG_DELTA);
        assertEquals(target.lng, result.lng, LAT_LNG_DELTA);
        assertEquals(target.alt, result.alt, DELTA);
    }

    @Test
    public void testRoundTrip_applyAndCalculate() {
        LatLngAlt start = new LatLngAlt(46.5197, 7.9969, 1000.0);
        Vector3 originalOffset = new Vector3(750.0f, 250.0f, 1100.0f);

        LatLngAlt intermediate = GeoUtils.applyOffset(start, originalOffset);
        Vector3 calculatedOffset = GeoUtils.calculateOffset(start, intermediate);

        // Use larger tolerance for round-trip tests due to coordinate transformation precision
        assertEquals(originalOffset.getX(), calculatedOffset.getX(), 0.1);
        assertEquals(originalOffset.getY(), calculatedOffset.getY(), DELTA);
        assertEquals(originalOffset.getZ(), calculatedOffset.getZ(), 0.1);
    }

    @Test
    public void testCalculateOffset_equator() {
        LatLngAlt from = new LatLngAlt(0.0, 0.0, 0.0);
        LatLngAlt to = new LatLngAlt(0.001, 0.001, 100.0);

        Vector3 offset = GeoUtils.calculateOffset(from, to);

        assertTrue(offset.getX() > 100.0); // East movement at equator
        assertEquals(100.0, offset.getY(), DELTA); // Altitude
        assertTrue(offset.getZ() > 100.0); // North movement
    }

    @Test
    public void testCalculateOffset_highLatitude() {
        LatLngAlt from = new LatLngAlt(80.0, 0.0, 0.0); // Near North Pole
        LatLngAlt to = new LatLngAlt(80.001, 0.001, 0.0);

        Vector3 offset = GeoUtils.calculateOffset(from, to);

        assertTrue(Math.abs(offset.getX()) < 50.0); // East movement compressed at high latitude
        assertEquals(0.0, offset.getY(), DELTA); // No altitude change
        assertTrue(offset.getZ() > 100.0); // North movement unchanged
    }

    @Test
    public void testApplyOffset_negativeOffsets() {
        LatLngAlt original = new LatLngAlt(46.5197, 7.9969, 1000.0);
        Vector3 negativeOffset = new Vector3(-500.0f, -200.0f, -300.0f);

        LatLngAlt result = GeoUtils.applyOffset(original, negativeOffset);

        assertTrue(result.lat < original.lat); // Should move south
        assertTrue(result.lng < original.lng); // Should move west
        assertEquals(800.0, result.alt, DELTA); // 1000 - 200
    }

    @Test
    public void testCalculateOffset_reversibility() {
        LatLngAlt point1 = new LatLngAlt(46.5197, 7.9969, 1000.0);
        LatLngAlt point2 = new LatLngAlt(47.0000, 8.5000, 1500.0);

        Vector3 offset1to2 = GeoUtils.calculateOffset(point1, point2);
        Vector3 offset2to1 = GeoUtils.calculateOffset(point2, point1);

        assertEquals(-offset1to2.getX(), offset2to1.getX(), DELTA);
        assertEquals(-offset1to2.getY(), offset2to1.getY(), DELTA);
        assertEquals(-offset1to2.getZ(), offset2to1.getZ(), DELTA);
    }
}
