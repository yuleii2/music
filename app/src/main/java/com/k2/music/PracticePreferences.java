package com.k2.music;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** User-owned offline preferences for practice, playback, and voicing recommendations. */
public final class PracticePreferences {
    public enum Proficiency {
        BEGINNER,
        INTERMEDIATE,
        ADVANCED
    }

    public enum PlaybackMode {
        WHOLE_CHORD,
        ARPEGGIO
    }

    public final Proficiency proficiency;
    public final boolean allowBarre;
    public final int maxFret;
    public final int defaultBpm;
    public final TimeSignature defaultTimeSignature;
    public final PlaybackMode defaultPlaybackMode;
    public final boolean accentFirstBeat;
    public final Set<String> familiarVoicingIds;

    public PracticePreferences(
            Proficiency proficiency,
            boolean allowBarre,
            int maxFret,
            int defaultBpm,
            TimeSignature defaultTimeSignature,
            PlaybackMode defaultPlaybackMode,
            boolean accentFirstBeat
    ) {
        this(
                proficiency,
                allowBarre,
                maxFret,
                defaultBpm,
                defaultTimeSignature,
                defaultPlaybackMode,
                accentFirstBeat,
                Collections.emptySet()
        );
    }

    public PracticePreferences(
            Proficiency proficiency,
            boolean allowBarre,
            int maxFret,
            int defaultBpm,
            TimeSignature defaultTimeSignature,
            PlaybackMode defaultPlaybackMode,
            boolean accentFirstBeat,
            Set<String> familiarVoicingIds
    ) {
        this.proficiency = Objects.requireNonNull(proficiency, "proficiency");
        if (maxFret < 1 || maxFret > 24) {
            throw new IllegalArgumentException("Maximum fret must be between 1 and 24.");
        }
        if (defaultBpm < 40 || defaultBpm > 240) {
            throw new IllegalArgumentException("Default BPM must be between 40 and 240.");
        }
        this.defaultTimeSignature = Objects.requireNonNull(defaultTimeSignature, "defaultTimeSignature");
        if (!defaultTimeSignature.isSupportedByMetronome()) {
            throw new IllegalArgumentException("Default time signature must be 2/4, 3/4, 4/4, or 6/8.");
        }
        this.defaultPlaybackMode = Objects.requireNonNull(defaultPlaybackMode, "defaultPlaybackMode");
        this.allowBarre = allowBarre;
        this.maxFret = maxFret;
        this.defaultBpm = defaultBpm;
        this.accentFirstBeat = accentFirstBeat;
        TreeSet<String> sortedIds = new TreeSet<>();
        if (familiarVoicingIds != null) {
            for (String id : familiarVoicingIds) {
                String normalized = id == null ? "" : id.trim();
                if (!normalized.isEmpty()) {
                    if (normalized.length() > 512) {
                        throw new IllegalArgumentException("Voicing familiarity id is too long.");
                    }
                    sortedIds.add(normalized);
                }
            }
        }
        if (sortedIds.size() > 10_000) {
            throw new IllegalArgumentException("Too many familiar voicing ids.");
        }
        this.familiarVoicingIds = Collections.unmodifiableSet(new LinkedHashSet<>(sortedIds));
    }

    public static PracticePreferences defaults() {
        return new PracticePreferences(
                Proficiency.BEGINNER,
                false,
                5,
                50,
                TimeSignature.FOUR_FOUR,
                PlaybackMode.WHOLE_CHORD,
                true
        );
    }

    public PracticePreferences withFamiliarVoicing(String voicingId, boolean familiar) {
        Set<String> updated = new LinkedHashSet<>(familiarVoicingIds);
        if (familiar) {
            updated.add(voicingId);
        } else {
            updated.remove(voicingId);
        }
        return new PracticePreferences(
                proficiency,
                allowBarre,
                maxFret,
                defaultBpm,
                defaultTimeSignature,
                defaultPlaybackMode,
                accentFirstBeat,
                updated
        );
    }

    public PracticePreferences withVoicingConstraints(boolean newAllowBarre, int newMaxFret) {
        return new PracticePreferences(
                proficiency,
                newAllowBarre,
                newMaxFret,
                defaultBpm,
                defaultTimeSignature,
                defaultPlaybackMode,
                accentFirstBeat,
                familiarVoicingIds
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PracticePreferences)) {
            return false;
        }
        PracticePreferences that = (PracticePreferences) other;
        return allowBarre == that.allowBarre
                && maxFret == that.maxFret
                && defaultBpm == that.defaultBpm
                && accentFirstBeat == that.accentFirstBeat
                && proficiency == that.proficiency
                && defaultTimeSignature.equals(that.defaultTimeSignature)
                && defaultPlaybackMode == that.defaultPlaybackMode
                && familiarVoicingIds.equals(that.familiarVoicingIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(proficiency, allowBarre, maxFret, defaultBpm, defaultTimeSignature,
                defaultPlaybackMode, accentFirstBeat, familiarVoicingIds);
    }
}
