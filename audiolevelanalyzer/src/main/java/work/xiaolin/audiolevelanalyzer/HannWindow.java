package work.xiaolin.audiolevelanalyzer;

final class HannWindow {

    private final float[] coefficients;

    HannWindow(int size) {
        coefficients = new float[size];
        for (int n = 0; n < size; n++) {
            coefficients[n] = (float) (0.5 - 0.5 * Math.cos(2.0 * Math.PI * n / size));
        }
    }

    void apply(float[] input, float[] output) {
        final float[] w = coefficients;
        final int n = w.length;
        for (int i = 0; i < n; i++) {
            output[i] = input[i] * w[i];
        }
    }
}
