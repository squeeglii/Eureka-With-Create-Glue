package org.valkyrienskies.eureka.gfss

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.AxisAngle4d
import org.joml.Matrix4d
import org.joml.Quaterniondc
import org.joml.Vector3d
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

    fun spawnNew(newHelmPos: BlockPos, rotation: Quaterniondc, level: ServerLevel) : SuperGlueEntity {

        val orientatedBox = rotateBoundingBox(this.relativeBoundingBox, rotation)
        val relativeToWorldBox = orientatedBox.move(newHelmPos.x.toDouble(), newHelmPos.y.toDouble(), newHelmPos.z.toDouble())

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
    private fun rotateBoundingBox(boundingBox: AABB, rotation: Quaterniondc) : AABB {

        val transform = Matrix4d().rotate(snapRotation(AxisAngle4d(rotation)))

        val p1: Vector3d = transform.transformPosition(boundingBox.minX, boundingBox.minY, boundingBox.minZ, Vector3d())
        val p2: Vector3d = transform.transformPosition(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ, Vector3d())

        return AABB(
            p1.x, p1.y, p1.z,
            p2.x, p2.y, p2.z
        );
    }


    // \/\/\/ Everything below is copied from ShipAssembler.kt \/\/\/

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

    private fun roundToNearestMultipleOf(number: Double, multiple: Double) = multiple * round(number / multiple)



}