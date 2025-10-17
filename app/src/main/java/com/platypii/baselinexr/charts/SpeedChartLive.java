package com.platypii.baselinexr.charts;

import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.Services;
import com.platypii.baselinexr.location.AtmosphericModel;
import com.platypii.baselinexr.location.KalmanFilter3D;
import com.platypii.baselinexr.location.LocationProvider;
import com.platypii.baselinexr.location.PolarLibrary;
import com.platypii.baselinexr.location.TimeOffset;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.AdjustBounds;
import com.platypii.baselinexr.util.Bounds;
import com.platypii.baselinexr.util.Convert;
import com.platypii.baselinexr.util.PubSub.Subscriber;
import com.platypii.baselinexr.util.SyncedList;

import static com.platypii.baselinexr.util.Numbers.isReal;

public class SpeedChartLive extends PlotSurface implements Subscriber<MLocation> {

    private static final int AXIS_SPEED = 0;
    private static final double GRAVITY = 9.80665; // m/s²

    // Sustained speeds result class
    private static class SustainedSpeeds {
        final double vxs;
        final double vys;

        SustainedSpeeds(double vxs, double vys) {
            this.vxs = vxs;
            this.vys = vys;
        }
    }
    @NonNull
    private final EllipseLayer ellipses;
    private final Bounds inner = new Bounds();
    private final Bounds outer = new Bounds();
    private final Bounds bounds = new Bounds();

    private static final long window = 15000; // The size of the view window, in milliseconds
    private final SyncedList<MLocation> history = new SyncedList<>();

    // Sustained speed history data structure
    private static class SustainedSpeedPoint {
        final long millis;
        final double vxs;
        final double vys;

        SustainedSpeedPoint(long millis, double vxs, double vys) {
            this.millis = millis;
            this.vxs = vxs;
            this.vys = vys;
        }
    }
    private final SyncedList<SustainedSpeedPoint> sustainedHistory = new SyncedList<>();

    // High-speed velocity history data structure for 90Hz updates
    private static class HighSpeedPoint {
        final long millis;
        final double vx;
        final double vy;

        HighSpeedPoint(long millis, double vx, double vy) {
            this.millis = millis;
            this.vx = vx;
            this.vy = vy;
        }
    }
    private final SyncedList<HighSpeedPoint> highSpeedHistory = new SyncedList<>();
    private long lastHighSpeedUpdate = 0;
    private long lastSustainedSpeedUpdate = 0;

    @Nullable
    private LocationProvider locationService = null;

    public SpeedChartLive(Context context, AttributeSet attrs) {
        super(context, attrs);

        final float density = getResources().getDisplayMetrics().density;
        options.padding.top = (int) (12 * density);
        options.padding.bottom = (int) (42 * density);
        options.padding.left = (int) (density);
        options.padding.right = (int) (76 * density);

        inner.x.min = outer.x.min = 0;
        inner.x.max = 9 * Convert.MPH;
        outer.x.max = 160 * Convert.MPH;
        inner.y.min = -2 * Convert.MPH;
        outer.y.min = -160 * Convert.MPH;
        inner.y.max = 2 * Convert.MPH;
        outer.y.max = 28 * Convert.MPH;

        options.axis.x = options.axis.y = PlotOptions.axisSpeed();

        history.setMaxSize(200);
        sustainedHistory.setMaxSize(200); // Same max size as regular history
        highSpeedHistory.setMaxSize(1350); // 90Hz * 15 seconds = 1350 points for window

        // Add layers
        ellipses = new EllipseLayer(options.density);
        ellipses.setEnabled(false); // Disable until the first data comes in
        addLayer(ellipses);
    }

