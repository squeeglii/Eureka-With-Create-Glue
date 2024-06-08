package org.valkyrienskies.eureka.gfss

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.AxisAngle4d
import org.joml.Matrix4d
import org.valkyrienskies.eureka.util.ShipAssembler
import org.valkyrienskies.eureka.util.ShipAssembler.roundToNearestMultipleOf
import org.valkyrienskies.eureka.util.ShipAssembler.snapRotation
import kotlin.math.*

class GlueEntityState(srcEntity: SuperGlueEntity, helmBlockCenter: Vec3) {

    private val relativeBoundingBox: AABB

    //HelmCenter: (-24.5, 121.5, 11.5), relativeBB: AABB[-1.5, -1.5, -0.5] -> [1.5, 6.5, 2.5], srcBB: AABB[-26.0, 120.0, 11.0] -> [-23.0, 128.0, 14.0]
    //HelmCenter: (-24.5, 121.5, 11.5), relativeBB: AABB[-0.5, -1.5, -3.5] -> [0.5, -0.5, 1.5], srcBB: AABB[-25.0, 120.0, 8.0] -> [-24.0, 121.0, 13.0]
    //NewHelmCenter: (-37.5, 131.5, -0.5), relativeBB: AABB[-1.5, -1.5, -0.5] -> [1.5, 6.5, 2.5], oriented: AABB[-2.5, -1.5, 1.5] -> [0.5, 6.5, 1.5], relativeToWorldBox: AABB[-40.0, 130.0, 1.0] -> [-37.0, 138.0, 1.0]
    //NewHelmCenter: (-37.5, 131.5, -0.5), relativeBB: AABB[-0.5, -1.5, -3.5] -> [0.5, -0.5, 1.5], oriented: AABB[-1.5, -1.5, 0.5] -> [3.5, -0.5, 0.5], relativeToWorldBox: AABB[-39.0, 130.0, 0.0] -> [-34.0, 131.0, 0.0]

    init {

        val srcBox = srcEntity.boundingBox
        val helmOriginTransform = helmBlockCenter.multiply(-1.0, -1.0, -1.0)
        val bbWithHelmAsOrigin = srcBox.move(helmOriginTransform)

        this.relativeBoundingBox = bbWithHelmAsOrigin

        println("HelmCenter: $helmBlockCenter, relativeBB: $relativeBoundingBox, srcBB: $srcBox")
    }

    fun spawnNew(newHelmPos: BlockPos, rotation: Rotation, level: ServerLevel) : SuperGlueEntity {

        val orientatedBox = rotateBoundingBox(this.relativeBoundingBox, rotation)
        val relativeToWorldBox = orientatedBox.move(newHelmPos)

        val dimensions = "${relativeToWorldBox.xsize}x${relativeToWorldBox.ysize}x${relativeToWorldBox.zsize}"

        println("NewHelmCenter: $newHelmPos, relativeBB: $relativeBoundingBox, oriented: $orientatedBox, relativeToWorldBox: $relativeToWorldBox, dimensions: $dimensions")

        val bpMin = BlockPos(relativeToWorldBox.minX, relativeToWorldBox.minY, relativeToWorldBox.minZ)
        val bpMax = BlockPos(relativeToWorldBox.maxX, relativeToWorldBox.maxY, relativeToWorldBox.maxZ)

        val centeredBB = AABB(
            bpMin,
            bpMax
        )

        val newEnt = SuperGlueEntity(level, centeredBB)
        level.addFreshEntity(newEnt)

        return newEnt
    }

    /** Rotates an axis aligned bounding box around the origin (0,0) */
    private fun rotateBoundingBox(boundingBox: AABB, rotation: Rotation) : AABB {

        val p1: Vec3
        val p2: Vec3



        when (rotation) {
            Rotation.NONE -> {
                p1 = Vec3(boundingBox.minX, boundingBox.minY, boundingBox.minZ)
                p2 = Vec3(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ)
            }
            Rotation.CLOCKWISE_90 -> {
                p1 = Vec3(boundingBox.minZ, boundingBox.minY, boundingBox.minX)
                p2 = Vec3(boundingBox.maxZ, boundingBox.maxY, boundingBox.maxX)
            }
            Rotation.CLOCKWISE_180 -> {
                p1 = Vec3(-(boundingBox.minX), boundingBox.minY, -boundingBox.minZ)
                p2 = Vec3(-(boundingBox.maxX), boundingBox.maxY, -boundingBox.maxZ)
            }
            Rotation.COUNTERCLOCKWISE_90 -> {
                p1 = Vec3(boundingBox.minZ, boundingBox.minY, -boundingBox.minX)
                p2 = Vec3(boundingBox.maxZ, boundingBox.maxY, -boundingBox.maxX)
            }
        }



        return AABB(
            ceil(p1.x), ceil(p1.y), ceil(p1.z),
            ceil(p2.x), ceil(p2.y), ceil(p2.z)
        );
    }



}