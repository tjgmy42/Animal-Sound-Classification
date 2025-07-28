package com.example.bandungzoochatbot;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.MediaExtractor;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.bandungzoochatbot.assets.LoadingDialog;
import com.masoudss.lib.WaveformSeekBar;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class AnimalSoundClassifier extends AppCompatActivity {
    private static final int PICK_AUDIO_REQUEST = 1;
    private static final int PERMISSION_REQUEST_CODE = 2;
    private static final String API_URL = "https://srv877031.hstgr.cloud/classify";
    private static final String TAG = "AnimalSoundClassifier";
    private static final int BUFFER_SIZE_FACTOR = 4; // Meningkatkan buffer size factor
    private static final int SAMPLING_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BYTES_PER_ELEMENT = 2; // Since we're using 16-bit audio
    private static final int MIN_RECORDING_DURATION_MS = 2000; // Minimum 2 seconds
    private static final int MAX_RECORDING_DURATION_MS = 30000; // Maximum 30 seconds

    private WaveformSeekBar waveformSeekBar;
    private ImageButton galleryButton, recordButton, doneButton, playButton;
    private TextView timerTextView, statusTextView, resultTextView;
    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private Uri recordedAudioUri;
    private String audioFilePath;
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private Handler timerHandler;
    private Handler mainHandler;
    private long startTime = 0L;  // Keep this for timer
    private long recordingStartTime;  // This is for recording duration
    private OkHttpClient httpClient;
    private Call currentCall;
    final LoadingDialog loadingDialog = new LoadingDialog(AnimalSoundClassifier.this);

    private volatile boolean shouldStopWaveformThread = false;
    private Thread waveformThread;
    private Thread playbackThread;
    private boolean isActivityDestroyed = false;
    private boolean isRecorderPrepared = false;
    private AudioRecord audioRecord;
    private Thread recordingThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_animal_sound_classifier);

        initializeViews();
        setupClickListeners();
        checkPermissions();
        initializeHttpClient();

        timerHandler = new Handler(Looper.getMainLooper());
        mainHandler = new Handler(Looper.getMainLooper());
    }

    private void initializeHttpClient() {
        if (BuildConfig.DEBUG) {
            httpClient = createUnsafeHttpClient();
        } else {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();
        }
    }

    private OkHttpClient createUnsafeHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType)
                                throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType)
                                throws CertificateException {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final javax.net.ssl.SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
            builder.connectTimeout(30, TimeUnit.SECONDS);
            builder.writeTimeout(30, TimeUnit.SECONDS);
            builder.readTimeout(30, TimeUnit.SECONDS);

            return builder.build();
        } catch (Exception e) {
            Log.e(TAG, "Error creating unsafe HTTP client", e);
            return new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();
        }
    }

    private void initializeViews() {
        try {
            waveformSeekBar = findViewById(R.id.waveformSeekBar);
            galleryButton = findViewById(R.id.galleryButton);
            recordButton = findViewById(R.id.recordButton);
            doneButton = findViewById(R.id.doneButton);
            playButton = findViewById(R.id.playButton);
            timerTextView = findViewById(R.id.timerTextView);
            statusTextView = findViewById(R.id.statusTextView);
            resultTextView = findViewById(R.id.resultTextView);

            if (waveformSeekBar != null) {
                waveformSeekBar.setSampleFrom(new int[]{0});
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
            finish();
        }
    }

    private void setupClickListeners() {
        if (recordButton != null) {
            recordButton.setOnClickListener(v -> {
                if (isRecording) {
                    stopRecording();
                } else {
                    startRecording();
                }
            });
        }

        if (galleryButton != null) {
            galleryButton.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("audio/*");
                startActivityForResult(intent, PICK_AUDIO_REQUEST);
            });

        }

        if (doneButton != null) {
            doneButton.setOnClickListener(v -> {
                if (recordedAudioUri != null) {
                    classifyAudioWithAPI(recordedAudioUri);
                } else {
                    showToast("Tidak ada audio untuk diklasifikasikan");
                }
            });
        }

        if (playButton != null) {
            playButton.setOnClickListener(v -> {
                if (isPlaying) {
                    stopPlaying();
                } else {
                    startPlaying();
                }
            });
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_CODE);
        }
    }

    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_CODE);
            return;
        }

        try {
            // Create temporary file for recording
            File outputDir = getExternalCacheDir();
            File outputFile = new File(outputDir, "temp_recording.m4a");
            if (outputFile.exists()) {
                outputFile.delete();
            }
            audioFilePath = outputFile.getAbsolutePath();

            // Initialize and configure MediaRecorder
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioChannels(1);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setOutputFile(audioFilePath);
            mediaRecorder.setMaxDuration(MAX_RECORDING_DURATION_MS);

            mediaRecorder.setOnInfoListener((mr, what, extra) -> {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    mainHandler.post(() -> {
                        showToast("Durasi maksimum tercapai");
                        stopRecording();
                    });
                }
            });

            mediaRecorder.setOnErrorListener((mr, what, extra) -> {
                mainHandler.post(() -> {
                    Log.e(TAG, "MediaRecorder error: " + what + ", " + extra);
                    showToast("Error dalam perekaman");
                    stopRecording();
                });
            });

            try {
                mediaRecorder.prepare();
                mediaRecorder.start();
                isRecording = true;
                isRecorderPrepared = true;
                recordingStartTime = SystemClock.elapsedRealtime();

                showToast("Rekam minimal " + (MIN_RECORDING_DURATION_MS / 1000) + " detik");

                if (recordButton != null) {
                    recordButton.setImageResource(R.drawable.ic_stop);
                }
                startTimer();
                startWaveformUpdates();

                Log.d(TAG, "Started recording to: " + audioFilePath);

            } catch (IOException e) {
                Log.e(TAG, "Error preparing MediaRecorder", e);
                cleanupMediaRecorder();
                showToast("Error mempersiapkan perekam: " + e.getMessage());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in startRecording", e);
            cleanupMediaRecorder();
            showToast("Error: " + e.getMessage());
        }
    }

    private void stopRecording() {
        if (!isRecording || mediaRecorder == null) {
            return;
        }

        long recordingDuration = SystemClock.elapsedRealtime() - recordingStartTime;
        Log.d(TAG, "Stopping recording. Duration: " + recordingDuration + "ms");

        if (recordingDuration < MIN_RECORDING_DURATION_MS) {
            showToast("Rekaman terlalu pendek, minimal " + (MIN_RECORDING_DURATION_MS / 1000) + " detik");
            cleanupMediaRecorder();
            if (audioFilePath != null) {
                new File(audioFilePath).delete();
            }
            recordedAudioUri = null;
        } else {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;

                // Check if file exists and is valid
                File recordedFile = new File(audioFilePath);
                if (recordedFile.exists() && recordedFile.length() > 0) {
                    recordedAudioUri = Uri.fromFile(recordedFile);

                    if (statusTextView != null) {
                        statusTextView.setText("Rekaman selesai (" +
                                String.format(Locale.getDefault(), "%.1f", recordingDuration/1000.0) +
                                " detik)");
                    }

                    Log.d(TAG, "Recording saved successfully. File size: " + recordedFile.length() + " bytes");
                } else {
                    throw new IOException("File rekaman tidak valid");
                }

            } catch (Exception e) {
                Log.e(TAG, "Error stopping recording", e);
                showToast("Error menyimpan rekaman: " + e.getMessage());
                recordedAudioUri = null;
            }
        }

        isRecording = false;
        isRecorderPrepared = false;

        if (recordButton != null) {
            recordButton.setImageResource(R.drawable.ic_mic);
        }
        stopTimer();
    }

    private void cleanupMediaRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaRecorder", e);
            }
            try {
                mediaRecorder.reset();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaRecorder", e);
            }
            mediaRecorder = null;
        }
    }

    private void startTimer() {
        startTime = SystemClock.uptimeMillis();
        timerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if ((isRecording || isPlaying) && !isActivityDestroyed) {
                    long timeInMillis = SystemClock.uptimeMillis() - startTime;
                    int seconds = (int) (timeInMillis / 1000);
                    int minutes = seconds / 60;
                    seconds = seconds % 60;
                    int milliseconds = (int) (timeInMillis % 1000) / 100;

                    if (timerTextView != null) {
                        timerTextView.setText(String.format(Locale.getDefault(),
                                "%02d:%02d.%d", minutes, seconds, milliseconds));
                    }

                    timerHandler.postDelayed(this, 100);
                }
            }
        }, 100);
    }

    private void stopTimer() {
        if (timerHandler != null) {
            timerHandler.removeCallbacksAndMessages(null);
        }
        if (timerTextView != null) {
            timerTextView.setText("00:00.0");
        }
    }

    private void startPlaying() {
        if (mediaPlayer != null) {
            stopPlaying();
        }

        if (recordedAudioUri != null) {
            try {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(this, recordedAudioUri);

                // Set audio attributes for better playback
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mediaPlayer.setAudioAttributes(
                            new android.media.AudioAttributes.Builder()
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                    .build()
                    );
                }

                // Prepare synchronously since file is local
                mediaPlayer.prepare();

                // Get and log duration before starting playback
                int duration = mediaPlayer.getDuration();
                Log.d(TAG, "Prepared MediaPlayer duration: " + duration + " ms");

                if (duration < 500) {
                    showToast("File audio terlalu pendek atau rusak");
                    stopPlaying();
                    return;
                }

                mediaPlayer.setOnPreparedListener(mp -> {
                    if (!isActivityDestroyed && mp != null) {
                        // Set volume to max
                        mp.setVolume(1.0f, 1.0f);
                        mp.start();
                        isPlaying = true;
                        startTime = SystemClock.uptimeMillis();
                        if (playButton != null) {
                            playButton.setImageResource(R.drawable.ic_pause);
                        }
                        startTimer();
                        updateWaveformDuringPlayback();
                    }
                });

                mediaPlayer.setOnCompletionListener(mp -> {
                    Log.d(TAG, "Playback completed, total duration: " + mediaPlayer.getDuration() + " ms");
                    stopPlaying();
                });

                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "MediaPlayer Error: " + what + ", " + extra);
                    stopPlaying();
                    showToast("Error memutar audio");
                    return true;
                });

            } catch (Exception e) {
                Log.e(TAG, "Error setting up MediaPlayer", e);
                showToast("Error memutar audio: " + e.getMessage());
                stopPlaying();
            }
        } else {
            showToast("Tidak ada rekaman untuk diputar");
        }
    }

    private void updateWaveformDuringPlayback() {
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }

        playbackThread = new Thread(() -> {
            final int[] amplitudes = new int[100];
            final int updateInterval = 50; // Update setiap 50ms

            while (isPlaying && !Thread.currentThread().isInterrupted() && !isActivityDestroyed) {
                try {
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        final int currentPosition = mediaPlayer.getCurrentPosition();
                        final int duration = mediaPlayer.getDuration();

                        // Generate dynamic waveform
                        for (int i = 0; i < amplitudes.length; i++) {
                            float progress = (float) i / amplitudes.length;
                            float positionProgress = (float) currentPosition / duration;

                            if (progress <= positionProgress) {
                                // Bagian yang sudah diputar - amplitudo dinamis
                                amplitudes[i] = 30 + (int)(Math.random() * 50);
                            } else {
                                // Bagian yang belum diputar
                                amplitudes[i] = 20;
                            }
                        }

                        final int[] currentAmplitudes = amplitudes.clone();
                        mainHandler.post(() -> {
                            if (waveformSeekBar != null && !isActivityDestroyed) {
                                waveformSeekBar.setSampleFrom(currentAmplitudes);
                            }
                        });
                    }
                    Thread.sleep(updateInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error updating waveform", e);
                    break;
                }
            }
        });
        playbackThread.start();
    }

    private void stopPlaying() {
        isPlaying = false;

        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaPlayer", e);
            }
            mediaPlayer = null;
        }

        if (playButton != null) {
            playButton.setImageResource(R.drawable.ic_play);
        }
        stopTimer();

        // Reset waveform with default amplitude
        if (waveformSeekBar != null) {
            int[] defaultAmplitudes = new int[100];
            for (int i = 0; i < defaultAmplitudes.length; i++) {
                defaultAmplitudes[i] = 50;
            }
            waveformSeekBar.setSampleFrom(defaultAmplitudes);
        }
    }

    private void startWaveformUpdates() {
        shouldStopWaveformThread = false;
        final int[] amplitudes = new int[100];
        final AtomicInteger currentIndex = new AtomicInteger(0);

        waveformThread = new Thread(() -> {
            while (isRecording && !shouldStopWaveformThread && !Thread.currentThread().isInterrupted() && !isActivityDestroyed) {
                try {
                    if (mediaRecorder != null) {
                        try {
                            int amplitude = mediaRecorder.getMaxAmplitude();
                            // Normalize amplitude (0-100)
                            int normalizedAmplitude = amplitude > 0 ?
                                    Math.min((int)(20 * Math.log10(amplitude)), 100) : 0;

                            amplitudes[currentIndex.get()] = Math.max(normalizedAmplitude, 10);
                            currentIndex.set((currentIndex.get() + 1) % amplitudes.length);

                            final int[] currentAmplitudes = amplitudes.clone();
                            mainHandler.post(() -> {
                                if (waveformSeekBar != null && !isActivityDestroyed) {
                                    waveformSeekBar.setSampleFrom(currentAmplitudes);
                                }
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Error getting amplitude", e);
                        }
                    }
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error in waveform thread", e);
                    break;
                }
            }
        });
        waveformThread.start();
    }

    private int[] generateWaveformSamples(int currentPosition, int duration) {
        int sampleCount = 100;
        int[] samples = new int[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            int samplePosition = (int) ((float) i / sampleCount * duration);
            if (samplePosition <= currentPosition && mediaPlayer != null && mediaPlayer.isPlaying()) {
                samples[i] = (int) (Math.random() * 100);
            } else {
                samples[i] = 0;
            }
        }
        return samples;
    }

    private String getPathFromUri(Uri uri) {
        String filePath = null;
        try {
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                String[] projection = {MediaStore.Audio.Media.DATA};
                try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                        filePath = cursor.getString(columnIndex);
                    }
                }
            }
            if (filePath == null) {
                filePath = uri.getPath();
                if (filePath != null && filePath.startsWith("/external/")) {
                    filePath = getExternalFilesDir(null) + "/" +
                            filePath.substring(filePath.lastIndexOf("/") + 1);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting file path: " + e.getMessage());
        }
        return filePath;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_AUDIO_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri selectedAudioUri = data.getData();
            if (selectedAudioUri != null) {
                recordedAudioUri = selectedAudioUri;
                audioFilePath = getPathFromUri(selectedAudioUri);

                if (statusTextView != null) {
                    statusTextView.setText("Audio dipilih dari galeri. Tekan 'Done' untuk mengklasifikasi.");
                }
                if (playButton != null) {
                    playButton.setEnabled(true);
                }
                if (resultTextView != null) {
                    resultTextView.setText("");
                }

                loadAudioFromUri(selectedAudioUri);
            } else {
                showToast("Tidak dapat membaca file audio");
            }
        }
    }

    private void loadAudioFromUri(Uri uri) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.prepare();

            int duration = mediaPlayer.getDuration();
            startCountdownTimer(duration);
            generateSimpleWaveform(duration);

            if (playButton != null) {
                playButton.setEnabled(true);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading audio", e);
            showToast("Error loading audio: " + e.getMessage());
        }
    }

    private void startCountdownTimer(int durationInMillis) {
        if (timerHandler != null) {
            timerHandler.removeCallbacksAndMessages(null);
        }
        startTime = SystemClock.uptimeMillis() + durationInMillis;

        timerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (timerTextView != null && !isActivityDestroyed) {
                    timerTextView.setText("00:00.0");
                }
            }
        }, 100);
    }

    private void generateSimpleWaveform(int duration) {
        int sampleCount = 100;
        int[] samples = new int[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            samples[i] = (int) (Math.random() * 100);
        }

        if (waveformSeekBar != null) {
            waveformSeekBar.setSampleFrom(samples);
        }
    }

    private void classifyAudioWithAPI(Uri audioUri) {
        if (statusTextView != null) {
            statusTextView.setText("Mengklasifikasi audio...");
        }

        File audioFile = null;
        try {
            if ("content".equalsIgnoreCase(audioUri.getScheme())) {
                audioFile = copyUriToFile(audioUri);
            } else {
                audioFile = new File(audioUri.getPath());
            }

            if (audioFile == null || !audioFile.exists()) {
                showToast("File audio tidak ditemukan");
                if (statusTextView != null) {
                    statusTextView.setText("Error: File tidak ditemukan");
                }
                return;
            }

            // Convert M4A to WAV if needed
            if (audioFile.getName().endsWith(".m4a")) {
                File wavFile = convertM4AtoWAV(audioFile);
                if (wavFile != null) {
                    audioFile = wavFile;
                } else {
                    showToast("Error mengkonversi audio");
                    return;
                }
            }

            // Add logging for debugging
            Log.d(TAG, "Audio file path: " + audioFile.getAbsolutePath());
            Log.d(TAG, "Audio file size: " + audioFile.length() + " bytes");
            Log.d(TAG, "Audio file exists: " + audioFile.exists());

            RequestBody fileBody = RequestBody.create(MediaType.parse("audio/wav"), audioFile);
            MultipartBody.Builder builder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", audioFile.getName(), fileBody);

            RequestBody requestBody = builder.build();

            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(requestBody)
                    .build();

            loadingDialog.startLoadingDialog();

            if (currentCall != null && !currentCall.isCanceled()) {
                currentCall.cancel();
            }

            currentCall = httpClient.newCall(request);
            currentCall.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (call.isCanceled() || isActivityDestroyed) {
                        return;
                    }

                    mainHandler.post(() -> {
                        if (!isActivityDestroyed) {
                            showToast("Error: " + e.getMessage());
                            if (statusTextView != null) {
                                statusTextView.setText("Error: Koneksi gagal");
                            }
                        }
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (call.isCanceled() || isActivityDestroyed) {
                        return;
                    }

                    try {
                        if (response.isSuccessful()) {
                            String responseBody = response.body().string();
                            Log.d(TAG, "Response: " + responseBody);

                            try {
                                JSONObject jsonResponse = new JSONObject(responseBody);
                                JSONObject animalData = jsonResponse.getJSONObject("animal_data");

                                String animalName = animalData.getString("animal");
                                String description = animalData.getString("description");
                                String fact = animalData.getString("fact");

                                mainHandler.post(() -> {
                                    if (!isActivityDestroyed) {
                                        Intent intent = new Intent(AnimalSoundClassifier.this,
                                                SoundClassificationResults.class);
                                        intent.putExtra("animal_name", animalName);
                                        intent.putExtra("animal_description", description);
                                        intent.putExtra("animal_fact", fact);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                        stopPlaying();
                                        startActivity(intent);
                                    }
                                });

                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing JSON", e);
                                mainHandler.post(() -> {
                                    if (!isActivityDestroyed) {
                                        showToast("Error: Server response tidak valid");
                                        if (statusTextView != null) {
                                            statusTextView.setText("Error: Response tidak valid");
                                        }
                                    }
                                });
                            }
                        } else {
                            Log.e(TAG, "Request failed with code: " + response.code());
                            String errorBody = response.body() != null ? response.body().string() : "No error details";
                            Log.e(TAG, "Error response: " + errorBody);

                            mainHandler.post(() -> {
                                if (!isActivityDestroyed) {
                                    String errorMessage;
                                    switch (response.code()) {
                                        case 404:
                                            errorMessage = "Error: Endpoint tidak ditemukan (404)";
                                            break;
                                        case 500:
                                            errorMessage = "Error: Format audio tidak sesuai atau rusak. Pastikan rekaman jelas dan tidak terlalu pendek";
                                            break;
                                        default:
                                            errorMessage = "Error: Server error " + response.code() + "\n" + errorBody;
                                    }

                                    showToast(errorMessage);
                                    if (statusTextView != null) {
                                        statusTextView.setText(errorMessage);
                                    }
                                }
                            });
                        }
                    } finally {
                        response.close();
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error in classifyAudioWithAPI", e);
            if (!isActivityDestroyed) {
                showToast("Error: " + e.getMessage());
                if (statusTextView != null) {
                    statusTextView.setText("Error: " + e.getMessage());
                }
            }
        }
    }

    private File convertM4AtoWAV(File m4aFile) {
        MediaExtractor extractor = null;
        MediaCodec decoder = null;
        FileOutputStream out = null;
        DataOutputStream dout = null;

        try {
            // Create output WAV file
            File wavFile = new File(getExternalCacheDir(), "converted_audio.wav");
            if (wavFile.exists()) {
                wavFile.delete();
            }

            // Setup extractor
            extractor = new MediaExtractor();
            extractor.setDataSource(m4aFile.getPath());

            // Find audio track
            MediaFormat format = null;
            int trackIndex = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    trackIndex = i;
                    break;
                }
            }

            if (trackIndex < 0 || format == null) {
                Log.e(TAG, "No audio track found in M4A file");
                return null;
            }

            // Select the audio track
            extractor.selectTrack(trackIndex);

            // Get audio format details
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

            // Create decoder
            String mime = format.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();

            // Prepare output file
            out = new FileOutputStream(wavFile);
            dout = new DataOutputStream(out);

            // Write WAV header (we'll update sizes later)
            writeWavHeader(dout, sampleRate, (short)channelCount);

            // Start decoding process
            boolean isEOS = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long presentationTimeUs = 0;
            long totalBytesWritten = 0;

            while (!isEOS) {
                // Handle input buffer
                int inputBufferIndex = decoder.dequeueInputBuffer(10000);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                    if (inputBuffer != null) {
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEOS = true;
                        } else {
                            presentationTimeUs = extractor.getSampleTime();
                            decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0);
                            extractor.advance();
                        }
                    }
                }

                // Handle output buffer
                int outputBufferIndex = decoder.dequeueOutputBuffer(info, 10000);
                if (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferIndex);
                    if (outputBuffer != null) {
                        byte[] chunk = new byte[info.size];
                        outputBuffer.get(chunk);
                        dout.write(chunk);
                        totalBytesWritten += chunk.length;
                        decoder.releaseOutputBuffer(outputBufferIndex, false);
                    }
                }
            }

            // Cleanup
            decoder.stop();
            dout.flush();
            out.flush();

            // Update WAV header with final sizes
            try (RandomAccessFile raf = new RandomAccessFile(wavFile, "rw")) {
                // Update RIFF chunk size
                raf.seek(4);
                raf.writeInt(Integer.reverseBytes((int)(totalBytesWritten + 36)));

                // Update data chunk size
                raf.seek(40);
                raf.writeInt(Integer.reverseBytes((int)totalBytesWritten));
            }

            Log.d(TAG, "Successfully converted M4A to WAV: " + wavFile.length() + " bytes");
            return wavFile;

        } catch (Exception e) {
            Log.e(TAG, "Error converting M4A to WAV", e);
            e.printStackTrace();
            return null;
        } finally {
            // Cleanup resources
            try {
                if (dout != null) dout.close();
                if (out != null) out.close();
                if (decoder != null) {
                    decoder.stop();
                    decoder.release();
                }
                if (extractor != null) extractor.release();
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up resources", e);
            }
        }
    }

    private void writeWavHeader(DataOutputStream out, int sampleRate, short channels) throws IOException {
        // RIFF header
        out.writeBytes("RIFF"); // ChunkID
        out.writeInt(0); // ChunkSize (will be updated later)
        out.writeBytes("WAVE"); // Format

        // fmt subchunk
        out.writeBytes("fmt "); // Subchunk1ID
        out.writeInt(Integer.reverseBytes(16)); // Subchunk1Size
        out.writeShort(Short.reverseBytes((short)1)); // AudioFormat (PCM = 1)
        out.writeShort(Short.reverseBytes(channels)); // NumChannels
        out.writeInt(Integer.reverseBytes(sampleRate)); // SampleRate
        out.writeInt(Integer.reverseBytes(sampleRate * channels * 2)); // ByteRate
        out.writeShort(Short.reverseBytes((short)(channels * 2))); // BlockAlign
        out.writeShort(Short.reverseBytes((short)16)); // BitsPerSample

        // data subchunk
        out.writeBytes("data"); // Subchunk2ID
        out.writeInt(0); // Subchunk2Size (will be updated later)
    }

    private File copyUriToFile(Uri uri) {
        try {
            File tempFile = new File(getCacheDir(), "temp_audio_" + System.currentTimeMillis() + ".tmp");

            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                FileOutputStream outputStream = new FileOutputStream(tempFile);

                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }

                outputStream.close();
                inputStream.close();

                return tempFile;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error copying URI to file", e);
        }
        return null;
    }

    private void showToast(String message) {
        if (!isActivityDestroyed) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isRecording || isRecorderPrepared) {
            stopRecording();
        }
        if (isPlaying) {
            stopPlaying();
        }
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
        }
        if (timerHandler != null) {
            timerHandler.removeCallbacksAndMessages(null);
        }
        if (loadingDialog.isShowing()) {
            loadingDialog.dismissDialog();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        shouldStopWaveformThread = true;

        if (waveformThread != null && waveformThread.isAlive()) {
            waveformThread.interrupt();
            waveformThread = null;
        }

        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
            playbackThread = null;
        }

        cleanupMediaResources();
    }

    @Override
    protected void onDestroy() {
        isActivityDestroyed = true;

        cleanupMediaResources();

        if (timerHandler != null) {
            timerHandler.removeCallbacksAndMessages(null);
            timerHandler = null;
        }

        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
            mainHandler = null;
        }

        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
        }

        shouldStopWaveformThread = true;
        if (waveformThread != null && waveformThread.isAlive()) {
            waveformThread.interrupt();
        }
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
        }
        if (loadingDialog.isShowing()) {
            loadingDialog.dismissDialog();
        }

        super.onDestroy();
    }

    private void cleanupMediaResources() {
        isPlaying = false;
        isRecording = false;

        if (audioRecord != null) {
            try {
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioRecord", e);
            }
            audioRecord = null;
        }

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaPlayer", e);
            }
            mediaPlayer = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast("Permission granted");
            } else {
                showToast("Permission denied");
                finish();
            }
        }
    }
}