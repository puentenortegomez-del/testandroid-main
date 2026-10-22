package com.puentenorte.androidscompat;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Calls the configured TTS service and normalizes its WAV response for voice chat. */
final class TtsSynthesizer {
    private static final int MAX_RESPONSE_BYTES = 10 * 1024 * 1024;
    private static final int SAMPLE_RATE = 48_000;
    private static final int MAX_SECONDS = 30;

    private TtsSynthesizer() {
    }

    static short[] synthesize(String endpoint, String text, int timeoutMillis) throws Exception {
        URI uri = URI.create(endpoint);
        String scheme = uri.getScheme();
        if (uri.getHost() == null || !("https".equalsIgnoreCase(scheme)
                || "http".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("El endpoint TTS debe ser una URL HTTP(S) válida.");
        }

        byte[] requestBody = text.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setInstanceFollowRedirects(false);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        connection.setRequestProperty("Accept", "audio/wav, audio/x-wav");
        connection.setFixedLengthStreamingMode(requestBody.length);

        try {
            try (var request = connection.getOutputStream()) {
                request.write(requestBody);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("El servicio TTS respondió HTTP " + status + ".");
            }
            byte[] wav = readBounded(connection.getInputStream(), MAX_RESPONSE_BYTES);
            return decodeWav(wav);
        } finally {
            connection.disconnect();
        }
    }

    private static short[] decodeWav(byte[] wav) throws Exception {
        try (AudioInputStream source = AudioSystem.getAudioInputStream(
                new ByteArrayInputStream(wav))) {
            AudioFormat sourceFormat = source.getFormat();
            float sourceRate = sourceFormat.getSampleRate();
            if (!Float.isFinite(sourceRate) || sourceRate <= 0 || sourceRate > 192_000) {
                throw new IllegalArgumentException("El WAV TTS declara una frecuencia de muestreo no válida.");
            }
            AudioFormat monoFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    sourceRate, 16, 1, 2, sourceRate, false);
            if (!AudioSystem.isConversionSupported(monoFormat, sourceFormat)) {
                throw new IllegalArgumentException(
                        "WAV incompatible: el servicio debe devolver audio PCM convertible a mono/16 bits.");
            }
            try (AudioInputStream normalized = AudioSystem.getAudioInputStream(monoFormat, source);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                int maxBytes = (int) (sourceRate * MAX_SECONDS * 2);
                while ((count = normalized.read(buffer)) != -1) {
                    if (output.size() + count > maxBytes) {
                        throw new IllegalArgumentException("El audio TTS supera los 30 segundos.");
                    }
                    output.write(buffer, 0, count);
                }
                byte[] pcm = output.toByteArray();
                int inputSampleCount = pcm.length / 2;
                if (inputSampleCount == 0) {
                    throw new IllegalArgumentException("El servicio TTS devolvió audio vacío.");
                }
                int outputSampleCount = (int) Math.min(SAMPLE_RATE * MAX_SECONDS,
                        Math.max(1L, Math.round((double) inputSampleCount
                                * SAMPLE_RATE / sourceRate)));
                short[] samples = new short[outputSampleCount];
                for (int i = 0; i < outputSampleCount; i++) {
                    double inputPosition = i * sourceRate / SAMPLE_RATE;
                    int left = Math.min((int) inputPosition, inputSampleCount - 1);
                    int right = Math.min(left + 1, inputSampleCount - 1);
                    double fraction = inputPosition - left;
                    short a = sampleAt(pcm, left);
                    short b = sampleAt(pcm, right);
                    samples[i] = (short) Math.round(a + (b - a) * fraction);
                }
                return samples;
            }
        }
    }

    private static short sampleAt(byte[] pcm, int sampleIndex) {
        int offset = sampleIndex * 2;
        return (short) ((pcm[offset] & 0xff) | (pcm[offset + 1] << 8));
    }

    private static byte[] readBounded(InputStream input, int limit) throws Exception {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > limit) {
                    throw new IllegalArgumentException("La respuesta TTS supera el límite de 10 MiB.");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
}
