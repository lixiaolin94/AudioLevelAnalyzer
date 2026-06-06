package work.xiaolin.audiolevelanalyzer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class DspPrimitivesTest {

    @Test
    public void radix2FftTransformsImpulseAndConstantSignals() {
        Radix2Fft fft = new Radix2Fft(8);
        float[] real = new float[8];
        float[] imag = new float[8];

        real[0] = 1f;
        fft.forward(real, imag);
        for (int i = 0; i < 8; i++) {
            assertEquals(1f, real[i], 0.000001f);
            assertEquals(0f, imag[i], 0.000001f);
        }

        real = new float[] {1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f};
        imag = new float[8];
        fft.forward(real, imag);
        assertEquals(8f, real[0], 0.000001f);
        assertEquals(0f, imag[0], 0.000001f);
        for (int i = 1; i < 8; i++) {
            assertEquals(0f, real[i], 0.000001f);
            assertEquals(0f, imag[i], 0.000001f);
        }
    }

    @Test
    public void hannWindowUsesPeriodicCoefficients() {
        HannWindow window = new HannWindow(4);
        float[] out = new float[4];

        window.apply(new float[] {1f, 1f, 1f, 1f}, out);

        assertArrayEquals(new float[] {0f, 0.5f, 1f, 0.5f}, out, 0.000001f);
    }

    @Test
    public void sampleRingKeepsMostRecentWindow() {
        SampleRing ring = new SampleRing(4, 48_000);
        float[] out = new float[4];

        ring.push(new float[] {1f, 2f}, 2);
        assertEquals(48_000, ring.snapshot(out));
        assertArrayEquals(new float[] {0f, 0f, 1f, 2f}, out, 0.000001f);

        ring.setSampleRate(44_100);
        ring.push(new float[] {3f, 4f, 5f}, 3);
        assertEquals(44_100, ring.snapshot(out));
        assertArrayEquals(new float[] {2f, 3f, 4f, 5f}, out, 0.000001f);

        ring.push(new float[] {6f, 7f, 8f, 9f, 10f}, 5);
        ring.snapshot(out);
        assertArrayEquals(new float[] {7f, 8f, 9f, 10f}, out, 0.000001f);
    }

    @Test
    public void sampleRingWrapsPartialWritesInTimeOrder() {
        SampleRing ring = new SampleRing(4, 48_000);
        float[] out = new float[4];

        ring.push(new float[] {1f, 2f, 3f, 4f}, 4);
        ring.push(new float[] {5f, 6f}, 2);
        ring.snapshot(out);

        assertArrayEquals(new float[] {3f, 4f, 5f, 6f}, out, 0.000001f);
    }

    @Test
    public void sampleRingRejectsOversizedCount() {
        SampleRing ring = new SampleRing(4, 48_000);

        assertThrows(IllegalArgumentException.class, () ->
                ring.push(new float[] {1f, 2f}, 3));
    }

    @Test
    public void bandMapperWritesToProvidedOutputArray() {
        AudioLevelAnalyzerConfig config = new AudioLevelAnalyzerConfig(
                8,
                8_000,
                1f,
                1f,
                1f,
                1f,
                new float[] {1f, 2_000f, 4_000f},
                new float[] {1f},
                AudioLevelAnalyzer.DEFAULT_ANALYSIS_RATE_HZ
        );
        BandMapper mapper = new BandMapper(config);
        float[] out = new float[] {-1f, -1f};

        mapper.map(new float[] {0.1f, 0.2f, 0.3f, 0.4f}, 8_000f, out);

        assertArrayEquals(new float[] {0.2f, 0.7f}, out, 0.000001f);
    }

    @Test
    public void bandMapperRefreshesCachedRangesWhenSampleRateChanges() {
        AudioLevelAnalyzerConfig config = new AudioLevelAnalyzerConfig(
                8,
                8_000,
                1f,
                1f,
                1f,
                1f,
                new float[] {1f, 2_000f, 4_000f},
                new float[] {1f},
                AudioLevelAnalyzer.DEFAULT_ANALYSIS_RATE_HZ
        );
        BandMapper mapper = new BandMapper(config);
        float[] out = new float[] {-1f, -1f};
        float[] magnitudes = new float[] {0.1f, 0.2f, 0.3f, 0.4f};

        mapper.map(magnitudes, 8_000f, out);
        assertArrayEquals(new float[] {0.2f, 0.7f}, out, 0.000001f);

        mapper.map(magnitudes, 16_000f, out);
        assertArrayEquals(new float[] {0f, 0.2f}, out, 0.000001f);

        mapper.map(magnitudes, 8_000f, out);
        assertArrayEquals(new float[] {0.2f, 0.7f}, out, 0.000001f);
    }

    @Test
    public void frequencyTiltEndPreservesDefaultSixBandShape() {
        float end = AudioLevelAnalyzerConfig.DEFAULT_FREQUENCY_TILT_END;

        assertEquals(1f, BandMapper.frequencyMultiplier(0, 6, end), 0.000001f);
        assertEquals(0.85f, BandMapper.frequencyMultiplier(1, 6, end), 0.000001f);
        assertEquals(0.70f, BandMapper.frequencyMultiplier(2, 6, end), 0.000001f);
        assertEquals(0.55f, BandMapper.frequencyMultiplier(3, 6, end), 0.000001f);
        assertEquals(0.40f, BandMapper.frequencyMultiplier(4, 6, end), 0.000001f);
        assertEquals(0.25f, BandMapper.frequencyMultiplier(5, 6, end), 0.000001f);

        assertEquals(0.25f, BandMapper.frequencyMultiplier(11, 12, end), 0.000001f);
    }
}
