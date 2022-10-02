package com.lunarclient.bukkitapi.cooldown;

import com.google.common.base.Preconditions;
import com.lunarclient.bukkitapi.LCPacketWrapper;
import com.lunarclient.bukkitapi.LunarClientAPI;
import com.lunarclient.bukkitapi.nethandler.client.LCPacketCooldown;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Data
public class LCCooldown implements LCPacketWrapper<LCPacketCooldown> {

    private final String name;
    private final long millis;
    private Material item;
    private final int itemId;
    // The packet that will be sent to the Lunar Client player
    // Because of @Data, we don't need a getter.
    private final LCPacketCooldown packet;


    /**
     * Used to create a persisting cooldown that can be sent to many players.
     *
     * @param name   A unique name that is not null
     * @param millis The duration for the cooldown in milliseconds, that is greater (or equal to reset) to 0.
     * @param itemId The ID of the material that will be the icon
     */
    public LCCooldown(String name, long millis, int itemId) {
        Preconditions.checkArgument(millis > 0, "Cooldown must have a valid time > 0.");
        this.name = Preconditions.checkNotNull(name, "Cooldown Name cannot be null.");
        this.millis = millis;
        this.item = LunarClientAPI.MATERIALS[itemId];
        this.itemId = itemId;
        packet = new LCPacketCooldown(name, millis, itemId);
    }

    /**
     * Used to create a persisting cooldown that can be sent to many players.
     *
     * @param name   A unique name that is not null
     * @param millis The duration for the cooldown in milliseconds, that is greater (or equal to reset) to 0.
     * @param item   The icon that will be displayed in Lunar Client.
     */
    public LCCooldown(String name, long millis, Material item) {
        this(name, millis, convertMaterialToId(item));
    }

    /**
     * Simply creates a LCCooldown with seconds instead of milliseconds.
     *
     * @param name    A unique name that is not null
     * @param seconds the duration for the cooldown in seconds, that is greater (or equal to reset) to 0.
     * @param item    The icon that will be displayed in Lunar Client.
     */
    public LCCooldown(String name, int seconds, Material item) {
        this(name, seconds * 1000L, item);
    }

    /**
     * Simply creates a LCCooldown with a variable amount of time
     *
     * @param name A unique name that is not null
     * @param time A duration greater than 0 milliseconds.
     * @param unit A unit of measurement to put the cooldown in
     * @param item The icon that will be displayed in Lunar Client.
     */
    public LCCooldown(String name, int time, TimeUnit unit, Material item) {
        this(name, unit.toMillis(time), item);
    }


    /**
     * Clear the cooldown from a player before it naturally expires.
     *
     * @param player The player to clear the cooldown for.
     */
    public void clear(Player player) {
        send(player, new LCPacketCooldown(name, 0, itemId));
    }

    /**
     * Converts a Material into a Lunar usable ID.
     *
     * @param material The material to convert.
     * @return The ID of the material.
     */
    public static int convertMaterialToId(Material material) {
        List<Material> materialList = Arrays.asList(LunarClientAPI.MATERIALS);
        return materialList.indexOf(material);
    }
}