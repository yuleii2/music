package com.k2.music;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Offline reverse chord identification based on sounding pitch classes. */
public final class ChordIdentifier {
    private final ChordRepository repository;
    private final ChordMatchScorer scorer;

    public ChordIdentifier(ChordRepository repository) {
        this(repository, new ChordMatchScorer());
    }

    public ChordIdentifier(ChordRepository repository, ChordMatchScorer scorer) {
        if (repository == null || scorer == null) {
            throw new IllegalArgumentException("repository and scorer are required");
        }
        this.repository = repository;
        this.scorer = scorer;
    }

    public List<ChordMatch> identifyFrets(int[] frets) {
        if (frets == null || frets.length != 6) {
            throw new IllegalArgumentException("指板输入必须包含六根弦，例如 X 3 2 0 1 0");
        }
        LinkedHashSet<Integer> pitchClasses = new LinkedHashSet<>();
        List<String> actualNames = new ArrayList<>();
        int bassMidi = Integer.MAX_VALUE;
        int bassPitch = -1;
        int soundingStringCount = 0;
        for (int string = 0; string < frets.length; string++) {
            int fret = frets[string];
            if (fret < 0) {
                continue;
            }
            soundingStringCount++;
            if (fret > 30) {
                throw new IllegalArgumentException("品位必须在 0 到 30 之间");
            }
            int midi = MusicTheoryUtils.STANDARD_TUNING_MIDI[string] + fret;
            int pitch = Math.floorMod(midi, 12);
            if (pitchClasses.add(pitch)) {
                actualNames.add(MusicTheoryUtils.pitchClassName(pitch, MusicTheoryUtils.AccidentalPreference.SHARPS));
            }
            if (midi < bassMidi) {
                bassMidi = midi;
                bassPitch = pitch;
            }
        }
        if (pitchClasses.size() < 2) {
            throw new IllegalArgumentException("请至少设置两个不同的发声音");
        }
        return identify(pitchClasses, bassPitch, actualNames, 5, soundingStringCount > pitchClasses.size());
    }

    public List<ChordMatch> identifyNotes(String rawNotes) {
        if (rawNotes == null || rawNotes.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入音符，例如 C E G");
        }
        String normalized = rawNotes.replace('，', ',').replace(';', ',').replace('；', ',');
        String[] tokens = normalized.trim().split("[,\\s]+");
        LinkedHashSet<Integer> pitchClasses = new LinkedHashSet<>();
        List<String> actualNames = new ArrayList<>();
        int bass = -1;
        int validTokenCount = 0;
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            int pitch = MusicTheoryUtils.noteToPitchClass(token);
            if (pitch < 0) {
                throw new IllegalArgumentException("无法识别音符：" + token);
            }
            validTokenCount++;
            if (bass < 0) {
                bass = pitch;
            }
            if (pitchClasses.add(pitch)) {
                actualNames.add(MusicTheoryUtils.normalizeNote(token, MusicTheoryUtils.AccidentalPreference.AUTO));
            }
        }
        if (pitchClasses.size() < 2) {
            throw new IllegalArgumentException("请至少输入两个不同的音符");
        }
        return identify(pitchClasses, bass, actualNames, 5, validTokenCount > pitchClasses.size());
    }

    public List<ChordMatch> identify(Collection<Integer> pitchClasses, int bassPitchClass, List<String> actualNotes, int limit) {
        return identify(pitchClasses, bassPitchClass, actualNotes, limit, false);
    }

    private List<ChordMatch> identify(
            Collection<Integer> pitchClasses,
            int bassPitchClass,
            List<String> actualNotes,
            int limit,
            boolean hasDuplicateTones
    ) {
        LinkedHashSet<Integer> actual = new LinkedHashSet<>();
        for (Integer pitch : pitchClasses) {
            if (pitch != null) {
                actual.add(Math.floorMod(pitch, 12));
            }
        }
        if (actual.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Chord> candidates = new LinkedHashMap<>();
        for (Chord chord : repository.allChords()) {
            if (chord == null || chord.notes.isEmpty() || chord.root == null) {
                continue;
            }
            String baseSymbol = stripSlash(chord.symbol);
            String key = MusicTheoryUtils.noteToPitchClass(chord.root) + ":" + chord.qualityId + ":" + baseSymbol;
            Chord previous = candidates.get(key);
            if (previous == null || (!previous.bassNote.isEmpty() && chord.bassNote.isEmpty())) {
                candidates.put(key, chord);
            }
        }

        List<ChordMatch> matches = new ArrayList<>();
        for (Chord chord : candidates.values()) {
            ChordMatchScorer.Score score = scorer.score(chord, actual, bassPitchClass, hasDuplicateTones);
            if (score.value < 35) {
                continue;
            }
            String bassName = bassPitchClass < 0 ? "" : MusicTheoryUtils.pitchClassName(
                    bassPitchClass,
                    chord.root != null && chord.root.contains("b")
                            ? MusicTheoryUtils.AccidentalPreference.FLATS
                            : MusicTheoryUtils.AccidentalPreference.SHARPS
            );
            int root = MusicTheoryUtils.noteToPitchClass(chord.root);
            String symbol = stripSlash(chord.symbol);
            if (bassPitchClass >= 0 && bassPitchClass != root
                    && (score.type == ChordMatch.MatchType.EXACT_INVERSION || score.value >= 88)) {
                symbol += "/" + bassName;
            }
            matches.add(new ChordMatch(
                    chord,
                    symbol,
                    score.value,
                    score.type,
                    chord.notes,
                    actualNotes,
                    names(score.missing, chord.root),
                    names(score.extra, chord.root),
                    bassPitchClass >= 0 && bassPitchClass != root,
                    bassName
            ));
        }

        Collections.sort(matches, new Comparator<ChordMatch>() {
            @Override
            public int compare(ChordMatch left, ChordMatch right) {
                int scoreOrder = Integer.compare(right.score, left.score);
                if (scoreOrder != 0) return scoreOrder;
                int missingOrder = Integer.compare(left.missingNotes.size(), right.missingNotes.size());
                if (missingOrder != 0) return missingOrder;
                int extraOrder = Integer.compare(left.extraNotes.size(), right.extraNotes.size());
                if (extraOrder != 0) return extraOrder;
                return left.symbol.compareTo(right.symbol);
            }
        });
        if (matches.size() > Math.max(1, limit)) {
            return new ArrayList<>(matches.subList(0, Math.max(1, limit)));
        }
        return matches;
    }

    private static List<String> names(Set<Integer> pitches, String rootHint) {
        return MusicTheoryUtils.namesForPitchClasses(
                pitches,
                rootHint != null && rootHint.contains("b")
                        ? MusicTheoryUtils.AccidentalPreference.FLATS
                        : MusicTheoryUtils.AccidentalPreference.SHARPS
        );
    }

    private static String stripSlash(String symbol) {
        int slash = symbol == null ? -1 : symbol.indexOf('/');
        return slash < 0 ? symbol : symbol.substring(0, slash);
    }
}
