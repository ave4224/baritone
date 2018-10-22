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
import java.util.Objects;
import java.util.function.Function;

public class FieldBasedCommand<T> implements ICommand{
    /**
     * Reference to the property field
     */
    protected final Field field;
    /**
     * Name of the chat
     */
    public final String name;
    /**
     * instance of the object the field belongs to
     */
    protected final Object object;
    /**
     * constructor to create new values
     */
    protected final Function<String, T> constructor;

    /**
     * Constructs chat chat capable based on the modification of a property field
     * @param constructor used to create new values from the chat
     * @param field reference to a static property field
     */
    public FieldBasedCommand(@NotNull Function<String, T> constructor,
                             @NotNull Field field){
        this(constructor, field, field.getName());
    }

    /**
     * Constructs chat chat capable based on the modification of a property field
     * @param constructor used to create new values from the chat
     * @param field reference to a static property field
     * @param name name of the chat for use from the chat
     */
    public FieldBasedCommand(@NotNull Function<String, T> constructor,
                             @NotNull Field field,
                             @NotNull String name){
        this(constructor, field, name, null);
    }

    /**
     * Constructs chat chat capable based on the modification of a property field
     * @param constructor used to create new values from the chat
     * @param field reference to an instance property field
     * @param name name of the chat for use from the chat
     * @param object instance the field belongs to
     */
    public FieldBasedCommand(@NotNull Function<String, T> constructor,
                             @NotNull Field field,
                             @NotNull String name,
                             @Nullable Object object){
        this.constructor = constructor;
        this.object = object;
        this.field = field;
        this.name = name;
    }

    @Override
    public boolean matches(@NotNull String command, @NotNull String[] args) {
        switch(args.length){
            case 0:
                return name.equals(command);
            case 1:
                if (name.equals(command)) {
                    return true;
                } else if ("get".equals(command)) {
                    return name.equals(args[0]);
                } else {
                    return false;
                }
            case 2:
                if("set".equals(command) && name.equals(args[0])){
                    return true;
                } else {
                    return false;
                }
            default:
                return false;
        }
    }

    @Override
    public String run(@NotNull String command, @NotNull String[] args) {
        switch(args.length){
            case 0:
                if(name.equals(command)){
                    return name + " is " + Objects.toString(getObject());
                } else {
                    return getUsage();
                }
            case 1:
                if (name.equals(command)) {
                    return name + " set to " + setObject(constructor.apply(args[0]));
                } else if ("get".equals(command) && name.equals(args[0])) {
                    return name + " is " + Objects.toString(getObject());
                } else {
                    return getUsage();
                }
            case 2:
                if("set".equals(command) && name.equals(args[0])){
                    return name + " set to " + setObject(constructor.apply(args[0]));
                } else {
                    return getUsage();
                }
            default:
                return getUsage();
        }
    }

    @Override
    public String getUsage() {
        return name + " [value]\n" +
                "get " + name + "\n" +
                "set " + name + " (value)";
    }

    /**
     * Getter for the field
     * @return the value stored in the field
     */
    protected T getObject(){
        try {
            return (T)field.get(object);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Getting field "+ field, ex);
        }
    }

    /**
     * Setter for the field
     * @param value the new value to store
     * @return the new value to stored
     */
    protected T setObject(T value){
        try {
            field.set(object, value);
            return value;
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Setting field "+ field, ex);
        }
    }
}
