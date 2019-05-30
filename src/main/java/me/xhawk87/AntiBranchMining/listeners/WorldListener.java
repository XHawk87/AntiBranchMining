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
package me.xhawk87.AntiBranchMining.listeners;

import me.xhawk87.AntiBranchMining.AntiBranchMining;
import me.xhawk87.AntiBranchMining.ChunkOreRemover;
import me.xhawk87.AntiBranchMining.WorldData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.WorldInitEvent;

/**
 *
 * @author XHawk87
 */
public class WorldListener implements Listener {

    private AntiBranchMining plugin;

    public void registerEvents(AntiBranchMining plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkPopulate(ChunkPopulateEvent event) {
        WorldData worldData = plugin.getWorldData(event.getWorld());
        if (!worldData.isEnabled()) {
            return;
        }
        ChunkOreRemover remover = new ChunkOreRemover(worldData, event.getChunk());
        if (!plugin.wasChecked(remover)) {
            plugin.getLogger().warning("Populated " + worldData.getWorld().getName() + " " + remover.getChunkX() + "," + remover.getChunkZ() + " again");
        }
        worldData.logQueued(remover);
        plugin.queue(remover);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldInit(WorldInitEvent event) {
        plugin.registerWorld(event.getWorld());
    }
}
