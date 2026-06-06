package work.xiaolin.audiolevelanalyzer.example.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public final class BarCountSelectorView extends View {

    private static final int[] OPTIONS = {3, 6, 24};

    private final int desiredHeight;
    private final float borderWidth;
    private final float cornerRadius;
    private final float selectedInset;
    private final RectF rect = new RectF();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int selectedIndex = 1;
    private OnValueChangedListener onValueChangedListener;

    public BarCountSelectorView(Context context) {
        this(context, null);
    }

    public BarCountSelectorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BarCountSelectorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        desiredHeight = Math.round(36f * density);
        borderWidth = density;
        cornerRadius = 6f * density;
        selectedInset = 3f * density;

        borderPaint.setColor(0x33FFFFFF);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidth);

        dividerPaint.setColor(0x26FFFFFF);
        dividerPaint.setStrokeWidth(borderWidth);

        selectedPaint.setColor(0xFFFFFFFF);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(12f * density);
        textPaint.setTypeface(android.graphics.Typeface.MONOSPACE);

        setClickable(true);
        setFocusable(true);
        updateContentDescription();
    }

    public int getValue() {
        return OPTIONS[selectedIndex];
    }

    public void setValue(int value, boolean notify) {
        int nextIndex = indexOf(value);
        if (nextIndex < 0 || nextIndex == selectedIndex) {
            return;
        }
        selectedIndex = nextIndex;
        updateContentDescription();
        invalidate();
        if (notify && onValueChangedListener != null) {
            onValueChangedListener.onValueChanged(getValue());
        }
    }

    public void setOnValueChangedListener(OnValueChangedListener listener) {
        onValueChangedListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        int measuredHeight = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) {
            return;
        }

        float segmentW = w / OPTIONS.length;
        float selectedLeft = selectedIndex * segmentW + selectedInset;
        float selectedRight = (selectedIndex + 1) * segmentW - selectedInset;
        rect.set(selectedLeft, selectedInset, selectedRight, h - selectedInset);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, selectedPaint);

        rect.set(borderWidth / 2f, borderWidth / 2f, w - borderWidth / 2f, h - borderWidth / 2f);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint);

        for (int i = 1; i < OPTIONS.length; i++) {
            float x = i * segmentW;
            canvas.drawLine(x, selectedInset, x, h - selectedInset, dividerPaint);
        }

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float y = h / 2f - (fm.ascent + fm.descent) / 2f;
        for (int i = 0; i < OPTIONS.length; i++) {
            textPaint.setColor(i == selectedIndex ? 0xFF0B0B0D : 0xB3FFFFFF);
            canvas.drawText(Integer.toString(OPTIONS[i]), segmentW * (i + 0.5f), y, textPaint);
        }
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
        if (getWidth() <= 0) {
            return;
        }
        int index = Math.max(0, Math.min(OPTIONS.length - 1, (int) (x / (getWidth() / (float) OPTIONS.length))));
        setValue(OPTIONS[index], true);
    }

    private void updateContentDescription() {
        setContentDescription("Bars " + getValue());
    }

    private static int indexOf(int value) {
        for (int i = 0; i < OPTIONS.length; i++) {
            if (OPTIONS[i] == value) {
                return i;
            }
        }
        return -1;
    }

    public interface OnValueChangedListener {
        void onValueChanged(int value);
    }
}
