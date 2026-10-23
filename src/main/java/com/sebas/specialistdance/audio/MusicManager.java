package com.sebas.specialistdance.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

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
                ALC10.alcMakeContextCurrent(previous);
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
        } else {
            ALC10.alcMakeContextCurrent(previous);
        }
    }

    private void releaseContext() {
        if (context != 0L) {
            if (threadLocalContext) {
                EXTThreadLocalContext.alcSetThreadContext(0L);
            }

            if (ALC10.alcGetCurrentContext() == context) {
                ALC10.alcMakeContextCurrent(0L);
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

    private void scanUserTracks() {
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
            files
                .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".ogg"))
                .forEach(p -> {
                    String name = p.getFileName().toString();
                    name = name.substring(0, name.length() - ".ogg".length());
                    userTracks.put(name, p);
                });
        } catch (IOException e) {
            log("could not scan music dir " + musicDir);
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
            AL10.alSourcei(sourceId, AL10.AL_LOOPING, AL10.AL_TRUE);
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

            int channels = info.channels();
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

    private ByteBuffer readTrack(String trackName) {
        try {
            if (trackName.equals(BUNDLED_NAME)) {
                try (InputStream in = MusicManager.class.getResourceAsStream(BUNDLED_RESOURCE)) {
                    if (in == null) {
                        log("bundled track missing: " + BUNDLED_RESOURCE);
                        return null;
                    }
                    return readAll(in);
                }
            }

            Path file = userTracks.get(trackName);

            if (file == null) {
                return null;
            }

            try (InputStream in = Files.newInputStream(file)) {
                return readAll(in);
            }
        } catch (IOException e) {
            log("could not read track: " + trackName);
            return null;
        }
    }

    private ByteBuffer readAll(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
        buffer.put(bytes).flip();
        return buffer;
    }

    private void log(String message) {
        if (config.debug) {
            System.out.println("Specialist Dance: " + message);
        }
    }
}