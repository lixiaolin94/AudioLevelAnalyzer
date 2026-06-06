package work.xiaolin.audiolevelanalyzer;

final class Radix2Fft {

    private final int n;
    private final int log2n;
    private final int[] bitReversed;
    private final float[] cosTable;
    private final float[] sinTable;

    Radix2Fft(int size) {
        if (size < 2 || (size & (size - 1)) != 0) {
            throw new IllegalArgumentException("FFT size must be a power of two >= 2: " + size);
        }
        this.n = size;
        this.log2n = Integer.numberOfTrailingZeros(size);
        this.bitReversed = new int[size];
        for (int i = 0; i < size; i++) {
            bitReversed[i] = reverseBits(i, log2n);
        }
        this.cosTable = new float[size / 2];
        this.sinTable = new float[size / 2];
        for (int i = 0; i < size / 2; i++) {
            double angle = -2.0 * Math.PI * i / size;
            cosTable[i] = (float) Math.cos(angle);
            sinTable[i] = (float) Math.sin(angle);
        }
    }

    void forward(float[] real, float[] imag) {
        for (int i = 0; i < n; i++) {
            int j = bitReversed[i];
            if (j > i) {
                float tr = real[i]; real[i] = real[j]; real[j] = tr;
                float ti = imag[i]; imag[i] = imag[j]; imag[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            int half = len >> 1;
            int step = n / len;
            for (int i = 0; i < n; i += len) {
                int k = 0;
                for (int j = 0; j < half; j++) {
                    float wr = cosTable[k];
                    float wi = sinTable[k];
                    int a = i + j;
                    int b = a + half;
                    float tr = wr * real[b] - wi * imag[b];
                    float ti = wr * imag[b] + wi * real[b];
                    real[b] = real[a] - tr;
                    imag[b] = imag[a] - ti;
                    real[a] += tr;
                    imag[a] += ti;
                    k += step;
                }
            }
        }
    }

    private static int reverseBits(int value, int bits) {
        int r = 0;
        for (int i = 0; i < bits; i++) {
            r = (r << 1) | (value & 1);
            value >>= 1;
        }
        return r;
    }
}
