package com.lunarclient.bukkitapi.listener;

import com.lunarclient.bukkitapi.LunarClientAPI;
import com.lunarclient.bukkitapi.event.LCPlayerRegisterEvent;
import com.lunarclient.bukkitapi.nethandler.client.LCPacketUpdateWorld;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

@RequiredArgsConstructor
public class LunarClientLoginListener implements Listener {

    private final LunarClientAPI lunarClientAPI;

    @EventHandler (priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(lunarClientAPI, () -> {
            if (!lunarClientAPI.isRunningLunarClient(player)) {
                lunarClientAPI.failPlayerRegister(player);
            }
        }, 2 * 20L);
    }

    @EventHandler
    public void onRegister(PlayerRegisterChannelEvent event) {
        if (!event.getChannel().equalsIgnoreCase(LunarClientAPI.MESSAGE_CHANNEL)) {
            return;
        }
        final Player player = event.getPlayer();

        this.lunarClientAPI.registerPlayer(player);
        this.lunarClientAPI.getServer().getPluginManager().callEvent(new LCPlayerRegisterEvent(event.getPlayer()));

        this.updateWorld(event.getPlayer());
    }

    @EventHandler
    public void onUnregister(PlayerUnregisterChannelEvent event) {
        if (event.getChannel().equalsIgnoreCase(LunarClientAPI.MESSAGE_CHANNEL)) {
            lunarClientAPI.unregisterPlayer(event.getPlayer(), false);
        }
    }

    @EventHandler
    public void onUnregister(PlayerQuitEvent event) {
        lunarClientAPI.unregisterPlayer(event.getPlayer(), true);
    }

    @EventHandler (priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        updateWorld(event.getPlayer());
    }

    private void updateWorld(Player player) {
        String worldIdentifier = lunarClientAPI.getWorldIdentifier(player.getWorld());

        lunarClientAPI.sendPacket(player, new LCPacketUpdateWorld(worldIdentifier));
    }
}
