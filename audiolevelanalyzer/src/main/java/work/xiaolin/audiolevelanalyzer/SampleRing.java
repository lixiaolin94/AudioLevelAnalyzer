package work.xiaolin.audiolevelanalyzer;

final class SampleRing {

    private final Object lock = new Object();
    private final int size;
    private final float[] ring;
    private int sampleRate;
    private int writeIndex;
    private int filled;

    SampleRing(int fftSize, int defaultSampleRate) {
        this.size = fftSize;
        this.ring = new float[fftSize];
        this.sampleRate = defaultSampleRate;
    }

    void setSampleRate(int value) {
        if (value <= 0) {
            return;
        }
        synchronized (lock) {
            sampleRate = value;
        }
    }

    void push(float[] samples, int count) {
        if (count <= 0) {
            return;
        }
        if (samples == null) {
            throw new NullPointerException("samples == null");
        }
        if (count > samples.length) {
            throw new IllegalArgumentException("count must be <= samples.length");
        }
        synchronized (lock) {
            if (count >= size) {
                System.arraycopy(samples, count - size, ring, 0, size);
                writeIndex = 0;
                filled = size;
            } else {
                for (int i = 0; i < count; i++) {
                    ring[writeIndex] = samples[i];
                    writeIndex++;
                    if (writeIndex == size) {
                        writeIndex = 0;
                    }
                }
                filled = Math.min(size, filled + count);
            }
        }
    }

    int snapshot(float[] out) {
        synchronized (lock) {
            int leadingZeros = size - filled;
            if (leadingZeros > 0) {
                java.util.Arrays.fill(out, 0, leadingZeros, 0f);
            }

            int oldest = filled == size ? writeIndex : 0;
            int first = Math.min(filled, size - oldest);
            System.arraycopy(ring, oldest, out, leadingZeros, first);
            if (first < filled) {
                System.arraycopy(ring, 0, out, leadingZeros + first, filled - first);
            }
            return sampleRate;
        }
    }
}
