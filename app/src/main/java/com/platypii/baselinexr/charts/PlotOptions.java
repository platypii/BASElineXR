package com.platypii.baselinexr.charts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.util.Convert;
import com.platypii.baselinexr.util.IntBounds;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Used by PlotView to represent screen padding, etc.
 */
public class PlotOptions {

    // Plot will scale to data bounds, with some padding on the sides
    public final IntBounds padding = new IntBounds();

    // Drawing options
    public final float density;
    final int background_color = 0x80333333; // Transparent grey (50% opacity)
    final int axis_color = 0xffdd0000;
    public final int grid_color = 0xffaaaaaa; // Lighter grey for better visibility
    final int grid_text_color = 0xffcccccc; // Lighter text for better visibility
    final float font_size;

    // Axis options
    final AxesOptions axis = new AxesOptions();

    // Constructor requires density
    PlotOptions(float density, float fontscale) {
        this.density = density;
        font_size = 18 * density * fontscale;
    }

    // Axis options
    static class AxesOptions {
        @NonNull
        AxisOptions x = new AxisOptions();
        @NonNull
        AxisOptions y = new AxisOptions();
    }

    static class AxisOptions {
        // Major units are the smallest unit that will get a major grid line
        // We sometimes use a multiple of major units too
        double major_units = 1;

        // Override this to change how labels are displayed
        @Nullable
        public String format(double value) {
            return null;
        }
    }

    @NonNull
    static AxisOptions axisDistance() {
        return new AxisOptions() {
            {
                major_units = Convert.metric ? 1 : Convert.FT;
            }

            @Override
            public String format(double value) {
                return Math.abs(value) < 0.1 ? "" : Convert.distance(Math.abs(value), 0, true);
            }
        };
    }

    @NonNull
    static AxisOptions axisTime() {
        return new AxisOptions() {
            private final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.US);

            {
                major_units = 60000; // 1 Minute
            }

            @Override
            public String format(double value) {
                return format.format((long) value);
            }
        };
    }

    @NonNull
    static AxisOptions axisSpeed() {
        return new AxisOptions() {
            {
                major_units = Convert.metric ? 50 * Convert.KPH : 50 * Convert.MPH;
            }

            @Override
            public String format(double value) {
                final double absValue = Math.abs(value);
                return absValue < 0.1 ? "" : Convert.speed(absValue, 0, true);
            }
        };
    }

}
