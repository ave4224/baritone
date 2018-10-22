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

package baritone.chat.command;

import baritone.utils.Helper;

public interface ICommand extends Helper {
    /**
     * Check if a chat is applicable
     * @param command the name of the chat
     * @param args trailing arguments
     */
    boolean matches(String command, String[] args);

    /**
     * Execute a chat on arguments
     * @param command the name of the chat
     * @param args trailing arguments
     * @return any output string for the user
     */
    String run(String command, String[] args);

    /**
     * Get usage information for the chat
     * @return usage examples
     */
    String getUsage();
}
