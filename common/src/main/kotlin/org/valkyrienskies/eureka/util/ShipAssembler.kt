package org.valkyrienskies.eureka.util

import com.google.common.collect.Sets
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import org.joml.AxisAngle4d
import org.joml.Matrix4d
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.getAttachment
import org.valkyrienskies.core.api.ships.saveAttachment
import org.valkyrienskies.core.impl.networking.simple.sendToClient
import org.valkyrienskies.core.util.datastructures.DenseBlockPosSet
import org.valkyrienskies.eureka.EurekaConfig
import org.valkyrienskies.eureka.gfss.GlueEntityStateSet
import org.valkyrienskies.mod.common.assembly.createNewShipWithBlocks
import org.valkyrienskies.mod.common.executeIf
import org.valkyrienskies.mod.common.isTickingChunk
import org.valkyrienskies.mod.common.networking.PacketRestartChunkUpdates
import org.valkyrienskies.mod.common.networking.PacketStopChunkUpdates
import org.valkyrienskies.mod.common.playerWrapper
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.util.logger
import org.valkyrienskies.mod.util.relocateBlock
import org.valkyrienskies.mod.util.updateBlock
import java.util.*
import kotlin.collections.HashSet
import kotlin.collections.set
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sign

object ShipAssembler {
    fun collectBlocks(level: ServerLevel, center: BlockPos, predicate: (BlockState) -> Boolean): ServerShip? {
        val blocks = DenseBlockPosSet()

        blocks.add(center.toJOML())
        val optGlueEntities = bfs(level, center, blocks, predicate)

        // Glue entities is only not present if the ship is invalid.
        // 0 glue entities == an empty set returned as present.
        if (optGlueEntities.isPresent) {
            val ship = createNewShipWithBlocks(center, blocks, level)
            val glueEntities = optGlueEntities.get()
            val savedGlue = GlueEntityStateSet(glueEntities, center)

            ship.saveAttachment<GlueEntityStateSet>(savedGlue)
            glueEntities.forEach { it.discard() }

            return ship;
        } else {
            return null
        }
    }

    private fun roundToNearestMultipleOf(number: Double, multiple: Double) = multiple * round(number / multiple)

    // modified from https://gamedev.stackexchange.com/questions/83601/from-3d-rotation-snap-to-nearest-90-directions
    private fun snapRotation(direction: AxisAngle4d): AxisAngle4d {
        val x = abs(direction.x)
        val y = abs(direction.y)
        val z = abs(direction.z)
        val angle = roundToNearestMultipleOf(direction.angle, PI / 2)

        return if (x > y && x > z) {
            direction.set(angle, direction.x.sign, 0.0, 0.0)
        } else if (y > x && y > z) {
            direction.set(angle, 0.0, direction.y.sign, 0.0)
        } else {
            direction.set(angle, 0.0, 0.0, direction.z.sign)
        }
    }

