/*
 * Copyright (C) 2013-2019 XHawk87
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package me.xhawk87.AntiBranchMining;

import me.xhawk87.AntiBranchMining.commands.OreStatsCommand;
import me.xhawk87.AntiBranchMining.listeners.WorldListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * AntiBranchMining
 *
 * @author XHawk87
 */
public class AntiBranchMining extends JavaPlugin {

    private static final EnumSet<Material> defaultOres = EnumSet.of(Material.DIAMOND_ORE, Material.GOLD_ORE,
            Material.IRON_ORE, Material.COAL_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE, Material.EMERALD_ORE,
            Material.NETHER_QUARTZ_ORE);
    private HashSet<ChunkOreRemover> populated = new HashSet<>();
    private HashSet<ChunkOreRemover> checked = new HashSet<>();
    private HashMap<UUID, WorldData> worlds = new HashMap<>();
    private boolean enabled;
    private int removalFactor;
    private EnumSet<Material> excluded = EnumSet.noneOf(Material.class);
    private EnumSet<Material> customOres = EnumSet.noneOf(Material.class);
    private int maxHeight;
    private Material oreReplacer;
    private long maxWorkDurationPerTick;
    private BukkitTask tick = null;
    private ArrayDeque<ChunkOreRemover> toCheck = new ArrayDeque<>();

    @Override
    public void onEnable() {
        super.onEnable();
        saveDefaultConfig();
        enabled = getConfig().getBoolean("enabled", true);
        if (getConfig().isList("ores")) {
            List<String> oresList = getConfig().getStringList("ores");
            for (String oreName : oresList) {
                Material ore = MaterialUtils.getMaterialFromString(oreName);
                if (ore != null && ore.isBlock()) {
                    customOres.add(ore);
                } else {
                    getLogger().warning(oreName + " is not a valid block in config.yml. Ignoring it");
                }
            }
        }
        if (customOres.isEmpty()) {
            customOres.addAll(defaultOres);
        }
        if (getConfig().isList("excluded")) {
            List<String> excludedList = getConfig().getStringList("excluded");
            for (String oreName : excludedList) {
                Material ore = MaterialUtils.getMaterialFromString(oreName);
                if (ore != null && customOres.contains(ore)) {
                    excluded.add(ore);
                } else {
                    getLogger().warning(oreName + " is not a valid ore in config.yml. Ignoring it");
                }
            }
        }
        maxHeight = getConfig().getInt("max-height", 64);
        removalFactor = getConfig().getInt("removal-factor", 100);
        String oreName = getConfig().getString("ore-replacement-material", "STONE");
        oreReplacer = MaterialUtils.getMaterialFromString(oreName);
        if (oreReplacer == null || !oreReplacer.isBlock()) {
            getLogger().warning(oreName + " is not a valid block");
            oreReplacer = Material.STONE;
        }
        maxWorkDurationPerTick = getConfig().getLong("max-work-duration-per-tick", 45000000);

        new WorldListener().registerEvents(this);

        Objects.requireNonNull(getCommand("orestats")).setExecutor(new OreStatsCommand(this));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        for (WorldData world : worlds.values()) {
            world.unload();
        }
    }

    public void addToPopulated(ChunkOreRemover populatedChunkRemover) {
        populated.add(populatedChunkRemover);
        for (ChunkOreRemover remover : populatedChunkRemover.getLocalGroup()) {
            if (!checked.contains(remover) && populated.containsAll(remover.getLocalGroup())) {
                toCheck.add(remover);
                if (tick == null) {
                    final AntiBranchMining plugin = this;
                    tick = Bukkit.getScheduler().runTask(this, new Runnable() {
                        @Override
                        public void run() {
                            long started = System.nanoTime();
                            while (!toCheck.isEmpty() && System.nanoTime() - started < maxWorkDurationPerTick) {
                                ChunkOreRemover checking = toCheck.pop();
                                checking.run();
                                checked.add(checking);
                            }
                            if (toCheck.isEmpty()) {
                                tick = null;
                            } else {
                                tick = Bukkit.getScheduler().runTask(plugin, this);
                            }
                        }
                    });
                }
            }
        }
    }

    public EnumSet<Material> getDefaultExcludedOres() {
        return excluded;
    }

    public int getDefaultMaxHeight() {
        return maxHeight;
    }

    public int getDefaultRemovalFactor() {
        return removalFactor;
    }

    public Material getDefaultOreReplacer() {
        return oreReplacer;
    }

    public EnumSet<Material> getDefaultOres() {
        return customOres;
    }

    public void displayStats(CommandSender sender) {
        for (WorldData worldData : worlds.values()) {
            worldData.displayStats(sender);
        }
    }

    public boolean isEnabledByDefault() {
        return enabled;
    }

    public WorldData getWorldData(World world) {
        WorldData worldData = worlds.get(world.getUID());
        if (worldData == null) {
            worldData = registerWorld(world);
        }
        return worldData;
    }

    public boolean isChunkPopulated(ChunkOreRemover remover) {
        return populated.contains(remover);
    }

    public WorldData registerWorld(World world) {
        WorldData worldData = new WorldData(this, world);
        worlds.put(world.getUID(), worldData);
        worldData.load();
        return worldData;
    }
}
