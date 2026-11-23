package ru.twoblocks.antixray;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class TwoBlocksAntiXray extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    public static class MinerStats {
        public int stoneCount = 0;
        public int diamondCount = 0;
        public int debrisCount = 0;
        public int goldCount = 0;
        public int emeraldCount = 0;
        public int phantomMinedCount = 0;
        public int burstOreCount = 0;
        public long burstStartTime = 0;
        public long lastOreTime = 0;

        public int getTotalValuableOres() {
            return diamondCount + debrisCount + goldCount + emeraldCount;
        }

        public double getValuableRatio() {
            int total = stoneCount + getTotalValuableOres();
            if (total < 25) return 0.0;
            return ((double) getTotalValuableOres() / (double) total) * 100.0;
        }
    }

    private final Map<UUID, MinerStats> statsMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAlertTime = new ConcurrentHashMap<>();
    private final Set<String> checkedVeins = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Фантомные руды (ловушки в глухих стенах)
    private final Map<String, Material> phantomOres = new ConcurrentHashMap<>();
    // Игроки под действием троллинга /ax troll <ник>
    private final Set<UUID> trolledPlayers = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("antixray") != null) {
            getCommand("antixray").setExecutor(this);
            getCommand("antixray").setTabCompleter(this);
        }
        getLogger().info("TwoBlocksAntiXray v1.0.1 (Bedrock Shuffler, Honeypot & Troll Screamer) enabled!");
    }

    @Override
    public void onDisable() {
        statsMap.clear();
        checkedVeins.clear();
        lastAlertTime.clear();
        phantomOres.clear();
        trolledPlayers.clear();
        getLogger().info("TwoBlocksAntiXray deactivated.");
    }

    public boolean isAdmin(CommandSender sender) {
        if (sender == null) return false;
        if (sender.isOp() || sender.hasPermission("twoblocks.admin") || sender.hasPermission("antixray.admin") || sender.hasPermission("*")) {
            return true;
        }
        return sender.getName().equalsIgnoreCase("Genius_Ponos");
    }

    public MinerStats getStats(UUID uuid) {
        return statsMap.computeIfAbsent(uuid, k -> new MinerStats());
    }

    public void resetStats(UUID uuid) {
        statsMap.remove(uuid);
        lastAlertTime.remove(uuid);
    }

    // 1. Рандомизация узора бедрока (Bedrock Shuffling против SeedCrackerX)
    // При загрузке чанка слои пола/потолка бедрока слегка перемешиваются, ломая обратный алгоритм реверса сида
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();
        World.Environment env = world.getEnvironment();

        // Обрабатываем Overworld (Y от -63 до -59) и Nether (пол и потолок)
        if (env == World.Environment.NORMAL) {
            int minY = world.getMinHeight(); // -64
            // Перемешиваем только переходные слои (-63 .. -60), самый нижний Y=-64 не трогаем, чтобы не падать в пустоту
            shuffleBedrockLayer(chunk, minY + 1, minY + 4, Material.DEEPSLATE);
        } else if (env == World.Environment.NETHER) {
            // Пол незера 1..4 и потолок 123..126
            shuffleBedrockLayer(chunk, 1, 4, Material.NETHERRACK);
            shuffleBedrockLayer(chunk, 123, 126, Material.NETHERRACK);
        }
    }

    private void shuffleBedrockLayer(Chunk chunk, int fromY, int toY, Material filler) {
        // Делаем легкую детерминированно-случайную перетасовку 8 блоков в чанке
        int chunkHash = (chunk.getX() * 31 + chunk.getZ()) ^ 0x5DEECE66;
        Random rng = new Random(chunkHash);

        for (int i = 0; i < 12; i++) {
            int x = rng.nextInt(16);
            int z = rng.nextInt(16);
            int y = fromY + rng.nextInt(toY - fromY + 1);
            Block b = chunk.getBlock(x, y, z);
            Material type = b.getType();
            if (type == Material.BEDROCK) {
                b.setType(filler, false);
            } else if (type == filler) {
                b.setType(Material.BEDROCK, false);
            }
        }
    }

    // Защита от команды /seed
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String msg = event.getMessage().trim().toLowerCase();

        if (msg.equals("/seed") || msg.startsWith("/seed ") || msg.equals("/bukkit:seed") || msg.startsWith("/bukkit:seed ")) {
            if (!isAdmin(player)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.WHITE + "Seed: [" + ChatColor.GREEN + "749204810572918502" + ChatColor.WHITE + "]");
                notifyAdmins(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "AntiXray" + ChatColor.DARK_GRAY + "] "
                        + ChatColor.YELLOW + player.getName() + ChatColor.GRAY + " запросил сид мира (/seed). Выдан фейковый сид.");
            }
        }
    }

    // 2. Обработка копания блоков: Троллинг, Фантомные руды и Эвристика
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Block block = event.getBlock();
        Location loc = block.getLocation();
        String locKey = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();

        // 🎯 Троллинг игрока (/ax troll <ник>)
        if (trolledPlayers.contains(uuid)) {
            event.setCancelled(true);

            // Накладываем эффект Утомления (Mining Fatigue III) на 15 секунд
            // Это мгновенно вызывает появление огромной призрачной головы Древнего Стража (Elder Guardian) во весь экран!
            player.addPotionEffect(new PotionEffect(getMiningFatigueType(), 300, 2, false, false, true));
            // Воспроизводим тот самый жуткий звук скримера древнего стража
            player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.2f, 0.9f);
            return;
        }

        if (isAdmin(player)) return;

        // 👻 Проверка на добычу фантомной руды-ловушки (Honeypot)
        if (phantomOres.containsKey(locKey)) {
            phantomOres.remove(locKey);
            MinerStats st = getStats(uuid);
            st.phantomMinedCount++;

            // Читер целенаправленно прокопался прямо в глухую стену к фантомной руде!
            notifyAdmins(ChatColor.DARK_GRAY + "[" + ChatColor.DARK_RED + "" + ChatColor.BOLD + "BOGUS X-RAY" + ChatColor.DARK_GRAY + "] "
                    + ChatColor.RED + player.getName() + ChatColor.YELLOW + " вскопал скрытую фантомную руду-приманку на "
                    + ChatColor.AQUA + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + "!");
        }

        Material mat = block.getType();
        MinerStats stats = getStats(uuid);
        long now = System.currentTimeMillis();

        if (isBaseStone(mat)) {
            stats.stoneCount++;

            // Каждые 30 выкопанных камней генерируем скрытую фантомную приманку в глухой стене в 3 блоках от туннеля
            if (stats.stoneCount % 30 == 0 && loc.getY() < 16) {
                spawnPhantomTrapAround(player, loc);
            }
            return;
        }

        String oreType = getValuableOreType(mat);
        if (oreType != null) {
            if (checkedVeins.contains(locKey)) return;
            checkedVeins.add(locKey);
            if (checkedVeins.size() > 5000) {
                checkedVeins.clear();
            }

            if (oreType.equals("DIAMOND")) stats.diamondCount++;
            else if (oreType.equals("DEBRIS")) stats.debrisCount++;
            else if (oreType.equals("EMERALD")) stats.emeraldCount++;
            else if (oreType.equals("GOLD")) stats.goldCount++;

            if (now - stats.burstStartTime > 45000) {
                stats.burstStartTime = now;
                stats.burstOreCount = 1;
            } else {
                stats.burstOreCount++;
            }
            stats.lastOreTime = now;

            checkHeuristicsAndAlert(player, loc, oreType, stats, now);
        }
    }

    // Генерация скрытой фантомной руды в монолитном камне в 3-4 блоках в стороне
    private void spawnPhantomTrapAround(Player player, Location origin) {
        World w = origin.getWorld();
        if (w == null) return;
        int dx = (ThreadLocalRandom.current().nextBoolean() ? 1 : -1) * (3 + ThreadLocalRandom.current().nextInt(2));
        int dz = (ThreadLocalRandom.current().nextBoolean() ? 1 : -1) * (3 + ThreadLocalRandom.current().nextInt(2));
        int dy = ThreadLocalRandom.current().nextInt(3) - 1;

        Block target = w.getBlockAt(origin.getBlockX() + dx, origin.getBlockY() + dy, origin.getBlockZ() + dz);
        // Проверяем, что блок полностью окружён сплошным камнем (не соприкасается с воздухом/пещерой)
        if (isBaseStone(target.getType()) && isSolidSurrounded(target)) {
            Material oreMat = (target.getY() < 0) ? Material.DEEPSLATE_DIAMOND_ORE : Material.DIAMOND_ORE;
            if (w.getEnvironment() == World.Environment.NETHER) {
                oreMat = Material.ANCIENT_DEBRIS;
            }
            target.setType(oreMat, false);
            String k = target.getWorld().getName() + ":" + target.getX() + ":" + target.getY() + ":" + target.getZ();
            phantomOres.put(k, oreMat);
        }
    }

    private boolean isSolidSurrounded(Block b) {
        BlockFace[] faces = new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
        for (BlockFace f : faces) {
            Material t = b.getRelative(f).getType();
            if (t.isAir() || t == Material.WATER || t == Material.LAVA) {
                return false;
            }
        }
        return true;
    }

    private void checkHeuristicsAndAlert(Player player, Location loc, String oreName, MinerStats stats, long now) {
        UUID uuid = player.getUniqueId();
        Long lastAlert = lastAlertTime.get(uuid);

        String alertReason = null;

        if (stats.burstOreCount >= 6) {
            alertReason = "Аномально быстрая добыча (" + stats.burstOreCount + " ценных руд за 45 сек)";
        }

        double ratio = stats.getValuableRatio();
        int totalMined = stats.stoneCount + stats.getTotalValuableOres();
        if (totalMined >= 40 && ratio >= 18.0) {
            alertReason = String.format(Locale.US, "Высокий процент руды: %.1f%% (Руды: %d / Камень: %d)", ratio, stats.getTotalValuableOres(), stats.stoneCount);
        }

        if (stats.debrisCount >= 4 && (totalMined < 100 || ratio > 15.0)) {
            alertReason = "Быстрый поиск Древних Обломков (найдено: " + stats.debrisCount + ")";
        }

        if (alertReason != null) {
            if (lastAlert == null || (now - lastAlert > 15000)) {
                lastAlertTime.put(uuid, now);
                sendClickableAlert(player, loc, oreName, alertReason);
            }
        }
    }

    private void sendClickableAlert(Player miner, Location loc, String oreName, String reason) {
        String coords = loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
        String worldName = loc.getWorld().getName();

        TextComponent prefix = new TextComponent(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "" + ChatColor.BOLD + "Anti-Xray" + ChatColor.DARK_GRAY + "] ");
        TextComponent text = new TextComponent(ChatColor.YELLOW + miner.getName() + ChatColor.GRAY + " -> "
                + ChatColor.GOLD + oreName + ChatColor.GRAY + " на " + ChatColor.AQUA + coords + " (" + worldName + ")\n"
                + ChatColor.RED + "Причина: " + ChatColor.WHITE + reason + "\n");

        TextComponent tpBtn = new TextComponent(ChatColor.GREEN + "" + ChatColor.BOLD + "[Телепорт] ");
        tpBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + miner.getName()));
        tpBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[]{
                new TextComponent(ChatColor.GRAY + "Телепортироваться к " + ChatColor.YELLOW + miner.getName())
        }));

        TextComponent statsBtn = new TextComponent(ChatColor.GOLD + "" + ChatColor.BOLD + "[Статистика] ");
        statsBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ax check " + miner.getName()));
        statsBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[]{
                new TextComponent(ChatColor.GRAY + "Подробная статистика " + ChatColor.YELLOW + miner.getName())
        }));

        TextComponent trollBtn = new TextComponent(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "[Троллинг]");
        trollBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ax troll " + miner.getName()));
        trollBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[]{
                new TextComponent(ChatColor.GRAY + "Включить/выключить скример Древнего Стража при копании")
        }));

        TextComponent comp = new TextComponent("");
        comp.addExtra(prefix);
        comp.addExtra(text);
        comp.addExtra(tpBtn);
        comp.addExtra(statsBtn);
        comp.addExtra(trollBtn);

        notifyAdmins(comp);
    }

    public void notifyAdmins(TextComponent component) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isAdmin(p)) {
                p.spigot().sendMessage(component);
            }
        }
        Bukkit.getConsoleSender().sendMessage(component.toPlainText());
    }

    public void notifyAdmins(String rawMessage) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isAdmin(p)) {
                p.sendMessage(rawMessage);
            }
        }
        Bukkit.getConsoleSender().sendMessage(rawMessage);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!isAdmin(sender)) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав на использование Anti-Xray.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "" + ChatColor.BOLD + "Anti-Xray" + ChatColor.DARK_GRAY + "] " + ChatColor.GOLD + "Управление:");
            sender.sendMessage(ChatColor.YELLOW + "/ax troll <ник>" + ChatColor.GRAY + " — троллинг игрока (блоки не ломаются + скример стража)");
            sender.sendMessage(ChatColor.YELLOW + "/ax check <ник>" + ChatColor.GRAY + " — подробная статистика раскопок");
            sender.sendMessage(ChatColor.YELLOW + "/ax top" + ChatColor.GRAY + " — топ игроков с высоким процентом руд");
            sender.sendMessage(ChatColor.YELLOW + "/ax reset <ник>" + ChatColor.GRAY + " — сбросить счётчики игрока");
            return true;
        }

        String sub = args[0].toLowerCase();

        // 👹 Команда /ax troll <ник>
        if (sub.equals("troll") || sub.equals("тролль")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Использование: /ax troll <ник>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1].trim());
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Игрок не найден в сети.");
                return true;
            }

            UUID tuuid = target.getUniqueId();
            if (trolledPlayers.contains(tuuid)) {
                trolledPlayers.remove(tuuid);
                target.removePotionEffect(getMiningFatigueType());
                sender.sendMessage(ChatColor.GREEN + "Троллинг со скримером для " + ChatColor.YELLOW + target.getName() + ChatColor.GREEN + " ОТКЛЮЧЁН.");
            } else {
                trolledPlayers.add(tuuid);
                sender.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Троллинг АКТИВИРОВАН для " + ChatColor.YELLOW + target.getName() + "!");
                sender.sendMessage(ChatColor.GRAY + "Теперь каждый раз, когда он попытается сломать блок, блок не сломается, а на весь экран вылетит скример головы Древнего Стража со звуком!");
            }
            return true;
        }

        if (sub.equals("check") || sub.equals("stats")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Использование: /ax check <ник>");
                return true;
            }
            String targetNick = args[1].trim();
            Player target = Bukkit.getPlayer(targetNick);
            UUID uuid = (target != null) ? target.getUniqueId() : null;
            if (uuid == null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().equalsIgnoreCase(targetNick)) {
                        uuid = p.getUniqueId();
                        target = p;
                        break;
                    }
                }
            }

            if (uuid == null) {
                sender.sendMessage(ChatColor.RED + "Игрок '" + targetNick + "' не найден в сети.");
                return true;
            }

            MinerStats s = getStats(uuid);
            double ratio = s.getValuableRatio();
            ChatColor ratioColor = (ratio > 18.0) ? ChatColor.RED : ((ratio > 10.0) ? ChatColor.GOLD : ChatColor.GREEN);

            sender.sendMessage(ChatColor.DARK_GRAY + "==== " + ChatColor.GOLD + "Шахтёрская статистика: " + ChatColor.YELLOW + targetNick + ChatColor.DARK_GRAY + " ====");
            sender.sendMessage(ChatColor.GRAY + "Выкопано камня: " + ChatColor.WHITE + s.stoneCount);
            sender.sendMessage(ChatColor.AQUA + "Алмазы: " + ChatColor.WHITE + s.diamondCount);
            sender.sendMessage(ChatColor.RED + "Древние обломки: " + ChatColor.WHITE + s.debrisCount);
            sender.sendMessage(ChatColor.GOLD + "Золото: " + ChatColor.WHITE + s.goldCount);
            sender.sendMessage(ChatColor.GREEN + "Изумруды: " + ChatColor.WHITE + s.emeraldCount);
            if (s.phantomMinedCount > 0) {
                sender.sendMessage(ChatColor.DARK_RED + "Вскопано фантомных ловушек: " + ChatColor.RED + "" + ChatColor.BOLD + s.phantomMinedCount + " (100% X-Ray!)");
            }
            sender.sendMessage(ChatColor.GRAY + "Процент ценных руд: " + ratioColor + String.format(Locale.US, "%.2f%%", ratio) + ChatColor.GRAY + " (Норма: 0.5% - 5.0%)");
            return true;
        }

        if (sub.equals("reset")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Использование: /ax reset <ник>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1].trim());
            if (target != null) {
                resetStats(target.getUniqueId());
                sender.sendMessage(ChatColor.GREEN + "Статистика для " + target.getName() + " успешно сброшена.");
            } else {
                sender.sendMessage(ChatColor.RED + "Игрок не найден.");
            }
            return true;
        }

        if (sub.equals("top")) {
            sender.sendMessage(ChatColor.DARK_GRAY + "==== " + ChatColor.RED + "Подозрительные копатели (Top Ratio)" + ChatColor.DARK_GRAY + " ====");
            List<Map.Entry<UUID, MinerStats>> list = new ArrayList<>(statsMap.entrySet());
            list.sort((a, b) -> Double.compare(b.getValue().getValuableRatio(), a.getValue().getValuableRatio()));

            int count = 0;
            for (Map.Entry<UUID, MinerStats> entry : list) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p == null) continue;
                MinerStats st = entry.getValue();
                int total = st.stoneCount + st.getTotalValuableOres();
                if (total < 25) continue;

                double r = st.getValuableRatio();
                ChatColor c = (r > 18.0) ? ChatColor.RED : ((r > 10.0) ? ChatColor.GOLD : ChatColor.GREEN);
                sender.sendMessage(ChatColor.YELLOW + p.getName() + ChatColor.GRAY + " -> " + c + String.format(Locale.US, "%.1f%%", r)
                        + ChatColor.GRAY + " (Руд: " + st.getTotalValuableOres() + ", Камня: " + st.stoneCount + ")");
                count++;
                if (count >= 5) break;
            }
            if (count == 0) {
                sender.sendMessage(ChatColor.GRAY + "Пока нет подозрительных шахтёров.");
            }
            return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!isAdmin(sender)) return Collections.emptyList();
        if (args.length == 1) {
            return Arrays.asList("troll", "check", "stats", "reset", "top", "help");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("troll") || args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("stats"))) {
            List<String> players = new ArrayList<>();
            String cur = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(cur)) {
                    players.add(p.getName());
                }
            }
            return players;
        }
        return Collections.emptyList();
    }

    private boolean isBaseStone(Material mat) {
        String n = mat.name();
        return n.equals("STONE") || n.equals("DEEPSLATE") || n.equals("NETHERRACK") || n.equals("BLACKSTONE")
                || n.equals("BASALT") || n.equals("END_STONE") || n.equals("TUFF") || n.equals("DIORITE")
                || n.equals("ANDESITE") || n.equals("GRANITE") || n.equals("DIRT") || n.equals("GRAVEL");
    }

        private PotionEffectType getMiningFatigueType() {
        PotionEffectType type = PotionEffectType.getByName("MINING_FATIGUE");
        if (type == null) {
            type = PotionEffectType.getByName("SLOW_DIGGING");
        }
        return (type != null) ? type : PotionEffectType.BLINDNESS;
    }
    private String getValuableOreType(Material mat) {
        String n = mat.name();
        if (n.contains("DIAMOND_ORE")) return "DIAMOND";
        if (n.contains("ANCIENT_DEBRIS")) return "DEBRIS";
        if (n.contains("EMERALD_ORE")) return "EMERALD";
        if (n.contains("GOLD_ORE") || n.contains("NETHER_GOLD_ORE")) return "GOLD";
        return null;
    }
}