package com.hpfxd.spectatorplus.paper.sync.handler;

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import com.hpfxd.spectatorplus.paper.SpectatorPlugin;
import com.hpfxd.spectatorplus.paper.sync.packet.ClientboundHealthSyncPacket;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class HealthSyncHandler implements Listener {
    private final SpectatorPlugin plugin;
    private final Object2FloatMap<UUID> playerHealth = new Object2FloatOpenHashMap<>();

    public HealthSyncHandler(SpectatorPlugin plugin) {
        this.plugin = plugin;

        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0, 0);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void tick() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            final float current = (float) player.getHealth();
            final float old = this.playerHealth.put(player.getUniqueId(), current);

            if (current != old) {
                this.plugin.getSyncController().sendPacket(
                        this.plugin.getSyncController().getSpectators(player, spectator -> true),
                        new ClientboundHealthSyncPacket(player.getUniqueId(), current)
                );
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStartSpectatingEntity(PlayerStartSpectatingEntityEvent event) {
        final Player spectator = event.getPlayer();

        if (event.getNewSpectatorTarget() instanceof final Player target) {
            this.plugin.getSyncController().sendPacket(spectator, new ClientboundHealthSyncPacket(target.getUniqueId(), (float) target.getHealth()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.playerHealth.removeFloat(event.getPlayer().getUniqueId());
    }
}
