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

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Level;

/**
 * @author XHawk87
 */
public class WorldData {

    private AntiBranchMining plugin;
    private EnumMap<Material, Integer> remaining = new EnumMap<>(Material.class);
    private long total;
    private int chunks;
    private UUID worldId;
    private String worldName;
    private boolean enabled;
    private EnumSet<Material> excluded = EnumSet.noneOf(Material.class);
    private EnumSet<Material> ores = EnumSet.noneOf(Material.class);
    private int maxHeight;
    private int removalFactor;
    private Material oreReplacer;
    private AsyncFileWriter queuedFileWriter;
    private AsyncFileWriter completedFileWriter;
    private Set<ChunkOreRemover> noOresFound = new HashSet<>();

    public WorldData(AntiBranchMining plugin, World world) {
        this.plugin = plugin;
        this.worldId = world.getUID();
        this.worldName = world.getName();
        ConfigurationSection data = plugin.getConfig().getConfigurationSection("worlds." + world.getName());

        if (data == null) {
            if (world.getEnvironment() != World.Environment.NORMAL) {
                enabled = false;
            } else {
                enabled = plugin.isEnabledByDefault();
            }
            ores.addAll(plugin.getDefaultOres());
            excluded.addAll(plugin.getDefaultExcludedOres());
            maxHeight = plugin.getDefaultMaxHeight();
            removalFactor = plugin.getDefaultRemovalFactor();
            oreReplacer = plugin.getDefaultOreReplacer();
        } else {
            enabled = data.getBoolean("enabled", true);
            if (data.isList("ores")) {
                List<String> oresList = data.getStringList("ores");
                for (String oreName : oresList) {
                    Material ore = MaterialUtils.getMaterialFromString(oreName);
                    if (ore != null && ore.isBlock()) {
                        ores.add(ore);
                    } else {
                        plugin.getLogger().warning(oreName + " is not a valid block in config.yml. Ignoring it");
                    }
                }
            }
            if (ores.isEmpty()) {
                ores.addAll(plugin.getDefaultOres());
            }
            if (data.isList("excluded")) {
                List<String> excludedList = data.getStringList("excluded");
                for (String oreName : excludedList) {
                    Material ore = MaterialUtils.getMaterialFromString(oreName);
                    if (ore != null && ores.contains(ore)) {
                        excluded.add(ore);
                    } else {
                        plugin.getLogger().warning(oreName + " is not a valid ore in config.yml. Ignoring it");
                    }
                }
            }
            maxHeight = data.getInt("max-height", 64);
            removalFactor = data.getInt("removal-factor", 100);

            String oreName = data.getString("ore-replacement-material", "STONE");
            oreReplacer = MaterialUtils.getMaterialFromString(oreName);
            if (oreReplacer == null || !oreReplacer.isBlock()) {
                plugin.getLogger().warning(oreName + " is not a valid block");
                oreReplacer = Material.STONE;
            }
        }
    }

    public File getWorldFolder() {
        File worldFolder = new File(plugin.getDataFolder(), worldName);
        if (!worldFolder.exists()) {
            if (!worldFolder.mkdirs()) {
                throw new RuntimeException("Failed to create world directory");
            }
        }
        return worldFolder;
    }

