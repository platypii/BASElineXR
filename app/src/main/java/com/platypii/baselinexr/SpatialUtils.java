package com.platypii.baselinexr;

import com.meta.spatial.core.Quaternion;

public class SpatialUtils {

    /**
     * Extract yaw (Y rotation) from quaternion using atan2 to avoid euler angle discontinuities
     * Returns yaw in radians, continuous and without flipping
     */
    public static double extractYawFromQuaternion(Quaternion q) {
        double x = q.getX();
        double y = q.getY();
        double z = q.getZ();
        double w = q.getW();

        // Convert quaternion to yaw using atan2 - this avoids discontinuities
        // Formula: yaw = atan2(2*(w*y + x*z), 1 - 2*(y*y + z*z))
        double yaw = Math.atan2(2.0 * (w * y + x * z), 1.0 - 2.0 * (y * y + z * z));
        return yaw;
    }
}