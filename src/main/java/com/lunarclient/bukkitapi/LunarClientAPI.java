package com.lunarclient.bukkitapi;

import com.lunarclient.bukkitapi.event.LCPacketReceivedEvent;
import com.lunarclient.bukkitapi.event.LCPacketSentEvent;
import com.lunarclient.bukkitapi.event.LCPlayerUnregisterEvent;
import com.lunarclient.bukkitapi.listener.LunarClientLoginListener;
import com.lunarclient.bukkitapi.nethandler.LCPacket;
import com.lunarclient.bukkitapi.nethandler.client.*;
import com.lunarclient.bukkitapi.nethandler.server.LCNetHandlerServer;
import com.lunarclient.bukkitapi.nethandler.shared.LCPacketWaypointAdd;
import com.lunarclient.bukkitapi.nethandler.shared.LCPacketWaypointRemove;
import com.lunarclient.bukkitapi.object.LCWaypoint;
import com.lunarclient.bukkitapi.object.StaffModule;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class LunarClientAPI extends JavaPlugin implements Listener {

    public static final String MESSAGE_CHANNEL = "lunarclient:pm";
    public static final ChatColor[] CHAT_COLORS = ChatColor.values();
    public static final Material[] MATERIALS = Material.values();

    @Getter
    private static LunarClientAPI instance;

    @Setter
    private LCNetHandlerServer netHandlerServer = new LunarClientDefaultNetHandler();
    private final Set<UUID> playersRunningLunarClient = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> playersNotRegistered = new HashSet<>();
    private final Map<UUID, List<LCPacket>> packetQueue = new HashMap<>();
    private final Map<UUID, Function<World, String>> worldIdentifiers = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        this.registerPluginChannel(MESSAGE_CHANNEL);
        this.getServer().getPluginManager().registerEvents(new LunarClientLoginListener(this), this);
    }

    /**
     * Registers the bukkit plugin channel based on configuration of allowed players
     *
     * @param bukkitChannel The incoming plugin channel based on Minecraft Version.
     */
    private void registerPluginChannel(final String bukkitChannel) {
        final Messenger messenger = getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(this, bukkitChannel);
        messenger.registerIncomingPluginChannel(this, bukkitChannel, (channel, player, bytes) -> {
            final LCPacket packet = LCPacket.handle(bytes, player);
            this.getServer().getPluginManager().callEvent(new LCPacketReceivedEvent(player, packet));
            packet.process(netHandlerServer);
        });
    }

    /**
     * Called when the player has been online for 2 seconds without
     * sending any sort of registration stating they're on lunar client.
     * <p>
     * This will remove all queued packets that we've saved, and prevent further packets
     * from being stored for this player.
     * <p>
     * Used in {@link LunarClientLoginListener}. Do not use unless you are certain you need this.
     *
     * @param player The player that has been online for at least 2 seconds.
     */
    public void failPlayerRegister(final Player player) {
        this.playersNotRegistered.add(player.getUniqueId());
        this.packetQueue.remove(player.getUniqueId());
    }

    /**
     * Register the player as a Lunar Client player based off of their
     * bukkit plugin channel registration.
     * <p>
     * NOTE: Do NOT use this as an anticheat method as this can be spoofed,
     * or changed. This is intended for cosmetic use only!
     *
     * @param player The player registering as a Lunar Client user.
     */
    public void registerPlayer(final Player player) {
        this.playersNotRegistered.remove(player.getUniqueId());
        this.playersRunningLunarClient.add(player.getUniqueId());
        if (this.packetQueue.containsKey(player.getUniqueId())) {
            for (LCPacket packet : packetQueue.get(player.getUniqueId())) {
                this.sendPacket(player, packet);
            }

            this.packetQueue.remove(player.getUniqueId());
        }
    }

    /**
     * Unregisters a player as a LunarClient player. This can be done by the client
     * or by quitting. Either way we remove the player from the set, if they did not
     * quit we don't want to remove them from NotRegistered players as we would
     * continue to hold packets for them.
     * <p>
     * If they did not quit, we don't want to hold packets for them forever so we
     * add them to the not registered players set.
     *
     * @param player The player who has unregistered themself from the plugin channel.
     * @param quit   A {@link Boolean} value of weather the player quit or simply unregistered from the channel.
     */
    public void unregisterPlayer(final Player player, boolean quit) {
        this.playersRunningLunarClient.remove(player.getUniqueId());
        if (quit) {
            this.playersNotRegistered.remove(player.getUniqueId());
        } else {
            this.playersNotRegistered.add(player.getUniqueId());
            this.getServer().getPluginManager().callEvent(new LCPlayerUnregisterEvent(player));
        }
    }

    /**
     * Checks if the player is currently running lunar client.
     *
     * @param player {@link Player} suspect of using Lunar Client.
     * @return The {@link Boolean} value of weather the online player is currently using Lunar Client.
     */
    public boolean isRunningLunarClient(final Player player) {
        return isRunningLunarClient(player.getUniqueId());
    }

    /**
     * Checks if a user is currently running LunarClient on the server.
     *
     * @param playerUuid The ID of the suspect LunarClient user.
     * @return The {@link Boolean} value of weather the player is currently running Lunar Client.
     */
    public boolean isRunningLunarClient(UUID playerUuid) {
        return playersRunningLunarClient.contains(playerUuid);
    }

    /**
     * Gets an immutable set of all the bukkit {@link Player} currently running lunar client.
     * <p>
     * NOTE: This would only be ideal for displaying an object of the player, for computation
     * it would probably be better to use the raw UUID of the players.
     *
     * @return An unmodifiableSet of the players currently running lunar client.
     */
    public Set<Player> getPlayersRunningLunarClient() {
        return Collections.unmodifiableSet(playersRunningLunarClient.stream().map(Bukkit::getPlayer).collect(Collectors.toSet()));
    }

    /**
     * Force set a specific {@link StaffModule} for a specific player.
     * Useful for enabling only a few {@link StaffModule}
     *
     * @param player The player receiving the staff modules.
     * @param module The staff module to set to a state.
     * @param state  The new state of the StaffModule.
     */
    public void setStaffModuleState(final Player player, StaffModule module, boolean state) {
        this.sendPacket(player, new LCPacketStaffModState(module.name(), state));
    }

    /**
     * Gives a player ALL staff modules.
     * Not recommended, a better path would to explicitly give a player each module.
     *
     * @param player The player to receive the enabled staff modules.
     */
    public void giveAllStaffModules(final Player player) {
        for (StaffModule module : StaffModule.values()) {
            this.setStaffModuleState(player, module, true);
        }
    }

    /**
     * Disables ALL staff modules for a specific player
     * regardless of if they are enabled.
     *
     * @param player The player receiving the new staff module state.
     */
    public void disableAllStaffModules(final Player player) {
        for (StaffModule module : StaffModule.values()) {
            this.setStaffModuleState(player, module, false);
        }
    }

    /**
     * Sends a validated teammate object to the player.
     * Tells the player of all its known team mates, ensure they're both online and in the world.
     *
     * @param player The player to receive the team mates
     * @param packet The teammates to send to the player.
     */
    public void sendTeammates(Player player, LCPacketTeammates packet) {
        this.validatePlayers(player, packet);
        this.sendPacket(player, packet);
    }

    /**
     * Validate teammates for the player (sendingTo).
     * Ensures the teammates are online and in the same world before sending the location.
     * Used above in sendTeammates.
     *
     * @param sendingTo The player we're sending teammates to.
     * @param packet    The teammates packet to verify.
     */
    private void validatePlayers(final Player sendingTo, LCPacketTeammates packet) {
        for (Map.Entry<UUID, Map<String, Double>> entry : packet.getPlayers().entrySet()) {
            final UUID uuid = entry.getKey();
            final Player player = Bukkit.getPlayer(uuid);

            if (player.getWorld().equals(sendingTo.getWorld())) {
                packet.getPlayers().remove(uuid);
            }
        }
    }

    /**
     * Create a hologram for a player on the server.
     *
     * @param player   The observer of the new hologram.
     * @param id       The randomly generated UUID for the hologram. This will need to be saved for other hologram actions.
     * @param position The location (x, y, z) of where the hologram will be placed in the world.
     * @param lines    The lines of the hologram to be sent to the player.
     */
    public void addHologram(Player player, UUID id, Vector position, String[] lines) {
        this.sendPacket(player, new LCPacketHologram(id, position.getX(), position.getY(), position.getZ(), Arrays.asList(lines)));
    }

    /**
     * Update the lines of a previously added hologram for a specific player.
     *
     * @param player The observer of the new hologram lines.
     * @param id     The ID of the previously placed hologram.
     * @param lines  The new lines to show to the player.
     */
    public void updateHologram(Player player, UUID id, String[] lines) {
        this.sendPacket(player, new LCPacketHologramUpdate(id, Arrays.asList(lines)));
    }

    /**
     * Remove a previously set hologram for a specific player.
     *
     * @param player The player to remove the hologram for.
     * @param id     The ID of the previously created hologram.
     */
    public void removeHologram(Player player, UUID id) {
        this.sendPacket(player, new LCPacketHologramRemove(id));
    }

    /**
     * Override the normal (bukkit) nametag with lunar client nametags.
     * This supports multiple lines, so index 0 will be bottom of the nametags.
     *
     * @param target  The player whos nametag will be affected for the viewer.
     * @param nametag The list of nametags that will be sent to the viewer. Supports color codes.
     * @param viewer  The observer who will see the targets nametag as a lunar client nametag.
     */
    public void overrideNametag(Player target, List<String> nametag, Player viewer) {
        this.sendPacket(viewer, new LCPacketNametagsOverride(target.getUniqueId(), nametag));
    }

    /**
     * Reset anything done to the nametag.
     * This will reset hideNametag or any
     * other action done to the nametag.
     *
     * @param target The player's nametag that will be reset for the viewer.
     * @param viewer The observer who will see the targets nametag as normal (bukkit).
     */
    public void resetNametag(Player target, Player viewer) {
        this.sendPacket(viewer, new LCPacketNametagsOverride(target.getUniqueId(), null));
    }

    /**
     * Hide the target's username from the viewer.
     *
     * @param target This player's nametag will be hidden from the viewer.
     * @param viewer The observer who will hide the targets nametag.
     */
    public void hideNametag(Player target, Player viewer) {
        this.sendPacket(viewer, new LCPacketNametagsOverride(target.getUniqueId(), Collections.emptyList()));
    }

    /**
     * Gets the world identifier weather that is custom or
     * the worlds generated Id for sending waypoints to the client.
     *
     * @param world The world to get the identifier for
     * @return {@link String} of the set name of the world, or default to the worlds unique id.
     */
    public String getWorldIdentifier(World world) {
        final UUID worldIdentifier = world.getUID();

        return this.worldIdentifiers.containsKey(worldIdentifier)
                ? this.worldIdentifiers.get(worldIdentifier).apply(world)
                : worldIdentifier.toString();
    }

    /**
     * Registers a custom name for the world to be identified as on the client.
     * <p>
     * Not required, if not set it will default to the unquie id of the world.
     *
     * @param world      The bukkit object for the world.
     * @param identifier The new function identifier.
     */
    public void registerWorldIdentifier(World world, Function<World, String> identifier) {
        this.worldIdentifiers.put(world.getUID(), identifier);
    }

    /**
     * Send a waypoint to a lunarclient player.
     * <p>
     * Note: You will likely need to persist this object in order to remove it later.
     *
     * @param player   A player running lunar client.
     * @param waypoint A new waypoint object to send to the player.
     */
    public void sendWaypoint(Player player, LCWaypoint waypoint) {
        this.sendPacket(player, new LCPacketWaypointAdd(waypoint.getName(), waypoint.getWorld(), waypoint.getColor(), waypoint.getX(), waypoint.getY(), waypoint.getZ(), waypoint.isForced(), waypoint.isVisible()));
    }

    /**
     * Remove a waypoint that the server sent from a player.
     *
     * @param player   A player running lunar client.
     * @param waypoint A waypoint object that the server has previously sent.
     */
    public void removeWaypoint(Player player, LCWaypoint waypoint) {
        this.sendPacket(player, new LCPacketWaypointRemove(waypoint.getName(), waypoint.getWorld()));
    }

    /**
     * The most basic component of the Lunar Client API used
     * to send packets to the Lunar Client player.
     * Implementation with direct method calls is not recommended,
     * and will likely be very brittle in regards to functionality.
     * <p>
     * sendPacket will either send the packet immediately to the Lunar Client player
     * or if for some reason there is a delay in the connection, it will queue the packet for
     * consumption at a later date, when the player registers.
     *
     * @param player The bukkit representation of the {@link Player} to receive the packet.
     * @param packet The Lunar Client packet that should be sent to the Lunar Client player.
     * @return {@link Boolean} value of weather the packet was sent.
     */
    public boolean sendPacket(final Player player, LCPacket packet) {
        UUID playerId = player.getUniqueId();
        if (isRunningLunarClient(playerId)) {
            player.sendPluginMessage(this, MESSAGE_CHANNEL, LCPacket.getPacketData(packet));
            Bukkit.getPluginManager().callEvent(new LCPacketSentEvent(player, packet));
            return true;
        }

        // If the player hasn't been on for 2 seconds, but also
        // hasn't registered we hold on to hope they are just lagging
        // and so we save packets for them until they are proven not
        // lunar client players.
        // Either way, the packet failed to send (this time).

        if (!playersNotRegistered.contains(playerId)) {
            if (!packetQueue.containsKey(playerId)) {
                packetQueue.put(playerId, new ArrayList<>());
            }
            packetQueue.get(playerId).add(packet);
        }
        return false;
    }

}