    fun unfillShip(
        level: ServerLevel,
        ship: ServerShip,
        direction: Direction,
        fakeWorldCore: BlockPos,
        realWorldCore: BlockPos
    ) {
        ship.isStatic = true

        // ship's rotation rounded to nearest 90*
        val shipToWorld = ship.transform.run {
            Matrix4d()
                .translate(positionInWorld)
                .rotate(snapRotation(AxisAngle4d(shipToWorldRotation)))
                .scale(shipToWorldScaling)
                .translate(-positionInShip.x(), -positionInShip.y(), -positionInShip.z())
        }

        val alloc0 = Vector3d()

        // Direction comes from direction ship is aligning to
        // We can assume that the ship in shipspace is always facing north, because it has to be
        val rotation: Rotation = when (direction) {
            Direction.SOUTH -> Rotation.NONE // Bug in Direction.from2DDataValue() can return south/north as opposite
            Direction.NORTH -> Rotation.CLOCKWISE_180
            Direction.EAST -> Rotation.CLOCKWISE_90
            Direction.WEST -> Rotation.COUNTERCLOCKWISE_90
            else -> {
                Rotation.NONE
            }
        }

        val chunksToBeUpdated = mutableMapOf<ChunkPos, Pair<ChunkPos, ChunkPos>>()

        ship.activeChunksSet.forEach { chunkX, chunkZ ->
            chunksToBeUpdated[ChunkPos(chunkX, chunkZ)] =
                Pair(ChunkPos(chunkX, chunkZ), ChunkPos(chunkX, chunkZ))
        }

        val chunkPairs = chunksToBeUpdated.values.toList()
        val chunkPoses = chunkPairs.flatMap { it.toList() }
        val chunkPosesJOML = chunkPoses.map { it.toJOML() }

        // Send a list of all the chunks that we plan on updating to players, so that they
        // defer all updates until assembly is finished
        level.players().forEach { player ->
            PacketStopChunkUpdates(chunkPosesJOML).sendToClient(player.playerWrapper)
        }

        val toUpdate = Sets.newHashSet<Triple<BlockPos, BlockPos, BlockState>>()

        ship.activeChunksSet.forEach { chunkX, chunkZ ->
            val chunk = level.getChunk(chunkX, chunkZ)
            for (section in chunk.sections) {
                if (section == null || section.hasOnlyAir()) continue
                for (x in 0..15) {
                    for (y in 0..15) {
                        for (z in 0..15) {
                            val state = section.getBlockState(x, y, z)
                            if (state.isAir) continue

                            val realX = (chunkX shl 4) + x
                            val realY = section.bottomBlockY() + y
                            val realZ = (chunkZ shl 4) + z

                            val inWorldPos =
                                shipToWorld.transformPosition(alloc0.set(realX + 0.5, realY + 0.5, realZ + 0.5)).floor()

                            val inWorldBlockPos =
                                BlockPos(inWorldPos.x.toInt(), inWorldPos.y.toInt(), inWorldPos.z.toInt())
                            val inShipPos = BlockPos(realX, realY, realZ)

                            toUpdate.add(Triple(inShipPos, inWorldBlockPos, state))
                            level.relocateBlock(inShipPos, inWorldBlockPos, false, null, rotation)
                        }
                    }
                }
            }
        }

        // If glue has been saved (which it should be in all cases with this modified eureka)
        // there should be a saved version of the glue.
        ship.getAttachment<GlueEntityStateSet>()?.reinstateAsSuperglue(realWorldCore, ship.transform.shipToWorldRotation, level)

        // We update the blocks after they're set to prevent blocks from breaking
        for (triple in toUpdate) {
            updateBlock(level, triple.first, triple.second, triple.third)
        }

        level.server.executeIf(
            // This condition will return true if all modified chunks have been both loaded AND
            // chunk update packets were sent to players
            { chunkPoses.all(level::isTickingChunk) }
        ) {
            // Once all the chunk updates are sent to players, we can tell them to restart chunk updates
            level.players().forEach { player ->
                PacketRestartChunkUpdates(chunkPosesJOML).sendToClient(player.playerWrapper)
            }
        }
    }

    private fun bfs(
        level: ServerLevel,
        start: BlockPos,
        blocks: DenseBlockPosSet,
        predicate: (BlockState) -> Boolean
    ): Optional<Set<SuperGlueEntity>> {
        //val blacklist = DenseBlockPosSet()

        val visited = DenseBlockPosSet()
        val stack = ObjectArrayList<BlockPos>()
        val glueCache = HashSet<SuperGlueEntity>()

        directed(start) { direction, pos ->
            val glued = SuperGlueEntity.isGlued(level, start, direction, glueCache)

            if(glued) {
                stack.push(pos)
            }
        }

        while (!stack.isEmpty) {
            val pos = stack.pop()

            if (predicate(level.getBlockState(pos))) {
                blocks.add(pos.x, pos.y, pos.z)

                directed(pos) { direction, adjPos ->
                    //if (!blacklist.contains(adjPos.x, adjPos.y, adjPos.z)) {
                    //    blacklist.add(adjPos.x, adjPos.y, adjPos.z)
                    //    stack.push(adjPos)
                    //}

                    // Skip any blocks where we've already asserted that they're connected.
                    if(visited.contains(adjPos.x, adjPos.y, adjPos.z))
                        return@directed

                    if(SuperGlueEntity.isGlued(level, pos, direction, glueCache))
                        stack.push(adjPos)
                }

                // Successfully explored - add to visited so that we avoid this block
                // in the future.
                visited.add(pos.x, pos.y, pos.z)
            }


            if ((EurekaConfig.SERVER.maxShipBlocks > 0) and (blocks.size > EurekaConfig.SERVER.maxShipBlocks)) {
                logger.info("Stopped ship assembly due too many blocks")
                return Optional.empty()
            }
        }
        if (EurekaConfig.SERVER.maxShipBlocks > 0) {
            logger.info("Assembled ship with ${blocks.size} blocks, out of ${EurekaConfig.SERVER.maxShipBlocks} allowed")
        }

        return Optional.of(glueCache)
    }

    private fun directed(origin: BlockPos, lambda: (Direction, BlockPos) -> Unit) {
        // For each side, run the passed methpd.
        Direction.entries.forEach {
            lambda(it, origin.relative(it))
        }
    }

    private val logger by logger()
}
