package com.sebas.specialistdance.audio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.EXTThreadLocalContext;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryUtil;

import com.sebas.specialistdance.SpecialistDanceConfig;

import net.fabricmc.loader.api.FabricLoader;

public class MusicManager {

    private static final String BUNDLED_NAME = "default";
    private static final String BUNDLED_RESOURCE = "/assets/specialist-dance/sounds/default.ogg";

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("ogg", "wav", "aiff", "aif", "au", "mp3", "flac");

    private final SpecialistDanceConfig config;
    private final Map<String, Path> userTracks = new TreeMap<>();

    private boolean available;
    private boolean threadLocalContext;
    private boolean switchedThreadContext;
    private long device;
    private long context;
    private int sourceId = -1;
    private int bufferId = -1;
    private String decodedTrack;

    public MusicManager(SpecialistDanceConfig config) {
        this.config = config;
    }

    public void init() {
        scanUserTracks();

        try {
            try {
                device = ALC10.alcOpenDevice((ByteBuffer) null);
            } catch (IllegalStateException notInitialized) {
                ALC.create();
                device = ALC10.alcOpenDevice((ByteBuffer) null);
            }

            if (device == 0L) {
                log("no audio device found, music disabled");
                return;
            }

            context = ALC10.alcCreateContext(device, (IntBuffer) null);
            if (context == 0L) {
                ALC10.alcCloseDevice(device);
                device = 0L;
                log("could not create audio context, music disabled");
                return;
            }

            long previous = ALC10.alcGetCurrentContext();
            ALC10.alcMakeContextCurrent(context);

            try {
                ALCCapabilities alcCapabilities = ALC.createCapabilities(device);
                AL.createCapabilities(alcCapabilities);
                threadLocalContext = alcCapabilities.ALC_EXT_thread_local_context;

                sourceId = AL10.alGenSources();
                int error = AL10.alGetError();

                if (error != AL10.AL_NO_ERROR) {
                    throw new IllegalStateException("OpenAL error " + error + " creating source");
                }
            } finally {
                if (previous != 0L) {
                    ALC10.alcMakeContextCurrent(previous);
                }
            }

            available = true;
        } catch (Exception e) {
            System.err.println("Specialist Dance: music init failed");
            e.printStackTrace();
            releaseContext();
        }
    }

    private long makeContextCurrent() {
        switchedThreadContext = false;

        if (threadLocalContext) {
            try {
                long previous = EXTThreadLocalContext.alcGetThreadContext();
                EXTThreadLocalContext.alcSetThreadContext(context);
                switchedThreadContext = true;
                return previous;
            } catch (Throwable t) {
                threadLocalContext = false;
                log("thread-local audio context unavailable, sharing the process context");
            }
        }

        long previous = ALC10.alcGetCurrentContext();
        ALC10.alcMakeContextCurrent(context);
        return previous;
    }

    private void restoreContext(long previous) {
        if (switchedThreadContext) {
            EXTThreadLocalContext.alcSetThreadContext(previous);
        } else if (previous != 0L) {
            ALC10.alcMakeContextCurrent(previous);
        }
    }

    private void releaseContext() {
        if (context != 0L) {
            if (threadLocalContext && EXTThreadLocalContext.alcGetThreadContext() == context) {
                EXTThreadLocalContext.alcSetThreadContext(0L);
            }

            ALC10.alcDestroyContext(context);
            context = 0L;
        }

        if (device != 0L) {
            ALC10.alcCloseDevice(device);
            device = 0L;
        }

        sourceId = -1;
    }

    public List<String> getAvailableTracks() {
        List<String> tracks = new ArrayList<>();
        tracks.add(BUNDLED_NAME);
        tracks.addAll(userTracks.keySet());
        return tracks;
    }

    public void scanUserTracks() {
        userTracks.clear();
        Path musicDir = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("specialist-dance")
            .resolve("music");

        try {
            Files.createDirectories(musicDir);
        } catch (IOException e) {
            log("could not create music dir " + musicDir);
            return;
        }

        try (Stream<Path> files = Files.list(musicDir)) {
            files.filter(Files::isRegularFile).sorted().forEach(p -> {
                String fileName = p.getFileName().toString();
                int dot = fileName.lastIndexOf('.');
                if (dot <= 0) {
                    return;
                }

                String ext = fileName.substring(dot +1).toLowerCase(Locale.ROOT);
                if (!SUPPORTED_EXTENSIONS.contains(ext)) {
                    return;
                }

                String name = fileName.substring(0, dot);
                if (userTracks.putIfAbsent(name, p) != null) {
                    log("Duplicate track name ignored: " + fileName);
                } 
            });
        } catch (IOException e) {
            log("Could not scan music dir " + musicDir);
        }
    }

