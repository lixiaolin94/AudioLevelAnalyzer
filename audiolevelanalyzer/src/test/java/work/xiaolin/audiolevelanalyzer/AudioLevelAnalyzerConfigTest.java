package work.xiaolin.audiolevelanalyzer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AudioLevelAnalyzerConfigTest {

    @Test
    public void logSpacedStopsAreStrictlyIncreasing() {
        float[] stops = AudioLevelAnalyzerConfig.withBarCount(4, 20f, 20_000f).getBandStops();

        assertEquals(5, stops.length);
        assertEquals(20f, stops[0], 0.0001f);
        assertEquals(20_000f, stops[4], 0.1f);
        for (int i = 1; i < stops.length; i++) {
            assertTrue(stops[i] > stops[i - 1]);
        }
    }

    @Test
    public void configRejectsInvalidPublicInputs() {
        float[] validStops = new float[] {20f, 80f, 160f};
        float[] validCoefficients = new float[] {1f};

        assertThrows(IllegalArgumentException.class, () ->
                new AudioLevelAnalyzerConfig(
                        1000,
                        48_000,
                        4f,
                        0.6f,
                        0.4f,
                        AudioLevelAnalyzerConfig.DEFAULT_FREQUENCY_TILT_END,
                        validStops,
                        validCoefficients,
                        20.0
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AudioLevelAnalyzerConfig(
                        1024,
                        48_000,
                        Float.NaN,
                        0.6f,
                        0.4f,
                        AudioLevelAnalyzerConfig.DEFAULT_FREQUENCY_TILT_END,
                        validStops,
                        validCoefficients,
                        20.0
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AudioLevelAnalyzerConfig(
                        1024,
                        48_000,
                        4f,
                        0.6f,
                        0.4f,
                        AudioLevelAnalyzerConfig.DEFAULT_FREQUENCY_TILT_END,
                        new float[] {20f, 20f},
                        validCoefficients,
                        20.0
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AudioLevelAnalyzerConfig(
                        1024,
                        48_000,
                        4f,
                        0.6f,
                        0.4f,
                        AudioLevelAnalyzerConfig.DEFAULT_FREQUENCY_TILT_END,
                        validStops,
                        validCoefficients,
                        Double.POSITIVE_INFINITY
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AudioLevelAnalyzerConfig(
                        1024,
                        48_000,
                        4f,
                        0.6f,
                        0.4f,
                        AudioLevelAnalyzerConfig.DEFAULT_FREQUENCY_TILT_END,
                        validStops,
                        validCoefficients,
                        AudioLevelAnalyzer.MAX_ANALYSIS_RATE_HZ + 1.0
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new AudioLevelAnalyzerConfig(
                        1024,
                        48_000,
                        4f,
                        0.6f,
                        0.4f,
                        -0.01f,
                        validStops,
                        validCoefficients,
                        20.0
                ));
    }

    @Test
    public void configRejectsFftSizeBelowTwoButAcceptsTwo() {
        float[] stops = new float[] {20f, 80f, 160f};
        float[] coefficients = new float[] {1f};

        assertThrows(IllegalArgumentException.class, () ->
                new AudioLevelAnalyzerConfig(
                        1, 48_000, 4f, 0.6f, 0.4f,
                        AudioLevelAnalyzerConfig.DEFAULT_FREQUENCY_TILT_END,
                        stops, coefficients, 20.0));

        AudioLevelAnalyzerConfig smallest = new AudioLevelAnalyzerConfig(
                2, 48_000, 4f, 0.6f, 0.4f,
                AudioLevelAnalyzerConfig.DEFAULT_FREQUENCY_TILT_END,
                stops, coefficients, 20.0);
        assertEquals(2, smallest.getFftSize());
    }

    @Test
    public void configRejectsInputGainAboveMax() {
        float[] stops = new float[] {20f, 80f, 160f};
        float[] coefficients = new float[] {1f};

        assertThrows(IllegalArgumentException.class, () ->
                new AudioLevelAnalyzerConfig(
                        1024, 48_000, AudioLevelAnalyzerConfig.MAX_INPUT_GAIN + 1f, 0.6f, 0.4f,
                        AudioLevelAnalyzerConfig.DEFAULT_FREQUENCY_TILT_END,
                        stops, coefficients, 20.0));

        AudioLevelAnalyzerConfig atMax = new AudioLevelAnalyzerConfig(
                1024, 48_000, AudioLevelAnalyzerConfig.MAX_INPUT_GAIN, 0.6f, 0.4f,
                AudioLevelAnalyzerConfig.DEFAULT_FREQUENCY_TILT_END,
                stops, coefficients, 20.0);
        assertEquals(AudioLevelAnalyzerConfig.MAX_INPUT_GAIN, atMax.getInputGain(), 0.000001f);
    }

    @Test
    public void configDefensivelyCopiesArrays() {
        float[] stops = new float[] {20f, 80f, 160f};
        float[] coefficients = new float[] {1f, 2f};
        AudioLevelAnalyzerConfig config = new AudioLevelAnalyzerConfig(
                1024,
                48_000,
                4f,
                0.6f,
                0.4f,
                AudioLevelAnalyzerConfig.DEFAULT_FREQUENCY_TILT_END,
                stops,
                coefficients,
                20.0
        );

        stops[0] = 40f;
        coefficients[0] = 9f;

        assertEquals(20f, config.getBandStops()[0], 0.000001f);
        assertEquals(1f, config.getAdjustmentCoefficients()[0], 0.000001f);

        float[] exposedStops = config.getBandStops();
        float[] exposedCoefficients = config.getAdjustmentCoefficients();
        exposedStops[0] = 50f;
        exposedCoefficients[0] = 10f;

        assertEquals(20f, config.getBandStops()[0], 0.000001f);
        assertEquals(1f, config.getAdjustmentCoefficients()[0], 0.000001f);
    }

    @Test
    public void runtimeSnapshotKeepsStyleSettings() {
        AudioLevelAnalyzerConfig base = AudioLevelAnalyzerConfig.withBarCount(8);

        AudioLevelAnalyzerConfig snapshot = base.withRuntime(2.5f, 30.0);

        assertEquals(2.5f, snapshot.getInputGain(), 0.000001f);
        assertEquals(30.0, snapshot.getAnalysisRateHz(), 0.000001);
        assertEquals(base.getBarCount(), snapshot.getBarCount());
        assertEquals(base.getFrequencyTiltEnd(), snapshot.getFrequencyTiltEnd(), 0.000001f);
    }
}
