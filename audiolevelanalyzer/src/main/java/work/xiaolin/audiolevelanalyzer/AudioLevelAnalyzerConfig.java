package work.xiaolin.audiolevelanalyzer;

/**
 * Configuration for {@link AudioLevelAnalyzer}.
 *
 * <p>The number of output levels is derived from {@code bandStops}:
 * {@code barCount = bandStops.length - 1}. Array inputs are defensively copied.
 */
public final class AudioLevelAnalyzerConfig {

    public static final float DEFAULT_INPUT_GAIN = 4.0f;
    public static final float DEFAULT_FREQUENCY_TILT_END = 0.25f;
    public static final double MIN_ANALYSIS_RATE_HZ = 1.0;
    public static final float MAX_INPUT_GAIN = 256.0f;

    private static final float[] DEFAULT_ADJUSTMENT_COEFFICIENTS = {
            0f,
            3.840823f,
            -8.182433f,
            7.772333f,
            -2.430722f,
    };
    private static final float[] DEFAULT_BAND_STOPS = {
            20f,
            81f,
            170f,
            527f,
            1500f,
            4500f,
            10_000f,
    };

    private final int fftSize;
    private final int defaultSampleRate;
    private final float inputGain;
    private final float amplitudeGain;
    private final float exponentialGain;
    private final float frequencyTiltEnd;
    private final float[] bandStopsStorage;
    private final float[] adjustmentCoefficientsStorage;
    private final double analysisRateHz;

    public AudioLevelAnalyzerConfig() {
        this(
                1024,
                48000,
                DEFAULT_INPUT_GAIN,
                0.6f,
                0.4f,
                DEFAULT_FREQUENCY_TILT_END,
                DEFAULT_BAND_STOPS,
                DEFAULT_ADJUSTMENT_COEFFICIENTS,
                AudioLevelAnalyzer.DEFAULT_ANALYSIS_RATE_HZ
        );
    }

    public AudioLevelAnalyzerConfig(
            int fftSize,
            int defaultSampleRate,
            float inputGain,
            float amplitudeGain,
            float exponentialGain,
            float frequencyTiltEnd,
            float[] bandStops,
            float[] adjustmentCoefficients,
            double analysisRateHz
    ) {
        if (fftSize < 2 || (fftSize & (fftSize - 1)) != 0) {
            throw new IllegalArgumentException("fftSize must be a power of two >= 2: " + fftSize);
        }
        if (defaultSampleRate <= 0) {
            throw new IllegalArgumentException("defaultSampleRate must be positive");
        }
        if (!isFinite(inputGain) || inputGain < 0f || inputGain > MAX_INPUT_GAIN) {
            throw new IllegalArgumentException("inputGain must be finite in [0, " + MAX_INPUT_GAIN + "]");
        }
        if (!isFinite(amplitudeGain) || amplitudeGain < 0f) {
            throw new IllegalArgumentException("amplitudeGain must be finite and non-negative");
        }
        if (!isFinite(exponentialGain) || exponentialGain <= 0f) {
            throw new IllegalArgumentException("exponentialGain must be finite and positive");
        }
        if (!isFinite(frequencyTiltEnd) || frequencyTiltEnd < 0f) {
            throw new IllegalArgumentException("frequencyTiltEnd must be finite and non-negative");
        }
        if (bandStops == null || bandStops.length < 2) {
            throw new IllegalArgumentException("bandStops must contain at least 2 boundaries");
        }
        if (adjustmentCoefficients == null || adjustmentCoefficients.length == 0) {
            throw new IllegalArgumentException("adjustmentCoefficients must be non-empty");
        }
        if (!isFinite(analysisRateHz) ||
                analysisRateHz < MIN_ANALYSIS_RATE_HZ ||
                analysisRateHz > AudioLevelAnalyzer.MAX_ANALYSIS_RATE_HZ) {
            throw new IllegalArgumentException(
                    "analysisRateHz must be in [" +
                            MIN_ANALYSIS_RATE_HZ + ", " +
                            AudioLevelAnalyzer.MAX_ANALYSIS_RATE_HZ + "]"
            );
        }

        this.bandStopsStorage = bandStops.clone();
        this.adjustmentCoefficientsStorage = adjustmentCoefficients.clone();
        for (int i = 0; i < bandStopsStorage.length; i++) {
            if (!isFinite(bandStopsStorage[i]) || bandStopsStorage[i] <= 0f) {
                throw new IllegalArgumentException("bandStops must contain finite positive values");
            }
            if (i > 0 && bandStopsStorage[i] <= bandStopsStorage[i - 1]) {
                throw new IllegalArgumentException("bandStops must be strictly increasing");
            }
        }
        for (float coefficient : adjustmentCoefficientsStorage) {
            if (!isFinite(coefficient)) {
                throw new IllegalArgumentException("adjustmentCoefficients must be finite");
            }
        }

        this.fftSize = fftSize;
        this.defaultSampleRate = defaultSampleRate;
        this.inputGain = inputGain;
        this.amplitudeGain = amplitudeGain;
        this.exponentialGain = exponentialGain;
        this.frequencyTiltEnd = frequencyTiltEnd;
        this.analysisRateHz = analysisRateHz;
    }

