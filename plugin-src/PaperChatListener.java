package ru.twoblocks.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class PaperChatListener implements Listener {
    private final TwoBlocksChat plugin;

    public PaperChatListener(TwoBlocksChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPaperChatLowest(AsyncChatEvent event) {
        handleChat(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPaperChatHighest(AsyncChatEvent event) {
        handleChat(event);
    }

    private void handleChat(AsyncChatEvent event) {
        try {
            Player player = event.getPlayer();
            if (player == null) return;

            if (plugin.checkAndEnforceChatRestriction(player)) {
                event.setCancelled(true);
                try {
                    event.viewers().clear();
                } catch (Throwable ignored) {}
                try {
                    event.message(Component.empty());
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }
}
