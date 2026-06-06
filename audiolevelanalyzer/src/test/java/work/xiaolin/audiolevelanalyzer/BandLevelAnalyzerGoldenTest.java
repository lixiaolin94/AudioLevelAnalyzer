package work.xiaolin.audiolevelanalyzer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public final class BandLevelAnalyzerGoldenTest {

    @Test
    public void analyzerProducesFiniteBoundedLevelsForNonFinitePcm() {
        AudioLevelAnalyzerConfig config = AudioLevelAnalyzerConfig.defaults();
        BandLevelAnalyzer analyzer = new BandLevelAnalyzer(config);
        float[] samples = new float[config.getFftSize()];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) (0.1 * Math.sin(2.0 * Math.PI * 440.0 * i / 48_000.0));
        }
        samples[10] = Float.NaN;
        samples[20] = Float.POSITIVE_INFINITY;
        samples[30] = Float.NEGATIVE_INFINITY;

        float[] out = new float[config.getBarCount()];
        analyzer.analyze(samples, 48_000f, out);

        for (float level : out) {
            assertTrue("level must be finite: " + level,
                    !Float.isNaN(level) && !Float.isInfinite(level));
            assertTrue("level must be in [0, 1]: " + level, level >= 0f && level <= 1f);
        }
    }

    @Test
    public void analyzerMatchesDefault440HzGoldenFrame() {
        AudioLevelAnalyzerConfig config = AudioLevelAnalyzerConfig.defaults();
        BandLevelAnalyzer analyzer = new BandLevelAnalyzer(config);
        float[] samples = new float[config.getFftSize()];
        float[] out = new float[config.getBarCount()];

        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) (0.1 * Math.sin(2.0 * Math.PI * 440.0 * i / 48_000.0));
        }
        analyzer.analyze(samples, 48_000f, out);

        assertArrayEquals(
                new float[] {
                        0.0060955673f,
                        0.012136583f,
                        0.7600501f,
                        0.063656926f,
                        0.0010411461f,
                        0.000057451016f,
                },
                out,
                0.000001f
        );
    }

    @Test
    public void analyzerClearsOutputForSilentFrame() {
        AudioLevelAnalyzerConfig config = AudioLevelAnalyzerConfig.defaults();
        BandLevelAnalyzer analyzer = new BandLevelAnalyzer(config);
        float[] out = new float[config.getBarCount()];
        Arrays.fill(out, 1f);

        analyzer.analyze(new float[config.getFftSize()], 48_000f, out);

        assertArrayEquals(new float[config.getBarCount()], out, 0.000001f);
    }

    @Test
    public void pipelineRejectsInvalidRuntimeInputs() {
        DspPipeline pipeline = new DspPipeline(
                new AudioLevelAnalyzerConfig(
                        1024,
                        48_000,
                        4f,
                        0.6f,
                        0.4f,
                        AudioLevelAnalyzerConfig.DEFAULT_FREQUENCY_TILT_END,
                        new float[] {20f, 81f, 170f, 527f, 1_500f, 4_500f, 10_000f},
                        new float[] {0f, 3.840823f, -8.182433f, 7.772333f, -2.430722f},
                        AudioLevelAnalyzer.DEFAULT_ANALYSIS_RATE_HZ
                )
        );

        assertThrows(IllegalArgumentException.class, () ->
                pipeline.setInputGain(Float.NaN));
        assertThrows(IllegalArgumentException.class, () ->
                pipeline.push(new float[] {0f, 1f}, 3, 48_000));
        assertThrows(IllegalArgumentException.class, () ->
                pipeline.push(new float[] {0f, 1f}, 2, 0));
    }
}
