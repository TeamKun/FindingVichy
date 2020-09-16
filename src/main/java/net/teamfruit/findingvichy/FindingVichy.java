package net.teamfruit.findingvichy;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.apache.commons.lang.math.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.user.User;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public final class FindingVichy extends JavaPlugin implements CommandExecutor {
    private File mappingsData;
    private Gson gson = new GsonBuilder().create();

    public static class MappingEntry {
        public String id;
        public String name;
    }

    public Map<String, MappingEntry> mappings;

    private void load() {
        reloadConfig();
        try {
            Type type = new TypeToken<Map<String, MappingEntry>>() {
            }.getType();
            mappings = gson.fromJson(
                    new JsonReader(new InputStreamReader(new FileInputStream(mappingsData), StandardCharsets.UTF_8)),
                    type
            );
        } catch (Exception e) {
            mappings = new HashMap<>();
        }
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        mappingsData = new File(getDataFolder(), "mappings.json");
        saveDefaultConfig();
        load();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            load();
            sender.sendMessage("Mapping Reloaded");
            return true;
        }

        String token = getConfig().getString("discord.token");
        long guildId = NumberUtils.toLong(getConfig().getString("discord.guild"));
        long channelId = NumberUtils.toLong(getConfig().getString("discord.channel"));

        DiscordApi api = new DiscordApiBuilder().setToken(token).login().join();

        Collection<User> members = api.getServerById(guildId)
                .flatMap(e -> e.getVoiceChannelById(channelId))
                .map(e -> e.getConnectedUsers())
                .orElseGet(Collections::emptyList);

        List<String> memberIds = members.stream()
                .map(User::getId)
                .map(Object::toString)
                .collect(Collectors.toList());

        api.disconnect();

        Server server = getServer();
        boolean onlineMode = server.getOnlineMode();
        Set<UUID> onlineIds = server.getOnlinePlayers().stream()
                .map(Player::getPlayerProfile)
                .map(PlayerProfile::getId)
                .collect(Collectors.toSet());
        Set<String> onlineNames = server.getOnlinePlayers().stream()
                .map(Player::getPlayerProfile)
                .map(PlayerProfile::getName)
                .collect(Collectors.toSet());
        List<String> vichies = memberIds.stream()
                .map(mappings::get)
                .filter(Objects::nonNull)
                .filter(e -> onlineMode ? !onlineIds.contains(uuidOrNull(e.id)) : !onlineNames.contains(e.name))
                .map(e -> e.name)
                .collect(Collectors.toList());

        server.broadcastMessage(ChatColor.GREEN + "========= ▼VCだけ入ってマイクラ入らないバカ一覧▼ =========");
        for (String vichy : vichies)
            Bukkit.broadcastMessage(ChatColor.DARK_GREEN + vichy);
        server.broadcastMessage(ChatColor.GREEN + "========= ▲VCだけ入ってマイクラ入らないバカ一覧▲ =========");

        return true;
    }

    private UUID uuidOrNull(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
        }
        return null;
    }
}
