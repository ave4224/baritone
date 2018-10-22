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

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import java.lang.reflect.Field;

public class BooleanBasedCommand extends FieldBasedCommand<Boolean>{

    public BooleanBasedCommand(@NotNull Field field){
        this(field, field.getName());
    }

    /**
     * Constructs chat chat capable based on the modification of a boolean property field
     * @param field reference to an instance property field
     * @param name name of the chat for use from the chat
     */
    public BooleanBasedCommand(@NotNull Field field, @NotNull String name){
        this(field, name, null);
    }

    /**
     * Constructs chat chat capable based on the modification of a boolean property field
     * @param field reference to an instance property field
     * @param name name of the chat for use from the chat
     * @param object instance the field belongs to
     */
    public BooleanBasedCommand(@NotNull Field field, @NotNull String name, @Nullable Object object){
        super(Boolean::valueOf, field, name, object);
    }

    @Override
    public boolean matches(@NotNull String command, @NotNull String[] args) {
        if(super.matches(command, args)) {
            return true;
        } else {
            return args.length == 1 && "toggle".equals(command) && name.equals(args[0]);
        }
    }

    @Override
    public String run(@NotNull String command, @NotNull String[] args) {
        if (name.equals(command) && args.length == 0) {
            return name + " set to " + setObject(!(getObject()));
        } else if("toggle".equals(command) && args.length == 1 && name.equals(args[0])) {
            return name + " set to " + setObject(!(getObject()));
        } else {
            return super.run(command, args);
        }
    }

    @Override
    public String getUsage() {
        return super.getUsage() + "\ntoggle " + name;
    }
}