    @Override
    public void drawData(@NonNull Plot plot) {
        options.padding.right = (int) (plot.options.font_size * 5);
        if (locationService != null) {
            final long currentTime = TimeOffset.phoneToGpsTime(System.currentTimeMillis());
            final MLocation loc = locationService.lastLoc;
            if (loc != null && currentTime - loc.millis <= window) {
                ellipses.setEnabled(true);

                // Draw Aura5 polar first (background)
                drawAura5Polar(plot);

                // Get predicted velocity for 90Hz updates and collect high-speed data
                double vx, vy;
                if (Services.location != null && Services.location.motionEstimator instanceof KalmanFilter3D) {
                    final KalmanFilter3D kf3d = (KalmanFilter3D) Services.location.motionEstimator;
                    final KalmanFilter3D.KFState predictedState = kf3d.getCachedPredictedState(System.currentTimeMillis());
                    vx = Math.sqrt(predictedState.velocity().x * predictedState.velocity().x + predictedState.velocity().z * predictedState.velocity().z);
                    vy = predictedState.velocity().y;

                    // Collect high-speed velocity data at ~90Hz
                    updateHighSpeedHistory(currentTime, vx, vy);
                } else {
                    // Fallback to GPS velocity
                    vx = locationService.lastLoc.groundSpeed();
                    vy = locationService.lastLoc.climb;
                }

                // Draw horizontal, vertical speed using predicted velocity
                drawSpeedLines(plot, vx, vy);

                // Draw accelBall first (large white dot)
                drawAccelBall(plot);

                // Draw sustained speed history first (background)
                drawSustainedSpeedHistory(plot, currentTime);

                // Draw current sustained speeds (if available)
                drawCurrentSustainedSpeeds(plot);

                // Draw high-speed history using 90Hz predicted state data
                drawHighSpeedHistory(plot, currentTime);

                // Draw horizontal, vertical speed labels using predicted velocity
                drawSpeedLabels(plot, vx, vy);

                // Draw current location using predicted velocity
                drawLocation(plot, currentTime, loc.millis, vx, vy);
            } else {
                // Draw "no gps signal"
                ellipses.setEnabled(false);
                plot.text.setTextAlign(Paint.Align.CENTER);
                //noinspection IntegerDivisionInFloatingPointContext
                plot.canvas.drawText("no gps signal", plot.width / 2, plot.height / 2, plot.text);
            }
        }
    }

    /**
     * Update high-speed history with predicted velocity data at ~90Hz
     */
    private void updateHighSpeedHistory(long currentTime, double vx, double vy) {
        // Throttle to ~90Hz (every 11ms)
        if (currentTime - lastHighSpeedUpdate >= 11) {
            highSpeedHistory.append(new HighSpeedPoint(currentTime, vx, vy));
            lastHighSpeedUpdate = currentTime;
        }
    }

    /**
     * Update sustained speed history with 90Hz interpolated kl/kd parameters
     */
    private void updateSustainedSpeedHistory(double vxs, double vys) {
        final long currentTime = TimeOffset.phoneToGpsTime(System.currentTimeMillis());
        // Throttle to ~90Hz (every 11ms)
        if (currentTime - lastSustainedSpeedUpdate >= 11) {
            sustainedHistory.append(new SustainedSpeedPoint(currentTime, vxs, vys));
            lastSustainedSpeedUpdate = currentTime;
        }
    }

    /**
     * Draw high-speed historical points using 90Hz predicted velocity data
     */
    private void drawHighSpeedHistory(@NonNull Plot plot, long currentTime) {
        synchronized (highSpeedHistory) {
            plot.paint.setStyle(Paint.Style.FILL);
            for (HighSpeedPoint point : highSpeedHistory) {
                final int t = (int) (currentTime - point.millis);
                if (t <= window) {
                    // Style point based on freshness
                    final int purple = 0x5500ff;
                    int darkness = 0xbb * (15000 - t) / (15000 - 1000); // Fade color to dark
                    darkness = Math.max(0x88, Math.min(darkness, 0xbb));
                    final int rgb = darken(purple, darkness);
                    int alpha = 0xff * (15000 - t) / (15000 - 10000); // fade out at t=10..15
                    alpha = Math.max(0, Math.min(alpha, 0xff));
                    final int color = (alpha << 24) + rgb; // 0xff5500ff

                    // Draw point with size based on age
                    float radius = 12f * (4000 - t) / 6000;
                    radius = Math.max(3, Math.min(radius, 12));
                    plot.paint.setColor(color);
                    plot.drawPoint(AXIS_SPEED, point.vx, point.vy, radius);
                }
            }
        }
    }

