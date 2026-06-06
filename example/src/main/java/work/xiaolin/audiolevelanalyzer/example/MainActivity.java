package work.xiaolin.audiolevelanalyzer.example;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Locale;

import work.xiaolin.audiolevelanalyzer.AudioLevelAnalyzerConfig;
import work.xiaolin.audiolevelanalyzer.AudioLevelAnalyzer;
import work.xiaolin.audiolevelanalyzer.example.audio.DecodedFilePlaybackSource;
import work.xiaolin.audiolevelanalyzer.example.ui.BarCountSelectorView;
import work.xiaolin.audiolevelanalyzer.example.ui.ValueSliderView;
import work.xiaolin.audiolevelanalyzer.example.ui.LevelBarsView;

public final class MainActivity extends Activity {

    private static final int DEFAULT_BAR_COUNT = 6;

    private volatile AudioLevelAnalyzer engine;
    private DecodedFilePlaybackSource source;
    private View visualStage;
    private LevelBarsView levelBars;
    private TextView playbackStateValue;
    private BarCountSelectorView barCountSelector;
    private ValueSliderView gainSeek;
    private TextView gainValue;
    private ValueSliderView analysisRateSeek;
    private TextView analysisRateValue;
    private ValueSliderView responseSeek;
    private TextView responseValue;
    private ValueSliderView dampingRatioSeek;
    private TextView dampingRatioValue;
    private TextView stopsValue;
    private TextView rawLevelsValue;
    private int currentBarCount = DEFAULT_BAR_COUNT;

    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        setContentView(R.layout.activity_main);
        findViewById(R.id.main).setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            }
        });

        visualStage = findViewById(R.id.visualStage);
        levelBars = findViewById(R.id.levelBars);
        playbackStateValue = findViewById(R.id.playbackStateValue);
        barCountSelector = findViewById(R.id.barCountSelector);
        gainSeek = findViewById(R.id.gainSeek);
        gainValue = findViewById(R.id.gainValue);
        analysisRateSeek = findViewById(R.id.analysisRateSeek);
        analysisRateValue = findViewById(R.id.analysisRateValue);
        responseSeek = findViewById(R.id.responseSeek);
        responseValue = findViewById(R.id.responseValue);
        dampingRatioSeek = findViewById(R.id.dampingRatioSeek);
        dampingRatioValue = findViewById(R.id.dampingRatioValue);
        stopsValue = findViewById(R.id.stopsValue);
        rawLevelsValue = findViewById(R.id.rawLevelsValue);

        levelBars.setAspectRatio(170f / 110f);
        visualStage.setContentDescription(getString(R.string.toggle_playback));

        source = new DecodedFilePlaybackSource(
                new DecodedFilePlaybackSource.Provider() {
                    @Override
                    public android.content.res.AssetFileDescriptor openFd() throws java.io.IOException {
                        return getResources().openRawResourceFd(R.raw.demo);
                    }
                }
        );
        source.setListener((mono, frames, sampleRate) -> {
            AudioLevelAnalyzer current = engine;
            if (current != null) {
                current.pushPcm(mono, frames, sampleRate);
            }
        });
        source.setPlayingListener(new work.xiaolin.audiolevelanalyzer.example.audio.PlaybackTransport.Listener() {
            @Override
            public void onPlayingChanged(boolean active) {
                AudioLevelAnalyzer current = engine;
                if (current != null) {
                    current.setActive(active);
                }
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        updatePlaybackState();
                    }
                });
            }
        });

        barCountSelector.setValue(DEFAULT_BAR_COUNT, false);
        gainSeek.setRange(0, 240, 20);
        analysisRateSeek.setRange(5, 120, 20);
        responseSeek.setRange(80, 1200, 500);
        dampingRatioSeek.setRange(1, 99, 80);

        visualStage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlayback();
            }
        });
        barCountSelector.setOnValueChangedListener(this::applyBarCount);
        gainSeek.setOnValueChangedListener(value -> applyGain(value / 10f));
        analysisRateSeek.setOnValueChangedListener(this::applyAnalysisRate);
        responseSeek.setOnValueChangedListener(this::applyResponseMs);
        dampingRatioSeek.setOnValueChangedListener(value -> applyDampingRatio(value / 100f));

        applyGain(gainSeek.getValue() / 10f);
        applyAnalysisRate(analysisRateSeek.getValue());
        applyResponseMs(responseSeek.getValue());
        applyDampingRatio(dampingRatioSeek.getValue() / 100f);
        replaceEngine(DEFAULT_BAR_COUNT);
        source.start();
        updatePlaybackState();
    }

    @SuppressWarnings("deprecation")
    private void configureSystemBars() {
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setStatusBarContrastEnforced(false);
        getWindow().setNavigationBarContrastEnforced(false);
    }

    private void applyBarCount(int barCount) {
        if (barCount == currentBarCount && engine != null) {
            return;
        }
        replaceEngine(barCount);
    }

    private void replaceEngine(int barCount) {
        currentBarCount = barCount;

        AudioLevelAnalyzer next = new AudioLevelAnalyzer(createConfig(barCount));
        next.setFrameListener(new AudioLevelAnalyzer.FrameListener() {
            @Override
            public void onFrame(float[] levels, long timestampNanos, boolean active) {
                levelBars.update(levels);
                updateInfoFrame(levels);
            }
        });
        if (source != null && !source.isPlaying()) {
            next.setActive(false);
        }
        next.start();

        AudioLevelAnalyzer previous = engine;
        engine = next;
        levelBars.setBarCount(next.getBarCount());
        stopsValue.setText(formatStops(next.getBandStops()));
        rawLevelsValue.setText(formatZeroLevels(next.getBarCount()));
        if (previous != null) {
            previous.release();
        }
    }

    private AudioLevelAnalyzerConfig createConfig(int barCount) {
        AudioLevelAnalyzerConfig base = AudioLevelAnalyzerConfig.defaults();
        return new AudioLevelAnalyzerConfig(
                base.getFftSize(),
                base.getDefaultSampleRate(),
                gainSeek.getValue() / 10f,
                base.getAmplitudeGain(),
                base.getExponentialGain(),
                base.getFrequencyTiltEnd(),
                presetStops(base, barCount),
                base.getAdjustmentCoefficients(),
                analysisRateSeek.getValue()
        );
    }

    private static float[] presetStops(AudioLevelAnalyzerConfig base, int barCount) {
        float[] anchors = base.getBandStops();
        if (barCount == DEFAULT_BAR_COUNT) {
            return anchors;
        }
        if (barCount == 3) {
            return new float[] {
                    anchors[0],
                    anchors[2],
                    anchors[4],
                    anchors[anchors.length - 1],
            };
        }
        if (barCount == 24) {
            return subdivideAnchors(
                    anchors,
                    barCount,
                    base.getFftSize(),
                    base.getDefaultSampleRate()
            );
        }
        return AudioLevelAnalyzerConfig.withBarCount(barCount).getBandStops();
    }

    private static float[] subdivideAnchors(
            float[] anchors,
            int barCount,
            int fftSize,
            int sampleRate
    ) {
        int[] segments = allocateSegments(anchors, barCount, fftSize, sampleRate);
        float[] out = new float[barCount + 1];
        int outIndex = 0;
        for (int i = 0; i < segments.length; i++) {
            float start = anchors[i];
            float end = anchors[i + 1];
            double startLog = Math.log(start);
            double endLog = Math.log(end);
            if (i == 0) {
                out[outIndex++] = start;
            }
            for (int j = 1; j <= segments[i]; j++) {
                out[outIndex++] = (float) Math.exp(startLog + (endLog - startLog) * j / segments[i]);
            }
        }
        return out;
    }

    private static int[] allocateSegments(float[] anchors, int barCount, int fftSize, int sampleRate) {
        int intervalCount = anchors.length - 1;
        int[] segments = new int[intervalCount];
        Arrays.fill(segments, 1);
        if (barCount <= intervalCount) {
            return segments;
        }

        float binHz = sampleRate / (float) fftSize;
        int[] maxSegments = new int[intervalCount];
        double[] weights = new double[intervalCount];
        for (int i = 0; i < intervalCount; i++) {
            int startBin = firstBinAtOrAbove(anchors[i], binHz);
            int endBin = firstBinAtOrAbove(anchors[i + 1], binHz);
            maxSegments[i] = Math.max(1, endBin - startBin);
            weights[i] = Math.log(anchors[i + 1] / anchors[i]);
        }

        int remaining = barCount - intervalCount;
        while (remaining > 0) {
            int best = -1;
            double bestScore = -1.0;
            for (int i = 0; i < intervalCount; i++) {
                if (segments[i] >= maxSegments[i]) {
                    continue;
                }
                double score = weights[i] / (segments[i] + 1.0);
                if (score > bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            segments[best]++;
            remaining--;
        }
        return segments;
    }

    private static int firstBinAtOrAbove(float frequencyHz, float binHz) {
        return (int) Math.ceil(frequencyHz / binHz);
    }

    private void applyGain(float gain) {
        AudioLevelAnalyzer current = engine;
        if (current != null) {
            current.setInputGain(gain);
        }
        gainValue.setText(String.format(Locale.US, "%.1f", gain));
    }

    private void applyAnalysisRate(int hz) {
        AudioLevelAnalyzer current = engine;
        if (current != null) {
            current.setAnalysisRateHz(hz);
        }
        analysisRateValue.setText(String.format(Locale.US, "%d", hz));
    }

    private void applyResponseMs(int responseMs) {
        levelBars.setSpringResponse(responseMs / 1000f);
        responseValue.setText(String.format(Locale.US, "%.2f", responseMs / 1000f));
    }

    private void applyDampingRatio(float ratio) {
        levelBars.setSpringDampingRatio(ratio);
        dampingRatioValue.setText(String.format(Locale.US, "%.2f", ratio));
    }

    private void updateInfoFrame(float[] rawLevels) {
        rawLevelsValue.setText(formatLevels(rawLevels));
    }

    private String formatStops(float[] stops) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < stops.length; i++) {
            if (i > 0) {
                out.append(" / ");
            }
            out.append(String.format(Locale.US, "%.0f", stops[i]));
        }
        return out.toString();
    }

    private String formatLevels(float[] levels) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < levels.length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(String.format(Locale.US, "%.2f", levels[i]));
        }
        return out.toString();
    }

    private String formatZeroLevels(int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append("0.00");
        }
        return out.toString();
    }

    private void togglePlayback() {
        if (source.isPlaying()) {
            pausePlayback();
        } else {
            source.play();
            updatePlaybackState();
        }
    }

    private void pausePlayback() {
        if (source != null && source.isPlaying()) {
            source.pause();
        }
        AudioLevelAnalyzer current = engine;
        if (current != null) {
            current.setActive(false);
        }
        updatePlaybackState();
    }

    private void updatePlaybackState() {
        boolean playing = source != null && source.isPlaying();
        playbackStateValue.setText(playing ? R.string.state_playing : R.string.state_paused);
        levelBars.setAlpha(playing ? 1f : 0.55f);
    }

    @Override
    protected void onPause() {
        pausePlayback();
        super.onPause();
    }

    @Override
    protected void onStop() {
        pausePlayback();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        AudioLevelAnalyzer current = engine;
        engine = null;
        if (current != null) {
            current.release();
        }
        if (source != null) {
            source.release();
        }
    }
}
