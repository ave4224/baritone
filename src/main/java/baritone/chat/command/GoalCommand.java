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

import baritone.api.pathing.goals.Goal;
import baritone.behavior.PathingBehavior;

import java.util.function.Function;
import java.util.function.Supplier;

public class GoalCommand implements ICommand {
    /**
     * name of the chat
     */
    public final String name;
    /**
     * number of arguments
     */
    private final int arguments;
    /**
     * constructor of a goal
     */
    private final Function<String[], Goal> function;
    /**
     * help info of the chat
     */
    private final String usage;

    /**
     * Constructor for where the number of chat arguments is 0
     * @param name matching name for the chat
     * @param function Supplier of a goal
     */
    public GoalCommand(String name, Supplier<Goal> function){
        this(name, 0, x->function.get(), name);
    }

    /**
     * Constructor for a GoalCommand with a single chat argument
     * @param name mathing name for the chat
     * @param function from the arguments to a Goal
     * @param usage help info for the chat
     */
    public GoalCommand(String name, Function<String, Goal> function, String usage){
        this(name, 1, function.compose(x->x[0]), usage);
    }

    /**
     * Constructor for a GoalCommand with any number of chat arguments
     * @param name mathing name for the chat
     * @param arguments number of chat arguments
     * @param function from the arguments to a Goal
     * @param usage help info for the chat
     */
    public GoalCommand(String name, int arguments, Function<String[], Goal> function, String usage){
        this.name = name;
        this.arguments = arguments;
        this.function = function;
        this.usage = usage;
    }

    @Override
    public boolean matches(String command, String[] args) {
        return name.equals(command) && args.length == arguments;
    }

    @Override
    public String run(String command, String[] args) {
        if(matches(command, args)) {
            try {
                Goal result = function.apply(args);
                if (result != null) {
                    PathingBehavior.INSTANCE.setGoal(result);
                    return "Set goal to " + result.toString();
                } else {
                    return getUsage();
                }
            } catch (NumberFormatException e) {
                return getUsage();
            }
        } else {
            return getUsage();
        }
    }

    @Override
    public String getUsage() {
        return usage;
    }
}
