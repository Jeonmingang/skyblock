package com.signition.samskybridge.rank;

import java.util.*;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.signition.samskybridge.Main;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.island.Island;

public class RankingService {

    private final java.util.Map<java.util.UUID,Integer> positions = new java.util.concurrent.ConcurrentHashMap<>();

    private final Main plugin;

    public RankingService(Main plugin) {
        this.plugin = plugin;
    }

    public List<Island> allIslands() {
        return new ArrayList<>(BentoBox.getInstance().getIslands().getIslands());
    }

    public void showTop(CommandSender sender, int n) {
        List<Island> list = allIslands();
        list.sort((a,b) -> {
            int la = plugin.levels().levelOf(a);
            int lb = plugin.levels().levelOf(b);
            if (lb != la) return Integer.compare(lb, la);
            long xa = plugin.data().getXP(a.getUniqueId());
            long xb = plugin.data().getXP(b.getUniqueId());
            return Long.compare(xb, xa);
        });
        sender.sendMessage(color(Main.get().configs().messages.getString("messages.rank-top-title").replace("{n}", String.valueOf(n))));
        int i=1;
        for (Island is : list.stream().limit(n).collect(Collectors.toList())) {
            String name = (is.getOwner() != null) ? is.getOwner().getName() : is.getUniqueId().toString().substring(0,8);
            int lvl = plugin.levels().levelOf(is);
            long xp = plugin.data().getXP(is.getUniqueId());
            sender.sendMessage(color(Main.get().configs().messages.getString("messages.rank-line").replace("{rank}", String.valueOf(i++)).replace("{name}", name).replace("{level}", String.valueOf(lvl)).replace("{xp}", String.valueOf(xp))));
        }
    }

    public void showMe(Player p) {
        Island is = BentoBox.getInstance().getIslands().getIslands().getIslandAt(p.getLocation()).orElse(null);
        if (is == null) { p.sendMessage("§c섬이 없습니다."); return; }
        int lvl = plugin.levels().levelOf(is);
        long xp = plugin.data().getXP(is.getUniqueId());
        long next = plugin.levels().neededFor(lvl+1);
        p.sendMessage(color(Main.get().configs().messages.getString("messages.rank-me").replace("{level}", String.valueOf(lvl)).replace("{xp}", String.valueOf(xp)).replace("{next}", String.valueOf(next))));
    }

    public void recalcNotice(CommandSender sender) {
        sender.sendMessage("§7레벨/경험치는 실시간 갱신됩니다. 표가 바뀌면 /ssb reload 또는 /섬 리로드 하세요.");
    }
}

    private String color(String s) { return org.bukkit.ChatColor.translateAlternateColorCodes('&', s); }


public java.util.Map<java.util.UUID,Integer> recompute() {
    java.util.List<Island> list = allIslands();
    list.sort((a,b) -> {
        int la = plugin.levels().levelOf(a);
        int lb = plugin.levels().levelOf(b);
        if (lb != la) return Integer.compare(lb, la);
        long xa = plugin.data().getXP(a.getUniqueId());
        long xb = plugin.data().getXP(b.getUniqueId());
        return Long.compare(xb, xa);
    });
    positions.clear();
    int r=1;
    for (Island is : list) positions.put(is.getUniqueId(), r++);
    return new java.util.HashMap<>(positions);
}

public int rankOf(Island is) {
    Integer v = positions.get(is.getUniqueId());
    return v == null ? -1 : v;
}
