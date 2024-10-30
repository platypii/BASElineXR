package com.platypii.baselinexr.location;

public class LatLngBounds {
    private final double minLatitude;
    private final double maxLatitude;
    private final double minLongitude;
    private final double maxLongitude;

    /**
     * Constructs a LatLngBounds object with specified southwest and northeast corners.
     *
     * @param southwest The southwest corner of the bounds.
     * @param northeast The northeast corner of the bounds.
     * @throws IllegalArgumentException if the southwest coordinates are not less than the northeast coordinates.
     */
    public LatLngBounds(LatLng southwest, LatLng northeast) {
        this(
                southwest.latitude,
                northeast.latitude,
                southwest.longitude,
                northeast.longitude
        );
    }

    /**
     * Private constructor to enforce the use of the Builder.
     *
     * @param minLatitude  The minimum latitude of the bounds.
     * @param maxLatitude  The maximum latitude of the bounds.
     * @param minLongitude The minimum longitude of the bounds.
     * @param maxLongitude The maximum longitude of the bounds.
     */
    private LatLngBounds(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {
        if (minLatitude > maxLatitude) {
            throw new IllegalArgumentException("minLatitude cannot be greater than maxLatitude.");
        }
        if (minLongitude > maxLongitude) {
            throw new IllegalArgumentException("minLongitude cannot be greater than maxLongitude.");
        }
        this.minLatitude = minLatitude;
        this.maxLatitude = maxLatitude;
        this.minLongitude = minLongitude;
        this.maxLongitude = maxLongitude;
    }

    /**
     * Checks if the given LatLng point is within the bounds.
     *
     * @param ll The LatLng point to check.
     * @return true if the point is within the bounds, false otherwise.
     */
    public boolean contains(LatLng ll) {
        return ll.latitude >= minLatitude && ll.latitude <= maxLatitude &&
                ll.longitude >= minLongitude && ll.longitude <= maxLongitude;
    }

    /**
     * Builder class for constructing LatLngBounds instances.
     */
    public static class Builder {
        private Double minLatitude = null;
        private Double maxLatitude = null;
        private Double minLongitude = null;
        private Double maxLongitude = null;

        /**
         * Includes a LatLng point into the bounds, expanding the bounds as necessary.
         *
         * @param ll The LatLng point to include.
         * @return The Builder instance (for chaining).
         */
        public Builder include(LatLng ll) {
            if (ll == null) {
                throw new IllegalArgumentException("LatLng cannot be null.");
            }

            if (minLatitude == null || ll.latitude < minLatitude) {
                minLatitude = ll.latitude;
            }
            if (maxLatitude == null || ll.latitude > maxLatitude) {
                maxLatitude = ll.latitude;
            }
            if (minLongitude == null || ll.longitude < minLongitude) {
                minLongitude = ll.longitude;
            }
            if (maxLongitude == null || ll.longitude > maxLongitude) {
                maxLongitude = ll.longitude;
            }
            return this;
        }

        /**
         * Builds the LatLngBounds instance based on the included points.
         *
         * @return A new LatLngBounds instance.
         * @throws IllegalStateException if no points have been included.
         */
        public LatLngBounds build() {
            if (minLatitude == null || maxLatitude == null ||
                    minLongitude == null || maxLongitude == null) {
                throw new IllegalStateException("At least one LatLng point must be included.");
            }
            return new LatLngBounds(minLatitude, maxLatitude, minLongitude, maxLongitude);
        }
    }
}
