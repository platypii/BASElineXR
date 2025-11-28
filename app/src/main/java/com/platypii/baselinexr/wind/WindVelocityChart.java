package com.platypii.baselinexr.wind;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;
import com.platypii.baselinexr.wind.WindDisplayFormatter;
import com.platypii.baselinexr.wind.WindEstimation;

/**
 * Custom view for displaying wind estimation velocity chart (vE vs vN scatter plot with circle fits)
 */
public class WindVelocityChart extends View {

    private Paint pointPaint;
    private Paint sustainedPointPaint;
    private Paint circlePaint;
    private Paint sustainedCirclePaint;
    private Paint axisPaint;
    private Paint textPaint;
    private Paint gridPaint;
    private Paint windVectorPaint;
    private Paint windVectorSustainedPaint;
    private Paint windLabelPaint;
    private Paint groundspeedVectorPaint;
    private Paint airspeedVectorPaint;

    private List<WindDataPoint> dataPoints;
    private LeastSquaresCircleFit.CircleFitResult gpsCircleFit;
    private LeastSquaresCircleFit.CircleFitResult sustainedCircleFit;
    private String layerTitle = "Wind Estimation";

    private float chartWidth;
    private float chartHeight;
    private float chartSize; // Use smaller dimension for consistent scaling
    private float margin = 40f;
    private double maxVelocity = 50.0; // m/s

    public WindVelocityChart(Context context) {
        super(context);
        init();
    }

    public WindVelocityChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // GPS velocity points (blue)
        pointPaint = new Paint();
        pointPaint.setColor(Color.BLUE);
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setAntiAlias(true);

        // Sustained velocity points (green)
        sustainedPointPaint = new Paint();
        sustainedPointPaint.setColor(Color.GREEN);
        sustainedPointPaint.setStyle(Paint.Style.FILL);
        sustainedPointPaint.setAntiAlias(true);

        // GPS circle fit (blue outline)
        circlePaint = new Paint();
        circlePaint.setColor(Color.BLUE);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(3f);
        circlePaint.setAntiAlias(true);

        // Sustained circle fit (green outline)
        sustainedCirclePaint = new Paint();
        sustainedCirclePaint.setColor(Color.GREEN);
        sustainedCirclePaint.setStyle(Paint.Style.STROKE);
        sustainedCirclePaint.setStrokeWidth(3f);
        sustainedCirclePaint.setAntiAlias(true);

        // Axis lines
        axisPaint = new Paint();
        axisPaint.setColor(Color.WHITE);
        axisPaint.setStrokeWidth(2f);
        axisPaint.setAntiAlias(true);

