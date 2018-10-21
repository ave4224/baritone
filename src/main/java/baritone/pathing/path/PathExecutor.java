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

package baritone.pathing.path;

import baritone.Baritone;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.movement.ActionCosts;
import baritone.api.pathing.movement.IMovement;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.VecUtils;
import baritone.pathing.calc.AbstractNodeCostSearch;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.movements.*;
import baritone.utils.BlockBreakHelper;
import baritone.utils.BlockStateInterface;
import baritone.utils.Helper;
import baritone.utils.InputOverrideHandler;
import net.minecraft.init.Blocks;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;

import java.util.*;

import static baritone.api.pathing.movement.MovementStatus.*;

/**
 * Behavior to execute a precomputed path. Does not (yet) deal with path segmentation or stitching
 * or cutting (jumping onto the next path if it starts with a backtrack of this path's ending)
 *
 * @author leijurv
 */
public class PathExecutor implements IPathExecutor, Helper {
    private static final double MAX_MAX_DIST_FROM_PATH = 3;
    private static final double MAX_DIST_FROM_PATH = 2;

    /**
     * Default value is equal to 10 seconds. It's find to decrease it, but it must be at least 5.5s (110 ticks).
     * For more information, see issue #102.
     *
     * @see <a href="https://github.com/cabaletta/baritone/issues/102">Issue #102</a>
     * @see <a href="https://i.imgur.com/5s5GLnI.png">Anime</a>
     */
    private static final double MAX_TICKS_AWAY = 200;

    private final IPath path;
    private int pathPosition;
    private int ticksAway;
    private int ticksOnCurrent;
    private Double currentMovementOriginalCostEstimate;
    private Integer costEstimateIndex;
    private boolean failed;
    private boolean recalcBP = true;
    private HashSet<BlockPos> toBreak = new HashSet<>();
    private HashSet<BlockPos> toPlace = new HashSet<>();
    private HashSet<BlockPos> toWalkInto = new HashSet<>();

    public PathExecutor(IPath path) {
        this.path = path;
        this.pathPosition = 0;
    }

