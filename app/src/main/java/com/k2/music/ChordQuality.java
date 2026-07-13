package com.k2.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 和弦类型公式。它描述“这个和弦应该由哪些音程构成”，不关心吉他上具体怎么按。
 */
public final class ChordQuality {
    public final String id;
    public final String displayName;
    public final String chineseName;
    public final int[] intervals;
    public final String[] intervalLabels;
    public final String category;
    public final int difficulty;
    public final String description;

    public ChordQuality(
            String id,
            String displayName,
            String chineseName,
            int[] intervals,
            String[] intervalLabels,
            String category,
            int difficulty,
            String description
    ) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ChordQuality id must not be empty.");
        }
        if (intervals == null || intervals.length == 0) {
            throw new IllegalArgumentException("ChordQuality must contain at least one interval.");
        }
        if (intervalLabels == null || intervalLabels.length != intervals.length) {
            throw new IllegalArgumentException("Interval labels must match intervals.");
        }
        this.id = id;
        this.displayName = displayName;
        this.chineseName = chineseName;
        this.intervals = Arrays.copyOf(intervals, intervals.length);
        this.intervalLabels = Arrays.copyOf(intervalLabels, intervalLabels.length);
        this.category = category;
        this.difficulty = difficulty;
        this.description = description;
    }

    public List<String> intervalLabelList() {
        List<String> labels = new ArrayList<>();
        Collections.addAll(labels, intervalLabels);
        return Collections.unmodifiableList(labels);
    }
}