        // Text labels
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24f);
        textPaint.setAntiAlias(true);

        // Grid lines
        gridPaint = new Paint();
        gridPaint.setColor(Color.GRAY);
        gridPaint.setStrokeWidth(3f);
        gridPaint.setAntiAlias(true);
        gridPaint.setAlpha(150);

        // Wind vector (GPS) - blue arrow
        windVectorPaint = new Paint();
        windVectorPaint.setColor(Color.BLUE);
        windVectorPaint.setStyle(Paint.Style.STROKE);
        windVectorPaint.setStrokeWidth(4f);
        windVectorPaint.setAntiAlias(true);

        // Wind vector (Sustained) - green arrow
        windVectorSustainedPaint = new Paint();
        windVectorSustainedPaint.setColor(Color.GREEN);
        windVectorSustainedPaint.setStyle(Paint.Style.STROKE);
        windVectorSustainedPaint.setStrokeWidth(4f);
        windVectorSustainedPaint.setAntiAlias(true);

        // Wind labels
        windLabelPaint = new Paint();
        windLabelPaint.setColor(Color.YELLOW);
        windLabelPaint.setTextSize(14f);
        windLabelPaint.setAntiAlias(true);
        windLabelPaint.setStyle(Paint.Style.FILL);
        windLabelPaint.setShadowLayer(2f, 1f, 1f, Color.BLACK);

        // Groundspeed vector (red arrow) - from origin to current GPS velocity
        groundspeedVectorPaint = new Paint();
        groundspeedVectorPaint.setColor(Color.RED);
        groundspeedVectorPaint.setStyle(Paint.Style.STROKE);
        groundspeedVectorPaint.setStrokeWidth(5f);
        groundspeedVectorPaint.setAntiAlias(true);

        // Airspeed vector (orange arrow) - from wind vector to GPS velocity
        airspeedVectorPaint = new Paint();
        airspeedVectorPaint.setColor(0xFFFF8C00); // Dark orange
        airspeedVectorPaint.setStyle(Paint.Style.STROKE);
        airspeedVectorPaint.setStrokeWidth(4f);
        airspeedVectorPaint.setAntiAlias(true);
    }

    /**
     * Create a Paint object for the title text
     */
    private Paint getTitlePaint() {
        Paint titlePaint = new Paint();
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(20f);
        titlePaint.setAntiAlias(true);
        titlePaint.setStyle(Paint.Style.FILL);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return titlePaint;
    }

    public void updateData(List<WindDataPoint> dataPoints,
                           LeastSquaresCircleFit.CircleFitResult gpsCircleFit,
                           LeastSquaresCircleFit.CircleFitResult sustainedCircleFit,
                           String layerTitle) {
        this.dataPoints = dataPoints;
        this.gpsCircleFit = gpsCircleFit;
        this.sustainedCircleFit = sustainedCircleFit;
        this.layerTitle = layerTitle != null ? layerTitle : "Wind Estimation";

        // Auto-scale based on data
        if (dataPoints != null && !dataPoints.isEmpty()) {
            double maxV = 0;
            for (WindDataPoint point : dataPoints) {
                maxV = Math.max(maxV, Math.abs(point.vE));
                maxV = Math.max(maxV, Math.abs(point.vN));
                maxV = Math.max(maxV, Math.abs(point.sustainedVE));
                maxV = Math.max(maxV, Math.abs(point.sustainedVN));
            }
            this.maxVelocity = Math.max(30.0, maxV * 1.2); // At least 30 m/s range
        }

        invalidate();
    }

    /**
     * Backward-compatible updateData method without layer title
     */
    public void updateData(List<WindDataPoint> dataPoints,
                           LeastSquaresCircleFit.CircleFitResult gpsCircleFit,
                           LeastSquaresCircleFit.CircleFitResult sustainedCircleFit) {
        updateData(dataPoints, gpsCircleFit, sustainedCircleFit, "Wind Estimation");
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        chartWidth = w - 2 * margin;
        chartHeight = h - 2 * margin;
        // Use the smaller dimension to ensure equal scaling on both axes
        chartSize = Math.min(chartWidth, chartHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (chartWidth <= 0 || chartHeight <= 0) return;

        // Draw title at the top
        drawTitle(canvas);

        // Draw grid
        drawGrid(canvas);

        // Draw axes
        drawAxes(canvas);

        // Draw data points
        if (dataPoints != null) {
            drawDataPoints(canvas);
        }

        // Draw circle fits
        if (gpsCircleFit != null) {
            drawCircleFit(canvas, gpsCircleFit, circlePaint);
        }
        if (sustainedCircleFit != null) {
            drawCircleFit(canvas, sustainedCircleFit, sustainedCirclePaint);
        }

        // Draw wind vectors and labels
        drawWindVectors(canvas);

        // Draw aircraft vectors (groundspeed and airspeed)
        drawAircraftVectors(canvas);

        // Draw axis labels
        drawLabels(canvas);
    }

    /**
     * Draw title at the top of the chart
     */

    private void drawTitle(Canvas canvas) {
        Paint titlePaint = getTitlePaint();
        float centerX = getWidth() / 2f;
        float titleY = 30f; // Position near the top of the view
        canvas.drawText(layerTitle, centerX, titleY, titlePaint);
    }

    private void drawGrid(Canvas canvas) {
        // Vertical grid lines
        for (int i = -4; i <= 4; i++) {
            double v = (maxVelocity * i) / 4.0;
            float x = valueToScreenX(v);
            canvas.drawLine(x, margin, x, getHeight() - margin, gridPaint);
        }

        // Horizontal grid lines
        for (int i = -4; i <= 4; i++) {
            double v = (maxVelocity * i) / 4.0;
            float y = valueToScreenY(v);
            canvas.drawLine(margin, y, getWidth() - margin, y, gridPaint);
        }
    }

    private void drawAxes(Canvas canvas) {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        // X-axis (East)
        canvas.drawLine(margin, centerY, getWidth() - margin, centerY, axisPaint);
        // Y-axis (North)
        canvas.drawLine(centerX, margin, centerX, getHeight() - margin, axisPaint);
    }

    private void drawDataPoints(Canvas canvas) {
        for (WindDataPoint point : dataPoints) {
            // GPS velocity point
            float x1 = valueToScreenX(point.vE);
            float y1 = valueToScreenY(point.vN);
            canvas.drawCircle(x1, y1, 4f, pointPaint);

            // Sustained velocity point
            float x2 = valueToScreenX(point.sustainedVE);
            float y2 = valueToScreenY(point.sustainedVN);
            canvas.drawCircle(x2, y2, 3f, sustainedPointPaint);
        }
    }

    private void drawCircleFit(Canvas canvas, LeastSquaresCircleFit.CircleFitResult circleFit, Paint paint) {
        float centerX = valueToScreenX(circleFit.centerX);
        float centerY = valueToScreenY(circleFit.centerY);
        float radius = (float)(circleFit.radius * chartSize / (2 * maxVelocity));

        canvas.drawCircle(centerX, centerY, radius, paint);

        // Draw center point
        canvas.drawCircle(centerX, centerY, 6f, paint);
    }

    private void drawWindVectors(Canvas canvas) {
        float originX = getWidth() / 2f;  // Chart origin (0,0)
        float originY = getHeight() / 2f;

        // Track wind label positions for collision detection
        java.util.List<Float> windLabelPositions = new java.util.ArrayList<>();

        // Draw GPS wind vector
        if (gpsCircleFit != null && gpsCircleFit.pointCount >= 3) {
            float circleX = valueToScreenX(gpsCircleFit.centerX);
            float circleY = valueToScreenY(gpsCircleFit.centerY);

            // Draw vector from origin to circle center (shows wind direction)
            drawArrow(canvas, originX, originY, circleX, circleY, windVectorPaint);

            // Create wind estimation for formatting
            WindEstimation gpsWind = new WindEstimation(
                    gpsCircleFit.getWindE(), gpsCircleFit.getWindN(),
                    gpsCircleFit.rSquared, gpsCircleFit.pointCount
            );

            // Split label into two lines
            double windSpeed = gpsCircleFit.getWindMagnitude() * 2.23694;
            double windDirection = gpsCircleFit.getWindDirection();
            String line1 = String.format("GPS Wind");
            String line2 = String.format("%.0f MPH @ %.0f°", windSpeed, windDirection);

            // Create blue paint for GPS wind label to match vector color
            Paint gpsLabelPaint = new Paint(windLabelPaint);
            gpsLabelPaint.setColor(Color.BLUE);

            // Position label so it ends at the vector end (shift text box to left)
            float textWidth1 = gpsLabelPaint.measureText(line1);
            float textWidth2 = gpsLabelPaint.measureText(line2);
            float maxWidth = Math.max(textWidth1, textWidth2);
            float labelX = circleX - maxWidth; // Text ends at vector tip
            float labelY = circleY - 8f; // Start higher for two lines

            // Check for collisions and stack if needed (check against both lines)
            for (Float existingY : windLabelPositions) {
                if (Math.abs(labelY - existingY) < 32f) { // Need more space for 2 lines
                    labelY = existingY - 32f; // Stack above
                }
            }
            windLabelPositions.add(labelY);
            windLabelPositions.add(labelY + 16f); // Reserve space for second line

            canvas.drawText(line1, labelX, labelY, gpsLabelPaint);
            canvas.drawText(line2, labelX, labelY + 16f, gpsLabelPaint);
        }

        // Draw sustained wind vector
        if (sustainedCircleFit != null && sustainedCircleFit.pointCount >= 3) {
            float circleX = valueToScreenX(sustainedCircleFit.centerX);
            float circleY = valueToScreenY(sustainedCircleFit.centerY);

            // Draw vector from origin to circle center (shows wind direction)
            drawArrow(canvas, originX, originY, circleX, circleY, windVectorSustainedPaint);

            // Split label into two lines
            double windSpeed = sustainedCircleFit.getWindMagnitude() * 2.23694;
            double windDirection = sustainedCircleFit.getWindDirection();
            String line1 = String.format("Sustained Wind");
            String line2 = String.format("%.0f MPH @ %.0f°", windSpeed, windDirection);

            // Create green paint for sustained wind label to match vector color
            Paint sustainedLabelPaint = new Paint(windLabelPaint);
            sustainedLabelPaint.setColor(Color.GREEN);

            // Position label so it ends at the vector end (shift text box to left)
            float textWidth1 = sustainedLabelPaint.measureText(line1);
            float textWidth2 = sustainedLabelPaint.measureText(line2);
            float maxWidth = Math.max(textWidth1, textWidth2);
            float labelX = circleX - maxWidth; // Text ends at vector tip
            float labelY = circleY - 8f; // Start higher for two lines

            // Check for collisions and stack if needed (check against both lines)
            for (Float existingY : windLabelPositions) {
                if (Math.abs(labelY - existingY) < 32f) { // Need more space for 2 lines
                    labelY = existingY - 32f; // Stack above
                }
            }
            windLabelPositions.add(labelY);
            windLabelPositions.add(labelY + 16f); // Reserve space for second line

            canvas.drawText(line1, labelX, labelY, sustainedLabelPaint);
            canvas.drawText(line2, labelX, labelY + 16f, sustainedLabelPaint);
        }
    }

    /**
     * Draw aircraft velocity vectors (groundspeed and airspeed)
     */
    private void drawAircraftVectors(Canvas canvas) {
        if (dataPoints == null || dataPoints.isEmpty()) return;

        float originX = getWidth() / 2f;  // Chart origin (0,0)
        float originY = getHeight() / 2f;

        // Use the most recent data point for current aircraft state
        WindDataPoint currentPoint = dataPoints.get(dataPoints.size() - 1);

        // Track aircraft label positions for collision detection
        java.util.List<Float> aircraftLabelPositions = new java.util.ArrayList<>();

        // Groundspeed vector (red) - from origin to current GPS velocity
        float gpsVelX = valueToScreenX(currentPoint.vE);
        float gpsVelY = valueToScreenY(currentPoint.vN);
        drawArrow(canvas, originX, originY, gpsVelX, gpsVelY, groundspeedVectorPaint);

        // Calculate groundspeed magnitude and direction
        double groundspeedMph = Math.sqrt(currentPoint.vE * currentPoint.vE + currentPoint.vN * currentPoint.vN) * 2.23694;
        double groundTrack = Math.toDegrees(Math.atan2(currentPoint.vE, currentPoint.vN));
        if (groundTrack < 0) groundTrack += 360;

        // Add groundspeed label at the end of the vector, offset to the right side, split into two lines
        String gsLine1 = "Groundspeed:";
        String gsLine2 = String.format("%.0f MPH @ %.0f°", groundspeedMph, groundTrack);

        // Create red paint for groundspeed label to match vector color
        Paint groundspeedLabelPaint = new Paint(windLabelPaint);
        groundspeedLabelPaint.setColor(Color.RED);

        float groundspeedLabelX = gpsVelX + 10f; // Offset to the right of vector end
        float groundspeedLabelY = gpsVelY - 8f; // Start higher for two lines
        aircraftLabelPositions.add(groundspeedLabelY);
        aircraftLabelPositions.add(groundspeedLabelY + 16f); // Reserve space for second line
        canvas.drawText(gsLine1, groundspeedLabelX, groundspeedLabelY, groundspeedLabelPaint);
        canvas.drawText(gsLine2, groundspeedLabelX, groundspeedLabelY + 16f, groundspeedLabelPaint);

        // Draw airspeed vector if we have wind data
        if (gpsCircleFit != null && gpsCircleFit.pointCount >= 3) {
            // Airspeed vector (orange) - from wind vector end to GPS velocity end
            // This represents the aircraft's velocity relative to the air mass
            float windX = valueToScreenX(gpsCircleFit.centerX);
            float windY = valueToScreenY(gpsCircleFit.centerY);

            drawArrow(canvas, windX, windY, gpsVelX, gpsVelY, airspeedVectorPaint);

            // Calculate airspeed magnitude and direction
            double airspeedX = currentPoint.vE - gpsCircleFit.centerX;
            double airspeedY = currentPoint.vN - gpsCircleFit.centerY;
            double airspeedMph = Math.sqrt(airspeedX * airspeedX + airspeedY * airspeedY) * 2.23694;
            double airspeedDirection = Math.toDegrees(Math.atan2(airspeedX, airspeedY));
            if (airspeedDirection < 0) airspeedDirection += 360;

            // Add airspeed label at the end of the vector, offset to the right side, split into two lines
            String asLine1 = "Airspeed:";
            String asLine2 = String.format("%.0f MPH @ %.0f°", airspeedMph, airspeedDirection);

            // Create orange paint for airspeed label to match vector color
            Paint airspeedLabelPaint = new Paint(windLabelPaint);
            airspeedLabelPaint.setColor(0xFFFF8C00); // Dark orange to match vector

            float airspeedLabelX = gpsVelX + 10f; // Offset to the right of vector end
            float airspeedLabelY = gpsVelY - 8f; // Start higher for two lines

            // Check for collisions and stack if needed (check against both lines)
            for (Float existingY : aircraftLabelPositions) {
                if (Math.abs(airspeedLabelY - existingY) < 10f) { // Reduced spacing between labels
                    airspeedLabelY = existingY + 10f; // Stack below with tighter spacing
                }
            }
            aircraftLabelPositions.add(airspeedLabelY);
            aircraftLabelPositions.add(airspeedLabelY + 16f); // Reserve space for second line

            canvas.drawText(asLine1, airspeedLabelX, airspeedLabelY, airspeedLabelPaint);
            canvas.drawText(asLine2, airspeedLabelX, airspeedLabelY + 16f, airspeedLabelPaint);
        }
    }

    private void drawArrow(Canvas canvas, float startX, float startY, float endX, float endY, Paint paint) {
        // Draw the main line
        canvas.drawLine(startX, startY, endX, endY, paint);

        // Calculate arrow head
        double angle = Math.atan2(endY - startY, endX - startX);
        float arrowLength = 15f;
        float arrowAngle = (float)(Math.PI / 6); // 30 degrees

        // Arrow head points
        float arrowX1 = endX - arrowLength * (float)Math.cos(angle - arrowAngle);
        float arrowY1 = endY - arrowLength * (float)Math.sin(angle - arrowAngle);
        float arrowX2 = endX - arrowLength * (float)Math.cos(angle + arrowAngle);
        float arrowY2 = endY - arrowLength * (float)Math.sin(angle + arrowAngle);

        // Draw arrow head
        canvas.drawLine(endX, endY, arrowX1, arrowY1, paint);
        canvas.drawLine(endX, endY, arrowX2, arrowY2, paint);
    }

    private void drawLabelArea(Canvas canvas) {
        if (dataPoints == null || dataPoints.isEmpty()) return;

        // Create a dedicated label area in the top-right corner
        float labelAreaX = getWidth() - 300f; // Start 300px from right edge
        float labelAreaY = 60f; // Start below the title
        float lineHeight = 25f;

        // Use a smaller font size for the label area to fit more information
        windLabelPaint.setTextSize(16f);

        int labelIndex = 0;

        // Get current data point
        WindDataPoint currentPoint = dataPoints.get(dataPoints.size() - 1);

        // Groundspeed label
        double groundspeed = Math.sqrt(currentPoint.vE * currentPoint.vE + currentPoint.vN * currentPoint.vN) * 2.23694;
        double groundTrack = Math.toDegrees(Math.atan2(currentPoint.vE, currentPoint.vN));
        if (groundTrack < 0) groundTrack += 360;

        // Draw colored indicator for groundspeed
        groundspeedVectorPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(labelAreaX - 20f, labelAreaY + labelIndex * lineHeight - 10f,
                labelAreaX - 5f, labelAreaY + labelIndex * lineHeight + 5f, groundspeedVectorPaint);
        groundspeedVectorPaint.setStyle(Paint.Style.STROKE); // Reset to stroke

        String groundspeedLabel = String.format("Groundspeed: %.0f MPH @ %.0f°", groundspeed, groundTrack);
        canvas.drawText(groundspeedLabel, labelAreaX, labelAreaY + labelIndex * lineHeight, windLabelPaint);
        labelIndex++;

        // GPS Wind label
        if (gpsCircleFit != null && gpsCircleFit.pointCount >= 3) {
            double windSpeed = gpsCircleFit.getWindMagnitude() * 2.23694;
            double windDirection = gpsCircleFit.getWindDirection();

            // Draw colored indicator for GPS wind
            windVectorPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(labelAreaX - 20f, labelAreaY + labelIndex * lineHeight - 10f,
                    labelAreaX - 5f, labelAreaY + labelIndex * lineHeight + 5f, windVectorPaint);
            windVectorPaint.setStyle(Paint.Style.STROKE); // Reset to stroke

            String gpsWindLabel = String.format("GPS Wind: %.0f MPH @ %.0f°", windSpeed, windDirection);
            canvas.drawText(gpsWindLabel, labelAreaX, labelAreaY + labelIndex * lineHeight, windLabelPaint);
            labelIndex++;

            // Airspeed label (only if we have wind data)
            double airspeedVE = currentPoint.vE - gpsCircleFit.centerX;
            double airspeedVN = currentPoint.vN - gpsCircleFit.centerY;
            double airspeed = Math.sqrt(airspeedVE * airspeedVE + airspeedVN * airspeedVN) * 2.23694;
            double heading = Math.toDegrees(Math.atan2(airspeedVE, airspeedVN));
            if (heading < 0) heading += 360;

            // Draw colored indicator for airspeed
            airspeedVectorPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(labelAreaX - 20f, labelAreaY + labelIndex * lineHeight - 10f,
                    labelAreaX - 5f, labelAreaY + labelIndex * lineHeight + 5f, airspeedVectorPaint);
            airspeedVectorPaint.setStyle(Paint.Style.STROKE); // Reset to stroke

            String airspeedLabel = String.format("Airspeed: %.0f MPH @ %.0f°", airspeed, heading);
            canvas.drawText(airspeedLabel, labelAreaX, labelAreaY + labelIndex * lineHeight, windLabelPaint);
            labelIndex++;
        }

        // Sustained Wind label
        if (sustainedCircleFit != null && sustainedCircleFit.pointCount >= 3) {
            double windSpeed = sustainedCircleFit.getWindMagnitude() * 2.23694;
            double windDirection = sustainedCircleFit.getWindDirection();

            // Draw colored indicator for sustained wind
            windVectorSustainedPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(labelAreaX - 20f, labelAreaY + labelIndex * lineHeight - 10f,
                    labelAreaX - 5f, labelAreaY + labelIndex * lineHeight + 5f, windVectorSustainedPaint);
            windVectorSustainedPaint.setStyle(Paint.Style.STROKE); // Reset to stroke

            String sustainedWindLabel = String.format("Sustained Wind: %.0f MPH @ %.0f°", windSpeed, windDirection);
            canvas.drawText(sustainedWindLabel, labelAreaX, labelAreaY + labelIndex * lineHeight, windLabelPaint);
            labelIndex++;
        }

        // Reset font size
        windLabelPaint.setTextSize(14f);
    }

    private void drawLabels(Canvas canvas) {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        // Use smaller text for value labels
        textPaint.setTextSize(18f);

        // Get current GPS velocity from most recent data point
        double currentVE = 0;
        double currentVN = 0;

        if (dataPoints != null && !dataPoints.isEmpty()) {
            // Use the last (most recent) data point
            WindDataPoint lastPoint = dataPoints.get(dataPoints.size() - 1);
            currentVE = lastPoint.vE;
            currentVN = lastPoint.vN;
        }

        // Convert to MPH
        double vE_mph = currentVE * 2.23694;
        double vN_mph = currentVN * 2.23694;

        // East velocity label at bottom, positioned at the actual vE value
        String eastLabel = String.format("vE: %.0f mph", vE_mph);
        float eastX = valueToScreenX(currentVE); // Position at actual vE value
        canvas.drawText(eastLabel, eastX - 40f, getHeight() - 5f, textPaint);

        // North velocity label on left side, positioned at the actual vN value
        String northLabel = String.format("vN: %.0f mph", vN_mph);
        float northY = valueToScreenY(currentVN); // Position at actual vN value
        canvas.save();
        canvas.rotate(-90f, 40f, northY);
        canvas.drawText(northLabel, 40f, northY, textPaint);
        canvas.restore();

        // Reset text size
        textPaint.setTextSize(24f);
    }

    private float valueToScreenX(double value) {
        return (float)(getWidth() / 2f + (value * chartSize / (2 * maxVelocity)));
    }

    private float valueToScreenY(double value) {
        return (float)(getHeight() / 2f - (value * chartSize / (2 * maxVelocity)));
    }
}

