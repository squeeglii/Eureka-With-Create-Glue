package org.valkyrienskies.eureka.gfss

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Vector3d

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

    fun spawnNew(newHelmCenterPos: Vec3, rotation: Matrix3f, level: ServerLevel) : SuperGlueEntity {

        val orientatedBox = rotateBoundingBox(this.relativeBoundingBox, rotation)
        val relativeToWorldBox = orientatedBox.move(newHelmCenterPos)

        println("NewHelmCenter: $newHelmCenterPos, relativeBB: $relativeBoundingBox, oriented: $orientatedBox, relativeToWorldBox: $relativeToWorldBox")

        val newEnt = SuperGlueEntity(level, relativeToWorldBox)
        level.addFreshEntity(newEnt)

        return newEnt
    }

    /** Rotates an axis aligned bounding box around the origin (0,0) */
    private fun rotateBoundingBox(boundingBox: AABB, rotation: Matrix3f) : AABB {

        val bbMin = Vector3d(boundingBox.minX, boundingBox.minY, boundingBox.minZ)
        val bbMax = Vector3d(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ)

        // Rotate min & max points around origin -- mutable
        bbMin.mul(rotation)
        bbMax.mul(rotation)

        return AABB(bbMin.x, bbMin.y, bbMax.z,
                    bbMax.x, bbMax.y, bbMax.z);
    }



}