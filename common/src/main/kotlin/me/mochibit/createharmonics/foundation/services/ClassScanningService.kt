package me.mochibit.createharmonics.foundation.services

import net.minecraft.client.Camera
import net.minecraft.client.renderer.GameRenderer

interface ClassScanningService {
    fun getClassesAnnotatedWith(annotation: Class<out Annotation>): List<Class<*>>

    fun getClassesAnnotatedByWithData(annotation: Class<out Annotation>): List<Pair<Class<*>, Map<String, Any>>>

    fun getSubtypesOf(type: Class<*>): List<Class<*>>
}

val classScanningService: ClassScanningService by lazy {
    loadService<ClassScanningService>()
}