    /**
     * Tick this executor
     *
     * @return True if a movement just finished (and the player is therefore in a "stable" state, like,
     * not sneaking out over lava), false otherwise
     */
    public boolean onTick() {
        if (pathPosition == path.length() - 1) {
            pathPosition++;
        }
        if (pathPosition >= path.length()) {
            return true; // stop bugging me, I'm done
        }
        BetterBlockPos whereShouldIBe = path.positions().get(pathPosition);
        BetterBlockPos whereAmI = playerFeet();
        if (!whereShouldIBe.equals(whereAmI)) {

            if (pathPosition == 0 && whereAmI.equals(whereShouldIBe.up()) && Math.abs(player().motionY) < 0.1 && !(path.movements().get(0) instanceof MovementAscend) && !(path.movements().get(0) instanceof MovementPillar)) {
                // avoid the Wrong Y coordinate bug
                // TODO add a timer here
                new MovementDownward(whereAmI, whereShouldIBe).update();
                return false;
            }

            //System.out.println("Should be at " + whereShouldIBe + " actually am at " + whereAmI);
            if (!Blocks.AIR.equals(BlockStateInterface.getBlock(whereAmI.down()))) {//do not skip if standing on air, because our position isn't stable to skip
                for (int i = 0; i < pathPosition - 1 && i < path.length(); i++) {//this happens for example when you lag out and get teleported back a couple blocks
                    if (whereAmI.equals(path.positions().get(i))) {
                        logDebug("Skipping back " + (pathPosition - i) + " steps, to " + i);
                        int previousPos = pathPosition;
                        pathPosition = Math.max(i - 1, 0); // previous step might not actually be done
                        for (int j = pathPosition; j <= previousPos; j++) {
                            path.movements().get(j).reset();
                        }
                        onChangeInPathPosition();
                        return false;
                    }
                }
                for (int i = pathPosition + 3; i < path.length(); i++) { //dont check pathPosition+1. the movement tells us when it's done (e.g. sneak placing)
                    // also don't check pathPosition+2 because reasons
                    if (whereAmI.equals(path.positions().get(i))) {
                        if (i - pathPosition > 2) {
                            logDebug("Skipping forward " + (i - pathPosition) + " steps, to " + i);
                        }
                        //System.out.println("Double skip sundae");
                        pathPosition = i - 1;
                        onChangeInPathPosition();
                        return false;
                    }
                }
            }
        }
        Tuple<Double, BlockPos> status = closestPathPos(path);
        if (possiblyOffPath(status, MAX_DIST_FROM_PATH)) {
            ticksAway++;
            System.out.println("FAR AWAY FROM PATH FOR " + ticksAway + " TICKS. Current distance: " + status.getFirst() + ". Threshold: " + MAX_DIST_FROM_PATH);
            if (ticksAway > MAX_TICKS_AWAY) {
                logDebug("Too far away from path for too long, cancelling path");
                cancel();
                return false;
            }
        } else {
            ticksAway = 0;
        }
        if (possiblyOffPath(status, MAX_MAX_DIST_FROM_PATH)) { // ok, stop right away, we're way too far.
            logDebug("too far from path");
            cancel();
            return false;
        }
        //this commented block is literally cursed.
        /*Out.log(actions.get(pathPosition));
        if (pathPosition < actions.size() - 1) {//if there are two ActionBridges in a row and they are at right angles, walk diagonally. This makes it so you walk at 45 degrees along a zigzag path instead of doing inefficient zigging and zagging
            if ((actions.get(pathPosition) instanceof ActionBridge) && (actions.get(pathPosition + 1) instanceof ActionBridge)) {
                ActionBridge curr = (ActionBridge) actions.get(pathPosition);
                ActionBridge next = (ActionBridge) actions.get(pathPosition + 1);
                if (curr.dx() != next.dx() || curr.dz() != next.dz()) {//two movement are not parallel, so this is a right angle
                    if (curr.amIGood() && next.amIGood()) {//nothing in the way
                        BlockPos cornerToCut1 = new BlockPos(next.to.getX() - next.from.getX() + curr.from.getX(), next.to.getY(), next.to.getZ() - next.from.getZ() + curr.from.getZ());
                        BlockPos cornerToCut2 = cornerToCut1.up();
                        //Block corner1 = Baritone.get(cornerToCut1).getBlock();
                        //Block corner2 = Baritone.get(cornerToCut2).getBlock();
                        //Out.gui("Cutting conner " + cornerToCut1 + " " + corner1, Out.Mode.Debug);
                        if (!Action.avoidWalkingInto(cornerToCut1) && !Action.avoidWalkingInto(cornerToCut2)) {
                            double x = (next.from.getX() + next.to.getX() + 1.0D) * 0.5D;
                            double z = (next.from.getZ() + next.to.getZ() + 1.0D) * 0.5D;
                            MovementManager.clearMovement();
                            if (!MovementManager.forward && curr.oneInTen != null && curr.oneInTen) {
                                MovementManager.clearMovement();
                                MovementManager.forward = LookManager.lookAtCoords(x, 0, z, false);
                            } else {
                                MovementManager.moveTowardsCoords(x, 0, z);
                            }
                            if (MovementManager.forward && !MovementManager.backward) {
                                thePlayer.setSprinting(true);
                            }
                            return false;
                        }
                    }
                }
            }
        }*/
        //long start = System.nanoTime() / 1000000L;
        for (int i = pathPosition - 10; i < pathPosition + 10; i++) {
            if (i < 0 || i >= path.movements().size()) {
                continue;
            }
            IMovement m = path.movements().get(i);
            HashSet<BlockPos> prevBreak = new HashSet<>(m.toBreak());
            HashSet<BlockPos> prevPlace = new HashSet<>(m.toPlace());
            HashSet<BlockPos> prevWalkInto = new HashSet<>(m.toWalkInto());
            m.resetBlockCache();
            if (!prevBreak.equals(new HashSet<>(m.toBreak()))) {
                recalcBP = true;
            }
            if (!prevPlace.equals(new HashSet<>(m.toPlace()))) {
                recalcBP = true;
            }
            if (!prevWalkInto.equals(new HashSet<>(m.toWalkInto()))) {
                recalcBP = true;
            }
        }
        if (recalcBP) {
            HashSet<BlockPos> newBreak = new HashSet<>();
            HashSet<BlockPos> newPlace = new HashSet<>();
            HashSet<BlockPos> newWalkInto = new HashSet<>();
            for (int i = pathPosition; i < path.movements().size(); i++) {
                newBreak.addAll(path.movements().get(i).toBreak());
                newPlace.addAll(path.movements().get(i).toPlace());
                newWalkInto.addAll(path.movements().get(i).toWalkInto());
            }
            toBreak = newBreak;
            toPlace = newPlace;
            toWalkInto = newWalkInto;
            recalcBP = false;
        }
        /*long end = System.nanoTime() / 1000000L;
        if (end - start > 0) {
            System.out.println("Recalculating break and place took " + (end - start) + "ms");
        }*/
        IMovement movement = path.movements().get(pathPosition);
        boolean canCancel = movement.safeToCancel();
        if (costEstimateIndex == null || costEstimateIndex != pathPosition) {
            costEstimateIndex = pathPosition;
            // do this only once, when the movement starts, and deliberately get the cost as cached when this path was calculated, not the cost as it is right now
            currentMovementOriginalCostEstimate = movement.getCost();
            for (int i = 1; i < Baritone.settings().costVerificationLookahead.get() && pathPosition + i < path.length() - 1; i++) {
                if (path.movements().get(pathPosition + i).calculateCostWithoutCaching() >= ActionCosts.COST_INF && canCancel) {
                    logDebug("Something has changed in the world and a future movement has become impossible. Cancelling.");
                    cancel();
                    return true;
                }
            }
        }
        double currentCost = movement.recalculateCost();
        if (currentCost >= ActionCosts.COST_INF && canCancel) {
            logDebug("Something has changed in the world and this movement has become impossible. Cancelling.");
            cancel();
            return true;
        }
        if (!movement.calculatedWhileLoaded() && currentCost - currentMovementOriginalCostEstimate > Baritone.settings().maxCostIncrease.get() && canCancel) {
            logDebug("Original cost " + currentMovementOriginalCostEstimate + " current cost " + currentCost + ". Cancelling.");
            cancel();
            return true;
        }
        if (shouldPause()) {
            logDebug("Pausing since current best path is a backtrack");
            clearKeys();
            return true;
        }
        MovementStatus movementStatus = movement.update();
        if (movementStatus == UNREACHABLE || movementStatus == FAILED) {
            logDebug("Movement returns status " + movementStatus);
            cancel();
            return true;
        }
        if (movementStatus == SUCCESS) {
            //System.out.println("Movement done, next path");
            pathPosition++;
            onChangeInPathPosition();
            onTick();
            return true;
        } else {
            sprintIfRequested();
            ticksOnCurrent++;
            if (ticksOnCurrent > currentMovementOriginalCostEstimate + Baritone.settings().movementTimeoutTicks.get()) {
                // only cancel if the total time has exceeded the initial estimate
                // as you break the blocks required, the remaining cost goes down, to the point where
                // ticksOnCurrent is greater than recalculateCost + 100
                // this is why we cache cost at the beginning, and don't recalculate for this comparison every tick
                logDebug("This movement has taken too long (" + ticksOnCurrent + " ticks, expected " + currentMovementOriginalCostEstimate + "). Cancelling.");
                cancel();
                return true;
            }
        }
        return canCancel; // movement is in progress, but if it reports cancellable, PathingBehavior is good to cut onto the next path
    }