    public int getFftSize() {
        return fftSize;
    }

    public int getDefaultSampleRate() {
        return defaultSampleRate;
    }

    public float getInputGain() {
        return inputGain;
    }

    public float getAmplitudeGain() {
        return amplitudeGain;
    }

    public float getExponentialGain() {
        return exponentialGain;
    }

    /**
     * High-frequency tilt endpoint shared across the configured band range. The first band uses
     * {@code 1.0}; the last band uses this value; intermediate bands are linearly interpolated.
     */
    public float getFrequencyTiltEnd() {
        return frequencyTiltEnd;
    }

    public float[] getBandStops() {
        return bandStopsStorage.clone();
    }

    public float[] getAdjustmentCoefficients() {
        return adjustmentCoefficientsStorage.clone();
    }

    public int getBarCount() {
        return bandStopsStorage.length - 1;
    }

    public double getAnalysisRateHz() {
        return analysisRateHz;
    }

    public static AudioLevelAnalyzerConfig defaults() {
        return new AudioLevelAnalyzerConfig();
    }

    public static AudioLevelAnalyzerConfig withBarCount(int barCount) {
        return withBarCount(barCount, 20f, 10_000f);
    }

    public static AudioLevelAnalyzerConfig withBarCount(
            int barCount,
            float minFrequencyHz,
            float maxFrequencyHz
    ) {
        return new AudioLevelAnalyzerConfig(
                1024,
                48000,
                DEFAULT_INPUT_GAIN,
                0.6f,
                0.4f,
                DEFAULT_FREQUENCY_TILT_END,
                logSpacedStops(barCount, minFrequencyHz, maxFrequencyHz),
                DEFAULT_ADJUSTMENT_COEFFICIENTS,
                AudioLevelAnalyzer.DEFAULT_ANALYSIS_RATE_HZ
        );
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    AudioLevelAnalyzerConfig withRuntime(float inputGain, double analysisRateHz) {
        return new AudioLevelAnalyzerConfig(
                fftSize,
                defaultSampleRate,
                inputGain,
                amplitudeGain,
                exponentialGain,
                frequencyTiltEnd,
                bandStopsStorage,
                adjustmentCoefficientsStorage,
                analysisRateHz
        );
    }

    private static float[] logSpacedStops(
            int barCount,
            float minFrequencyHz,
            float maxFrequencyHz
    ) {
        if (barCount <= 0) {
            throw new IllegalArgumentException("barCount must be positive");
        }
        if (!isFinite(minFrequencyHz) || minFrequencyHz <= 0f) {
            throw new IllegalArgumentException("minFrequencyHz must be finite and positive");
        }
        if (!isFinite(maxFrequencyHz) || maxFrequencyHz <= minFrequencyHz) {
            throw new IllegalArgumentException("maxFrequencyHz must be greater than minFrequencyHz");
        }

        double minLog = Math.log(minFrequencyHz);
        double maxLog = Math.log(maxFrequencyHz);
        float[] out = new float[barCount + 1];
        for (int i = 0; i <= barCount; i++) {
            out[i] = (float) Math.exp(minLog + (maxLog - minLog) * i / barCount);
        }
        return out;
    }
}
