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

import java.util.function.BiFunction;
import java.util.function.Predicate;

public class GenericCommand implements ICommand{
    private final Predicate<String> name;
    private final int arguments;
    private final BiFunction<String, String[], String> function;

    private GenericCommand(String name, int arguments, BiFunction<String, String[], String> function){
        this.name = name::equals;
        this.arguments = arguments;
        this.function = function;
    }

    private GenericCommand(Predicate<String> name, int arguments, BiFunction<String, String[], String> function){
        this.name = name;
        this.arguments = arguments;
        this.function = function;
    }

    @Override
    public boolean matches(String command, String[] args) {
        return name.test(command) && args.length == arguments;
    }

    @Override
    public String run(String command, String[] args) {
        if(matches(command, args)) {
            return function.apply(command, args);
        } else {
            return getUsage();
        }
    }

    @Override
    public String getUsage() {
        return name + " takes " + arguments + "arguments";
    }

}
