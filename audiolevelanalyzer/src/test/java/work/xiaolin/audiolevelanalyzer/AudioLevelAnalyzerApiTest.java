package work.xiaolin.audiolevelanalyzer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public final class AudioLevelAnalyzerApiTest {

    @Test
    public void publicApiUsesFocusedRuntimeSettings() {
        Set<String> names = new HashSet<>();
        for (Method method : AudioLevelAnalyzer.class.getMethods()) {
            names.add(method.getName());
        }

        assertTrue(names.contains("getConfig"));
        assertTrue(names.contains("getBarCount"));
        assertTrue(names.contains("getBandStops"));
        assertTrue(names.contains("setInputGain"));
        assertTrue(names.contains("setAnalysisRateHz"));
        assertFalse(names.contains("getFftSize"));
        assertFalse(names.contains("getDefaultSampleRate"));
        assertFalse(names.contains("getAdjustmentCoefficients"));
        assertFalse(names.contains("setFftSize"));
        assertFalse(names.contains("setDefaultSampleRate"));
        assertFalse(names.contains("setAmplitudeGain"));
        assertFalse(names.contains("setExponentialGain"));
        assertFalse(names.contains("setFrequencyTiltEnd"));
        assertFalse(names.contains("setBandStops"));
        assertFalse(names.contains("setAdjustmentCoefficients"));
        assertFalse(names.contains("setFrequencyExponent"));
        assertFalse(names.contains("updateConfig"));
        assertFalse(Modifier.isPublic(DspPipeline.class.getModifiers()));
    }

    @Test
    public void engineCanBeConstructedInJvmTestsForRuntimeSettings() {
        AudioLevelAnalyzer engine = new AudioLevelAnalyzer();

        engine.setInputGain(2.5f);
        engine.setAnalysisRateHz(30.0);

        assertEquals(2.5f, engine.getInputGain(), 0.000001f);
        assertEquals(30.0, engine.getAnalysisRateHz(), 0.000001);
        assertEquals(2.5f, engine.getConfig().getInputGain(), 0.000001f);
        assertEquals(30.0, engine.getConfig().getAnalysisRateHz(), 0.000001);
    }

    @Test
    public void releaseIsIdempotentAndMakesLifecycleCallsNoOps() {
        AudioLevelAnalyzer engine = new AudioLevelAnalyzer();
        engine.setFrameListener((levels, timestampNanos, active) -> { });

        engine.release();
        engine.release();
        engine.stop();
        engine.setActive(false);
        engine.setActive(true);
        engine.pushPcm(new float[256], 256, 48_000);
    }

    @Test
    public void listenerChangesAdvanceDeliveryGeneration() throws Exception {
        AudioLevelAnalyzer engine = new AudioLevelAnalyzer();
        Field listenerGeneration = AudioLevelAnalyzer.class.getDeclaredField("listenerGeneration");
        listenerGeneration.setAccessible(true);

        assertEquals(0L, listenerGeneration.getLong(engine));

        engine.setFrameListener((levels, timestampNanos, active) -> { });
        assertEquals(1L, listenerGeneration.getLong(engine));

        engine.setFrameListener(null);
        assertEquals(2L, listenerGeneration.getLong(engine));

        engine.release();
        assertEquals(3L, listenerGeneration.getLong(engine));

        engine.setFrameListener((levels, timestampNanos, active) -> { });
        assertEquals(3L, listenerGeneration.getLong(engine));
    }

    @Test
    public void engineRejectsInvalidRuntimeSettings() {
        AudioLevelAnalyzer engine = new AudioLevelAnalyzer();

        assertThrows(IllegalArgumentException.class, () ->
                engine.setInputGain(Float.NaN));
        assertThrows(IllegalArgumentException.class, () ->
                engine.setInputGain(-0.1f));
        assertThrows(IllegalArgumentException.class, () ->
                engine.setAnalysisRateHz(Double.NaN));
        assertThrows(IllegalArgumentException.class, () ->
                engine.setAnalysisRateHz(AudioLevelAnalyzerConfig.MIN_ANALYSIS_RATE_HZ - 0.1));
        assertThrows(IllegalArgumentException.class, () ->
                engine.setAnalysisRateHz(AudioLevelAnalyzer.MAX_ANALYSIS_RATE_HZ + 0.1));
    }
}
