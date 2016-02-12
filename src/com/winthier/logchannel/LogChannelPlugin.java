package com.winthier.logchannel;

import com.dthielke.herochat.Channel;
import com.dthielke.herochat.Chatter;
import com.dthielke.herochat.Herochat;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;

public class LogChannelPlugin extends JavaPlugin implements Listener {
    private static final String LOG_PERMISSION = "logchannel.log";
    private String deathMessage, joinMessage, leaveMessage;
    private boolean mute;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        loadConfiguration();
        getServer().getPluginManager().registerEvents(this, this);
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
        }
        return false;
    }

    public void loadConfiguration() {
        deathMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("DeathMessage"));
        joinMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("JoinMessage"));
        leaveMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("LeaveMessage"));
        mute = getConfig().getBoolean("Mute");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        String msg = ChatColor.stripColor(event.getDeathMessage());
        event.setDeathMessage(null);
        getLogger().info(msg);
        if (mute || !event.getEntity().hasPermission("logchannel.log")) return;
        if (msg != null) {
            msg = deathMessage.replaceAll(Pattern.quote("{message}"), Matcher.quoteReplacement(msg));
            logToChannel(event.getEntity(), msg, event.getEntity().getKiller() != null);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);
        // if (mute || !event.getPlayer().hasPermission("logchannel.log")) return;
        // String msg = joinMessage.replaceAll(Pattern.quote("{player}"), Matcher.quoteReplacement(event.getPlayer().getName()));
        // logToChannel(event.getPlayer(), msg);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.setQuitMessage(null);
        // if (mute || !event.getPlayer().hasPermission("logchannel.log")) return;
        // String msg = leaveMessage.replaceAll(Pattern.quote("{player}"), Matcher.quoteReplacement(event.getPlayer().getName()));
        // logToChannel(event.getPlayer(), msg);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerKick(PlayerKickEvent event) {
        event.setLeaveMessage(null);
    }

    public void logToChannel(Player cause, String message) {
        logToChannel(cause, message, false);
    }

    public void logToChannel(Player cause, String message, boolean local) {
        String channelName = getConfig().getString("ChannelName");
        Channel channel = Herochat.getChannelManager().getChannel(channelName);
        if (channel == null) {
            getLogger().warning("Channel not found: " + channelName);
            return;
        }
        for (Chatter chatter : channel.getMembers()) {
            Player player = chatter.getPlayer();
            if (player == null) continue;
            if (cause != null && chatter.getIgnores().contains(cause.getName().toLowerCase())) continue;
            if (local) {
                Location loc1 = cause.getLocation();
                Location loc2 = player.getLocation();
                if (!loc1.getWorld().equals(loc2.getWorld())) continue;
                if (loc1.distanceSquared(loc2) > 128.0*128.0) continue;
            }
            player.sendMessage(message);
        }
    }
}