    private Tuple<Double, BlockPos> closestPathPos(IPath path) {
        double best = -1;
        BlockPos bestPos = null;
        for (BlockPos pos : path.positions()) {
            double dist = VecUtils.entityDistanceToCenter(player(), pos);
            if (dist < best || best == -1) {
                best = dist;
                bestPos = pos;
            }
        }
        return new Tuple<>(best, bestPos);
    }

    private boolean shouldPause() {
        Optional<AbstractNodeCostSearch> current = AbstractNodeCostSearch.getCurrentlyRunning();
        if (!current.isPresent()) {
            return false;
        }
        if (!player().onGround) {
            return false;
        }
        if (!MovementHelper.canWalkOn(playerFeet().down())) {
            // we're in some kind of sketchy situation, maybe parkouring
            return false;
        }
        if (!MovementHelper.canWalkThrough(playerFeet()) || !MovementHelper.canWalkThrough(playerFeet().up())) {
            // suffocating?
            return false;
        }
        if (!path.movements().get(pathPosition).safeToCancel()) {
            return false;
        }
        Optional<IPath> currentBest = current.get().bestPathSoFar();
        if (!currentBest.isPresent()) {
            return false;
        }
        List<BetterBlockPos> positions = currentBest.get().positions();
        if (positions.size() < 3) {
            return false; // not long enough yet to justify pausing, its far from certain we'll actually take this route
        }
        // the first block of the next path will always overlap
        // no need to pause our very last movement when it would have otherwise cleanly exited with MovementStatus SUCCESS
        positions = positions.subList(1, positions.size());
        return positions.contains(playerFeet());
    }

