package work.xiaolin.audiolevelanalyzer.example.audio;

import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class DecodedFilePlaybackSource implements AudioSource, PlaybackTransport {

    public interface Provider {
        AssetFileDescriptor openFd() throws IOException;
    }

    private static final String TAG = "FilePlaybackSource";
    private static final long TIMEOUT_US = 10_000L;

    private final Provider provider;

    private volatile AudioSource.Listener pcmListener;
    private volatile PlaybackTransport.Listener playingListener;

    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean playing;
    private volatile boolean audioAvailable = true;

    private final Object pauseLock = new Object();
    private Thread thread;
    private AudioTrack audioTrack;

    public DecodedFilePlaybackSource(Provider provider) {
        this.provider = provider;
    }

    @Override
    public void setListener(AudioSource.Listener listener) {
        this.pcmListener = listener;
    }

    @Override
    public void setPlayingListener(PlaybackTransport.Listener listener) {
        this.playingListener = listener;
    }

    @Override
    public boolean isPlaying() { return playing; }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        paused = false;
        playing = true;
        thread = new Thread(this::runDecodeLoop, "level-decode");
        thread.start();
        notifyPlaying(true);
    }

    @Override
    public void play() {
        if (!running) {
            start();
        } else if (paused) {
            resumeInternal();
        }
    }

    @Override
    public void pause() {
        if (!running || paused) {
            return;
        }
        paused = true;
        playing = false;
        if (audioTrack != null) {
            audioTrack.pause();
        }
        notifyPlaying(false);
    }

    private void resumeInternal() {
        paused = false;
        playing = true;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
        notifyPlaying(true);
    }

    @Override
    public void stop() {
        running = false;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        playing = false;
    }

    @Override
    public void release() {
        stop();
        if (audioTrack != null) {
            try {
                audioTrack.release();
            } catch (Exception ignored) {
            }
            audioTrack = null;
        }
    }

    private void notifyPlaying(boolean value) {
        PlaybackTransport.Listener l = playingListener;
        if (l != null) {
            l.onPlayingChanged(value);
        }
    }

    private void runDecodeLoop() {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        AssetFileDescriptor afd = null;
        try {
            afd = provider.openFd();
            extractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());

            int trackIndex = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    trackIndex = i;
                    format = f;
                    break;
                }
            }
            if (trackIndex < 0) {
                Log.e(TAG, "no audio track in source");
                return;
            }
            extractor.selectTrack(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if ("audio/raw".equals(mime)) {
                decodeRaw(extractor, format);
            } else {
                codec = MediaCodec.createDecoderByType(mime);
                codec.configure(format, null, null, 0);
                codec.start();
                decodeCompressed(extractor, codec);
            }
        } catch (Exception e) {
            Log.e(TAG, "decode loop error", e);
        } finally {
            safeStop(codec);
            safeRelease(codec);
            extractor.release();
            if (afd != null) {
                try { afd.close(); } catch (IOException ignored) { }
            }
            if (audioTrack != null) {
                try { audioTrack.stop(); } catch (Exception ignored) { }
            }
        }
    }

    private void decodeCompressed(MediaExtractor extractor, MediaCodec codec) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int channels = 2;
        int sampleRate = 48000;

        while (running) {
            if (paused) {
                synchronized (pauseLock) {
                    while (paused && running) {
                        try { pauseLock.wait(200); } catch (InterruptedException ignored) { }
                    }
                }
                if (!running) {
                    break;
                }
                if (audioTrack != null) {
                    audioTrack.play();
                }
            }

            int inIndex = codec.dequeueInputBuffer(TIMEOUT_US);
            if (inIndex >= 0) {
                ByteBuffer inBuf = codec.getInputBuffer(inIndex);
                int size = inBuf != null ? extractor.readSampleData(inBuf, 0) : -1;
                if (size < 0) {
                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    codec.queueInputBuffer(inIndex, 0, 0, 0L, 0);
                } else {
                    codec.queueInputBuffer(inIndex, 0, size, extractor.getSampleTime(), 0);
                    extractor.advance();
                }
            }

            int outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US);
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat of = codec.getOutputFormat();
                sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                audioTrack = buildAudioTrack(sampleRate, channels);
                if (audioTrack != null) {
                    audioTrack.play();
                }
            } else if (outIndex >= 0) {
                ByteBuffer outBuf = codec.getOutputBuffer(outIndex);
                if (outBuf != null && info.size > 0) {
                    handlePcm(outBuf, info, channels, sampleRate);
                }
                codec.releaseOutputBuffer(outIndex, false);
            }
        }
    }

    private void decodeRaw(MediaExtractor extractor, MediaFormat format) {
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int pcmEncoding = format.containsKey(MediaFormat.KEY_PCM_ENCODING)
                ? format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                : AudioFormat.ENCODING_PCM_16BIT;
        if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT) {
            Log.e(TAG, "unsupported PCM encoding: " + pcmEncoding);
            return;
        }

        audioTrack = buildAudioTrack(sampleRate, channels);
        if (audioTrack != null) {
            audioTrack.play();
        }

        ByteBuffer buffer = ByteBuffer.allocateDirect(32 * 1024);
        while (running) {
            if (paused) {
                synchronized (pauseLock) {
                    while (paused && running) {
                        try { pauseLock.wait(200); } catch (InterruptedException ignored) { }
                    }
                }
                if (!running) {
                    break;
                }
                if (audioTrack != null) {
                    audioTrack.play();
                }
            }

            buffer.clear();
            int size = extractor.readSampleData(buffer, 0);
            if (size < 0) {
                extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                continue;
            }

            handlePcm(buffer, 0, size, channels, sampleRate);
            extractor.advance();
        }
    }

    private void handlePcm(ByteBuffer outBuf, MediaCodec.BufferInfo info, int channels, int sampleRate) {
        handlePcm(outBuf, info.offset, info.size, channels, sampleRate);
    }

    private void handlePcm(ByteBuffer outBuf, int offset, int byteCount, int channels, int sampleRate) {
        int sampleCount = byteCount / 2;
        if (sampleCount == 0) {
            return;
        }
        short[] shorts = new short[sampleCount];
        outBuf.position(offset);
        outBuf.limit(offset + byteCount);
        outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);

        int frames = sampleCount / channels;
        if (frames > 0) {
            AudioSource.Listener l = pcmListener;
            if (l != null) {
                float[] mono = new float[frames];
                float inv = 1.0f / (channels * 32768.0f);
                int idx = 0;
                for (int f = 0; f < frames; f++) {
                    int sum = 0;
                    for (int c = 0; c < channels; c++) {
                        sum += shorts[idx++];
                    }
                    mono[f] = sum * inv;
                }
                l.onPcm(mono, frames, sampleRate);
            }
        }

        if (audioAvailable && audioTrack != null) {
            audioTrack.write(shorts, 0, sampleCount);
        } else if (frames > 0) {
            try {
                Thread.sleep(frames * 1000L / sampleRate);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private AudioTrack buildAudioTrack(int sampleRate, int channels) {
        int channelMask = channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        int minBuf = AudioTrack.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT);
        int bytesPerSec = sampleRate * channels * 2;
        int bufSize = Math.max(minBuf, bytesPerSec * 150 / 1000);
        try {
            return new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelMask)
                            .build())
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } catch (Exception e) {
            Log.w(TAG, "no audio output, analysis-only", e);
            audioAvailable = false;
            return null;
        }
    }

    private static void safeStop(MediaCodec codec) {
        if (codec != null) {
            try { codec.stop(); } catch (Exception ignored) { }
        }
    }

    private static void safeRelease(MediaCodec codec) {
        if (codec != null) {
            try { codec.release(); } catch (Exception ignored) { }
        }
    }
}
