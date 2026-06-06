package work.xiaolin.audiolevelanalyzer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AudioLevelAnalyzerSchedulerTest {

    @Test
    public void nextTargetUsesAbsoluteTimelineAndSkipsMissedFrames() {
        long target = 1_000L;

        target = AudioLevelAnalyzer.nextTargetAfter(target, 1_000L, 50L);
        assertEquals(1_050L, target);
        target = AudioLevelAnalyzer.nextTargetAfter(target, 1_051L, 50L);
        assertEquals(1_100L, target);
        target = AudioLevelAnalyzer.nextTargetAfter(target, 1_220L, 50L);
        assertEquals(1_250L, target);
    }

    @Test
    public void nextTargetCanStartFromClearedState() {
        assertEquals(250L, AudioLevelAnalyzer.nextTargetAfter(0L, 200L, 50L));
    }

    @Test
    public void delayMillisRoundsUpToAvoidEarlyHandlerRuns() {
        assertEquals(0L, AudioLevelAnalyzer.delayMillisUntil(1_000_000L, 1_000_000L));
        assertEquals(1L, AudioLevelAnalyzer.delayMillisUntil(1_000_000L, 1_000_001L));
        assertEquals(5L, AudioLevelAnalyzer.delayMillisUntil(1_000_000L, 5_800_000L));
    }

    @Test(timeout = 1_000L)
    public void nextTargetTerminatesNearLongMaxWithoutSpinning() {
        long now = Long.MAX_VALUE - 100L;

        long target = AudioLevelAnalyzer.nextTargetAfter(now, now, 8_333_333L);

        assertTrue("target must advance past now", target - now > 0L);
    }

    @Test
    public void frameGateKeepsOnlyCurrentLifecycleFrames() {
        assertTrue(AudioLevelAnalyzer.isFrameCurrent(
                false, true, true, 3L, 3L, true));
        assertTrue(AudioLevelAnalyzer.isFrameCurrent(
                false, false, false, 4L, 4L, false));

        assertFalse(AudioLevelAnalyzer.isFrameCurrent(
                false, false, true, 4L, 3L, true));
        assertFalse(AudioLevelAnalyzer.isFrameCurrent(
                false, true, true, 5L, 5L, false));
        assertFalse(AudioLevelAnalyzer.isFrameCurrent(
                false, true, true, 5L, 4L, false));
        assertFalse(AudioLevelAnalyzer.isFrameCurrent(
                true, false, false, 4L, 4L, false));
    }

    @Test
    public void intervalMustBePositive() {
        assertThrows(IllegalArgumentException.class, () ->
                AudioLevelAnalyzer.nextTargetAfter(1_000L, 1_000L, 0L));
    }
}
