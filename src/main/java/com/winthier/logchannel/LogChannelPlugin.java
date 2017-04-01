package com.winthier.logchannel;

import com.winthier.chat.ChatPlugin;
import com.winthier.chat.channel.Channel;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class LogChannelPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    private static final String LOG_PERMISSION = "logchannel.log";
    private String deathMessage, joinMessage, leaveMessage;
    private boolean mute = false, muteDeaths = false;
    private Permission permission = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        loadConfiguration();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "LogChannel", this);
    }

    private Permission getPermission() {
        if (permission == null) {
            RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(Permission.class);
            if (permissionProvider != null) permission = permissionProvider.getProvider();
            if (permission == null) {
                getLogger().warning("Failed to setup Vault Permission");
            }
        }
        return permission;
    }

    private boolean hasPermission(UUID uuid, String permName) {
        Player player = getServer().getPlayer(uuid);
        if (player != null) return player.hasPermission(permName);
        Permission permission = getPermission();
        if (permission == null) return false;
        OfflinePlayer off = getServer().getOfflinePlayer(uuid);
        return permission.playerHas((String)null, off, permName);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String args[]) {
        if (args.length == 0) {
            return false;
        } else if ("Reload".equalsIgnoreCase(args[0]) && args.length == 1) {
            reloadConfig();
            loadConfiguration();
            sender.sendMessage("Configuration reloaded");
            return true;
        } else if ("Log".equalsIgnoreCase(args[0]) && args.length > 1) {
            StringBuilder sb = new StringBuilder(args[1]);
            for (int i = 2; i < args.length; ++i) sb.append(" ").append(args[i]);
            logToChannel(null, ChatColor.translateAlternateColorCodes('&', sb.toString()));
            return true;
        } else if ("Mute".equalsIgnoreCase(args[0]) && args.length == 1) {
            mute = !mute;
            sender.sendMessage("LogChannel " + (mute ? "muted" : "unmuted"));
            return true;
        }
        return false;
    }

    public void loadConfiguration() {
        deathMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("DeathMessage"));
        joinMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("JoinMessage"));
        leaveMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("LeaveMessage"));
        mute = getConfig().getBoolean("Mute", mute);
        muteDeaths = getConfig().getBoolean("MuteDeaths", muteDeaths);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        String msg = ChatColor.stripColor(event.getDeathMessage());
        event.setDeathMessage(null);
        getLogger().info(msg);
        if (mute || muteDeaths || !event.getEntity().hasPermission("logchannel.log")) return;
        if (msg != null) {
            msg = deathMessage.replace("{message}", msg);
            logToChannel(event.getEntity(), msg);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);
        // if (mute || !event.getPlayer().hasPermission("logchannel.log")) return;
        // String msg = joinMessage.replace("{player}", event.getPlayer().getName());
        // logToChannel(event.getPlayer(), msg);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.setQuitMessage(null);
        // if (mute || !event.getPlayer().hasPermission("logchannel.log")) return;
        // String msg = leaveMessage.replace("{player}", event.getPlayer().getName());
        // logToChannel(event.getPlayer(), msg);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerKick(PlayerKickEvent event) {
        event.setLeaveMessage(null);
    }

    public void logToChannel(Player cause, String message) {
        logToChannel(cause, message, false);
    }

    public void logToChannel(Player cause, String message, boolean local) {
        String channelName = getConfig().getString("ChannelName");
        Channel channel = ChatPlugin.getInstance().findChannel(channelName);
        if (channel == null) {
            getLogger().warning("Channel not found: " + channelName);
            return;
        }
        for (Player player : channel.getLocalMembers()) {
            if (player == null) continue;
            if (cause != null && ChatPlugin.getInstance().doesIgnore(player.getUniqueId(), cause.getUniqueId())) continue;
            if (local) {
                Location loc1 = cause.getLocation();
                Location loc2 = player.getLocation();
                if (!loc1.getWorld().equals(loc2.getWorld())) continue;
                if (loc1.distanceSquared(loc2) > 128.0*128.0) continue;
            }
            player.sendMessage(message);
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (mute) return;
        if (!channel.equals("LogChannel")) return;
        ByteArrayInputStream bis = new ByteArrayInputStream(message);
        DataInputStream in = new DataInputStream(bis);
        try {
            String msg = in.readUTF();
            UUID uuid = UUID.fromString(in.readUTF());
            String name = in.readUTF();
            // System.out.println("Received message "+msg+" "+uuid+" "+name+": "+hasPermission(uuid, "logchannel.log"));
            if (!hasPermission(uuid, "logchannel.log")) return;
            final String output;
            if (msg.equals("Join")) {
                output = joinMessage.replace("{player}", name);
            } else if (msg.equals("Leave")) {
                output = leaveMessage.replace("{player}", name);
            } else {
                getLogger().warning("Unknown message type: " + msg);
                return;
            }
            logToChannel(null, output, false);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
        }
    }
}
