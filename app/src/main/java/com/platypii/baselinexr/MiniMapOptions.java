package com.platypii.baselinexr;

public record MiniMapOptions(
    double latMin,
    double latMax,
    double lngMin,
    double lngMax,
    int drawableResource
) {

    /**
     * Check if GPS coordinates are within this minimap's bounds
     */
    public boolean contains(double lat, double lng) {
        return lat >= latMin && lat <= latMax && lng >= lngMin && lng <= lngMax;
    }

    /**
     * Convert GPS coordinates to normalized coordinates (0.0 to 1.0)
     */
    public double[] gpsToNormalized(double lat, double lng) {
        double normalizedLat = (lat - latMin) / (latMax - latMin);
        double normalizedLng = (lng - lngMin) / (lngMax - lngMin);
        return new double[]{normalizedLng, 1.0 - normalizedLat}; // Y inverted for screen coordinates
    }

    /**
     * Convert GPS coordinates to pixel coordinates for given image dimensions
     */
    public int[] gpsToPixel(double lat, double lng, int imageWidth, int imageHeight) {
        double[] normalized = gpsToNormalized(lat, lng);
        int pixelX = (int) (normalized[0] * imageWidth);
        int pixelY = (int) (normalized[1] * imageHeight);
        return new int[]{pixelX, pixelY};
    }
}