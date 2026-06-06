package work.xiaolin.audiolevelanalyzer.example.audio;

public interface PlaybackTransport {

    interface Listener {
        void onPlayingChanged(boolean playing);
    }

    boolean isPlaying();

    void play();

    void pause();

    void setPlayingListener(Listener listener);
}