    /**
     * Draw historical points at 90Hz with predicted state for current location (legacy GPS-based)
     */
    private void drawHistory(@NonNull Plot plot, long currentTime) {
        synchronized (history) {
            plot.paint.setStyle(Paint.Style.FILL);
            for (MLocation loc : history) {
                final int t = (int) (currentTime - loc.millis);
                if (t <= window) {
                    double vx, vy;

                    // Use predicted velocity for the most recent point (90Hz updates)
                    if (t < 100 && Services.location != null && Services.location.motionEstimator instanceof KalmanFilter3D) {
                        final KalmanFilter3D kf3d = (KalmanFilter3D) Services.location.motionEstimator;
                        final KalmanFilter3D.KFState predictedState = kf3d.getCachedPredictedState(System.currentTimeMillis());
                        vx = Math.sqrt(predictedState.velocity().x * predictedState.velocity().x + predictedState.velocity().z * predictedState.velocity().z);
                        vy = predictedState.velocity().y;
                    } else {
                        // Use GPS velocity for historical points
                        vx = loc.groundSpeed();
                        vy = loc.climb;
                    }

                    // Style point based on freshness
                    final int purple = 0x5500ff;
                    int darkness = 0xbb * (15000 - t) / (15000 - 1000); // Fade color to dark
                    darkness = Math.max(0x88, Math.min(darkness, 0xbb));
                    final int rgb = darken(purple, darkness);
                    int alpha = 0xff * (15000 - t) / (15000 - 10000); // fade out at t=10..15
                    alpha = Math.max(0, Math.min(alpha, 0xff));
                    final int color = (alpha << 24) + rgb; // 0xff5500ff

                    // Draw point
                    float radius = 12f * (4000 - t) / 6000;
                    radius = Math.max(3, Math.min(radius, 12));
                    plot.paint.setColor(color);
                    plot.drawPoint(AXIS_SPEED, vx, vy, radius);
                }
            }
        }
    }    /**
     * Draw sustained speed history (background layer)
     */
    private void drawSustainedSpeedHistory(@NonNull Plot plot, long currentTime) {
        synchronized (sustainedHistory) {
            plot.paint.setStyle(Paint.Style.FILL);
            for (SustainedSpeedPoint point : sustainedHistory) {
                final int t = (int) (currentTime - point.millis);
                if (t <= window ) { // Don't draw the most recent point (it's drawn separately)
                    // Style point based on freshness (similar to regular history but green)
                    final int green = 0x00ff88;
                    int darkness = 0xbb * (15000 - t) / (15000 - 1000); // Fade color to dark
                    darkness = Math.max(0x44, Math.min(darkness, 0xbb));
                    final int rgb = darken(green, darkness);
                    int alpha = 0xff * (15000 - t) / (15000 - 10000); // fade out at t=10..15
                    alpha = Math.max(0, Math.min(alpha, 0xff));
                    final int color = (alpha << 24) + rgb;

                    // Draw point with size based on age
                    float radius = 8f * (4000 - t) / 6000;
                    radius = Math.max(2, Math.min(radius, 8));
                    plot.paint.setColor(color);
                    plot.drawPoint(AXIS_SPEED, point.vxs, point.vys, radius);
                }
            }
        }
    }

