package me.mochibit.createharmonics.foundation.services

import net.minecraft.client.Camera
import net.minecraft.client.renderer.GameRenderer

/**
 * Class scanning service
 *
 */
interface ClassScanningService {
    /**
     * Returns the classes annotated withs
     *
     * @param annotation
     * @return the class<*>s
     */
    fun getClassesAnnotatedWith(annotation: Class<out Annotation>): List<Class<*>>

    /**
     * Returns the classes annotated by with datas
     *
     * @param annotation
     * @return the pair<class<*s
     */
    fun getClassesAnnotatedByWithData(annotation: Class<out Annotation>): List<Pair<Class<*>, Map<String, Any>>>

    /**
     * Returns the subtypes ofs
     *
     * @param type
     * @return the class<*>s
     */
    fun getSubtypesOf(type: Class<*>): List<Class<*>>
}

val classScanningService: ClassScanningService by lazy {
    loadService<ClassScanningService>()
}


