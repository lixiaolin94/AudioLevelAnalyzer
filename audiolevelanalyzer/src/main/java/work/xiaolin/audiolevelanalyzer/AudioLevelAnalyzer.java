package work.xiaolin.audiolevelanalyzer;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

/**
 * Data-only audio level analyzer.
 *
 * <p>Callers feed normalized mono PCM through {@link #pushPcm(float[], int, int)} and receive
 * finite levels in {@code [0, 1]} through {@link FrameListener}. Drawing, animation, playback,
 * and capture are owned by the app.
 */
public final class AudioLevelAnalyzer {

    public static final double DEFAULT_ANALYSIS_RATE_HZ = 20.0;
    public static final double MAX_ANALYSIS_RATE_HZ = 120.0;

    private final Object lifecycleLock = new Object();

    private volatile Handler mainHandler;
    private HandlerThread analyzeThread;
    private Handler analyzeHandler;

    private volatile boolean running;
    private volatile boolean active = true;
    private boolean started;
    private volatile boolean released;
    private volatile long tickGeneration;
    private final AudioLevelAnalyzerConfig config;
    private final DspPipeline pipeline;
    private volatile float inputGain;
    private volatile double analysisRateHz;
    private volatile FrameListener frameListener;
    private long listenerGeneration;
    private volatile long nextTargetNanos;

    private volatile float[] latestLevels;

    public AudioLevelAnalyzer() {
        this(AudioLevelAnalyzerConfig.defaults());
    }

    public AudioLevelAnalyzer(AudioLevelAnalyzerConfig config) {
        if (config == null) {
            throw new NullPointerException("config == null");
        }
        this.config = config;
        this.pipeline = new DspPipeline(config);
        this.inputGain = config.getInputGain();
        this.analysisRateHz = config.getAnalysisRateHz();
        this.latestLevels = new float[pipeline.getAmplitudeCount()];
    }

    public void setFrameListener(FrameListener listener) {
        synchronized (lifecycleLock) {
            if (released) {
                return;
            }
            frameListener = listener;
            listenerGeneration++;
        }
    }

    public AudioLevelAnalyzerConfig getConfig() {
        return config.withRuntime(inputGain, analysisRateHz);
    }

    public double getAnalysisRateHz() {
        return analysisRateHz;
    }

    public void setAnalysisRateHz(double value) {
        if (Double.isNaN(value) ||
                Double.isInfinite(value) ||
                value < AudioLevelAnalyzerConfig.MIN_ANALYSIS_RATE_HZ ||
                value > MAX_ANALYSIS_RATE_HZ) {
            throw new IllegalArgumentException(
                    "analysisRateHz must be in [" +
                            AudioLevelAnalyzerConfig.MIN_ANALYSIS_RATE_HZ + ", " +
                            MAX_ANALYSIS_RATE_HZ + "]"
            );
        }
        analysisRateHz = value;
    }

    public float getInputGain() {
        return inputGain;
    }

    public void setInputGain(float value) {
        pipeline.setInputGain(value);
        inputGain = value;
    }

    public int getBarCount() {
        return config.getBarCount();
    }

    public float[] getBandStops() {
        return config.getBandStops();
    }

    public void pushPcm(float[] mono, int frames, int sampleRate) {
        if (released) {
            return;
        }
        if (mono == null) {
            throw new NullPointerException("mono == null");
        }
        if (frames < 0 || frames > mono.length) {
            throw new IllegalArgumentException("frames must be between 0 and mono.length");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive");
        }
        pipeline.push(mono, frames, sampleRate);
    }

    public void start() {
        synchronized (lifecycleLock) {
            if (released || started) {
                return;
            }
            started = true;
            if (active) {
                beginLoopLocked();
            }
        }
    }

    public void setActive(boolean value) {
        synchronized (lifecycleLock) {
            if (released || active == value) {
                return;
            }
            active = value;
            if (!started) {
                return;
            }
            if (value) {
                beginLoopLocked();
            } else {
                long generation = endLoopLocked();
                postClearedLocked(generation);
            }
        }
    }

    public void stop() {
        synchronized (lifecycleLock) {
            if (released || !started) {
                return;
            }
            started = false;
            endLoopLocked();
        }
    }

    public void release() {
        HandlerThread threadToQuit;
        synchronized (lifecycleLock) {
            if (released) {
                return;
            }
            released = true;
            started = false;
            endLoopLocked();
            frameListener = null;
            listenerGeneration++;
            threadToQuit = analyzeThread;
            analyzeThread = null;
            analyzeHandler = null;
        }
        if (threadToQuit != null) {
            threadToQuit.quitSafely();
        }
    }