    private boolean possiblyOffPath(Tuple<Double, BlockPos> status, double leniency) {
        double distanceFromPath = status.getFirst();
        if (distanceFromPath > leniency) {
            // when we're midair in the middle of a fall, we're very far from both the beginning and the end, but we aren't actually off path
            if (path.movements().get(pathPosition) instanceof MovementFall) {
                BlockPos fallDest = path.positions().get(pathPosition + 1); // .get(pathPosition) is the block we fell off of
                return VecUtils.entityFlatDistanceToCenter(player(), fallDest) >= leniency; // ignore Y by using flat distance
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    /**
     * Regardless of current path position, snap to the current player feet if possible
     */
    public boolean snipsnapifpossible() {
        int index = path.positions().indexOf(playerFeet());
        if (index == -1) {
            return false;
        }
        pathPosition = index;
        clearKeys();
        return true;
    }

    // TODO: Make this use a helper to discover if sprinting is good then do it if it is
    private void sprintIfRequested() {

        // first and foremost, if allowSprint is off, or if we don't have enough hunger, don't try and sprint
        if (!new CalculationContext().canSprint()) {
            Baritone.INSTANCE.getInputOverrideHandler().setInputForceState(InputOverrideHandler.Input.SPRINT, false);
            player().setSprinting(false);
            return;
        }

        // if the movement requested sprinting, then we're done
        if (Baritone.INSTANCE.getInputOverrideHandler().isInputForcedDown(mc.gameSettings.keyBindSprint)) {
            if (!player().isSprinting()) {
                player().setSprinting(true);
            }
            return;
        }

        // we'll take it from here, no need for minecraft to see we're holding down control and sprint for us
        Baritone.INSTANCE.getInputOverrideHandler().setInputForceState(InputOverrideHandler.Input.SPRINT, false);

        // however, descend doesn't request sprinting, because it doesn't know the context of what movement comes after it
        IMovement current = path.movements().get(pathPosition);
        if (current instanceof MovementDescend && pathPosition < path.length() - 2) {

            // (dest - src) + dest is offset 1 more in the same direction
            // so it's the block we'd need to worry about running into if we decide to sprint straight through this descend

            BlockPos into = current.getDest().subtract(current.getSrc().down()).add(current.getDest());
            for (int y = 0; y <= 2; y++) { // we could hit any of the three blocks
                if (MovementHelper.avoidWalkingInto(BlockStateInterface.getBlock(into.up(y)))) {
                    logDebug("Sprinting would be unsafe");
                    player().setSprinting(false);
                    return;
                }
            }

            IMovement next = path.movements().get(pathPosition + 1);
            if (next instanceof MovementAscend && current.getDirection().up().equals(next.getDirection().down())) {
                // a descend then an ascend in the same direction
                if (!player().isSprinting()) {
                    player().setSprinting(true);
                }
                pathPosition++;
                // okay to skip clearKeys and / or onChangeInPathPosition here since this isn't possible to repeat, since it's asymmetric
                logDebug("Skipping descend to straight ascend");
                return;
            }
            if (canSprintInto(current, next)) {
                if (playerFeet().equals(current.getDest())) {
                    pathPosition++;
                    onChangeInPathPosition();
                }
                if (!player().isSprinting()) {
                    player().setSprinting(true);
                }
                return;
            }
            //logDebug("Turning off sprinting " + movement + " " + next + " " + movement.getDirection() + " " + next.getDirection().down() + " " + next.getDirection().down().equals(movement.getDirection()));
        }
        if (current instanceof MovementAscend && pathPosition != 0) {
            IMovement prev = path.movements().get(pathPosition - 1);
            if (prev instanceof MovementDescend && prev.getDirection().up().equals(current.getDirection().down())) {
                BlockPos center = current.getSrc().up();
                if (player().posY >= center.getY()) { // playerFeet adds 0.1251 to account for soul sand
                    Baritone.INSTANCE.getInputOverrideHandler().setInputForceState(InputOverrideHandler.Input.JUMP, false);
                    if (!player().isSprinting()) {
                        player().setSprinting(true);
                    }
                    return;
                }
            }
        }
        player().setSprinting(false);
    }

    private static boolean canSprintInto(IMovement current, IMovement next) { // TODO: Move this into IMovement
        if (next instanceof MovementDescend) {
            if (next.getDirection().equals(current.getDirection())) {
                return true;
            }
        }
        if (next instanceof MovementTraverse) {
            if (next.getDirection().down().equals(current.getDirection()) && MovementHelper.canWalkOn(next.getDest().down())) {
                return true;
            }
        }
        return next instanceof MovementDiagonal && Baritone.settings().allowOvershootDiagonalDescend.get();
    }

    private void onChangeInPathPosition() {
        clearKeys();
        ticksOnCurrent = 0;
    }

    private static void clearKeys() {
        // i'm just sick and tired of this snippet being everywhere lol
        Baritone.INSTANCE.getInputOverrideHandler().clearAllKeys();
    }

    private void cancel() {
        clearKeys();
        BlockBreakHelper.stopBreakingBlock();
        pathPosition = path.length() + 3;
        failed = true;
    }

    public int getPosition() {
        return pathPosition;
    }

    @Override
    public IPath getPath() {
        return path;
    }

    public boolean failed() {
        return failed;
    }

    public boolean finished() {
        return pathPosition >= path.length();
    }

    public Set<BlockPos> toBreak() {
        return Collections.unmodifiableSet(toBreak);
    }

    public Set<BlockPos> toPlace() {
        return Collections.unmodifiableSet(toPlace);
    }

    public Set<BlockPos> toWalkInto() {
        return Collections.unmodifiableSet(toWalkInto);
    }
}
