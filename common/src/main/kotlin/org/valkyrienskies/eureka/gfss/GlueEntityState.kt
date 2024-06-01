package org.valkyrienskies.eureka.gfss

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3dc

class GlueEntityState(srcEntity: SuperGlueEntity, helmBlockCenter: Vec3) {

    val boundingBoxCenter: Vec3 = srcEntity.getPosition(0f).subtract(helmBlockCenter)
    val boundingBoxSize: AABB = srcEntity.boundingBox;

    fun spawnNew(newHelmPos: BlockPos, rotation: Rotation) : SuperGlueEntity {
        val newHelmCenter = Vec3.atCenterOf(newHelmPos)

    }

}