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

package baritone.api.behavior;

import net.minecraft.block.Block;

/**
 * @author Brady
 * @since 9/23/2018
 */
public interface IMineBehavior extends IBehavior {

    /**
     * Begin to search for and mine the specified blocks until
     * the number of specified items to get from the blocks that
     * are mined. This is based on the first target block to mine.
     *
     * @param quantity The number of items to get from blocks mined
     * @param blocks The blocks to mine
     */
    void mine(int quantity, String... blocks);

    /**
     * Begin to search for and mine the specified blocks until
     * the number of specified items to get from the blocks that
     * are mined. This is based on the first target block to mine.
     *
     * @param quantity The number of items to get from blocks mined
     * @param blocks The blocks to mine
     */
    void mine(int quantity, Block... blocks);

    /**
     * Begin to search for and mine the specified blocks.
     *
     * @param blocks The blocks to mine
     */
    default void mine(String... blocks) {
        this.mine(0, blocks);
    }

    /**
     * Begin to search for and mine the specified blocks.
     *
     * @param blocks The blocks to mine
     */
    default void mine(Block... blocks) {
        this.mine(0, blocks);
    }

    /**
     * Cancels the current mining task
     */
    void cancel();
}
