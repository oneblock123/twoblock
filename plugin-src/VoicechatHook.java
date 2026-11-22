package ru.twoblocks.chat;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.events.*;
import de.maxhenkel.voicechat.api.mp3.Mp3Decoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VoicechatHook implements VoicechatPlugin {
    private final TwoBlocksChat plugin;
    private VoicechatServerApi serverApi;
    private final Map<UUID, AudioPlayer> activeAudioPlayers = new ConcurrentHashMap<>();
    private String status = "Инициализация...";

    public VoicechatHook(TwoBlocksChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getPluginId() {
        return "twoblocks";
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (api instanceof VoicechatServerApi) {
            this.serverApi = (VoicechatServerApi) api;
            this.status = "готов (initialize: ServerApi)";
            return;
        }
        if (api != null) {
            VoicechatServerApi scanned = scanObjectForApi(api);
            if (scanned != null) {
                this.serverApi = scanned;
                this.status = "готов (initialize: scanned)";
                return;
            }
            this.status = "init: " + api.getClass().getSimpleName();
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        this.status = "events зарегистрированы";
        try {
            registration.registerEvent(VoicechatServerStartedEvent.class, e -> {
                if (e != null && e.getVoicechat() != null) {
                    this.serverApi = e.getVoicechat();
                    this.status = "готов (ServerStartedEvent)";
                }
            });
        } catch (Throwable ignored) {}
        try {
            registration.registerEvent(VoicechatServerStartingEvent.class, e -> {
                if (e != null && e.getVoicechat() != null) {
                    this.serverApi = e.getVoicechat();
                    this.status = "готов (ServerStartingEvent)";
                }
            });
        } catch (Throwable ignored) {}
        try {
            registration.registerEvent(PlayerConnectedEvent.class, e -> {
                if (e != null && e.getVoicechat() != null) {
                    this.serverApi = e.getVoicechat();
                    this.status = "готов (PlayerConnectedEvent)";
                }
            });
        } catch (Throwable ignored) {}
        try {
            registration.registerEvent(PlayerStateChangedEvent.class, e -> {
                if (e != null && e.getVoicechat() != null) {
                    this.serverApi = e.getVoicechat();
                    this.status = "готов (PlayerStateChangedEvent)";
                }
            });
        } catch (Throwable ignored) {}
        try {
            registration.registerEvent(MicrophonePacketEvent.class, e -> {
                if (e != null && e.getVoicechat() != null) {
                    this.serverApi = e.getVoicechat();
                    this.status = "готов (MicrophonePacketEvent)";
                }
            });
        } catch (Throwable ignored) {}
    }

    public VoicechatServerApi getServerApi() {
        if (this.serverApi != null) {
            return this.serverApi;
        }
        tryResolveServerApi();
        return this.serverApi;
    }

    public String getStatus() {
        return status;
    }

    private void tryResolveServerApi() {
        try {
            RegisteredServiceProvider<BukkitVoicechatService> rsp =
                    Bukkit.getServicesManager().getRegistration(BukkitVoicechatService.class);
            if (rsp != null && rsp.getProvider() != null) {
                BukkitVoicechatService svc = rsp.getProvider();
                VoicechatServerApi found = scanObjectForApi(svc);
                if (found != null) {
                    this.serverApi = found;
                    this.status = "готов (рефлексия svc)";
                    return;
                }
            }

            for (Class<?> c : Bukkit.getServicesManager().getKnownServices()) {
                if (c.getName().contains("Voicechat")) {
                    try {
                        RegisteredServiceProvider<?> r = Bukkit.getServicesManager().getRegistration(c);
                        if (r != null && r.getProvider() != null) {
                            VoicechatServerApi found = scanObjectForApi(r.getProvider());
                            if (found != null) {
                                this.serverApi = found;
                                this.status = "готов (рефлексия known svc)";
                                return;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }

            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                if (p.getName().toLowerCase().contains("voicechat")) {
                    VoicechatServerApi found = scanObjectForApi(p);
                    if (found != null) {
                        this.serverApi = found;
                        this.status = "готов (рефлексия plugin " + p.getName() + ")";
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            this.status = "ошибка поиска api: " + t.getMessage();
        }
    }

    private VoicechatServerApi scanObjectForApi(Object obj) {
        if (obj == null) return null;
        if (obj instanceof VoicechatServerApi) {
            return (VoicechatServerApi) obj;
        }
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (VoicechatServerApi.class.isAssignableFrom(m.getReturnType()) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                        Object res = m.invoke(obj);
                        if (res instanceof VoicechatServerApi) {
                            return (VoicechatServerApi) res;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            Class<?> curr = obj.getClass();
            while (curr != null && curr != Object.class) {
                for (Field f : curr.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(obj);
                        if (val instanceof VoicechatServerApi) {
                            return (VoicechatServerApi) val;
                        }
                        if (val != null && !f.getType().isPrimitive() && !f.getType().getName().startsWith("java.")) {
                            for (Field f2 : val.getClass().getDeclaredFields()) {
                                try {
                                    f2.setAccessible(true);
                                    Object val2 = f2.get(val);
                                    if (val2 instanceof VoicechatServerApi) {
                                        return (VoicechatServerApi) val2;
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    } catch (Throwable ignored) {}
                }
                curr = curr.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public boolean isReady() {
        return getServerApi() != null;
    }

    public void playTts(Player player, String message) {
        playTts(player, message, "normal");
    }

    public void playTts(Player player, String message, boolean isDemon) {
        playTts(player, message, isDemon ? "demon" : "normal");
    }

    public void playTts(Player player, String message, String voiceType) {
        if (player == null) {
            return;
        }

        VoicechatServerApi api = getServerApi();
        if (api == null) {
            player.sendMessage(ChatColor.RED + "Голосовой сервис не активен (" + status + ").");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String encoded = URLEncoder.encode(message, "UTF-8");
                String lang = (voiceType != null && voiceType.equalsIgnoreCase("anime")) ? "ja" : "ru";
                byte[] mp3Bytes = fetchTtsAudio(encoded, lang);

                if (mp3Bytes == null || mp3Bytes.length == 0) {
                    player.sendMessage(ChatColor.RED + "Не удалось получить аудио (ошибка всех TTS сервисов).");
                    return;
                }

                short[] pcm = decodeMp3To48kMono(mp3Bytes, api);
                if (pcm == null || pcm.length == 0) {
                    player.sendMessage(ChatColor.RED + "Ошибка: не удалось декодировать аудиопоток.");
                    return;
                }

                pcm = applyVoiceEffect(pcm, voiceType);

                UUID channelId = player.getUniqueId();

                AudioPlayer old = activeAudioPlayers.remove(channelId);
                if (old != null && old.isPlaying()) {
                    try {
                        old.stopPlaying();
                    } catch (Throwable ignored) {}
                }

                EntityAudioChannel entityChannel = api.createEntityAudioChannel(channelId, api.fromEntity(player));
                if (entityChannel != null) {
                    OpusEncoder encoder = api.createEncoder();
                    AudioPlayer audioPlayer = api.createAudioPlayer(entityChannel, encoder, pcm);
                    activeAudioPlayers.put(channelId, audioPlayer);
                    audioPlayer.startPlaying();
                }

                VoicechatConnection senderConn = api.getConnectionOf(player.getUniqueId());
                if (senderConn != null) {
                    try {
                        StaticAudioChannel selfChannel = api.createStaticAudioChannel(
                                channelId,
                                api.fromServerLevel(player.getWorld()),
                                senderConn
                        );
                        if (selfChannel != null) {
                            OpusEncoder selfEncoder = api.createEncoder();
                            AudioPlayer selfAudioPlayer = api.createAudioPlayer(selfChannel, selfEncoder, pcm);
                            selfAudioPlayer.startPlaying();
                        }
                    } catch (Throwable ignored) {}

                    if (senderConn.isInGroup()) {
                        Group group = senderConn.getGroup();
                        if (group != null) {
                            for (Player target : Bukkit.getOnlinePlayers()) {
                                if (target.equals(player)) continue;
                                VoicechatConnection targetConn = api.getConnectionOf(target.getUniqueId());
                                if (targetConn != null && targetConn.isInGroup()) {
                                    Group targetGroup = targetConn.getGroup();
                                    if (targetGroup != null && targetGroup.getId().equals(group.getId())) {
                                        try {
                                            StaticAudioChannel staticChannel = api.createStaticAudioChannel(
                                                    channelId,
                                                    api.fromServerLevel(target.getWorld()),
                                                    targetConn
                                            );
                                            if (staticChannel != null) {
                                                OpusEncoder staticEncoder = api.createEncoder();
                                                AudioPlayer staticAudioPlayer = api.createAudioPlayer(staticChannel, staticEncoder, pcm);
                                                staticAudioPlayer.startPlaying();
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            }
                        }
                    }
                } else {
                    player.sendMessage(ChatColor.YELLOW + "[Предупреждение] Вы не подключены к Voice Chat каналу сервера.");
                }

                playFeedbackSound(player, voiceType);

            } catch (Throwable t) {
                player.sendMessage(ChatColor.RED + "Ошибка воспроизведения: " + t.getMessage());
            }
        });
    }

    private byte[] fetchTtsAudio(String encodedText, String lang) {
        String tl = (lang != null && !lang.isEmpty()) ? lang : "ru";
        String[] endpoints = new String[]{
                "https://translate.google.com/translate_tts?ie=UTF-8&tl=" + tl + "&client=gtx&q=" + encodedText,
                "https://translate.google.com/translate_tts?ie=UTF-8&tl=" + tl + "&client=tw-ob&q=" + encodedText
        };

        for (String urlStr : endpoints) {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                conn.setRequestProperty("Referer", "https://translate.google.com/");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream();
                         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        byte[] buf = new byte[4096];
                        int r;
                        while ((r = in.read(buf)) != -1) {
                            out.write(buf, 0, r);
                        }
                        byte[] res = out.toByteArray();
                        if (res.length > 512) {
                            return res;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private short[] decodeMp3To48kMono(byte[] mp3Bytes, VoicechatServerApi api) {
        if (api != null) {
            try {
                Mp3Decoder decoder = api.createMp3Decoder(new ByteArrayInputStream(mp3Bytes));
                if (decoder != null) {
                    short[] decoded = decoder.decode();
                    if (decoded != null && decoded.length > 0) {
                        return decoded;
                    }
                }
            } catch (Throwable ignored) {}
        }

        try {
            javazoom.jl.decoder.Bitstream bitstream = new javazoom.jl.decoder.Bitstream(new ByteArrayInputStream(mp3Bytes));
            javazoom.jl.decoder.Decoder decoder = new javazoom.jl.decoder.Decoder();

            javazoom.jl.decoder.Header frameHeader = bitstream.readFrame();
            if (frameHeader == null) {
                return null;
            }

            int srcSampleRate = frameHeader.frequency();
            int srcChannels = (frameHeader.mode() == javazoom.jl.decoder.Header.SINGLE_CHANNEL) ? 1 : 2;

            java.util.List<short[]> chunks = new java.util.ArrayList<>();
            int totalSamples = 0;

            while (frameHeader != null) {
                javazoom.jl.decoder.SampleBuffer output = (javazoom.jl.decoder.SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);
                if (output != null) {
                    int len = output.getBufferLength();
                    short[] buf = output.getBuffer();
                    short[] copy = new short[len];
                    System.arraycopy(buf, 0, copy, 0, len);
                    chunks.add(copy);
                    totalSamples += len;
                }
                bitstream.closeFrame();
                frameHeader = bitstream.readFrame();
            }

            if (totalSamples == 0) {
                return null;
            }

            short[] rawPcm = new short[totalSamples];
            int offset = 0;
            for (short[] chunk : chunks) {
                System.arraycopy(chunk, 0, rawPcm, offset, chunk.length);
                offset += chunk.length;
            }

            short[] monoPcm;
            if (srcChannels > 1) {
                monoPcm = new short[rawPcm.length / srcChannels];
                for (int i = 0; i < monoPcm.length; i++) {
                    int sum = 0;
                    for (int ch = 0; ch < srcChannels; ch++) {
                        sum += rawPcm[i * srcChannels + ch];
                    }
                    monoPcm[i] = (short) (sum / srcChannels);
                }
            } else {
                monoPcm = rawPcm;
            }

            if (srcSampleRate == 48000) {
                return monoPcm;
            }

            double ratio = 48000.0 / srcSampleRate;
            int targetLength = (int) Math.round(monoPcm.length * ratio);
            short[] resampled = new short[targetLength];

            for (int i = 0; i < targetLength; i++) {
                double srcIdx = i / ratio;
                int idx0 = (int) Math.floor(srcIdx);
                int idx1 = Math.min(idx0 + 1, monoPcm.length - 1);
                double frac = srcIdx - idx0;
                resampled[i] = (short) Math.round((1.0 - frac) * monoPcm[idx0] + frac * monoPcm[idx1]);
            }

            return resampled;
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[TwoBlocks] JLayer decode error: " + t.getMessage());
            return null;
        }
    }

    private short[] applyVoiceEffect(short[] input, String voiceType) {
        if (input == null || input.length == 0 || voiceType == null) return input;
        switch (voiceType.toLowerCase()) {
            case "demon":
                return applyDemonEffect(input);
            case "chipmunk":
                return applyChipmunkEffect(input);
            case "robot":
                return applyRobotEffect(input);
            case "giant":
                return applyGiantEffect(input);
            case "radio":
                return applyRadioEffect(input);
            case "echo":
                return applyEchoEffect(input);
            case "fast":
                return applyPitchEffect(input, 1.35);
            case "slow":
                return applyPitchEffect(input, 0.78);
            case "anime":
                return applyPitchEffect(input, 1.12);
            default:
                return input;
        }
    }

    private void playFeedbackSound(Player player, String voiceType) {
        if (player == null) return;
        Sound sound = Sound.UI_BUTTON_CLICK;
        float pitch = 1.6f;
        if (voiceType != null) {
            switch (voiceType.toLowerCase()) {
                case "demon":
                    sound = Sound.ENTITY_WITHER_SPAWN;
                    pitch = 0.6f;
                    break;
                case "chipmunk":
                    sound = Sound.ENTITY_BAT_TAKEOFF;
                    pitch = 1.8f;
                    break;
                case "robot":
                    sound = Sound.BLOCK_BEACON_ACTIVATE;
                    pitch = 1.7f;
                    break;
                case "giant":
                    sound = Sound.ENTITY_ENDER_DRAGON_GROWL;
                    pitch = 0.5f;
                    break;
                case "radio":
                    sound = Sound.BLOCK_NOTE_BLOCK_BELL;
                    pitch = 2.0f;
                    break;
                case "echo":
                    sound = Sound.BLOCK_AMETHYST_BLOCK_CHIME;
                    pitch = 1.2f;
                    break;
                case "anime":
                    sound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                    pitch = 1.5f;
                    break;
            }
        }
        player.playSound(player.getLocation(), sound, 0.4f, pitch);
    }

    private short[] applyDemonEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            double pitchFactor = 0.68;
            int newLength = (int) Math.round(input.length / pitchFactor);
            short[] pitched = new short[newLength];

            for (int i = 0; i < newLength; i++) {
                double srcPos = i * pitchFactor;
                int idx0 = (int) Math.floor(srcPos);
                int idx1 = Math.min(idx0 + 1, input.length - 1);
                double frac = srcPos - idx0;
                pitched[i] = (short) Math.round((1.0 - frac) * input[idx0] + frac * input[idx1]);
            }

            int delaySamples = (int) (48000 * 0.085);
            short[] output = new short[newLength];

            for (int i = 0; i < newLength; i++) {
                int dry = pitched[i];
                int wet = (i >= delaySamples) ? (int) (pitched[i - delaySamples] * 0.42) : 0;
                int combined = dry + wet;
                if (combined > 32767) combined = 32767;
                else if (combined < -32768) combined = -32768;
                output[i] = (short) combined;
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applyChipmunkEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            double pitchFactor = 1.55;
            int newLength = (int) Math.round(input.length / pitchFactor);
            short[] output = new short[newLength];

            for (int i = 0; i < newLength; i++) {
                double srcPos = i * pitchFactor;
                int idx0 = (int) Math.floor(srcPos);
                int idx1 = Math.min(idx0 + 1, input.length - 1);
                double frac = srcPos - idx0;
                output[i] = (short) Math.round((1.0 - frac) * input[idx0] + frac * input[idx1]);
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applyGiantEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            double pitchFactor = 0.54;
            int newLength = (int) Math.round(input.length / pitchFactor);
            short[] pitched = new short[newLength];

            for (int i = 0; i < newLength; i++) {
                double srcPos = i * pitchFactor;
                int idx0 = (int) Math.floor(srcPos);
                int idx1 = Math.min(idx0 + 1, input.length - 1);
                double frac = srcPos - idx0;
                pitched[i] = (short) Math.round((1.0 - frac) * input[idx0] + frac * input[idx1]);
            }

            int delaySamples = (int) (48000 * 0.12);
            short[] output = new short[newLength];

            for (int i = 0; i < newLength; i++) {
                int dry = pitched[i];
                int wet = (i >= delaySamples) ? (int) (pitched[i - delaySamples] * 0.48) : 0;
                int combined = dry + wet;
                if (combined > 32767) combined = 32767;
                else if (combined < -32768) combined = -32768;
                output[i] = (short) combined;
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applyRobotEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            short[] output = new short[input.length];
            double freq = 52.0;
            double twoPiF = 2.0 * Math.PI * freq / 48000.0;
            for (int i = 0; i < input.length; i++) {
                double mod = 0.5 + 0.5 * Math.sin(i * twoPiF);
                int val = (int) (input[i] * (0.35 + 0.65 * mod));
                val = (val >> 5) << 5;
                if (val > 32767) val = 32767;
                else if (val < -32768) val = -32768;
                output[i] = (short) val;
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applyRadioEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            short[] output = new short[input.length];
            double hpY = 0;
            double prevX = 0;
            double lpY = 0;
            for (int i = 0; i < input.length; i++) {
                double x = input[i];
                hpY = 0.94 * (hpY + x - prevX);
                prevX = x;
                lpY = lpY + 0.32 * (hpY - lpY);
                int val = (int) (lpY * 1.8);
                if (val > 22000) val = 22000;
                else if (val < -22000) val = -22000;
                output[i] = (short) val;
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applyEchoEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            int d1 = (int) (48000 * 0.14);
            int d2 = (int) (48000 * 0.28);
            int d3 = (int) (48000 * 0.42);
            int extra = d3 + 2000;
            short[] output = new short[input.length + extra];

            for (int i = 0; i < output.length; i++) {
                int dry = (i < input.length) ? input[i] : 0;
                int e1 = (i >= d1 && i - d1 < input.length) ? (int) (input[i - d1] * 0.45) : 0;
                int e2 = (i >= d2 && i - d2 < input.length) ? (int) (input[i - d2] * 0.28) : 0;
                int e3 = (i >= d3 && i - d3 < input.length) ? (int) (input[i - d3] * 0.15) : 0;
                int combined = dry + e1 + e2 + e3;
                if (combined > 32767) combined = 32767;
                else if (combined < -32768) combined = -32768;
                output[i] = (short) combined;
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applyPitchEffect(short[] input, double pitchFactor) {
        if (input == null || input.length == 0 || pitchFactor <= 0.05) return input;
        try {
            int newLength = (int) Math.round(input.length / pitchFactor);
            short[] output = new short[newLength];
            for (int i = 0; i < newLength; i++) {
                double srcPos = i * pitchFactor;
                int idx0 = (int) Math.floor(srcPos);
                int idx1 = Math.min(idx0 + 1, input.length - 1);
                double frac = srcPos - idx0;
                output[i] = (short) Math.round((1.0 - frac) * input[idx0] + frac * input[idx1]);
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }
}
