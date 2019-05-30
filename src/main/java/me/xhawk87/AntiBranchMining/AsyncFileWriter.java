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

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;

public class AsyncFileWriter {
    private final Plugin plugin;
    private final File file;
    private final BlockingDeque<String> lines = new LinkedBlockingDeque<>();
    private boolean running = false;

    public AsyncFileWriter(Plugin plugin, File file) {
        this.plugin = plugin;
        this.file = file;
    }

    public void appendLine(String message) {
        lines.addLast(message);
        if (!running) {
            running = true;
            new BukkitRunnable() {

                @Override
                public void run() {
                    try (BufferedWriter out = new BufferedWriter(new FileWriter(file, true))) {
                        while (running){
                            out.write(lines.takeFirst());
                        }
                    } catch (IOException|InterruptedException ex) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to save to " + file.getPath() + ": " + lines.toString(), ex);
                    }
                }
            }.runTaskAsynchronously(plugin);
        }
    }

    public void close() {
        running = false;
    }
}