    public void play() {
        if (!available) {
            return;
        }

        if (!ensureTrackLoaded(config.musicTrack)) {
            log("track '" + config.musicTrack + "' not found, trying default");

            if(!ensureTrackLoaded(BUNDLED_NAME)) {
                return;
            }
        }

        long previous = makeContextCurrent();

        try {
            AL10.alSourceStop(sourceId);
            AL10.alSourcei(sourceId, AL10.AL_BUFFER, bufferId);
            AL10.alSourcei(sourceId, AL10.AL_LOOPING, AL10.AL_FALSE);
            AL10.alSourcef(sourceId, AL10.AL_GAIN, Math.clamp(config.musicVolume, 0.0f, 1.0f));
            AL10.alSourcePlay(sourceId);
        } finally {
            restoreContext(previous);
        }

        log("playing track: " + decodedTrack);
    }

    public void applyVolume() {
        if (!available) {
            return;
        }

        long previous = makeContextCurrent();

        try {
            AL10.alSourcef(sourceId, AL10.AL_GAIN, Math.clamp(config.musicVolume, 0.0f, 1.0f));
        } finally {
            restoreContext(previous);
        }
    }

    public void stop() {
        if (!available) {
            return;
        }

        long previous = makeContextCurrent();

        try {
            AL10.alSourceStop(sourceId);
        } finally {
            restoreContext(previous);
        }
    }

    public boolean isPlaying() {
        if (!available) {
            return false;
        }

        long previous = makeContextCurrent();

        try {
            int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
            return state == AL10.AL_PLAYING;
        } finally {
            restoreContext(previous);
        }
    }

    public void shutdown() {
        if (!available) {
            return;
        }

        long previous = makeContextCurrent();

        try {
            AL10.alSourceStop(sourceId);
            AL10.alSourcei(sourceId, AL10.AL_BUFFER, 0);
            AL10.alDeleteBuffers(bufferId);
            AL10.alDeleteSources(sourceId);
        } finally {
            restoreContext(previous);
        }

        available = false;
        bufferId = -1;
        decodedTrack = null;
        releaseContext();
    }

    private boolean ensureTrackLoaded(String trackName) {
        if (trackName == null || trackName.isBlank()) {
            trackName = BUNDLED_NAME;
        }

        if (trackName.equals(decodedTrack) && bufferId >= 0) {
            return true;
        }

        String extension = extensionOf(trackName);
        if (extension.isEmpty()) {
            return false;
        }

        if ("ogg".equals(extension)) {
            return loadOgg(trackName);
        }

        return loadWithJavaSound(trackName, extension);
    }

