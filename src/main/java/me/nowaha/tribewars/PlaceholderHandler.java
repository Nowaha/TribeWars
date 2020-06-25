package me.nowaha.tribewars;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class PlaceholderHandler extends PlaceholderExpansion {

    public TribeWars plugin;

    public PlaceholderHandler(TribeWars plugin) {
        this.plugin = plugin;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null){
            return "";
        }

        if (identifier.equals("c")){
            if (plugin.getPlayerTribe(player.getUniqueId()) != null) {
                return "§" + plugin.getTribeColor(plugin.getPlayerTribe(player.getUniqueId())).getChar();
            } else {
                return "§" + plugin.getTribeColor("neutral").getChar();
            }
        } else if (identifier.equals("cd")){
            if (!plugin.isWarActive()) return "";

            if (plugin.getPlayerTribe(player.getUniqueId()) != null) {
                return plugin.getPlayerTribe(player.getUniqueId()).equalsIgnoreCase("red") ? "§4" : "§1";
            } else {
                return "§8";
            }
        }

        return null;
    }

    @Override
    public boolean persist(){
        return true;
    }

    @Override
    public boolean canRegister(){
        return true;
    }

    @Override
    public String getIdentifier() {
        return "war";
    }

    @Override
    public String getAuthor() {
        return "Nowaha";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }
}
