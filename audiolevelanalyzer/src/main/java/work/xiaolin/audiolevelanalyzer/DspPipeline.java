package work.xiaolin.audiolevelanalyzer;

final class DspPipeline {

    private final int amplitudeCount;
    private final SampleRing ring;
    private final BandLevelAnalyzer analyzer;
    private final float[] window;

    DspPipeline(AudioLevelAnalyzerConfig config) {
        if (config == null) {
            throw new NullPointerException("config == null");
        }
        this.amplitudeCount = config.getBarCount();
        this.ring = new SampleRing(config.getFftSize(), config.getDefaultSampleRate());
        this.analyzer = new BandLevelAnalyzer(config);
        this.window = new float[config.getFftSize()];
    }

    int getAmplitudeCount() {
        return amplitudeCount;
    }

    void setInputGain(float gain) {
        if (Float.isNaN(gain) || Float.isInfinite(gain) || gain < 0f
                || gain > AudioLevelAnalyzerConfig.MAX_INPUT_GAIN) {
            throw new IllegalArgumentException(
                    "inputGain must be finite in [0, " + AudioLevelAnalyzerConfig.MAX_INPUT_GAIN + "]");
        }
        analyzer.setInputGain(gain);
    }

    void push(float[] mono, int frames, int sampleRate) {
        if (mono == null) {
            throw new NullPointerException("mono == null");
        }
        if (frames < 0 || frames > mono.length) {
            throw new IllegalArgumentException("frames must be between 0 and mono.length");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive");
        }
        ring.setSampleRate(sampleRate);
        ring.push(mono, frames);
    }

    void analyze(float[] out) {
        int sampleRate = ring.snapshot(window);
        analyzer.analyze(window, sampleRate, out);
    }
}
