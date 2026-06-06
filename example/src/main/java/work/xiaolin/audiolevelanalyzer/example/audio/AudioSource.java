package work.xiaolin.audiolevelanalyzer.example.audio;

public interface AudioSource {

    interface Listener {
        void onPcm(float[] mono, int frames, int sampleRate);
    }

    void setListener(Listener listener);

    void start();

    void stop();

    void release();
}
