package com.platypii.baselinexr.charts;

import androidx.annotation.NonNull;

public abstract class ChartLayer {
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public abstract void drawData(@NonNull Plot plot);

}
