package work.xiaolin.audiolevelanalyzer.example.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public final class ValueSliderView extends View {

    private int value;
    private int minValue;
    private int maxValue = 100;
    private final float trackHeight;
    private final float thumbWidth;
    private final float thumbHeight;
    private final float thumbCornerRadius;
    private final int desiredHeight;
    private final Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private OnValueChangedListener onValueChangedListener;

    public ValueSliderView(Context context) {
        this(context, null);
    }

    public ValueSliderView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ValueSliderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        trackHeight = 3f * density;
        thumbWidth = 4f * density;
        thumbHeight = 22f * density;
        thumbCornerRadius = 1.5f * density;
        desiredHeight = Math.round(40f * density);

        inactivePaint.setColor(0x33FFFFFF);
        inactivePaint.setStrokeCap(Paint.Cap.BUTT);
        inactivePaint.setStrokeWidth(trackHeight);

        activePaint.setColor(0xFFFFFFFF);
        activePaint.setStrokeCap(Paint.Cap.BUTT);
        activePaint.setStrokeWidth(trackHeight);

        thumbPaint.setColor(0xFFFFFFFF);
        setClickable(true);
    }

    public int getValue() {
        return value;
    }

    public void setOnValueChangedListener(OnValueChangedListener listener) {
        onValueChangedListener = listener;
    }

    public void setRange(int min, int max, int initial) {
        if (max <= min) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        minValue = min;
        maxValue = max;
        setValue(initial, false);
    }

    private void setValue(int next) {
        setValue(next, true);
    }

    private void setValue(int next, boolean notify) {
        int coerced = Math.max(minValue, Math.min(maxValue, next));
        if (coerced == value) {
            return;
        }
        value = coerced;
        invalidate();
        if (notify && onValueChangedListener != null) {
            onValueChangedListener.onValueChanged(value);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        int measuredHeight = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cy = getHeight() / 2f;
        float start = getPaddingLeft();
        float end = getWidth() - getPaddingRight();
        if (end <= start) {
            return;
        }

        float fraction = (value - minValue) / (float) (maxValue - minValue);
        float x = start + (end - start) * fraction;
        canvas.drawLine(start, cy, end, cy, inactivePaint);
        canvas.drawLine(start, cy, x, cy, activePaint);
        float thumbLeft = clamp(x - thumbWidth / 2f, 0f, getWidth() - thumbWidth);
        canvas.drawRoundRect(
                thumbLeft,
                cy - thumbHeight / 2f,
                thumbLeft + thumbWidth,
                cy + thumbHeight / 2f,
                thumbCornerRadius,
                thumbCornerRadius,
                thumbPaint
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                setPressed(true);
                updateFromTouch(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                updateFromTouch(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
                updateFromTouch(event.getX());
                setPressed(false);
                performClick();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void updateFromTouch(float x) {
        float start = getPaddingLeft();
        float end = getWidth() - getPaddingRight();
        if (end <= start) {
            return;
        }
        float fraction = clamp((x - start) / (end - start), 0f, 1f);
        setValue(minValue + Math.round((maxValue - minValue) * fraction));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public interface OnValueChangedListener {
        void onValueChanged(int value);
    }
}