    private void beginLoopLocked() {
        if (running) {
            return;
        }
        running = true;
        nextTargetNanos = System.nanoTime();
        long generation = ++tickGeneration;
        ensureThreadLocked();
        analyzeHandler.post(() -> runTick(generation));
    }

    private long endLoopLocked() {
        running = false;
        nextTargetNanos = 0L;
        return ++tickGeneration;
    }

    private void postClearedLocked(long generation) {
        Runnable clearTargets = () -> {
            float[] clearedLevels = new float[pipeline.getAmplitudeCount()];
            synchronized (lifecycleLock) {
                if (!isFrameCurrentLocked(generation, false)) {
                    return;
                }
                latestLevels = clearedLevels;
            }
            postFrame(System.nanoTime(), clearedLevels, false, generation);
        };
        if (analyzeHandler != null) {
            analyzeHandler.post(clearTargets);
        } else {
            clearTargets.run();
        }
    }

    private void ensureThreadLocked() {
        if (analyzeThread != null) {
            return;
        }
        analyzeThread = new HandlerThread("audio-level-analyzer");
        analyzeThread.start();
        analyzeHandler = new Handler(analyzeThread.getLooper());
    }

    private long analysisIntervalNs() {
        return Math.max(1L, (long) (1_000_000_000.0 / analysisRateHz));
    }

    private void runTick(long generation) {
        if (!running || !active || generation != tickGeneration) {
            return;
        }
        analyzeWindow(System.nanoTime(), generation);
        if (running && active && generation == tickGeneration) {
            scheduleNextTick(System.nanoTime(), generation);
        }
    }

    private void scheduleNextTick(long nowNanos, long generation) {
        long target = nextTargetAfter(nextTargetNanos, nowNanos, analysisIntervalNs());
        nextTargetNanos = target;
        long delayMs = delayMillisUntil(System.nanoTime(), target);
        Handler handler = analyzeHandler;
        if (handler != null) {
            handler.postDelayed(() -> runTick(generation), delayMs);
        }
    }

    private void analyzeWindow(long now, long generation) {
        pipeline.analyze(latestLevels);
        postFrame(now, latestLevels, true, generation);
    }

    private void postFrame(long timestampNanos, float[] levels, boolean frameActive, long frameGeneration) {
        final long listenerToken;
        synchronized (lifecycleLock) {
            if (!isFrameCurrentLocked(frameGeneration, frameActive) || frameListener == null) {
                return;
            }
            listenerToken = listenerGeneration;
        }
        final float[] frameLevels = levels.clone();
        mainHandler().post(() -> {
            FrameListener live;
            synchronized (lifecycleLock) {
                if (!isFrameCurrentLocked(frameGeneration, frameActive) ||
                        listenerGeneration != listenerToken) {
                    return;
                }
                live = frameListener;
            }
            if (live != null) {
                live.onFrame(frameLevels, timestampNanos, frameActive);
            }
        });
    }

    private boolean isFrameCurrentLocked(long frameGeneration, boolean frameActive) {
        return isFrameCurrent(released, running, active, tickGeneration, frameGeneration, frameActive);
    }

    static boolean isFrameCurrent(
            boolean released,
            boolean running,
            boolean active,
            long currentGeneration,
            long frameGeneration,
            boolean frameActive
    ) {
        if (released || currentGeneration != frameGeneration) {
            return false;
        }
        return frameActive ? running && active : !active;
    }

    private Handler mainHandler() {
        Handler handler = mainHandler;
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
            mainHandler = handler;
        }
        return handler;
    }

    static long nextTargetAfter(long previousTargetNanos, long nowNanos, long intervalNanos) {
        if (intervalNanos <= 0L) {
            throw new IllegalArgumentException("intervalNanos must be positive");
        }
        long target = previousTargetNanos;
        if (target <= 0L) {
            target = nowNanos;
        }
        do {
            target += intervalNanos;
        } while (target - nowNanos <= 0L);
        return target;
    }

    static long delayMillisUntil(long nowNanos, long targetNanos) {
        long remainingNanos = targetNanos - nowNanos;
        if (remainingNanos <= 0L) {
            return 0L;
        }
        return (remainingNanos + 999_999L) / 1_000_000L;
    }

    public interface FrameListener {
        void onFrame(float[] levels, long timestampNanos, boolean active);
    }
}