    /**
     * Draw current sustained speeds using cached predicted state from 90Hz updates with interpolated kl/kd
     */
    private void drawCurrentSustainedSpeeds(@NonNull Plot plot) {
        if (Services.location != null && Services.location.motionEstimator instanceof KalmanFilter3D) {
            final KalmanFilter3D kf3d = (KalmanFilter3D) Services.location.motionEstimator;

            // Use cached predicted state from last predictDelta() call with 90Hz interpolated kl/kd
            final KalmanFilter3D.KFState state = kf3d.getCachedPredictedState(System.currentTimeMillis());

            if (isReal(state.kl()) && isReal(state.kd())) {
                // Calculate sustained speeds using 90Hz interpolated wingsuit parameters
                final double klkd_squared = state.kl() * state.kl() + state.kd() * state.kd();
                final double klkd_power = Math.pow(klkd_squared, 0.75);

                final double vxs = state.kl() / klkd_power;
                final double vys = -state.kd() / klkd_power;

                // Update sustained speed history at 90Hz
                updateSustainedSpeedHistory(vxs, vys);

                // Draw current sustained speeds with a distinct style
                plot.paint.setStyle(Paint.Style.FILL);
                final int color = 0xff00ff88; // Solid green for current point
                plot.paint.setColor(color);
                plot.drawPoint(AXIS_SPEED, vxs, vys, 10f);
            }
        }
    }

    /**
     * Draw accelBall (speeds + c * acceleration) as a large white dot using cached predicted state from 90Hz GpsToWorldTransform updates
     */
    private void drawAccelBall(@NonNull Plot plot) {
        if (Services.location != null && Services.location.motionEstimator instanceof KalmanFilter3D) {
            final KalmanFilter3D kf3d = (KalmanFilter3D) Services.location.motionEstimator;

            // Use cached predicted state from last predictDelta() call
            final KalmanFilter3D.KFState state = kf3d.getCachedPredictedState(System.currentTimeMillis());

            // Get velocity and acceleration from the predicted filter state
            final double vx = state.velocity().x;
            final double vz = state.velocity().z;
            final double ax = state.acceleration().x;
            final double az = state.acceleration().z;
            final double vax = Math.sqrt((vx+ax)*(vx+ax) + (vz+az)*(vz+az));
            final double vay = state.acceleration().y+state.velocity().y ;

            final double vy = state.velocity().y;
            final double ay = state.acceleration().y;

            if (isReal(vax) && isReal(vay) ) {
                // Calculate accelBall position: speeds + c * acceleration
                // final double c = 1.2; // Acceleration scaling constant
                final double accelBallX = vax ;
                final double accelBallY = vay ;

                // Draw large white dot
                plot.paint.setStyle(Paint.Style.FILL);
                final int color = 0xffffffff; // White
                plot.paint.setColor(color);
                plot.drawPoint(AXIS_SPEED, accelBallX, accelBallY, 15f); // Large radius
            }
        }
    }

    /**
     * Convert CL/CD coefficients to sustained speeds using atmospheric density
     */
    private static SustainedSpeeds coefftoss(double cl, double cd, double s, double m, double rho) {
        final double k = 0.5 * rho * s / m;
        final double kl = cl * k / GRAVITY;
        final double kd = cd * k / GRAVITY;
        final double denom = Math.pow(kl * kl + kd * kd, 0.75);
        return new SustainedSpeeds(kl / denom, kd / denom);
    }

