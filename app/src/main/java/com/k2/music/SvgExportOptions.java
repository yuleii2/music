package com.k2.music;

/** Immutable rendering options for direct-vector guitar voicing SVG output. */
public final class SvgExportOptions {
    public static final int DEFAULT_WIDTH = 400;
    public static final int DEFAULT_HEIGHT = 800;

    public final int width;
    public final int height;
    public final boolean showFingerNumbers;
    public final boolean showNoteNames;
    public final boolean transparentBackground;

    public SvgExportOptions(
            int width,
            int height,
            boolean showFingerNumbers,
            boolean showNoteNames,
            boolean transparentBackground
    ) {
        if (width < 120 || height < 180) {
            throw new IllegalArgumentException("SVG dimensions must be at least 120 x 180.");
        }
        this.width = width;
        this.height = height;
        this.showFingerNumbers = showFingerNumbers;
        this.showNoteNames = showNoteNames;
        this.transparentBackground = transparentBackground;
    }

    public static SvgExportOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .width(width)
                .height(height)
                .showFingerNumbers(showFingerNumbers)
                .showNoteNames(showNoteNames)
                .transparentBackground(transparentBackground);
    }

    public static final class Builder {
        private int width = DEFAULT_WIDTH;
        private int height = DEFAULT_HEIGHT;
        private boolean showFingerNumbers = true;
        private boolean showNoteNames;
        private boolean transparentBackground;

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Builder showFingerNumbers(boolean showFingerNumbers) {
            this.showFingerNumbers = showFingerNumbers;
            return this;
        }

        public Builder showNoteNames(boolean showNoteNames) {
            this.showNoteNames = showNoteNames;
            return this;
        }

        public Builder transparentBackground(boolean transparentBackground) {
            this.transparentBackground = transparentBackground;
            return this;
        }

        public SvgExportOptions build() {
            return new SvgExportOptions(
                    width,
                    height,
                    showFingerNumbers,
                    showNoteNames,
                    transparentBackground
            );
        }
    }
}
