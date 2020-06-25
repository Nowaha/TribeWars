package me.nowaha.tribewars;

import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.joda.time.Period;

import java.util.*;

public final class TribeWars extends JavaPlugin implements Listener {

    PlaceholderHandler placeholderHandler = null;

    HashMap<UUID, Long> playersInCombat = new HashMap<>();

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveResource("config.yml", false);

        if (!getConfig().isSet("nextwar")) {
            getConfig().set("nextwar", new Date().getTime() + (1000 * 60 * 60 * 24));
            saveConfig();
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null){
            placeholderHandler = new PlaceholderHandler(this);
            placeholderHandler.register();
        }

        getServer().getPluginManager().registerEvents(this, this);
        start();

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (playersInCombat.containsKey(player.getUniqueId()) && playersInCombat.get(player.getUniqueId()) - new Date().getTime() > 0) {
                    if (player.isFlying()) {
                        player.setFlying(false);
                        player.setAllowFlight(false);
                    }
                } else {
                    playersInCombat.remove(player.getUniqueId());
                }
            }
        }, 0, 1);
    }

    List<Integer> announcedTimes = new ArrayList<>();

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            if (isWarActive()) {
                e.getPlayer().sendMessage(getMessage("starting.started"));
                e.getPlayer().sendMessage(getMessage("starting.started2"));
            }
        }, 10);
    }

    public boolean isWarActive() {
        return getConfig().getLong("nextwar", Integer.MAX_VALUE) <= System.currentTimeMillis();
    }

    public ChatColor getTribeColor(String tribe) {
        if (tribe.equalsIgnoreCase("blue")) {
            return ChatColor.BLUE;
        } else if (tribe.equalsIgnoreCase("red")) {
            return ChatColor.RED;
        } else {
            return ChatColor.GRAY;
        }
    }

    void start() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (getConfig().isSet("warend")) {
                if (getConfig().getLong("warend") < System.currentTimeMillis()) {
                    // War should end!
                    reloadConfig();
                    getConfig().set("warend", null);
                    long nextWar = new Date().getTime() + (1000 * 60 * 60 * (24 + new Random().nextInt(49)));
                    getConfig().set("nextwar", (long) nextWar);



                    Bukkit.broadcastMessage(getMessage("ending.ended.message"));
                    if (getKills("red") > getKills("blue")) {
                        Bukkit.broadcastMessage(getMessage("ending.ended.redwon"));
                        getConfig().set("tribes.red.totalvictories", getConfig().getInt("tribes.red.totalvictories", 0) + 1);

                        for (Player player : Bukkit.getOnlinePlayers()) {
                            String tribe = getPlayerTribe(player.getUniqueId());
                            if (tribe == null) continue;

                            if (tribe.equalsIgnoreCase("red")) {
                                for (String command : getConfig().getString("wincommands.red", "").split("\\>")) {
                                    getServer().dispatchCommand(getServer().getConsoleSender(), command);
                                }
                            }
                        }
                    } else if (getKills("red") < getKills("blue")) {
                        Bukkit.broadcastMessage(getMessage("ending.ended.bluewon"));
                        getConfig().set("tribes.blue.totalvictories", getConfig().getInt("tribes.blue.totalvictories", 0) + 1);

                        for (Player player : Bukkit.getOnlinePlayers()) {
                            String tribe = getPlayerTribe(player.getUniqueId());
                            if (tribe == null) continue;

                            if (tribe.equalsIgnoreCase("blue")) {
                                for (String command : getConfig().getString("wincommands.blue", "").split("\\>")) {
                                    getServer().dispatchCommand(getServer().getConsoleSender(), command);
                                }
                            }
                        }
                    } else {
                        Bukkit.broadcastMessage(getMessage("ending.ended.tie"));
                    }

                    for (Player player :
                            Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 10, 0);
                        player.showTitle(new ComponentBuilder("§cWAR HAS ENDED!").create(), new ComponentBuilder("The fight to be the best tribe is over.").create(), 20, 100, 20);
                        String tribe = getPlayerTribe(player.getUniqueId());
                        if (tribe != null) {
                            player.sendMessage(getTribeColor(tribe) + "§lYour war stats:");
                            player.sendMessage(getTribeColor(tribe) + "§l> §e" + getKills(player.getUniqueId()) + " kills");
                            player.sendMessage(getTribeColor(tribe) + "§l> §e" + getDeaths(player.getUniqueId()) + " deaths");
                        } else {
                            player.sendMessage("§7§lYou were neutral and did not participate in the war.");
                        }
                    }

                    try {
                        for (String key :
                                getConfig().getConfigurationSection("players").getKeys(false)) {
                            getConfig().set("players." + key + ".currentkills", null);
                            getConfig().set("players." + key + ".currentdeaths", null);
                        }
                    } catch (NullPointerException ignored) {};

                    getConfig().set("tribes.red.currentkills", null);
                    getConfig().set("tribes.red.currentdeaths", null);

                    getConfig().set("tribes.blue.currentkills", null);
                    getConfig().set("tribes.blue.currentdeaths", null);

                    saveConfig();
                    
                    Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
                        Bukkit.broadcastMessage("§eNext war at §f" + new Date(getConfig().getLong("nextwar")).toGMTString() + "§e.");
                        Period period2 = new Period(nextWar - System.currentTimeMillis());
                        Integer days = (int)Math.floor(period2.getHours() / 24);
                        Integer hours = (period2.getHours() - (days * 24));
                        Bukkit.broadcastMessage("§eTime until next war: §f" + (days > 0 ? days + " days, " : "") + (hours > 0 ? hours + " hours, " : "") + (period2.getMinutes() > 0 ? period2.getMinutes() + " minutes, " : "") + period2.getSeconds() + " seconds§e.");
                    }, 200);

                    getServer().dispatchCommand(getServer().getConsoleSender(), "rg flag __global__ pvp -w test7 deny");
                    getServer().dispatchCommand(getServer().getConsoleSender(), "rg flag __global__ pvp -w test7_end deny");
                }
                return;
            }
            Period period = new Period(getConfig().getLong("nextwar") - System.currentTimeMillis());

            if (period.getHours() <= 0 && period.getMinutes() <= 0 && period.getSeconds() <= 0 && !announcedTimes.contains(200)) {
                announcedTimes.add(200);
                Bukkit.broadcastMessage(getMessage("starting.started"));
                Bukkit.broadcastMessage(getMessage("starting.started2"));
                for (Player player :
                        Bukkit.getOnlinePlayers()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 10, 0);
                    player.showTitle(new ComponentBuilder("§cWAR HAS STARTED!").create(), new ComponentBuilder("If you're not neutral, fight and gather kills to win this war.").create(), 20, 300, 20);
                }

                getServer().dispatchCommand(getServer().getConsoleSender(), "rg flag __global__ pvp -w test7 allow");
                getServer().dispatchCommand(getServer().getConsoleSender(), "rg flag __global__ pvp -w test7_end allow");

                reloadConfig();
                getConfig().set("warend", System.currentTimeMillis() + (1000 * 60 * 60 * 24));
                Bukkit.broadcastMessage(getMessage("ending.hr24"));
                saveConfig();
            } else if (period.getHours() < 6 && !announcedTimes.contains(6)) {
                announcedTimes.add(6);
                Bukkit.broadcastMessage(getMessage("starting.hr6"));
            } else if (period.getHours() < 3 && !announcedTimes.contains(3)) {
                announcedTimes.add(3);
                Bukkit.broadcastMessage(getMessage("starting.hr3"));
            } else if (period.getHours() < 1 && !announcedTimes.contains(1)) {
                announcedTimes.add(1);
                Bukkit.broadcastMessage(getMessage("starting.hr1"));
            } else if (period.getMinutes() < 30 && announcedTimes.contains(1) && !announcedTimes.contains(30)) {
                announcedTimes.add(30);
                Bukkit.broadcastMessage(getMessage("starting.min30"));
            } else if (period.getMinutes() < 5 && announcedTimes.contains(1) && !announcedTimes.contains(5)) {
                announcedTimes.add(5);
                Bukkit.broadcastMessage(getMessage("starting.min5"));
            } else if (period.getHours() < 12 && !announcedTimes.contains(12)) {
                announcedTimes.add(12);
                Bukkit.broadcastMessage(getMessage("starting.hr12"));
            }
        }, 100, 20);
    }

    String getPlayerTribe(UUID uuid) {
        return getConfig().getString("players." + uuid.toString() + ".tribe", null);
    }
    String getPlayerTribe(Player player) {
        return getPlayerTribe(player.getUniqueId());
    }

    void setPlayerTribe(UUID uuid, String tribe) {
        if (tribe.equalsIgnoreCase("red") || tribe.equalsIgnoreCase("blue"))
            getConfig().set("players." + uuid.toString() + ".tribe", tribe.toLowerCase());
        else
            getConfig().set("players." + uuid.toString() + ".tribe", null);

        saveConfig();
    }
    void setPlayerTribe(Player player, String tribe) {
       setPlayerTribe(player.getUniqueId(), tribe);
    }

    public Integer getStat(String key) {
        return getConfig().getInt(key, 0);
    }

    public Integer getDeaths(UUID uuid, Boolean total) {
        return getStat("players." + uuid.toString() + (total ? ".totaldeaths" : ".currentdeaths"));
    }
    public Integer getDeaths(String tribe, Boolean total) {
        return getStat("tribes." + tribe + (total ? ".totaldeaths" : ".currentdeaths"));
    }

    public Integer getDeaths(UUID uuid) {
        return getDeaths(uuid, false);
    }
    public Integer getDeaths(String tribe) {
        return getDeaths(tribe, false);
    }

    public Integer getKills(UUID uuid, Boolean total) {
        return getStat("players." + uuid.toString() + (total ? ".totalkills" : ".currentkills"));
    }
    public Integer getKills(String tribe, Boolean total) {
        return getStat("tribes." + tribe + (total ? ".totalkills" : ".currentkills"));
    }

    public Integer getKills(UUID uuid) {
        return getKills(uuid, false);
    }
    public Integer getKills(String tribe) {
        return getKills(tribe, false);
    }

    public List<UUID> getTribeMembers(String tribe) {
        List<UUID> result = new ArrayList<>();

        try {
            for (String key :
                    getConfig().getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                if (getConfig().getString("players." + key + ".tribe", "neutral").equalsIgnoreCase(tribe)) {
                    result.add(uuid);
                }
            }
        } catch (Exception ignored) { }

        return result;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player) sender;

        if (command.getLabel().equalsIgnoreCase("war")) {
            sender.sendMessage("§8§m-----------------------------------------------------");
            if (getConfig().getLong("nextwar") > System.currentTimeMillis()) {
                sender.sendMessage("§6Tribe War Status: " + "§aIntermission");
                Period period = new Period(getConfig().getLong("nextwar") - System.currentTimeMillis());
                sender.sendMessage("§eNext war at §f" + new Date(getConfig().getLong("nextwar")).toGMTString() + "§e.");
                Integer days = (int)Math.floor(period.getHours() / 24);
                Integer hours = (period.getHours() - (days * 24));
                sender.sendMessage("§eTime until next war: §f" + (days > 0 ? days + " days, " : "") + (hours > 0 ? hours + " hours, " : "") + (period.getMinutes() > 0 ? period.getMinutes() + " minutes, " : "") + period.getSeconds() + " seconds§e.");
            } else {
                sender.sendMessage("§6Tribe War Status: " + "§cWar In Progress");
                if (getPlayerTribe(player) != null) {
                    sender.sendMessage(getTribeColor(getPlayerTribe(player)) + "Your kills: §f" + getKills(player.getUniqueId()));
                    sender.sendMessage(getTribeColor(getPlayerTribe(player)) + "Your deaths: §f" + getDeaths(player.getUniqueId()));
                } else {
                    sender.sendMessage("§7You are not participating.");
                }

                sender.sendMessage("§eTribe kills: §c" + getKills("red") + " §f: §9" + getKills("blue"));
                sender.sendMessage("§eTribe deaths: §c" + getDeaths("red") + " §f: §9" + getDeaths("blue"));

                Period period = new Period(getConfig().getLong("warend") - System.currentTimeMillis());
                sender.sendMessage("§cWar ends at §f" + new Date(getConfig().getLong("warend")).toGMTString() + "§e.");
                Integer days = (int)Math.floor(period.getHours() / 24);
                Integer hours = (period.getHours() - (days * 24));
                sender.sendMessage("§cTime until war end: §f" + (days > 0 ? days + " days, " : "") + (hours > 0 ? hours + " hours, " : "") + (period.getMinutes() > 0 ? period.getMinutes() + " minutes, " : "") + period.getSeconds() + " seconds§e.");
            }
            sender.sendMessage("§8§m-----------------------------------------------------");
        } else if (command.getLabel().equalsIgnoreCase("tribe")) {
            if (args.length == 0) {
                String tribe = getPlayerTribe(player.getUniqueId());
                if (tribe == null) {
                    player.sendMessage("§7§lYou're currently not in a tribe. " +
                            "§7\nPick one with §f/tribe <§9blue§f/§cred§f/§7neutral§f>§e.");
                } else {
                    player.sendMessage(getTribeColor(tribe) + "§lYour tribe stats:");
                    player.sendMessage(getTribeColor(tribe) + "§l> §e" + getStat("tribes." + tribe + ".totalvictories") + "§e victories");
                    player.sendMessage(getTribeColor(tribe) + "§l> §e" + getKills(tribe, true) + "§e total kills");
                    player.sendMessage(getTribeColor(tribe) + "§l> §e" + getDeaths(tribe, true) + "§e total deaths");
                    if (isWarActive()) {
                        player.sendMessage(getTribeColor(tribe) + "§l> §e" + getKills(tribe, false) + "§e current kills");
                        player.sendMessage(getTribeColor(tribe) + "§l> §e" + getDeaths(tribe, false) + "§e current deaths");
                    }
                }
            } else {
                if (isWarActive()) {
                    if (getPlayerTribe(player.getUniqueId()) != null) {
                        player.sendMessage(getMessage("errors.cannotswitchwhileinwar"));
                        return true;
                    } else {
                        if (getConfig().getLong("warend") - new Date().getTime() < (1000 * 60 * 60 * 12)) {
                            player.sendMessage(getMessage("errors.cannotswitchwhileinwar"));
                            return true;
                        }
                    }
                }

                String tribe = args[0].toLowerCase();

                Integer redMemberCount = getTribeMembers("red").size();
                Integer blueMemberCount = getTribeMembers("blue").size();

                switch (tribe) {
                    case "blue":
                        if (Math.abs(blueMemberCount - redMemberCount) >= 5) {
                            if (blueMemberCount > redMemberCount) {
                                // Too many blues;
                                player.sendMessage("§eSorry, but there's no room in the " + getTribeColor("blue") + "blue §etribe right now.");
                                return true;
                            }
                        }
                        setPlayerTribe(player, "blue");
                        player.sendMessage("§eYou have joined the " + getTribeColor("blue") + "blue §etribe.");
                        break;
                    case "red":
                        if (Math.abs(blueMemberCount - redMemberCount) >= 5) {
                            if (redMemberCount > blueMemberCount) {
                                // Too many reds;
                                player.sendMessage("§eSorry, but there's no room in the " + getTribeColor("red") + "red §etribe right now.");
                                return true;
                            }
                        }
                        setPlayerTribe(player, "red");
                        player.sendMessage("§eYou have joined the " + getTribeColor("red") + "red §etribe.");
                        break;
                    default:
                        setPlayerTribe(player, "neutral");
                        player.sendMessage("§eYou have joined the " + getTribeColor("neutral") + "neutral §etribe.");
                        break;
                }
            }
        } else if (command.getLabel().equalsIgnoreCase("reloadwar")) {
            if (sender.hasPermission("tribewars.admin")) {
                sender.sendMessage("§eSure, one second!");
                reloadConfig();
                sender.sendMessage("§aDone reloading, boss!");
            } else {
                sender.sendMessage("§c§lNOPE! §cYou can't do that.");
            }
        }
        return super.onCommand(sender, command, label, args);
    }

    String getMessage(String path) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages." + path));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (isWarActive()) {
            if (e.getEntity().getType().equals(EntityType.PLAYER) && (e.getDamager().getType().equals(EntityType.PLAYER) || e.getDamager() instanceof Projectile)) {
                Player player = (Player) e.getEntity();

                Player killer = null;
                if (e.getDamager().getType() == EntityType.PLAYER) {
                    killer = (Player) e.getDamager();
                } else {
                    Projectile projectile = (Projectile) e.getDamager();
                    if (projectile.getShooter() instanceof Player) {
                        killer = (Player) projectile.getShooter();
                    }
                }

                if (killer == null) {
                    return;
                }

                if (getPlayerTribe(player.getUniqueId()) == null) {
                    e.setCancelled(true);
                    e.setDamage(0);
                    killer.sendMessage(getMessage("errors.targetneutral"));
                    return;
                }

                String tribe = getPlayerTribe(killer.getUniqueId());
                if (tribe != null) {
                    if (getPlayerTribe(player.getUniqueId()).equalsIgnoreCase(tribe)) {
                        e.setCancelled(true);
                        e.setDamage(0);
                        e.getDamager().sendMessage(getMessage("errors.targetteammate"));
                        return;
                    }

                    if (!playersInCombat.containsKey(player.getUniqueId())) {
                        player.sendMessage("§c§lTRIBE WARS! §cYou are now in combat.");
                    }

                    if (!playersInCombat.containsKey(killer.getUniqueId())) {
                        killer.sendMessage("§c§lTRIBE WARS! §cYou are now in combat.");
                    }

                    playersInCombat.put(player.getUniqueId(), new Date().getTime() + (1000 * 15));
                    playersInCombat.put(killer.getUniqueId(), new Date().getTime() + (1000 * 15));

                    if (e.getFinalDamage() > player.getHealth()) {
                        playerKilled(killer);
                    }
                } else {
                    e.setCancelled(true);
                    e.setDamage(0);
                    killer.sendMessage(getMessage("errors.youneutral"));
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent e) {
        if (playersInCombat.containsKey(e.getPlayer().getUniqueId()) && playersInCombat.get(e.getPlayer().getUniqueId()) - new Date().getTime() > 0) {
            Integer seconds = Math.toIntExact((playersInCombat.get(e.getPlayer().getUniqueId()) - new Date().getTime()) / 1000);
            e.getPlayer().sendMessage("§c§lTRIBE WARS! §cYou can't use commands in combat! (" + seconds + "s)");
            e.setCancelled(true);
        } else {
            playersInCombat.remove(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (isWarActive()) {
            String tribe = getPlayerTribe(e.getEntity().getUniqueId());
            if (tribe != null) {
                playerDied(e.getEntity());
            }
        }
    }

    void playerKilled(Player player) {
        String tribe = getPlayerTribe(player.getUniqueId());
        if (tribe == null) return;

        reloadConfig();
        Integer kills = getConfig().getInt("players." + player.getUniqueId() + ".currentkills", 0) + 1;
        Integer totalKills = getConfig().getInt("players." + player.getUniqueId() + ".totalkills", 0) + 1;
        getConfig().set("players." + player.getUniqueId() + ".currentkills", kills);
        getConfig().set("players." + player.getUniqueId() + ".totalkills", totalKills);

        Integer killsTribe = getConfig().getInt("tribes." + tribe + ".currentkills", 0) + 1;
        Integer totalKillsTribe = getConfig().getInt("tribes." + tribe + ".totalkills", 0) + 1;
        getConfig().set("tribes." + tribe + ".currentkills", killsTribe);
        getConfig().set("tribes." + tribe + ".totalkills", totalKillsTribe);
        saveConfig();
    }

    void playerDied(Player player) {
        String tribe = getPlayerTribe(player.getUniqueId());
        if (tribe == null) return;

        reloadConfig();
        Integer deaths = getConfig().getInt("players." + player.getUniqueId() + ".currentdeaths", 0) + 1;
        Integer totalDeaths = getConfig().getInt("players." + player.getUniqueId() + ".totaldeaths", 0) + 1;
        getConfig().set("players." + player.getUniqueId() + ".currentdeaths", deaths);
        getConfig().set("players." + player.getUniqueId() + ".totaldeaths", totalDeaths);

        Integer deathsTribe = getConfig().getInt("tribes." + tribe + ".currentdeaths", 0) + 1;
        Integer totalDeathsTribe = getConfig().getInt("tribes." + tribe + ".totaldeaths", 0) + 1;
        getConfig().set("tribes." + tribe + ".currentdeaths", deathsTribe);
        getConfig().set("tribes." + tribe + ".totaldeaths", totalDeathsTribe);
        saveConfig();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
