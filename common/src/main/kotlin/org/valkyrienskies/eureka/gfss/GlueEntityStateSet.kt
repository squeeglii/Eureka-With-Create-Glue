package org.valkyrienskies.eureka.gfss

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.common.collect.ImmutableSet
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.valkyrienskies.core.util.toImmutableSet
import java.util.Objects
import java.util.stream.Collectors

@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE
)
@JsonIgnoreProperties(ignoreUnknown = true)
class GlueEntityStateSet(states: Collection<SuperGlueEntity>, helmPos: BlockPos) {

    val glueStates: ImmutableSet<GlueEntityState> = states.stream()
                                                          .map { GlueEntityState(it, Vec3.atCenterOf(helmPos)) }
                                                          .collect(Collectors.toSet())
                                                          .toImmutableSet()

    fun reinstateAsSuperglue(newHelmPos: BlockPos, rotation: Rotation, level: ServerLevel) : Set<SuperGlueEntity> {

        val helmCenter = Vec3.atCenterOf(newHelmPos)
        val rotMat = rotation.rotation().transformation()
        val jomlRotMat = mojMathToJomlMatrix(rotMat)

        return glueStates.stream()
                         .map { it.spawnNew(helmCenter, jomlRotMat, level) }
                         .filter { Objects.nonNull(it) }
                         .map { it as SuperGlueEntity }
                         .collect(Collectors.toSet())
    }

    private fun mojMathToJomlMatrix(mojMat: com.mojang.math.Matrix3f) : Matrix3f {
        return Matrix3f(mojMat.m00, mojMat.m01, mojMat.m02,
                        mojMat.m10, mojMat.m11, mojMat.m12,
                        mojMat.m20, mojMat.m21, mojMat.m22)
    }
}