    public EnumSet<Material> getExcluded() {
        return excluded;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getRemovalFactor() {
        return removalFactor;
    }

    public Material getOreReplacer() {
        return oreReplacer;
    }

    public EnumSet<Material> getOres() {
        return ores;
    }

    public World getWorld() {
        return plugin.getServer().getWorld(worldId);
    }

    public UUID getWorldId() {
        return worldId;
    }

    public void displayStats(CommandSender sender) {
        if (chunks == 0) {
            sender.sendMessage("AntiBranchMining has not run yet on " + getWorld().getName());
            return;
        }
        sender.sendMessage(getWorld().getName() + ": Average " + (total / chunks) + " ns per chunk (" + ((total / (double) chunks) / 1000000000.0D) + "s)");
        for (Material material : remaining.keySet()) {
            int amount = remaining.get(material);
            sender.sendMessage(material.name() + ": " + amount + " remaining in " + chunks + " chunks (average " + ((double) amount / (double) chunks) + " per chunk)");
        }
    }

    public void load() {
        final File queuedFile = new File(getWorldFolder(), "queued.log");
        final File completedFile = new File(getWorldFolder(), "completed.log");
        queuedFileWriter = new AsyncFileWriter(plugin, queuedFile);
        completedFileWriter = new AsyncFileWriter(plugin, completedFile);
        final WorldData worldData = this;
        new BukkitRunnable() {
            @Override
            public void run() {
                Set<String> toQueue = new TreeSet<>();
                try (BufferedReader in = new BufferedReader(new FileReader(queuedFile))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        toQueue.add(line);
                    }
                } catch (FileNotFoundException ex) {
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Cannot read " + queuedFile.getPath(), ex);
                }
                try (BufferedReader in = new BufferedReader(new FileReader(completedFile))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        String[] parts = line.split(" ");
                        toQueue.remove(parts[0]);
                        long duration = Long.parseLong(parts[1]);
                        total += duration;
                        chunks++;
                        for (int i = 2; i < parts.length; i++) {
                            String[] kvp = parts[i].split(":");
                            String materialName = kvp[0];
                            int amount = Integer.parseInt(kvp[1]);
                            Material material = MaterialUtils.getMaterialFromString(materialName);
                            if (material == null) {
                                throw new IOException("Unknown material: " + materialName +
                                        ". This may mean that the file is corrupted or from an outdated version. " +
                                        "Please delete the AntiBranchMining folder and try again");
                            } else {
                                if (remaining.containsKey(material)) {
                                    remaining.put(material, remaining.get(material) + amount);
                                } else {
                                    remaining.put(material, amount);
                                }
                            }
                        }
                    }
                } catch (FileNotFoundException ex) {
                    // ignore
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Cannot read " + completedFile.getPath(), ex);
                }

                int i = 1;
                for (String record : toQueue) {
                    String[] parts = record.split(",");
                    final int x = Integer.parseInt(parts[0]);
                    final int z = Integer.parseInt(parts[1]);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            plugin.queue(new ChunkOreRemover(worldData, getWorld().getChunkAt(x, z)));
                        }
                    }.runTaskLater(plugin, i++);
                }

                final int queued = toQueue.size();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        plugin.getLogger().info("Loaded data for " + getWorld().getName() + ", checking " + queued + " chunks");
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    public void unload() {
        queuedFileWriter.close();
        completedFileWriter.close();
    }

    public void logQueued(ChunkOreRemover remover) {
        queuedFileWriter.appendLine(remover.toString() + "\n");
    }

    public void logCompleted(ChunkOreRemover remover, long duration, EnumMap<Material, Integer> remaining) {
        chunks++;
        total += duration;
        StringBuilder sb = new StringBuilder();
        sb.append(remover.toString());
        sb.append(" ").append(duration);
        for (Map.Entry<Material, Integer> entry : remaining.entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();
            sb.append(" ").append(material.getId()).append(":").append(amount);
            if (this.remaining.containsKey(material)) {
                this.remaining.put(material, this.remaining.get(material) + amount);
            } else {
                this.remaining.put(material, amount);
            }
        }
        sb.append("\n");
        completedFileWriter.appendLine(sb.toString());
    }

    public boolean hasNoOres(ChunkOreRemover remover) {
        if (noOresFound.contains(remover)) {
            plugin.getLogger().info("Confirmed that " + getWorld().getName() + " " + remover.toString() + " still has no ores on the second pass");
            return true;
        }
        return false;
    }

    public void notifyNoOres(final ChunkOreRemover remover) {
        noOresFound.add(remover);
        plugin.getLogger().info("No ores were found in " + getWorld().getName() + " " + remover.toString() + ": Scheduling for a second check in 5 seconds");
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.queue(remover);
            }
        }.runTaskLater(plugin, 100);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof WorldData) {
            WorldData other = (WorldData) obj;
            return other.worldId.equals(worldId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.worldId);
    }
}
