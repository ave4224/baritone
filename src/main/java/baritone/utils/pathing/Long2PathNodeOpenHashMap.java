/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils.pathing;

import baritone.api.pathing.goals.Goal;
import baritone.pathing.calc.PathNode;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

public final class Long2PathNodeOpenHashMap extends Long2ObjectOpenHashMap<PathNode> {

    // This function is adapted from the superclass Long2ObjectOpenHashMap's private function insert
    // It does not fall under Baritone's copyright
    // The following copyright notice applies to this function only
    /*
     * Copyright (C) 2002-2016 Sebastiano Vigna
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *     http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
    public PathNode getOrCreate(final long k, final int x, final int y, final int z, final Goal goal) {
        int pos;
        long curr;
        final long[] key = this.key;
        // The starting point.
        if (!((curr = key[pos = (int) it.unimi.dsi.fastutil.HashCommon.mix((k)) & mask]) == (0))) {
            if (((curr) == (k))) return (PathNode) ((Object[]) value)[pos];
            while (!((curr = key[pos = (pos + 1) & mask]) == (0)))
                if (((curr) == (k))) return (PathNode) ((Object[]) value)[pos];
        }
        key[pos] = k;
        PathNode ret = new PathNode(x, y, z, goal);
        ((Object[]) value)[pos] = ret;
        if (size++ >= maxFill) rehash(arraySize(size + 1, f));
        return ret;
    }
}
