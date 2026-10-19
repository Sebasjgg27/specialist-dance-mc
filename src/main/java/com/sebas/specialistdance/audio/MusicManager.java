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
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryUtil;

import com.sebas.specialistdance.SpecialistDanceConfig;

import net.fabricmc.loader.api.FabricLoader;

public class MusicManager {

    private static final String BUNDLED_NAME = "default";
    private static final String BUNDLED_RESOURCE = "/assets/specialist-dance/sounds/default.ogg";

    private final SpecialistDanceConfig config;
    private final Map<String, Path> userTracks = new TreeMap<>();

    private boolean available;
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
            device = ALC10.alcOpenDevice((ByteBuffer) null);

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
            ALC10.alcMakeContextCurrent(context);
            sourceId = AL10.alGenSources();
            ALC10.alcMakeContextCurrent(0L);
            available = true;
        } catch (Exception e) {
            System.err.println("Specialist Dance: music init failed");
            e.printStackTrace();
        }
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
        if (!config.musicEnabled || !available) {
            return;
        }

        if (!ensureTrackLoaded(config.musicTrack)) {
            log("track '" + config.musicTrack + "' not found, trying default");

            if(!ensureTrackLoaded(BUNDLED_NAME)) {
                return;
            }
        }

        ALC10.alcMakeContextCurrent(context);

        try {
            AL10.alSourceStop(sourceId);
            AL10.alSourcei(sourceId, AL10.AL_BUFFER, bufferId);
            AL10.alSourcei(sourceId, AL10.AL_LOOPING, AL10.AL_TRUE);
            AL10.alSourcef(sourceId, AL10.AL_GAIN, Math.clamp(config.musicVolume, 0.0f, 1.0f));
            AL10.alSourcePlay(sourceId);
        } finally {
            ALC10.alcMakeContextCurrent(0L);
        }

        log("playing track: " + decodedTrack);
    }

    public void stop() {
        if (!available) {
            return;
        }

        ALC10.alcMakeContextCurrent(context);

        try {
            AL10.alSourceStop(sourceId);
        } finally {
            ALC10.alcMakeContextCurrent(0L);
        }
    }

    public void shutdown() {
        if (!available) {
            return;
        }

        ALC10.alcMakeContextCurrent(context);

        try {
            AL10.alSourceStop(sourceId);
            AL10.alSourcei(sourceId, AL10.AL_BUFFER, 0);
            AL10.alDeleteBuffers(bufferId);
            AL10.alDeleteSources(sourceId);
        } finally {
            ALC10.alcMakeContextCurrent(0L);
        }

        ALC10.alcDestroyContext(context);
        ALC10.alcCloseDevice(device);
        available = false;
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

        IntBuffer channels = BufferUtils.createIntBuffer(1);
        IntBuffer sampleRate = BufferUtils.createIntBuffer(1);
        ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(ogg, channels, sampleRate);
        MemoryUtil.memFree(ogg);

        if (pcm == null) {
            log("failed to decode track: " + trackName);
            return false;
        }

        ALC10.alcMakeContextCurrent(context);

        try {
            if (bufferId >= 0) {
                AL10.alSourcei(sourceId, AL10.AL_BUFFER, 0);
                AL10.alDeleteBuffers(bufferId);
            }

            bufferId = AL10.alGenBuffers();
            int format = channels.get(0) == 1
                ? AL10.AL_FORMAT_MONO16
                : AL10.AL_FORMAT_STEREO16;
            AL10.alBufferData(bufferId, format, pcm, sampleRate.get(0));
        } finally {
            ALC10.alcMakeContextCurrent(0L);
        }

        MemoryUtil.memFree(pcm);
        decodedTrack = trackName;
        log("loaded track: " + trackName + " (" + sampleRate.get(0) + " Hz, " + channels.get(0) + " ch)");
        return true;
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