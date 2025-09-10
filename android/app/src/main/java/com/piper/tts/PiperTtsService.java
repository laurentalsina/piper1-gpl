package com.piper.tts;

import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeechService;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class PiperTtsService extends TextToSpeechService {

    private static final String TAG = "PiperTtsService";

    private long synthesizerPtr = 0;

    // These will be configured later
    private String modelPath;
    private String configPath;
    private String espeakDataPath;
    private int sampleRate = 22050; // Default, will be read from config

    static {
        System.loadLibrary("piper_jni");
    }

    private native long nativeCreateSynthesizer(String modelPath, String configPath, String espeakDataPath);
    private native void nativeDeleteSynthesizer(long synthesizerPtr);
    private native float[] nativeSynthesize(long synthesizerPtr, String text);

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        try {
            File appDir = getFilesDir();
            extractAssets(appDir);
            modelPath = new File(appDir, "piper/voice.onnx").getAbsolutePath();
            configPath = new File(appDir, "piper/voice.onnx.json").getAbsolutePath();
            espeakDataPath = new File(appDir, "piper/espeak-ng-data").getAbsolutePath();
            synthesizerPtr = nativeCreateSynthesizer(modelPath, configPath, espeakDataPath);
            if (synthesizerPtr == 0) {
                Log.e(TAG, "Failed to create synthesizer");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract assets", e);
        }
    }

    private void extractAssets(File targetDir) throws IOException {
        String[] assetDirs = {"piper"};
        for (String dir : assetDirs) {
            File target = new File(targetDir, dir);
            if (!target.exists()) {
                target.mkdirs();
            }
            copyAssets(dir, target);
        }
    }

    private void copyAssets(String assetPath, File targetDir) throws IOException {
        String[] assets = getAssets().list(assetPath);
        for (String asset : assets) {
            String newAssetPath = assetPath + "/" + asset;
            File targetFile = new File(targetDir, asset);
            try {
                String[] subAssets = getAssets().list(newAssetPath);
                if (subAssets.length == 0) {
                    copyFile(newAssetPath, targetFile);
                } else {
                    targetFile.mkdirs();
                    copyAssets(newAssetPath, targetFile);
                }
            } catch (IOException e) {
                // It's a file, not a directory
                copyFile(newAssetPath, targetFile);
            }
        }
    }

    private void copyFile(String assetPath, File targetFile) throws IOException {
        try (InputStream in = getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (synthesizerPtr != 0) {
            nativeDeleteSynthesizer(synthesizerPtr);
            synthesizerPtr = 0;
        }
    }

    @Override
    protected String[] onGetLanguage() {
        // For now, let's say we support US English
        return new String[]{"eng", "USA", ""};
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        // For now, let's say we support US English
        if (lang.equals("eng") && country.equals("USA")) {
            return TextToSpeechService.LANG_COUNTRY_AVAILABLE;
        }
        return TextToSpeechService.LANG_NOT_SUPPORTED;
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        return onIsLanguageAvailable(lang, country, variant);
    }

    @Override
    protected void onStop() {
        // In a streaming implementation, you would stop synthesis here.
        // For our simple, non-streaming version, there's not much to do.
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        String text = request.getCharSequenceText().toString();
        Log.d(TAG, "Synthesizing text: " + text);

        // This is a placeholder for when the synthesizer is not ready
        if (synthesizerPtr == 0) {
            Log.e(TAG, "Synthesizer not initialized!");
            callback.error();
            return;
        }

        // Start synthesis. The callback needs to know the audio format.
        // We get the sample rate from our voice config.
        callback.start(sampleRate, android.media.AudioFormat.ENCODING_PCM_FLOAT, 1);

        float[] audioData = nativeSynthesize(synthesizerPtr, text);

        if (audioData == null) {
            callback.error();
            return;
        }

        // The audio data needs to be provided in byte chunks.
        // We need to convert our float[] to a byte[].
        final int bufferSize = 4096; // A reasonable buffer size
        byte[] buffer = new byte[bufferSize];
        int offset = 0;
        int floatOffset = 0;

        while (floatOffset < audioData.length) {
            int bytesWritten = 0;
            while (bytesWritten < bufferSize && floatOffset < audioData.length) {
                // Convert float to little-endian bytes
                int bits = Float.floatToIntBits(audioData[floatOffset]);
                buffer[bytesWritten++] = (byte) (bits);
                buffer[bytesWritten++] = (byte) (bits >> 8);
                buffer[bytesWritten++] = (byte) (bits >> 16);
                buffer[bytesWritten++] = (byte) (bits >> 24);
                floatOffset++;
            }
            if (bytesWritten > 0) {
                final int bytesToWrite = bytesWritten;
                 if (callback.getMaxBufferSize() < bytesToWrite) {
                    // This should not happen with a reasonably sized buffer
                    Log.e(TAG, "Buffer size too large for callback");
                    callback.error();
                    return;
                }
                byte[] chunk = new byte[bytesToWrite];
                System.arraycopy(buffer, 0, chunk, 0, bytesToWrite);
                callback.audioAvailable(chunk);
            }
        }

        callback.done();
    }
}