    private String extensionOf(String trackName) {
        if (BUNDLED_NAME.equals(trackName)) {
            return "ogg";
        }

        Path file = userTracks.get(trackName);
        if (file == null) {
            return "";
        }

        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean loadOgg(String trackName) {
        ByteBuffer ogg = readTrack(trackName);

        if (ogg == null) {
            return false;
        }

        IntBuffer errorBuffer = BufferUtils.createIntBuffer(1);
        long decoder = STBVorbis.stb_vorbis_open_memory(ogg, errorBuffer, null);

        if (decoder == MemoryUtil.NULL) {
            MemoryUtil.memFree(ogg);
            log("failed to open track: " + trackName + " (error " + errorBuffer.get(0) + ")");
            return false;
        }

        STBVorbisInfo info = STBVorbisInfo.malloc();

        try {
            STBVorbis.stb_vorbis_get_info(decoder, info);

            int sourceChannels = info.channels();

            if (sourceChannels != 1 && sourceChannels != 2) {
                log("unsupported channel count (" + sourceChannels + ") for track: " + trackName);
                return false;
            }

            int channels = sourceChannels;
            int sampleRate = info.sample_rate();
            int samples = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);

            if (samples <= 0) {
                log("track is empty: " + trackName);
                return false;
            }

            ShortBuffer pcm = MemoryUtil.memAllocShort(samples * channels);
            int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);

            if (frames <= 0) {
                MemoryUtil.memFree(pcm);
                log("failed to decode track: " + trackName);
                return false;
            }

            pcm.limit(frames * channels);
            uploadPcm(pcm, channels, sampleRate);

            MemoryUtil.memFree(pcm);
            decodedTrack = trackName;
            log("loaded track: " + trackName + " (" + sampleRate + " Hz, " + channels + " ch)");
            return true;
        } finally {
            info.free();
            STBVorbis.stb_vorbis_close(decoder);
            MemoryUtil.memFree(ogg);
        }
    }

    private void uploadPcm(ShortBuffer pcm, int channels, int sampleRate) {
        long previous = makeContextCurrent();

        try {
            if (bufferId >= 0) {
                AL10.alSourcei(sourceId, AL10.AL_BUFFER, 0);
                AL10.alDeleteBuffers(bufferId);
            }

            bufferId = AL10.alGenBuffers();
            int format = channels == 1
                ? AL10.AL_FORMAT_MONO16
                : AL10.AL_FORMAT_STEREO16;
            AL10.alBufferData(bufferId, format, pcm, sampleRate);
        } finally {
            restoreContext(previous);
        }
    }

    private boolean loadWithJavaSound(String trackName, String extension) {
        byte[] fileBytes = readTrackBytes(trackName);
        if (fileBytes == null) {
            return false;
        }

        AudioInputStream source = null;
        AudioInputStream pcmIn = null;
        ShortBuffer pcm = null;

        try {
            source = AudioSystem.getAudioInputStream(new ByteArrayInputStream(fileBytes));
            AudioFormat src = source.getFormat();

            int srcChannels = src.getChannels();
            int sampleRate = Math.round(src.getSampleRate());

            AudioFormat target = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                src.getSampleRate(),
                16,
                srcChannels,
                srcChannels * 2,
                src.getSampleRate(),
                false
            );

            pcmIn = AudioSystem.getAudioInputStream(target, source);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = pcmIn.read(chunk)) > 0) {
                out.write(chunk, 0, read);
            }

            byte[] data = out.toByteArray();
            int frames = data.length / (srcChannels * 2);
            if (frames <= 0) {
                log("Track is empty: " + trackName);
                return false;
            }

            int outChannels = srcChannels;

            if (outChannels != 1 && outChannels != 2) {
                log("unsupported channel count (" + outChannels + ") for track: " + trackName);
                return false;
            }

            ShortBuffer decoded = ByteBuffer.wrap(data)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer();
            decoded.limit(frames * srcChannels);

            pcm = MemoryUtil.memAllocShort(frames * outChannels);

            if (outChannels == srcChannels) {
                pcm.put (decoded);
            } else {
                for (int frame = 0; frame < frames; frame++) {
                    int base = frame * srcChannels;
                    pcm.put(decoded.get(base));
                    pcm.put(decoded.get(base + 1));
                }
            }

            pcm.flip();
            uploadPcm(pcm, outChannels, sampleRate);

            decodedTrack = trackName;
            log("loaded track: " + trackName + " (" + sampleRate + " Hz, " + outChannels + " ch, " + extension + ")");
            return true;
        } catch (UnsupportedAudioFileException e) {
            log("unsupported audio format for " + trackName + " (" + extension + "): " + e.getMessage());
            return false;
        } catch (IOException | IllegalArgumentException e) {
            log("failed to decode track: " + trackName + " (" + extension + "): " + e.getMessage());
            return false;
        } finally {
            if (pcm != null) {
                MemoryUtil.memFree(pcm);
            }
            closeQuietly(pcmIn);
            closeQuietly(source);
        }
    }

    private void closeQuietly(AudioInputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {}
        }
    }

    private ByteBuffer readTrack(String trackName) {
        byte[] bytes = readTrackBytes(trackName);
        if (bytes == null) {
            return null;
        }

        ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
        buffer.put(bytes).flip();
        return buffer;
    }

    private byte[] readTrackBytes(String trackName) {
        try {
            if (BUNDLED_NAME.equals(trackName)) {
                try (InputStream in = MusicManager.class.getResourceAsStream(BUNDLED_RESOURCE)) {
                    return in == null ? null : in.readAllBytes();
                }
            }

            Path file = userTracks.get(trackName);
            return file == null ? null : Files.readAllBytes(file);
        } catch (IOException e) {
            log("Could not read track: " + trackName);
            return null;
        } 
    }

    private void log(String message) {
        if (config.debug) {
            System.out.println("Specialist Dance: " + message);
        }
    }
}