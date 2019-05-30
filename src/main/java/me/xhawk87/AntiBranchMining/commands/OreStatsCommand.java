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
package me.xhawk87.AntiBranchMining.commands;

import me.xhawk87.AntiBranchMining.AntiBranchMining;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 *
 * @author XHawk87
 */
public class OreStatsCommand implements CommandExecutor {

    private AntiBranchMining plugin;

    public OreStatsCommand(AntiBranchMining plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("antibranchmining.commands.orestats")) {
            sender.sendMessage("You do not have permission to use this command");
            return true;
        }

        plugin.displayStats(sender);
        return true;
    }
}
