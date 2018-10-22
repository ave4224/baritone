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
import baritone.api.cache.IWaypoint;
import baritone.api.pathing.goals.*;
import baritone.api.utils.RayTraceUtils;
import baritone.behavior.FollowBehavior;
import baritone.behavior.MineBehavior;
import baritone.behavior.PathingBehavior;
import baritone.cache.ChunkPacker;
import baritone.cache.Waypoint;
import baritone.cache.WorldProvider;
import baritone.chat.command.GoalCommand;
import baritone.chat.command.ICommand;
import baritone.pathing.calc.AbstractNodeCostSearch;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.Moves;
import baritone.utils.BlockStateInterface;
import baritone.utils.Helper;
import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ChatBuiltins {

    private ChatBuiltins() {
    }

    @ChatAccessible(names = {"settings", "baritone"})
    static String settings(String command, String[] args) {
        return String.join("\n", Baritone.settings().allSettings.stream().map(Settings.Setting::toString).toArray(String[]::new));
    }

    @ChatAccessible(names = {"repack", "rescan"})
    static String rescan(String command, String[] args) {
        Helper helper = Helper.HELPER;
        ChunkProviderClient cli = helper.world().getChunkProvider();
        int playerChunkX = helper.playerFeet().getX() >> 4;
        int playerChunkZ = helper.playerFeet().getZ() >> 4;
        int count = 0;
        for (int x = playerChunkX - 40; x <= playerChunkX + 40; x++) {
            for (int z = playerChunkZ - 40; z <= playerChunkZ + 40; z++) {
                Chunk chunk = cli.getLoadedChunk(x, z);
                if (chunk != null) {
                    count++;
                    WorldProvider.INSTANCE.getCurrentWorld().getCachedWorld().queueForPacking(chunk);
                }
            }
        }
        return "Queued " + count + " chunks for repacking";
    }

    @ChatAccessible(names = {"cancel", "stop"})
    static String cancel(String command, String[] args) {
        MineBehavior.INSTANCE.cancel();
        FollowBehavior.INSTANCE.cancel();
        PathingBehavior.INSTANCE.cancel();
        return "ok canceled";
    }

    @ChatAccessible(names = {"forcecancel"})
    static String forceCancel(String command, String[] args) {
        MineBehavior.INSTANCE.cancel();
        FollowBehavior.INSTANCE.cancel();
        PathingBehavior.INSTANCE.cancel();
        AbstractNodeCostSearch.forceCancel();
        PathingBehavior.INSTANCE.forceCancel();
        return "ok force canceled";
    }

    @ChatAccessible(names = {"gc", "garbage"})
    static String garbageCollect(String command, String[] args) {
        System.gc();
        return "Called System.gc();";
    }

    @ChatAccessible(names = {"follow", "ray"})
    static String follow(String command, String[] args) {
        Optional<Entity> toFollow = RayTraceUtils.getSelectedEntity();
        if (toFollow.isPresent()) {
            FollowBehavior.INSTANCE.follow(toFollow.get());
            return "Following" + toFollow.get();
        } else {
            return "No entity targeted";
        }
    }

    @ChatAccessible(argc = 1, names = {"follow", "followplayer"})
    static String followplayer(String command, String[] args) {
        for (EntityPlayer ep : Helper.HELPER.world().playerEntities) {
            if (ep.getName().toLowerCase().equals(args[0]) && ep != Helper.HELPER.player()) {
                FollowBehavior.INSTANCE.follow(ep);
                return "Following player " + ep.getName();
            }
        }
        return "No player " + args[0] + " found";
    }

    @ChatAccessible()
    static String reloadall(String command, String[] args) {
        WorldProvider.INSTANCE.getCurrentWorld().getCachedWorld().reloadAllFromDisk();
        return "reloaded";
    }

    @ChatAccessible()
    static String saveall(String command, String[] args) {
        WorldProvider.INSTANCE.getCurrentWorld().getCachedWorld().save();
        return "saved";
    }

    @ChatAccessible(argc = 1)
    static String find(String command, String[] args) {
        String blockType = args[0];
        LinkedList<BlockPos> locs = WorldProvider.INSTANCE.getCurrentWorld().getCachedWorld().getLocationsOf(blockType, 1, 4);
        for (BlockPos pos : locs) {
            Block actually = BlockStateInterface.get(pos).getBlock();
            if (!ChunkPacker.blockToString(actually).equalsIgnoreCase(blockType)) {
                System.out.println("Was looking for " + blockType + " but actually found " + actually + " " + ChunkPacker.blockToString(actually));
            }
        }
        return "Have " + locs.size() + " locations";
    }

    @ChatAccessible()
    static String path(String command, String[] args) {
        if (!PathingBehavior.INSTANCE.path()) {
            if (PathingBehavior.INSTANCE.getGoal() == null) {
                return "No goal.";
            } else {
                if (PathingBehavior.INSTANCE.getGoal().isInGoal(Helper.HELPER.playerFeet())) {
                    return "Already in goal";
                } else {
                    return "Currently executing a path. Please cancel it first.";
                }
            }
        }
        return "";
    }

    @ChatAccessible()
    static String costs(String command, String[] args) {
        return Stream.of(Moves.values()).map(x -> x.apply0(Helper.HELPER.playerFeet())).filter(Objects::nonNull).sorted(Comparator.comparingDouble(Movement::getCost)).map(Movement::toString).collect(Collectors.joining("\n"));
    }

    @ChatAccessible(argc = 1, names = {"list", "waypoints", "get", "show"})
    static String waypointlist(String command, String[] args) {
        String type = args[0];
        if (type.endsWith("s")) {
            // for example, "show deaths"
            type = type.substring(0, type.length() - 1);
        }
        //return Optional.of(Waypoint.Tag.fromString(type)).map(WorldProvider.INSTANCE.getCurrentWorld().getWaypoints()::getByTag).map(Set<IWaypoint>::stream).map(x->x.sorted(Comparator.comparingLong(IWaypoint::getCreationTimestamp))).map(x->x.map(IWaypoint::toString)).map(x->x.collect(Collectors.joining("\n"))).orElse("Not a valid tag. Tags are: " + Arrays.asList(Waypoint.Tag.values()).toString().toLowerCase());
        Waypoint.Tag tag = Waypoint.Tag.fromString(type);
        if (tag == null) {
            return ("Not a valid tag. Tags are: " + Arrays.asList(Waypoint.Tag.values()).toString().toLowerCase());
        }
        // might as well show them from oldest to newest
        return WorldProvider.INSTANCE.getCurrentWorld().getWaypoints().getByTag(tag).stream().sorted(Comparator.comparingLong(IWaypoint::getCreationTimestamp)).map(IWaypoint::toString).collect(Collectors.joining("\n")); // good good more of this
    }

    @ChatAccessible(argc = 1, names = {"save"})
    static String savePos(String command, String[] args) {
        BlockPos pos = Helper.HELPER.playerFeet();
        WorldProvider.INSTANCE.getCurrentWorld().getWaypoints().addWaypoint(new Waypoint(args[0], Waypoint.Tag.USER, pos));
        return "Saved user defined position " + pos + " under name '" + args[0] + "'. Say 'goto user' to set goal, say 'list user' to list.";
    }

    @ChatAccessible(argc = 4, names = {"save"})
    static String saveXYZ(String command, String[] args) {
        BlockPos pos = Helper.HELPER.playerFeet();
        try {
            pos = new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
        } catch (NumberFormatException ex) {
            return "Unable to parse coordinate integers";
        }
        WorldProvider.INSTANCE.getCurrentWorld().getWaypoints().addWaypoint(new Waypoint(args[0], Waypoint.Tag.USER, pos));
        return "Saved user defined position " + pos + " under name '" + args[0] + "'. Say 'goto user' to set goal, say 'list user' to list.";
    }

    @ChatAccessible()
    static String sethome(String command, String[] args) {
        WorldProvider.INSTANCE.getCurrentWorld().getWaypoints().addWaypoint(new Waypoint("", Waypoint.Tag.HOME, Helper.HELPER.playerFeet()));
        return "Saved. Say home to set goal.";
    }

    private static final class FUNCTIONS {

        private static final ICommand[] COMMAND_LISTING = new ICommand[]{
                new GoalCommand("goal", FUNCTIONS::constructAtPlayer),
                new GoalCommand("goal", FUNCTIONS::constructAtYPos, "goal y"),
                new GoalCommand("goal", 2, FUNCTIONS::constructAtXZPos, "goal x z"),
                new GoalCommand("goal", 3, FUNCTIONS::constructAtXYZPos, "goal x y z"),
                new GoalCommand("axis", GoalAxis::new),
                new GoalCommand("thisway", FUNCTIONS::constructInDirection, "thisway distance"),
                new GoalCommand("invert", FUNCTIONS::invert),
                new ICommand() {
                    @Override
                    public boolean matches(String command, String[] args) {
                        return "mine".equals(command);
                    }

                    @Override
                    public String getUsage() {
                        return "mine\nmine quantity block\nmine block1 block2...";
                    }

                    @Override
                    public String run(String command, String[] args) {
                        switch (args.length) {
                            case 0:
                                MineBehavior.INSTANCE.cancel();
                                return "Mining cancelled";
                            case 1:
                                Block b = ChunkPacker.stringToBlock(args[0]);
                                if (b == null) {
                                    return "Unknown block: " + args[0];
                                } else {
                                    MineBehavior.INSTANCE.mine(0, b);
                                    return "Mining block: " + b.getLocalizedName();
                                }
                            case 2:
                                try {
                                    Integer q = new Integer(args[0]);
                                    Block bl = ChunkPacker.stringToBlock(args[1]);
                                    if (bl == null) {
                                        return "Unknown block: " + args[1];
                                    } else {
                                        MineBehavior.INSTANCE.mine(q, bl);
                                        return "Mining block: " + bl.getLocalizedName();
                                    }
                                } catch (NumberFormatException e) {
                                    Block b0 = ChunkPacker.stringToBlock(args[0]);
                                    Block b1 = ChunkPacker.stringToBlock(args[1]);
                                    if (b0 == null) {
                                        return "Unknown number or block: " + args[0];
                                    } else if (b1 == null) {
                                        return "Unknown block: " + args[1];
                                    } else {
                                        MineBehavior.INSTANCE.mine(0, b0, b1);
                                        return "Mining started";
                                    }
                                }
                            default:
                                Block[] blocks = new Block[args.length];
                                for (int i = 0; i < blocks.length; i++) {
                                    blocks[i] = ChunkPacker.stringToBlock(args[i]);
                                    if (blocks[i] == null) {
                                        return "Unknown block: " + args[i];
                                    }
                                }
                                MineBehavior.INSTANCE.mine(0, blocks);
                                return "Mining started";
                        }
                    }
                }
        };

        private static Goal constructAtPlayer() {
            return new GoalBlock(Helper.HELPER.playerFeet());
        }

        private static Goal constructAtYPos(String s) {
            return new GoalYLevel(new Integer(s));
        }

        private static Goal constructAtXZPos(String[] s) {
            return new GoalXZ(new Integer(s[0]), new Integer(s[1]));
        }

        private static Goal constructAtXYZPos(String[] s) {
            return new GoalBlock(new Integer(s[0]), new Integer(s[1]), new Integer(s[2]));
        }

        private static Goal constructInDirection(String s) {
            Helper helper = Helper.HELPER;
            return GoalXZ.fromDirection(helper.playerFeetAsVec(), helper.player().rotationYaw, new Double(s));
        }

        private static Goal invert() {
            Goal goal = PathingBehavior.INSTANCE.getGoal();
            BlockPos runAwayFrom;
            if (goal instanceof GoalXZ) {
                runAwayFrom = new BlockPos(((GoalXZ) goal).getX(), 0, ((GoalXZ) goal).getZ());
            } else if (goal instanceof GoalBlock) {
                runAwayFrom = ((GoalBlock) goal).getGoalPos();
            } else {
                runAwayFrom = Helper.HELPER.playerFeet();
            }
            return (new GoalRunAway(1, runAwayFrom) {
                @Override
                public boolean isInGoal(BlockPos pos) {
                    return false;
                }
            });
        }

    }

}
