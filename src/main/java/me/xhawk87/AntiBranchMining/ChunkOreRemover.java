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

import java.util.*;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * ChunkOreRemover
 *
 * @author XHawk87
 */
public class ChunkOreRemover implements Runnable {
    
    private static final Random random = new Random();
    private WorldData worldData;
    private int chunkX, chunkZ;
    
    public ChunkOreRemover(WorldData worldData, Chunk chunk) {
        this(worldData, chunk.getX(), chunk.getZ());
    }
    
    public ChunkOreRemover(WorldData worldData, int x, int y) {
        this.worldData = worldData;
        this.chunkX = x;
        this.chunkZ = y;
    }
    
    private Block getBlock(Chunk chunk, int dx, int y, int dz) {
        if (y < 0 || y > 255) {
            return null;
        }
        if (dx >= 0 && dx <= 15 && dz >= 0 && dz <= 15) {
            return chunk.getBlock(dx, y, dz);
        } else {
            int x = chunk.getX() * 16 + dx;
            int z = chunk.getZ() * 16 + dz;
            if (chunk.getWorld().isChunkLoaded(x >> 4, z >> 4)) {
                return chunk.getWorld().getBlockAt(x, y, z);
            } else {
                return null;
            }
        }
    }
    
    private void allowLinked(Block block, HashMap<Block, HashSet<Block>> toRemove, HashSet<Block> allowed) {
        if (toRemove.containsKey(block)) {
            HashSet<Block> linked = toRemove.get(block);
            toRemove.remove(block);
            allowed.add(block);
            for (Block link : linked) {
                allowLinked(link, toRemove, allowed);
            }
        }
    }
    
    private void addLinked(Block block, HashMap<Block, HashSet<Block>> toRemove, HashSet<Block> vein) {
        if (toRemove.containsKey(block) && !vein.contains(block)) {
            vein.add(block);
            HashSet<Block> linked = toRemove.get(block);
            for (Block link : linked) {
                if (link.getType() == block.getType()) {
                    addLinked(link, toRemove, vein);
                }
            }
        }
    }
    
    private void addRemainingOres(EnumMap<Material, Integer> remaining, Material type, int size) {
        if (!remaining.containsKey(type)) {
            remaining.put(type, size);
        } else {
            remaining.put(type, remaining.get(type) + size);
        }
    }
    
    @Override
    public void run() {
        long started = System.nanoTime();
        
        HashMap<Block, HashSet<Block>> toRemove = new HashMap<>();
        HashSet<Block> allowed = new HashSet<>();
        World world = worldData.getWorld();
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        int maxHeight = worldData.getMaxHeight();
        int removalFactor = worldData.getRemovalFactor();
        EnumSet<Material> excludedOres = worldData.getExcluded();
        Material oreReplacer = worldData.getOreReplacer();
        EnumSet<Material> ores = worldData.getOres();
        boolean hasNoOres = true;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y <= maxHeight; y++) {
                    Block block = getBlock(chunk, x, y, z);
                    if (block == null) {
                        continue;
                    }
                    
                    if (ores.contains(block.getType())) {
                        hasNoOres = false;
                        HashSet<Block> nearOres = new HashSet<>();
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                for (int dz = -1; dz <= 1; dz++) {
                                    if (dx == 0 && dy == 0 && dz == 0) {
                                        continue;
                                    }
                                    Block near = getBlock(chunk, x + dx, y + dy, z + dz);
                                    if (near == null) {
                                        continue;
                                    }
                                    if (ores.contains(near.getType())) {
                                        nearOres.add(near);
                                    }
                                    if (allowed.contains(near) || near.isEmpty() || near.isLiquid()) {
                                        allowed.add(block);
                                    }
                                }
                            }
                        }
                        if (allowed.contains(block)) {
                            for (Block near : nearOres) {
                                allowLinked(near, toRemove, allowed);
                            }
                            toRemove.remove(block);
                        } else {
                            toRemove.put(block, nearOres);
                        }
                    }
                }
            }
        }
        
        EnumMap<Material, Integer> remaining = new EnumMap<>(Material.class);
        while (!toRemove.isEmpty()) {
            HashSet<Block> vein = new HashSet<>();
            Block next = toRemove.keySet().iterator().next();
            addLinked(next, toRemove, vein);
            Material type = vein.iterator().next().getType();
            if (excludedOres.contains(type)
                    || (removalFactor < 100 && random.nextInt(100) >= removalFactor)) {
                addRemainingOres(remaining, type, vein.size());
            } else {
                for (Block block : vein) {
                    block.setType(oreReplacer);
                }
            }
            toRemove.keySet().removeAll(vein);
        }
        
        for (Block block : allowed) {
            addRemainingOres(remaining, block.getType(), 1);
        }
        
        
        long duration = System.nanoTime() - started;
        if (hasNoOres && !worldData.hasNoOres(this)) {
            worldData.notifyNoOres(this);
        } else {
            worldData.logCompleted(this, duration, remaining);
        }
    }
    
    public UUID getWorldName() {
        return worldData.getWorldId();
    }
    
    public int getChunkX() {
        return chunkX;
    }
    
    public int getChunkZ() {
        return chunkZ;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ChunkOreRemover) {
            ChunkOreRemover other = (ChunkOreRemover) obj;
            return other.chunkX == chunkX && other.chunkZ == chunkZ && other.worldData.equals(worldData);
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 13 * hash + Objects.hashCode(this.worldData);
        hash = 13 * hash + this.chunkX;
        hash = 13 * hash + this.chunkZ;
        return hash;
    }
    
    @Override
    public String toString() {
        return chunkX + "," + chunkZ;
    }

    public Set<ChunkOreRemover> getLocalGroup() {
        Set<ChunkOreRemover> neighbours = new HashSet<>();
        for (int dx = -1; dx <= 1; dx += 1) {
            for (int dz = -1; dz <= 1; dz += 1) {
                neighbours.add(new ChunkOreRemover(worldData, chunkX + dx, chunkZ + dz));
            }
        }
        return neighbours;
    }
}