    /**
     * Draw Aura5 polar on the speed chart
     * todo optimize this
     */
    private void drawAura5Polar(@NonNull Plot plot) {
        final PolarLibrary.WingsuitPolar polar = PolarLibrary.AURA_FIVE_POLAR;

        // Get current altitude for density calculation (use GPS altitude or default to 1000m)
        float altitude = 1000f; // Default altitude
        if (locationService != null && locationService.lastLoc != null) {
            altitude = (float) locationService.lastLoc.altitude_gps;
        }

        // Calculate density with 10 degree temperature offset
        final float density = AtmosphericModel.calculateDensity(altitude, 10f);

        plot.paint.setStyle(Paint.Style.STROKE);
        plot.paint.setStrokeWidth(6* options.density); // 2dp thick line
        plot.paint.setStrokeCap(Paint.Cap.ROUND);

        SustainedSpeeds prevSpeeds = null;

        // Draw line segmnts connecting the polar points
        for (int i = 0; i < polar.stallPoint.size(); i++) {
            PolarLibrary.PolarPoint point = polar.stallPoint.get(i);

            // Convert to sustained speeds
            SustainedSpeeds speeds = coefftoss(point.cl, point.cd, polar.s, polar.m, density);

            // Draw line segment from previous point to current point
            if (prevSpeeds != null) {
                // Get AOA for color (use index to map to AOA array)
                int aoa = 0;
                if (i < polar.aoas.length) {
                    aoa = polar.aoas[i];
                }

                // Color based on AOA - gradient from blue (low AOA) to red (high AOA)
                int color = getAOAColor(aoa);
                plot.paint.setColor(color);

                // Draw line segment
                final float x1 = plot.getX(prevSpeeds.vxs);
                final float y1 = plot.getY(-prevSpeeds.vys);
                final float x2 = plot.getX(speeds.vxs);
                final float y2 = plot.getY(-speeds.vys);

                plot.canvas.drawLine(x1, y1, x2, y2, plot.paint);
            }

            prevSpeeds = speeds;
        }
    }

    /**
     * Get color based on AOA (angle of attack)
     * @param aoa Angle of attack in degrees
     * @return ARGB color
     */
    private static int getAOAColor(int aoa) {
        // Normalize AOA to 0-1 range (assuming AOA range 0-90 degrees)
        float normalized = Math.max(0f, Math.min(1f, aoa / 90f));

        // Create color gradient: Blue (low AOA) -> Green -> Yellow -> Red (high AOA)
        int red, green, blue;

        if (normalized < 0.33f) {
            // Blue to Green
            float t = normalized / 0.33f;
            red = 0;
            green = (int) (255 * t);
            blue = (int) (255 * (1 - t));
        } else if (normalized < 0.66f) {
            // Green to Yellow
            float t = (normalized - 0.33f) / 0.33f;
            red = (int) (255 * t);
            green = 255;
            blue = 0;
        } else {
            // Yellow to Red
            float t = (normalized - 0.66f) / 0.34f;
            red = 255;
            green = (int) (255 * (1 - t));
            blue = 0;
        }

        return 0x88000000 | (red << 16) | (green << 8) | blue; // Semi-transparent
    }

    /**
     * Draw the current location, including position, glide slope, and x and y axis ticks.
     */
    private void drawLocation(@NonNull Plot plot, long currentTime, long millis, double vx, double vy) {
        // Style point based on freshness
        final int t = (int) (currentTime - millis);
        final int rgb = 0x5500ff;
        int alpha = 0xff * (30000 - t) / (30000 - 10000);
        alpha = Math.max(0, Math.min(alpha, 0xff));
        final int color = (alpha << 24) + rgb; // 0xff5500ff

        // Draw point
        float radius = 16f * (6000 - t) / 8000;
        radius = Math.max(3, Math.min(radius, 16));
        plot.paint.setColor(color);
        plot.paint.setStyle(Paint.Style.FILL);
        plot.drawPoint(AXIS_SPEED, vx, vy, radius);
    }

