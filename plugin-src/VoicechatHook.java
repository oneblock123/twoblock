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
                byte[] mp3Bytes = null;
                boolean isNeuralAi = false;

                // Try Fish Audio neural AI voice clone if API key is set and voice is one of the characters
                if (!voiceType.equalsIgnoreCase("normal") && !voiceType.equalsIgnoreCase("giant") && !voiceType.equalsIgnoreCase("chipmunk")) {
                    mp3Bytes = fetchFishAudio(player, message, voiceType);
                    if (mp3Bytes != null && mp3Bytes.length > 512) {
                        isNeuralAi = true;
                    }
                }

                // If not using Fish Audio or if Fish Audio failed/offline, use Google TTS
                if (mp3Bytes == null) {
                    String encoded = URLEncoder.encode(message, "UTF-8");
                    mp3Bytes = fetchTtsAudio(encoded, "ru");
                }

                if (mp3Bytes == null || mp3Bytes.length == 0) {
                    player.sendMessage(ChatColor.RED + "Не удалось получить аудио (ошибка всех TTS сервисов).");
                    return;
                }

                short[] pcm = decodeMp3To48kMono(mp3Bytes, api);
                if (pcm == null || pcm.length == 0) {
                    player.sendMessage(ChatColor.RED + "Ошибка: не удалось декодировать аудиопоток.");
                    return;
                }

                // Only apply DSP acoustic voice modifications if generated via Google TTS fallback
                if (!isNeuralAi) {
                    pcm = applyVoiceEffect(pcm, voiceType);
                }

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
                int selfVol = plugin.getPlayerTtsVolume(player.getUniqueId());

                if (senderConn != null) {
                    if (selfVol > 0) {
                        try {
                            short[] selfPcm = (selfVol == 100) ? pcm : applyVolume(pcm, selfVol / 100.0f);
                            StaticAudioChannel selfChannel = api.createStaticAudioChannel(
                                    channelId,
                                    api.fromServerLevel(player.getWorld()),
                                    senderConn
                            );
                            if (selfChannel != null) {
                                OpusEncoder selfEncoder = api.createEncoder();
                                AudioPlayer selfAudioPlayer = api.createAudioPlayer(selfChannel, selfEncoder, selfPcm);
                                selfAudioPlayer.startPlaying();
                            }
                        } catch (Throwable ignored) {}
                    }

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
                                            int targetVol = plugin.getPlayerTtsVolume(target.getUniqueId());
                                            if (targetVol <= 0) continue;
                                            short[] targetPcm = (targetVol == 100) ? pcm : applyVolume(pcm, targetVol / 100.0f);

                                            StaticAudioChannel staticChannel = api.createStaticAudioChannel(
                                                    channelId,
                                                    api.fromServerLevel(target.getWorld()),
                                                    targetConn
                                            );
                                            if (staticChannel != null) {
                                                OpusEncoder staticEncoder = api.createEncoder();
                                                AudioPlayer staticAudioPlayer = api.createAudioPlayer(staticChannel, staticEncoder, targetPcm);
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

    private byte[] fetchFishAudio(Player player, String text, String voiceType) {
        String apiKey = plugin.getFishAudioApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null;
        }
        String modelId = plugin.getFishAudioModelId(voiceType);
        if (modelId == null || modelId.trim().isEmpty()) {
            return null;
        }

        // Try standard request first, and if 402/insufficient credit try with free tier header model: s2.1-pro-free
        byte[] res = doFishAudioRequest(apiKey, modelId, text, null);
        if (res == null) {
            res = doFishAudioRequest(apiKey, modelId, text, "s2.1-pro-free");
        }
        return res;
    }

    private byte[] doFishAudioRequest(String apiKey, String modelId, String text, String modelHeader) {
        try {
            URL url = new URL("https://api.fish.audio/v1/tts");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 TwoBlocksChat/1.0");
            if (modelHeader != null && !modelHeader.isEmpty()) {
                conn.setRequestProperty("model", modelHeader);
            }
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(12000);
            conn.setDoOutput(true);

            String jsonPayload = "{\"text\":" + quoteJson(text) + ",\"reference_id\":" + quoteJson(modelId.trim()) + ",\"format\":\"mp3\"}";

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                try (InputStream in = conn.getInputStream();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[4096];
                    int r;
                    while ((r = in.read(buf)) != -1) {
                        out.write(buf, 0, r);
                    }
                    byte[] bytes = out.toByteArray();
                    if (bytes.length > 512) {
                        return bytes;
                    }
                }
            } else {
                try (InputStream err = conn.getErrorStream()) {
                    if (err != null) {
                        ByteArrayOutputStream errOut = new ByteArrayOutputStream();
                        byte[] buf = new byte[1024];
                        int r;
                        while ((r = err.read(buf)) != -1) errOut.write(buf, 0, r);
                        Bukkit.getLogger().warning("[TwoBlocksChat] Fish Audio HTTP " + code + (modelHeader != null ? " (" + modelHeader + ")" : "") + ": " + errOut.toString(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            }
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[TwoBlocksChat] Fish Audio request failed: " + t.getMessage());
        }
        return null;
    }

    private String quoteJson(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
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
            case "sidorovich":
                return applySidorovichEffect(input);
            case "bandit":
                return applyBanditEffect(input);
            case "patrick":
                return applyPatrickEffect(input);
            case "spongebob":
                return applySpongebobEffect(input);
            case "georgian":
                return applyGeorgianEffect(input);
            case "katya":
                return applyKatyaEffect(input);
            case "catgirl":
                return applyCatgirlEffect(input);
            case "pudge":
                return applyPudgeEffect(input);
            case "navalny":
                return applyNavalnyEffect(input);
            case "ut":
                return applyUtEffect(input);
            case "brainrot":
                return applyBrainrotEffect(input);
            case "giant":
                return applyGiantEffect(input);
            case "chipmunk":
                return applyChipmunkEffect(input);
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
                case "sidorovich":
                    sound = Sound.ENTITY_VILLAGER_TRADE;
                    pitch = 0.8f;
                    break;
                case "bandit":
                    sound = Sound.ITEM_CROSSBOW_SHOOT;
                    pitch = 0.7f;
                    break;
                case "patrick":
                    sound = Sound.ENTITY_SALMON_FLOP;
                    pitch = 0.6f;
                    break;
                case "spongebob":
                    sound = Sound.ENTITY_PUFFER_FISH_BLOW_UP;
                    pitch = 1.8f;
                    break;
                case "georgian":
                    sound = Sound.BLOCK_BELL_USE;
                    pitch = 1.2f;
                    break;
                case "katya":
                    sound = Sound.ENTITY_CAT_HISS;
                    pitch = 1.5f;
                    break;
                case "catgirl":
                    sound = Sound.ENTITY_CAT_PURREOW;
                    pitch = 1.7f;
                    break;
                case "pudge":
                    sound = Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR;
                    pitch = 0.5f;
                    break;
                case "navalny":
                    sound = Sound.BLOCK_NOTE_BLOCK_BELL;
                    pitch = 1.4f;
                    break;
                case "ut":
                    sound = Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
                    pitch = 1.6f;
                    break;
                case "brainrot":
                    sound = Sound.ENTITY_PARROT_IMITATE_CREEPER;
                    pitch = 1.8f;
                    break;
                case "giant":
                    sound = Sound.ENTITY_ENDER_DRAGON_GROWL;
                    pitch = 0.5f;
                    break;
                case "chipmunk":
                    sound = Sound.ENTITY_BAT_TAKEOFF;
                    pitch = 1.8f;
                    break;
            }
        }
        player.playSound(player.getLocation(), sound, 0.4f, pitch);
    }

    public static short[] applyVolume(short[] input, float volume) {
        if (input == null || input.length == 0) return input;
        if (Math.abs(volume - 1.0f) < 0.01f) return input;
        short[] output = new short[input.length];
        for (int i = 0; i < input.length; i++) {
            int val = (int) (input[i] * volume);
            output[i] = clamp(val);
        }
        return output;
    }

    private static short clamp(int val) {
        if (val > 32767) return 32767;
        if (val < -32768) return -32768;
        return (short) val;
    }

    private short[] applySidorovichEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            short[] pitched = applyPitchEffect(input, 0.82);
            int len = pitched.length;

            short[] filtered = new short[len];
            double hpY = 0;
            double prevX = 0;
            double lpY = 0;
            for (int i = 0; i < len; i++) {
                double x = pitched[i];
                hpY = 0.93 * (hpY + x - prevX);
                prevX = x;
                lpY = lpY + 0.36 * (hpY - lpY);

                int val = (int) (lpY * 1.7);
                if (val > 18000) val = 18000 + (val - 18000) / 3;
                else if (val < -18000) val = -18000 + (val + 18000) / 3;
                filtered[i] = clamp(val);
            }

            int delaySamples = (int) (48000 * 0.042);
            short[] output = new short[len + delaySamples];
            for (int i = 0; i < output.length; i++) {
                int dry = (i < len) ? filtered[i] : 0;
                int wet = (i >= delaySamples && (i - delaySamples) < len) ? (int) (filtered[i - delaySamples] * 0.28) : 0;
                output[i] = clamp(dry + wet);
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applyBanditEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            short[] pitched = applyPitchEffect(input, 0.89);
            int len = pitched.length;

            int combDelay = (int) (48000 * 0.020);
            short[] output = new short[len + combDelay];
            for (int i = 0; i < output.length; i++) {
                int dry = (i < len) ? pitched[i] : 0;
                int wet = (i >= combDelay && (i - combDelay) < len) ? (int) (output[i - combDelay] * 0.38) : 0;
                int val = dry + wet;
                if (val > 15000) val = 15000 + (val - 15000) / 3;
                else if (val < -15000) val = -15000 + (val + 15000) / 3;
                output[i] = clamp((int) (val * 1.15));
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applyPatrickEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            short[] pitched = applyPitchEffect(input, 0.72);
            int len = pitched.length;

            short[] output = new short[len];
            double lpY = 0;
            for (int i = 0; i < len; i++) {
                lpY = lpY + 0.17 * (pitched[i] - lpY);
                int val = (int) (lpY * 1.55);
                output[i] = clamp(val);
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applySpongebobEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            short[] pitched = applyPitchEffect(input, 1.44);
            int len = pitched.length;

            short[] output = new short[len];
            double twoPiF = 2.0 * Math.PI * 8.5 / 48000.0;
            double hpY = 0;
            double prevX = 0;

            for (int i = 0; i < len; i++) {
                double trem = 0.68 + 0.32 * Math.sin(i * twoPiF);
                double x = pitched[i] * trem;
                hpY = 0.91 * (hpY + x - prevX);
                prevX = x;
                output[i] = clamp((int) (x * 0.7 + hpY * 0.6));
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applyGeorgianEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            short[] pitched = applyPitchEffect(input, 0.87);
            int len = pitched.length;

            int d1 = (int) (48000 * 0.032);
            int d2 = (int) (48000 * 0.062);
            short[] output = new short[len + d2];

            for (int i = 0; i < output.length; i++) {
                int dry = (i < len) ? pitched[i] : 0;
                int echo1 = (i >= d1 && (i - d1) < len) ? (int) (pitched[i - d1] * 0.22) : 0;
                int echo2 = (i >= d2 && (i - d2) < len) ? (int) (pitched[i - d2] * 0.15) : 0;
                int val = dry + echo1 + echo2;
                output[i] = clamp((int) (val * 1.1));
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applyKatyaEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            short[] pitched = applyPitchEffect(input, 1.37);
            int len = pitched.length;

            short[] output = new short[len];
            double twoPiF = 2.0 * Math.PI * 6.2 / 48000.0;

            for (int i = 0; i < len; i++) {
                double trem = 0.74 + 0.26 * Math.sin(i * twoPiF);
                int val = (int) (pitched[i] * trem * 1.35);
                if (val > 22000) val = 22000 + (val - 22000) / 4;
                else if (val < -22000) val = -22000 + (val + 22000) / 4;
                output[i] = clamp(val);
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applyCatgirlEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            short[] pitched = applyPitchEffect(input, 1.54);
            int len = pitched.length;

            int chorusDelay = (int) (48000 * 0.013);
            short[] output = new short[len + chorusDelay];

            for (int i = 0; i < output.length; i++) {
                int dry = (i < len) ? pitched[i] : 0;
                int wet = (i >= chorusDelay && (i - chorusDelay) < len) ? (int) (pitched[i - chorusDelay] * 0.32) : 0;
                output[i] = clamp((int) ((dry + wet) * 1.15));
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applyPudgeEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            short[] pitched = applyPitchEffect(input, 0.60);
            int len = pitched.length;

            int d = (int) (48000 * 0.070);
            short[] output = new short[len + d];

            for (int i = 0; i < output.length; i++) {
                int dry = (i < len) ? pitched[i] : 0;
                int wet = (i >= d && (i - d) < len) ? (int) (pitched[i - d] * 0.44) : 0;
                int val = dry + wet;
                if (val > 14000) val = 14000 + (val - 14000) / 4;
                else if (val < -14000) val = -14000 + (val + 14000) / 4;
                output[i] = clamp((int) (val * 1.4));
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applyNavalnyEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            short[] pitched = applyPitchEffect(input, 1.04);
            int len = pitched.length;

            int slapDelay = (int) (48000 * 0.095);
            short[] output = new short[len + slapDelay];

            for (int i = 0; i < output.length; i++) {
                int dry = (i < len) ? pitched[i] : 0;
                int echo = (i >= slapDelay && (i - slapDelay) < len) ? (int) (pitched[i - slapDelay] * 0.24) : 0;
                int val = (int) (dry * 1.25) + echo;
                output[i] = clamp(val);
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applyUtEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            short[] pitched = applyPitchEffect(input, 0.64);
            int len = pitched.length;

            int d1 = (int) (48000 * 0.090);
            int d2 = (int) (48000 * 0.175);
            short[] output = new short[len + d2];

            for (int i = 0; i < output.length; i++) {
                int dry = (i < len) ? pitched[i] : 0;
                int echo1 = (i >= d1 && (i - d1) < len) ? (int) (pitched[i - d1] * 0.38) : 0;
                int echo2 = (i >= d2 && (i - d2) < len) ? (int) (pitched[i - d2] * 0.24) : 0;
                int val = dry + echo1 + echo2;
                if (val > 20000) val = 20000 + (val - 20000) / 3;
                else if (val < -20000) val = -20000 + (val + 20000) / 3;
                output[i] = clamp((int) (val * 1.35));
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applyBrainrotEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        try {
            short[] pitched = applyPitchEffect(input, 1.28);
            int len = pitched.length;

            short[] output = new short[len];
            double twoPiF = 2.0 * Math.PI * 12.0 / 48000.0;

            for (int i = 0; i < len; i++) {
                double wobble = 0.80 + 0.20 * Math.sin(i * twoPiF);
                int val = (int) (pitched[i] * wobble * 1.5);
                if (val > 16000) val = 16000 + (val - 16000) / 3;
                else if (val < -16000) val = -16000 + (val + 16000) / 3;
                output[i] = clamp(val);
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
            short[] output = new short[newLength + delaySamples];

            for (int i = 0; i < output.length; i++) {
                int dry = (i < newLength) ? pitched[i] : 0;
                int wet = (i >= delaySamples && (i - delaySamples) < newLength) ? (int) (pitched[i - delaySamples] * 0.48) : 0;
                output[i] = clamp(dry + wet);
            }
            return output;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private short[] applyChipmunkEffect(short[] input) {
        if (input == null || input.length == 0) return input;
        return applyPitchEffect(input, 1.55);
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
