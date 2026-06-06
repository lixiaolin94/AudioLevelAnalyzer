package work.xiaolin.audiolevelanalyzer;

final class BandLevelAnalyzer {

    private final HannWindow window;
    private final Radix2Fft fft;
    private final BandMapper bandMapper;

    private final int fftSize;
    private final int complexCount;
    private final int amplitudeCount;
    private final float magnitudeScale;
    private final float[] windowed;
    private final float[] real;
    private final float[] imag;
    private final float[] magnitudes;

    private volatile float inputGain;

    BandLevelAnalyzer(AudioLevelAnalyzerConfig config) {
        if (config == null) {
            throw new NullPointerException("config == null");
        }
        this.fftSize = config.getFftSize();
        this.complexCount = fftSize / 2;
        this.amplitudeCount = config.getBarCount();
        this.magnitudeScale = 4.0f / complexCount;
        this.inputGain = config.getInputGain();
        this.window = new HannWindow(fftSize);
        this.fft = new Radix2Fft(fftSize);
        this.bandMapper = new BandMapper(config);
        this.windowed = new float[fftSize];
        this.real = new float[fftSize];
        this.imag = new float[fftSize];
        this.magnitudes = new float[complexCount];
    }

    void setInputGain(float gain) { this.inputGain = gain; }

    void analyze(float[] windowSamples, float sampleRate, float[] out) {
        if (windowSamples.length != fftSize) {
            throw new IllegalArgumentException("window must be " + fftSize + " samples");
        }
        if (out.length != amplitudeCount) {
            throw new IllegalArgumentException("out must be " + amplitudeCount + " amplitudes");
        }
        window.apply(windowSamples, windowed);
        System.arraycopy(windowed, 0, real, 0, fftSize);
        java.util.Arrays.fill(imag, 0f);
        fft.forward(real, imag);

        float scale = magnitudeScale * inputGain;
        for (int k = 0; k < complexCount; k++) {
            magnitudes[k] = (float) Math.sqrt(real[k] * real[k] + imag[k] * imag[k]) * scale;
        }
        bandMapper.map(magnitudes, sampleRate, out);
    }
}