    private void drawSpeedLines(@NonNull Plot plot, double vx, double vy) {
        final double v = Math.sqrt(vx * vx + vy * vy);
        final float sx = plot.getX(vx);
        final float sy = plot.getY(vy);
        final float cx = plot.getX(0);
        final float cy = plot.getY(0);

        // Draw horizontal and vertical speed lines
        plot.paint.setStrokeWidth(options.density);
        plot.paint.setColor(0xff666666);
        plot.canvas.drawLine(cx, (int) sy, sx, (int) sy, plot.paint); // Horizontal
        plot.canvas.drawLine((int) sx, cy, (int) sx, sy, plot.paint); // Vertical

        // Draw total speed circle
        plot.paint.setStyle(Paint.Style.STROKE);
        plot.paint.setColor(0xff444444);
        final float r = Math.abs(plot.getX(v) - cx);
        plot.canvas.drawCircle(cx, cy, r, plot.paint);

        // Draw glide line
        plot.paint.setColor(0xff999999);
        plot.paint.setStrokeCap(Paint.Cap.ROUND);
        plot.canvas.drawLine(cx, cy, sx, sy, plot.paint);
    }

    private void drawSpeedLabels(@NonNull Plot plot, double vx, double vy) {
        final double v = Math.sqrt(vx * vx + vy * vy);
        final float sx = plot.getX(vx);
        final float sy = plot.getY(vy);
        final float cx = plot.getX(0);
        final float cy = plot.getY(0);

        // Draw horizontal and vertical speed labels (unless near axis)
        plot.text.setColor(0xff888888);
        if (sy - cy < -44 * options.density || 18 * options.density < sy - cy) {
            // Horizontal speed label
            plot.canvas.drawText(Convert.speed(vx, 0, true), sx + 3 * options.density, cy + options.font_size, plot.text);
        }
        if (42 * options.density < sx - cx) {
            // Vertical speed label
            plot.canvas.drawText(Convert.speed(Math.abs(vy), 0, true), cx + 3 * options.density, sy + options.font_size, plot.text);
        }

        // Draw total speed label
        plot.text.setColor(0xffcccccc);
        plot.text.setTextAlign(Paint.Align.LEFT);
        final String totalSpeed = Convert.speed(v);
        plot.canvas.drawText(totalSpeed, sx + 6 * options.density, sy + options.font_size + 6 * options.density, plot.text);
        final String glideRatio = Convert.glide2(vx, vy, 2, true);
        plot.canvas.drawText(glideRatio, sx + 6 * options.density, sy + 2 * options.font_size + 8 * options.density, plot.text);
    }

    // Always keep square aspect ratio
    @NonNull
    @Override
    public Bounds getBounds(@NonNull Bounds dataBounds, int axis) {
        bounds.set(dataBounds);
        AdjustBounds.clean(bounds, inner, outer);
        AdjustBounds.squareBounds(bounds, getWidth(), getHeight(), options.padding);
        return bounds;
    }

    /**
     * Darken a color by linear scaling of each color * (factor / 256)
     *
     * @param color argb color 0xff5000be
     * @param factor scale factor out of 256
     * @return darkened color
     */
    private static int darken(@ColorInt int color, int factor) {
        final int a = (color >> 24) & 0xff;
        int r = (color >> 16) & 0xff;
        int g = (color >> 8) & 0xff;
        int b = (color) & 0xff;
        r = r * factor >> 8;
        g = g * factor >> 8;
        b = b * factor >> 8;
        return (a << 24) + (r << 16) + (g << 8) + b;
    }

    public void start(@NonNull LocationProvider locationService) {
        this.locationService = locationService;
        // Start listening for location updates
        locationService.locationUpdates.subscribe(this);
    }

    public void stop() {
        // Stop listening for location updates
        if (locationService != null) {
            locationService.locationUpdates.unsubscribe(this);
        }
        // Clear high-speed history
        //synchronized (highSpeedHistory) {
        //    highSpeedHistory.clear();
        //}
        // Clear sustained speed history
        //synchronized (sustainedHistory) {
        //    sustainedHistory.clear();
        //}
        // Reset timing counters
        lastHighSpeedUpdate = 0;
        lastSustainedSpeedUpdate = 0;
    }

    @Override
    public void apply(@NonNull MLocation loc) {
        history.append(loc);

        // Sustained speed history is now updated at 90Hz in drawCurrentSustainedSpeeds()
        // No need to add GPS-rate sustained speed points here
    }

}
