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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Predicate;

public class ReflectMethodCommand implements ICommand{
    private final Predicate<String> name;
    private final int arguments;
    private final Method function;

    public ReflectMethodCommand(int arguments, Method method){
        this(method.getName(), arguments, method);
    }

    public ReflectMethodCommand(String name, int arguments, Method method){
        this(name::equals, arguments, method);
    }

    public ReflectMethodCommand(String[] names, int arguments, Method method){
        this((new ArrayList<String>(Arrays.asList(names)))::contains, arguments, method);
    }

    public ReflectMethodCommand(Predicate<String> name, int arguments, Method method){
        this.name = name;
        this.arguments = arguments;
        this.function = method;
    }

    @Override
    public boolean matches(String command, String[] args) {
        return name.test(command) && args.length == arguments;
    }

    @Override
    public String run(String command, String[] args) {
        if(matches(command, args)) {
            try {
                return (String)function.invoke(null, (Object)command, (Object)args);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        } else {
            return getUsage();
        }
    }

    @Override
    public String getUsage() {
        return name + " takes " + arguments + "arguments";
    }
}
