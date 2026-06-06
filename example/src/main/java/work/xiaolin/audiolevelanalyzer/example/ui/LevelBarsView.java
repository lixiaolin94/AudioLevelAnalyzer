package work.xiaolin.audiolevelanalyzer.example.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

public final class LevelBarsView extends View {

    private static final int DEFAULT_BAR_COUNT = 6;
    private static final int BAR_COLOR = 0xFFFFFFFF;
    private static final float MIN_RESPONSE_SECONDS = 0.001f;
    private static final float MIN_DAMPING_RATIO = 0.001f;
    private static final float MAX_DAMPING_RATIO = 0.999f;
    private static final float MAX_FRAME_DELTA_SECONDS = 1f / 15f;
    private static final double TWO_PI = Math.PI * 2.0;

    private int barCount = DEFAULT_BAR_COUNT;
    private int n = barCount;
    private float[] amps = new float[n];
    private float[] velocities = new float[n];
    private float[] targets = new float[n];
    private float springResponse = LevelBarsStyle.SPRING_RESPONSE;
    private float springDampingRatio = LevelBarsStyle.SPRING_DAMPING_RATIO;
    private float aspectRatio;
    private boolean animating;
    private long lastFrameNanos;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            doAnimationFrame(frameTimeNanos);
        }
    };

    public LevelBarsView(Context context) {
        this(context, null);
    }

    public LevelBarsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LevelBarsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        barPaint.setColor(BAR_COLOR);
    }

    public void setBarCount(int value) {
        int nextCount = Math.max(1, value);
        if (nextCount == n) {
            return;
        }
        int previousCount = n;
        barCount = nextCount;
        n = barCount;
        amps = resample(amps, previousCount, n);
        velocities = resample(velocities, previousCount, n);
        targets = resample(targets, previousCount, n);
        lastFrameNanos = 0L;
        invalidate();
        ensureAnimation();
    }

    public void setSpringResponse(float value) {
        springResponse = Math.max(MIN_RESPONSE_SECONDS, value);
        ensureAnimation();
    }

    public void setSpringDampingRatio(float value) {
        springDampingRatio = clamp(value, MIN_DAMPING_RATIO, MAX_DAMPING_RATIO);
        ensureAnimation();
    }

    public void setAspectRatio(float value) {
        aspectRatio = value;
        requestLayout();
    }

    public void update(float[] values) {
        int count = Math.min(values.length, n);
        for (int i = 0; i < count; i++) {
            targets[i] = clamp(values[i], 0f, 1f);
        }
        for (int i = count; i < n; i++) {
            targets[i] = 0f;
        }
        ensureAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) {
            return;
        }

        float slot = w / n;
        float barW0 = slot / 2f;
        float cy = h / 2f;
        for (int i = 0; i < n; i++) {
            float amp = amps[i];
            float widthAmp = clamp(amp, 0f, 1f);
            float heightAmp = clamp(amp, LevelBarsStyle.MIN_REBOUND_AMPLITUDE, 1f);
            float barW = barW0 + LevelBarsStyle.X_SCALE_MULTIPLIER * widthAmp;
            float defaultBarH = barW * LevelBarsStyle.MIN_HEIGHT_MULTIPLIER;
            float minBarH = barW * LevelBarsStyle.MIN_VISIBLE_HEIGHT_MULTIPLIER;
            float barH = clamp(defaultBarH + heightAmp * (h - defaultBarH), minBarH, h);
            float cx = (i + 0.5f) * slot;
            float r = Math.min(barW * LevelBarsStyle.BAR_CORNER_RADIUS_RATIO, barH / 2f);
            canvas.drawRoundRect(
                    cx - barW / 2f,
                    cy - barH / 2f,
                    cx + barW / 2f,
                    cy + barH / 2f,
                    r,
                    r,
                    barPaint
            );
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (aspectRatio <= 0f) {
            return;
        }
        int w = getMeasuredWidth();
        int h = getMeasuredHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        int nw = w;
        int nh = h;
        if (w / (float) h > aspectRatio) {
            nw = Math.round(h * aspectRatio);
        } else {
            nh = Math.round(w / aspectRatio);
        }
        setMeasuredDimension(nw, nh);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isSettled()) {
            ensureAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animating) {
            Choreographer.getInstance().removeFrameCallback(frameCallback);
            animating = false;
        }
        lastFrameNanos = 0L;
        super.onDetachedFromWindow();
    }

    private void ensureAnimation() {
        if (!isAttachedToWindow() || animating) {
            return;
        }
        animating = true;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private void doAnimationFrame(long frameTimeNanos) {
        float dt;
        if (lastFrameNanos == 0L) {
            dt = 1f / 60f;
        } else {
            dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f;
        }
        lastFrameNanos = frameTimeNanos;
        boolean settled = advanceSpring(clamp(dt, 0f, MAX_FRAME_DELTA_SECONDS));
        invalidate();
        if (settled) {
            animating = false;
            lastFrameNanos = 0L;
        } else {
            Choreographer.getInstance().postFrameCallback(frameCallback);
        }
    }

    private boolean advanceSpring(float dtSeconds) {
        float response = Math.max(springResponse, MIN_RESPONSE_SECONDS);
        double dampingRatio = clamp(springDampingRatio, MIN_DAMPING_RATIO, MAX_DAMPING_RATIO);
        double omega0 = TWO_PI / response;
        double omegaD = omega0 * Math.sqrt(1.0 - dampingRatio * dampingRatio);
        double dt = dtSeconds;
        double decay = Math.exp(-dampingRatio * omega0 * dt);
        double cosTerm = Math.cos(omegaD * dt);
        double sinTerm = Math.sin(omegaD * dt);
        boolean settled = true;

        for (int i = 0; i < n; i++) {
            double displacement = amps[i] - targets[i];
            double velocity = velocities[i];
            double velocityTerm = (velocity + dampingRatio * omega0 * displacement) / omegaD;

            double nextDisplacement = decay * (displacement * cosTerm + velocityTerm * sinTerm);
            double nextVelocity = decay * (
                    -dampingRatio * omega0 * (displacement * cosTerm + velocityTerm * sinTerm) +
                            (-displacement * omegaD * sinTerm + velocityTerm * omegaD * cosTerm)
            );

            amps[i] = (float) (targets[i] + nextDisplacement);
            velocities[i] = (float) nextVelocity;

            if (Math.abs(amps[i] - targets[i]) <= LevelBarsStyle.MIN_VISIBLE_CHANGE &&
                    Math.abs(velocities[i]) <= LevelBarsStyle.MIN_VISIBLE_CHANGE) {
                amps[i] = targets[i];
                velocities[i] = 0f;
            } else {
                settled = false;
            }
        }
        return settled;
    }

    private boolean isSettled() {
        for (int i = 0; i < n; i++) {
            if (Math.abs(amps[i] - targets[i]) > LevelBarsStyle.MIN_VISIBLE_CHANGE ||
                    Math.abs(velocities[i]) > LevelBarsStyle.MIN_VISIBLE_CHANGE) {
                return false;
            }
        }
        return true;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float[] resample(float[] source, int sourceCount, int targetCount) {
        float[] out = new float[targetCount];
        if (sourceCount <= 0 || source == null || source.length == 0) {
            return out;
        }
        if (targetCount == 1) {
            out[0] = source[Math.min(source.length - 1, Math.max(0, sourceCount / 2))];
            return out;
        }
        if (sourceCount == 1) {
            java.util.Arrays.fill(out, source[0]);
            return out;
        }
        for (int i = 0; i < targetCount; i++) {
            float sourceIndex = i * (sourceCount - 1f) / (targetCount - 1f);
            int lo = (int) Math.floor(sourceIndex);
            int hi = Math.min(sourceCount - 1, lo + 1);
            float t = sourceIndex - lo;
            out[i] = source[lo] + (source[hi] - source[lo]) * t;
        }
        return out;
    }
}
