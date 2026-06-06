package work.xiaolin.audiolevelanalyzer;

final class BandMapper {

    private final int fftSize;
    private final int amplitudeCount;
    private final float[] stops;
    private final float[] bands;
    private final int[] bandStarts;
    private final int[] bandEnds;
    private final float amplitudeGain;
    private final float exponentialGain;
    private final float frequencyTiltEnd;
    private final float[] adjustmentCoefficients;
    private float cachedSampleRate = -1f;
    private int cachedMagnitudeCount = -1;

    BandMapper(AudioLevelAnalyzerConfig config) {
        if (config == null) {
            throw new NullPointerException("config == null");
        }
        this.fftSize = config.getFftSize();
        this.amplitudeCount = config.getBarCount();
        this.stops = config.getBandStops();
        this.bands = new float[amplitudeCount];
        this.bandStarts = new int[amplitudeCount];
        this.bandEnds = new int[amplitudeCount];
        this.amplitudeGain = config.getAmplitudeGain();
        this.exponentialGain = config.getExponentialGain();
        this.frequencyTiltEnd = config.getFrequencyTiltEnd();
        this.adjustmentCoefficients = config.getAdjustmentCoefficients();
    }

    void map(float[] magnitudes, float sampleRate, float[] out) {
        if (out.length != amplitudeCount) {
            throw new IllegalArgumentException("out must be " + amplitudeCount + " amplitudes");
        }
        final int bandCount = amplitudeCount;
        final int magnitudeCount = magnitudes.length;
        final float amplitudeGain = this.amplitudeGain;
        final float exponentialGain = this.exponentialGain;
        final float frequencyTiltEnd = this.frequencyTiltEnd;
        final float[] coeff = adjustmentCoefficients;

        updateBandRanges(sampleRate, magnitudeCount);
        java.util.Arrays.fill(bands, 0f);
        for (int i = 0; i < bandCount; i++) {
            int start = bandStarts[i];
            int end = bandEnds[i];
            float sum = 0f;
            for (int k = start; k < end; k++) {
                sum += magnitudes[k];
            }
            bands[i] = sum;
        }

        for (int i = 0; i < bandCount; i++) {
            float v = (float) Math.pow(bands[i] * amplitudeGain, exponentialGain);
            float v2 = frequencyMultiplier(i, bandCount, frequencyTiltEnd) * v;
            float p = evaluatePolynomial(coeff, v2);
            out[i] = clamp01(v * p);
        }
    }

    static float frequencyMultiplier(int index, int bandCount, float frequencyTiltEnd) {
        if (bandCount <= 1) {
            return 1f;
        }
        return 1f + (frequencyTiltEnd - 1f) * index / (bandCount - 1f);
    }

    private static float evaluatePolynomial(float[] c, float x) {
        float result = 0f;
        for (int i = c.length - 1; i >= 0; i--) {
            result = result * x + c[i];
        }
        return result;
    }

    private static float clamp01(float v) {
        if (!(v > 0f)) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private void updateBandRanges(float sampleRate, int magnitudeCount) {
        if (sampleRate == cachedSampleRate && magnitudeCount == cachedMagnitudeCount) {
            return;
        }
        float binHz = sampleRate / fftSize;
        for (int i = 0; i < amplitudeCount; i++) {
            bandStarts[i] = clampBin(firstBinAtOrAbove(stops[i], binHz), magnitudeCount);
            bandEnds[i] = clampBin(firstBinAtOrAbove(stops[i + 1], binHz), magnitudeCount);
        }
        cachedSampleRate = sampleRate;
        cachedMagnitudeCount = magnitudeCount;
    }

    private static int firstBinAtOrAbove(float frequencyHz, float binHz) {
        return (int) Math.ceil(frequencyHz / binHz);
    }

    private static int clampBin(int bin, int binCount) {
        if (bin < 0) return 0;
        if (bin > binCount) return binCount;
        return bin;
    }
}
