package org.valkyrienskies.eureka.gfss

import com.google.common.collect.ImmutableSet
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.core.util.toImmutableSet
import java.util.stream.Collectors

class GlueEntityStateSet(states: Collection<SuperGlueEntity>, helmPos: BlockPos) {

    val glueStates: ImmutableSet<GlueEntityState> = states.stream()
                                                          .map { GlueEntityState(it, Vec3.atCenterOf(helmPos)) }
                                                          .collect(Collectors.toSet())
                                                          .toImmutableSet()

    fun reinstateAsSuperglue(newHelmPos: BlockPos, rotation: Rotation) : Set<SuperGlueEntity> {
        return glueStates.stream()
                         .map { it.spawnNew(newHelmPos, rotation) }
                         .collect(Collectors.toSet())
    }
}