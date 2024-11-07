package com.platypii.baselinexr.tracks;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.places.Place;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Class representing online track info
 */
public class TrackMetadata {
    public final String track_id;
    public final long date;
    public final String date_string;
    public final String trackUrl;
    public final String trackKml;
    @Nullable
    public final String jumpType;
    @Nullable
    public final Place place;
    @Nullable
    public final String suit;
    @Nullable
    public final String canopy;
    public boolean starred = false;

    TrackMetadata(String track_id, long date, String date_string, String trackUrl, String trackKml, @Nullable Place place, @Nullable String jumpType, @Nullable String suit, @Nullable String canopy) {
        this.track_id = track_id;
        this.date = date;
        this.date_string = date_string;
        this.trackUrl = trackUrl;
        this.trackKml = trackKml;
        this.place = place;
        this.jumpType = jumpType;
        this.suit = suit;
        this.canopy = canopy;
    }

    /**
     * Returns the file location of the local track data file
     */
    @NonNull
    public File localFile(@NonNull Context context) {
        final File trackDir = TrackFiles.getTrackDirectory(context);
        return new File(trackDir, "tracks/" + track_id + "/track.csv.gz");
    }

    /**
     * Returns the file location of the local abbreviated (gps only) track data file
     */
    @NonNull
    public File abbrvFile(@NonNull Context context) {
        final File trackDir = TrackFiles.getTrackDirectory(context);
        return new File(trackDir, "tracks/" + track_id + "/track-abbrv.csv");
    }

    @Nullable
    public TrackData trackData(Context context) {
        final File trackFile = abbrvFile(context);
        if (trackFile.exists()) {
            return new TrackData(track_id, trackFile);
        } else {
            return null;
        }
    }

    /**
     * Returns short "Name, Country" string, similar to old location field.
     */
    @NonNull
    public String location() {
        return place == null ? "" : place.niceString();
    }

    /**
     * Returns "Place - Suit"
     */
    @NonNull
    public String subtitle() {
        if (place != null && suit != null) {
            return place.niceString() + " (" + suit + ")";
        } else if (place != null) {
            return place.niceString();
        } else if (suit != null) {
            return "(" + suit + ")";
        } else {
            return "";
        }
    }

    @NonNull
    public String getName() {
        final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        final String shortDate = df.format(new Date(date));
        final String shortLocation = place == null ? "" : place.niceString();
        return shortDate + " " + shortLocation;
    }

    public boolean isBASE() {
        if (jumpType != null) {
            return "BASE".equals(jumpType);
        } else {
            return place != null && place.isBASE();
        }
    }

    public boolean isSkydive() {
        if (jumpType != null) {
            return "Skydive".equals(jumpType);
        } else {
            return place != null && "DZ".equals(place.objectType);
        }
    }

    @Override
    public boolean equals(Object cd) {
        return cd instanceof TrackMetadata && ((TrackMetadata) cd).track_id.equals(track_id);
    }

    @NonNull
    @Override
    public String toString() {
        return track_id;
    }

}
