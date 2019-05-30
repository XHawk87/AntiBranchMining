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

public class MaterialUtils {

    public static Material getMaterialFromString(String materialName) {
        materialName = materialName.toUpperCase();
        Material material = Material.getMaterial(materialName);
        return (material == null) ? Material.getMaterial(materialName, true) : material;
    }
}
