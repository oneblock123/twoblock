package ru.twoblocks.chat;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AntiXrayManager implements Listener {
    private final TwoBlocksChat plugin;

    public static class MinerStats {
        public int stoneCount = 0;
        public int diamondCount = 0;
        public int debrisCount = 0;
        public int goldCount = 0;
        public int emeraldCount = 0;
        public int ironCount = 0;
        public long lastOreTime = 0;
        public int burstOreCount = 0;
        public long burstStartTime = 0;

        public int getTotalOres() {
            return diamondCount + debrisCount + goldCount + emeraldCount;
        }

        public double getValuableRatio() {
            int total = stoneCount + getTotalOres();
            if (total < 30) return 0.0;
            return ((double) getTotalOres() / (double) total) * 100.0;
        }
    }

    private final Map<UUID, MinerStats> statsMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAlertTime = new ConcurrentHashMap<>();
    private final Set<String> checkedVeins = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public AntiXrayManager(TwoBlocksChat plugin) {
        this.plugin = plugin;
    }

    public MinerStats getStats(UUID uuid) {
        return statsMap.computeIfAbsent(uuid, k -> new MinerStats());
    }

    public void resetStats(UUID uuid) {
        statsMap.remove(uuid);
        lastAlertTime.remove(uuid);
    }

    public Map<UUID, MinerStats> getAllStats() {
        return statsMap;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String msg = event.getMessage().trim().toLowerCase();

        // 3. Защита от сид-кракеров: перехват /seed
        if (msg.equals("/seed") || msg.startsWith("/seed ") || msg.equals("/bukkit:seed") || msg.startsWith("/bukkit:seed ")) {
            if (!plugin.isNplAdmin(player)) {
                event.setCancelled(true);
                // Фейковый сид для обмана сид-кракера
                player.sendMessage(ChatColor.WHITE + "Seed: [" + ChatColor.GREEN + "749204810572918502" + ChatColor.WHITE + "]");
                
                // Уведомить админов о попытке узнать сид
                notifyAdmins(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "AntiXray" + ChatColor.DARK_GRAY + "] "
                        + ChatColor.YELLOW + player.getName() + ChatColor.GRAY + " запросил сид мира (/seed). Выдан фейковый сид.");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.isNplAdmin(player)) return; // Не проверяем доверенных админов

        Block block = event.getBlock();
        Material mat = block.getType();
        UUID uuid = player.getUniqueId();
        MinerStats stats = getStats(uuid);
        long now = System.currentTimeMillis();

        if (isBaseStone(mat)) {
            stats.stoneCount++;
            return;
        }

        String oreType = getValuableOreType(mat);
        if (oreType != null) {
            // Предотвращение накрутки от поломки одного и того же блока в жиле
            String locKey = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
            if (checkedVeins.contains(locKey)) return;
            checkedVeins.add(locKey);
            if (checkedVeins.size() > 5000) {
                checkedVeins.clear();
            }

            if (oreType.equals("DIAMOND")) stats.diamondCount++;
            else if (oreType.equals("DEBRIS")) stats.debrisCount++;
            else if (oreType.equals("EMERALD")) stats.emeraldCount++;
            else if (oreType.equals("GOLD")) stats.goldCount++;

            // Burst-проверка (аномальная скорость нахождения руды за короткое время)
            if (now - stats.burstStartTime > 45000) { // Окно 45 секунд
                stats.burstStartTime = now;
                stats.burstOreCount = 1;
            } else {
                stats.burstOreCount++;
            }
            stats.lastOreTime = now;

            // 4. Эвристический анализ
            checkHeuristicsAndAlert(player, block.getLocation(), oreType, stats, now);
        }
    }

    private void checkHeuristicsAndAlert(Player player, Location loc, String oreName, MinerStats stats, long now) {
        UUID uuid = player.getUniqueId();
        Long lastAlert = lastAlertTime.get(uuid);

        String alertReason = null;

        // Подозрение 1: Слишком много ценной руды за 45 секунд (быстрый туннель к рудам)
        if (stats.burstOreCount >= 6) {
            alertReason = "Аномально быстрая добыча (" + stats.burstOreCount + " ценных руд за 45 сек)";
        }

        // Подозрение 2: Аномально высокий процент руды к камню
        double ratio = stats.getValuableRatio();
        int totalMined = stats.stoneCount + stats.getTotalOres();
        if (totalMined >= 40 && ratio >= 18.0) {
            alertReason = String.format("Высокий процент руды: %.1f%% (Руды: %d / Камень: %d)", ratio, stats.getTotalOres(), stats.stoneCount);
        }

        // Подозрение 3: Быстрая добыча обломков незерита (Ancient Debris)
        if (stats.debrisCount >= 4 && (totalMined < 100 || ratio > 15.0)) {
            alertReason = "Быстрый поиск Древних Обломков (найдено: " + stats.debrisCount + ")";
        }

        if (alertReason != null) {
            // Кулдаун между алертами на одного игрока - 15 секунд, чтобы не спамить в чат
            if (lastAlert == null || (now - lastAlert > 15000)) {
                lastAlertTime.put(uuid, now);
                sendClickableAlert(player, loc, oreName, alertReason, ratio, totalMined);
            }
        }
    }

    private void sendClickableAlert(Player miner, Location loc, String oreName, String reason, double ratio, int totalMined) {
        String coords = loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
        String worldName = loc.getWorld().getName();

        TextComponent prefix = new TextComponent(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "" + ChatColor.BOLD + "Anti-Xray" + ChatColor.DARK_GRAY + "] ");
        TextComponent text = new TextComponent(ChatColor.YELLOW + miner.getName() + ChatColor.GRAY + " -> "
                + ChatColor.GOLD + oreName + ChatColor.GRAY + " на " + ChatColor.AQUA + coords + " (" + worldName + ")\n"
                + ChatColor.RED + "Причина: " + ChatColor.WHITE + reason + "\n");

        TextComponent tpBtn = new TextComponent(ChatColor.GREEN + "" + ChatColor.BOLD + "[Телепорт] ");
        tpBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + miner.getName()));
        tpBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[]{
                new TextComponent(ChatColor.GRAY + "Нажмите, чтобы телепортироваться к " + ChatColor.YELLOW + miner.getName())
        }));

        TextComponent statsBtn = new TextComponent(ChatColor.GOLD + "" + ChatColor.BOLD + "[Статистика] ");
        statsBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ax check " + miner.getName()));
        statsBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[]{
                new TextComponent(ChatColor.GRAY + "Посмотреть подробную статистику раскопок " + ChatColor.YELLOW + miner.getName())
        }));

        TextComponent comp = new TextComponent("");
        comp.addExtra(prefix);
        comp.addExtra(text);
        comp.addExtra(tpBtn);
        comp.addExtra(statsBtn);

        notifyAdmins(comp);
    }

    public void notifyAdmins(TextComponent component) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.isNplAdmin(p)) {
                p.spigot().sendMessage(component);
            }
        }
        Bukkit.getConsoleSender().sendMessage(component.toPlainText());
    }

    public void notifyAdmins(String rawMessage) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.isNplAdmin(p)) {
                p.sendMessage(rawMessage);
            }
        }
        Bukkit.getConsoleSender().sendMessage(rawMessage);
    }

    private boolean isBaseStone(Material mat) {
        String n = mat.name();
        return n.equals("STONE") || n.equals("DEEPSLATE") || n.equals("NETHERRACK") || n.equals("BLACKSTONE")
                || n.equals("BASALT") || n.equals("END_STONE") || n.equals("TUFF") || n.equals("DIORITE")
                || n.equals("ANDESITE") || n.equals("GRANITE") || n.equals("DIRT") || n.equals("GRAVEL");
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