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

package baritone.chat;

import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.event.events.ChatEvent;
import baritone.behavior.Behavior;
import baritone.chat.command.BooleanBasedCommand;
import baritone.chat.command.FieldBasedCommand;
import baritone.chat.command.ICommand;
import baritone.chat.command.ReflectMethodCommand;
import baritone.utils.Helper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class BaritoneChatControl extends Behavior implements Helper {

    public static BaritoneChatControl INSTANCE = new BaritoneChatControl();

    private BaritoneChatControl() {
    }

    public void initAndRegister() {
        registerSettingsCommands();
        registerCommands(ChatBuiltins.class);
        Baritone.INSTANCE.registerBehavior(this);
    }

    private final List<ICommand> commands = new ArrayList<>();

    public void registerCommand(ICommand command) {
        commands.add(command);
    }

    @Override
    public void onSendChatMessage(ChatEvent event) {
        if (!Baritone.settings().chatControl.get()) {
            if (!Baritone.settings().removePrefix.get()) {
                return;
            }
        }
        String msg = event.getMessage();
        if (Baritone.settings().prefix.get()) {
            if (!msg.startsWith("#")) {
                return;
            }
            msg = msg.substring(1);
        }
        msg = msg.toLowerCase();
        int splitpoint = msg.indexOf(' ');
        String command;
        String[] args;
        if (splitpoint == -1) {
            command = msg;
            args = new String[0];
        } else {
            command = msg.substring(0, splitpoint);
            args = msg.substring(splitpoint + 1).split(" ");
        }
        SafeCommandExecutor sce = new SafeCommandExecutor(command, args);
        Optional<String> results = commands.stream().filter(sce).map(sce).map("\n"::concat).reduce(String::concat); // gotta love it
        if (results.isPresent()) {
            event.cancel();
            logDirect(results.get().trim());
        }
    }

    private static class SafeCommandExecutor implements Function<ICommand, String>, Predicate<ICommand> {
        private String command;
        private String[] args;

        private SafeCommandExecutor(String command, String[] args) {
            this.command = command;
            this.args = args;
        }

        @Override
        public String apply(ICommand toRun) {
            try {
                return toRun.run(command, args);
            } catch (Exception ex) {
                return "Exception running chat: " + command + " " + String.join(" ", args) + "\n" + ex.getMessage();
            }
        }

        @Override
        public boolean test(ICommand toMatch) {
            try {
                return toMatch.matches(command, args);
            } catch (Exception ex) {
                System.out.println("Exception matching chat: " + command + " " + String.join(" ", args) + "\n" + ex.getMessage());
                return false;
            }
        }
    }

    private void registerSettingsCommands() {
        try {
            Map<Class<?>, Function<String, Object>> constructors = new HashMap<>();
            constructors.put(Long.class, Long::new);
            constructors.put(Integer.class, Integer::new);
            constructors.put(Short.class, Short::new);
            constructors.put(Byte.class, Byte::new);
            constructors.put(String.class, String::new);
            final Field settingField = Settings.Setting.class.getDeclaredField("value");
            for (Settings.Setting<?> setting : Baritone.settings().allSettings) {
                Class<?> clazz = setting.getValueClass();
                if (Boolean.class.equals(clazz)) {
                    registerCommand(new BooleanBasedCommand(settingField, setting.getName(), setting));
                } else if (constructors.containsKey(clazz)) {
                    registerCommand(new FieldBasedCommand<>(constructors.get(clazz), settingField, setting.getName(), setting));
                }
            }
        } catch (NoSuchFieldException e) {
            Helper.HELPER.logDirect(e.getMessage());
        }
    }

    private static final Class[] callableSignature = new Class[]{String.class, String[].class};

    private void registerCommands(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (Arrays.deepEquals(callableSignature, method.getParameterTypes())
                    && Modifier.isStatic(method.getModifiers())
                    && String.class.equals(method.getReturnType())
                    ) {
                for (Annotation annotation : method.getDeclaredAnnotations()) {
                    if (annotation instanceof ChatAccessible) {
                        ChatAccessible metadata = (ChatAccessible) annotation;
                        ICommand command;
                        switch (metadata.names().length) {
                            case 0:
                                command = new ReflectMethodCommand(metadata.argc(), method);
                                break;
                            case 1:
                                command = new ReflectMethodCommand(metadata.names()[0], metadata.argc(), method);
                                break;
                            default:
                                command = new ReflectMethodCommand(metadata.names(), metadata.argc(), method);
                                break;
                        }
                        registerCommand(command);
                    }
                }

            }
        }
    }
}
