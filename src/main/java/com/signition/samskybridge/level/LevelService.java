package com.signition.samskybridge.level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;

import com.signition.samskybridge.Main;
import com.signition.samskybridge.data.DataService;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.island.Island;

public class LevelService implements Listener {

    private final Map<UUID, Long> tickXP = new ConcurrentHashMap<>();

    private final Main plugin;
    private final DataService data;

    private final Map<String, Long> blockXP = new ConcurrentHashMap<>();
    private final Map<String, Long> prefixXP = new ConcurrentHashMap<>();
    private final Map<String, Long> containsXP = new ConcurrentHashMap<>();
    private final java.util.List<java.util.AbstractMap.SimpleEntry<java.util.regex.Pattern, Long>> regexXP = new java.util.ArrayList<>();
    private final TreeMap<Integer, Long> xpTable = new TreeMap<>(); // level -> cumulativeXP

    public LevelService(Main plugin, DataService data) {
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> tickXP.clear(), 1L, 1L);
        this.plugin = plugin;
        this.data = data;
        reload();
    }

    public void reload() {
        blockXP.clear();
        prefixXP.clear();
        containsXP.clear();
        regexXP.clear();
        xpTable.clear();
        // Load blocks.yml
        ConfigurationSection bs = plugin.configs().blocks.getConfigurationSection("blocks");
        if (bs != null) {
            for (String k : bs.getKeys(false)) {
                blockXP.put(k.toLowerCase(java.util.Locale.ENGLISH), bs.getLong(k));
            }
        }
        // Load pattern tables
        java.util.List<java.util.Map<?,?>> list;
        list = plugin.configs().blocks.getMapList("patterns.prefix");
        for (java.util.Map<?,?> m : list) {
            java.util.List<?> arr = (java.util.List<?>) m;
        }
        // Simpler: also support 'patterns.prefix' as list of [pattern, xp]
        for (Object o : plugin.configs().blocks.getList("patterns.prefix", java.util.Collections.emptyList())) {
            if (o instanceof java.util.List) {
                java.util.List<?> a = (java.util.List<?>) o;
                if (a.size() >= 2) {
                    String pat = String.valueOf(a.get(0));
                    long v = Long.parseLong(String.valueOf(a.get(1)));
                    prefixXP.put(pat.toLowerCase(java.util.Locale.ENGLISH), v);
                }
            }
        }
        for (Object o : plugin.configs().blocks.getList("patterns.contains", java.util.Collections.emptyList())) {
            if (o instanceof java.util.List) {
                java.util.List<?> a = (java.util.List<?>) o;
                if (a.size() >= 2) {
                    String pat = String.valueOf(a.get(0));
                    long v = Long.parseLong(String.valueOf(a.get(1)));
                    containsXP.put(pat.toLowerCase(java.util.Locale.ENGLISH), v);
                }
            }
        }
        for (Object o : plugin.configs().blocks.getList("patterns.regex", java.util.Collections.emptyList())) {
            if (o instanceof java.util.List) {
                java.util.List<?> a = (java.util.List<?>) o;
                if (a.size() >= 2) {
                    String pat = String.valueOf(a.get(0));
                    long v = Long.parseLong(String.valueOf(a.get(1)));
                    try { regexXP.add(new java.util.AbstractMap.SimpleEntry<>(java.util.regex.Pattern.compile(pat), v)); } catch (Exception ignored) {}
                }
            }
        }

        // Load levels.yml
        long per = plugin.configs().levels.getLong("leveling.xp-per-level", 1000L);
        ConfigurationSection tbl = plugin.configs().levels.getConfigurationSection("leveling.xp-table");
        if (tbl != null) {
            for (String k : tbl.getKeys(false)) {
                int lvl = Integer.parseInt(k);
                long req = tbl.getLong(k);
                xpTable.put(lvl, req);
            }
        } else {
            // generate with growth: next required = prev_required * (1 + growth%)
            double base = plugin.configs().levels.getDouble("leveling.base-xp", plugin.configs().levels.getDouble("leveling.xp-per-level", 1000.0));
            double growth = plugin.configs().levels.getDouble("leveling.growth-percent", 1.5) / 100.0; // default +1.5%%
            int maxLevel = plugin.configs().levels.getInt("leveling.max-level", 200);
            if (maxLevel < 1) maxLevel = 100;
            double req = Math.max(1.0, base);
            long cumulative = 0L;
            xpTable.put(0, 0L);
            for (int i=1; i<=maxLevel; i++) {
                cumulative += Math.round(req);
                xpTable.put(i, cumulative);
                req = req * (1.0 + growth);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (plugin.configs().storage.getBoolean("settings.ignore-creative", true) && p.getGameMode() == GameMode.CREATIVE) return;
        if (!isWorldEnabled(e.getBlockPlaced().getWorld().getName())) return;
        Block b = e.getBlockPlaced();
        // Check island
        Island is = BentoBox.getInstance().getIslands().getIslandAt(b.getLocation()).orElse(null);
        if (is == null) return;
        int cap = plugin.configs().storage.getInt("settings.xp-per-tick-cap", 200);
        if (cap > 0) {
            long sum = tickXP.getOrDefault(is.getUniqueId(), 0L);
            if (sum >= cap) return;
        }
        String key = blockKey(b);
        long xp = xpFor(key);
        if (xp <= 0) return;
        UUID id = is.getUniqueId();
        long cur = data.getXP(id);
        cur += xp;
        data.setXP(id, cur);
        if (plugin.configs().storage.getInt("settings.xp-per-tick-cap", 200) > 0) {
            tickXP.put(id, tickXP.getOrDefault(id, 0L) + xp);
        }
        int oldL = data.getLevel(id);
        int newL = levelForXP(cur);
        if (newL != oldL) {
            data.setLevel(id, newL);
            p.sendMessage(color(plugin.configs().messages.getString("messages.level-up").replace("{newLevel}", String.valueOf(newL)).replace("{xp}", String.valueOf(xp))));
        }
        // Optionally notify progress
        // plugin.getRanking().update(is); // if ranking service exposes update
    }

    

public long thresholdFor(int level) {
    Long v = xpTable.get(level);
    if (v == null) {
        // find nearest lower
        long last = 0L;
        for (java.util.Map.Entry<Integer, Long> e : xpTable.entrySet()) {
            if (e.getKey() <= level) last = e.getValue(); else break;
        }
        return last;
    }
    return v;
}

public long nextThreshold(int level) {
    Long v = xpTable.get(level + 1);
    if (v == null) return Long.MAX_VALUE / 4;
    return v;
}

    public int levelForXP(long xp) {
        int lvl = 0;
        for (Map.Entry<Integer, Long> e : xpTable.entrySet()) {
            if (xp >= e.getValue()) lvl = e.getKey();
            else break;
        }
        return lvl;
    }

    public long neededFor(int nextLevel) {
        Long v = xpTable.get(nextLevel);
        if (v == null) return Long.MAX_VALUE;
        return v;
    }

    public long xpFor(String key) {
        if (key == null) return 0L;
        Long v = blockXP.get(key.toLowerCase(java.util.Locale.ENGLISH));
        if (v != null) return v;
        return 0L;
    }

    private String blockKey(Block b) {
        try {
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            java.lang.reflect.Method adapt = adapter.getMethod("adapt", org.bukkit.block.Block.class);
            Object weBlock = adapt.invoke(null, b);
            java.lang.reflect.Method getType = weBlock.getClass().getMethod("getBlockType");
            Object type = getType.invoke(weBlock);
            java.lang.reflect.Method getId = type.getClass().getMethod("getId");
            Object id = getId.invoke(type);
            if (id != null) return id.toString();
        } catch (Throwable ignore) { }
        try {
            BlockData bd = b.getBlockData();
            String s = bd.getAsString();
            if (s != null && s.contains(":")) return s;
        } catch (Throwable t) {}
        return b.getType().name().toLowerCase(java.util.Locale.ENGLISH);
    }

    public long xpOf(Island is) {
        return data.getXP(is.getUniqueId());
    }
    public int levelOf(Island is) {
        return data.getLevel(is.getUniqueId());
    }
}
