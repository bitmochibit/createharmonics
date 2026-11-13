package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.foundation.services.PlatformService
import me.mochibit.createharmonics.foundation.services.classScanningService
import me.mochibit.createharmonics.foundation.services.platformService
import net.minecraft.core.Registry

/**
 * Registrable
 *
 * @property registrationOrder the registration order
 * @property targetEnvironment the target environment
 */
interface Registrable {
    /**
     * The registration order priority. Lower values are registered first.
     * Override this property to control registration order relative to other AutoRegistrable implementations.
     * Default is 0, increase the value for registrations that depend on others (e.g., Ponders = 5).
     */
    val registrationOrder: Int
        get() = 0

    /**
     * If set, this registrable will only be executed on the specified environment.
     * Null means "any environment" (default).
     */
    val targetEnvironment: PlatformService.Environment?
        get() = null

    /**
     * Adds
     *
     * @param registry
     */
    fun register(registry: Registry<*>? = null)
}

/**
 * Registry phase
 * Enum — a fixed set of named constants.
 *
 * @constructor Creates a new RegistryPhase
 */
enum class RegistryPhase {
    /**
     * Pre freeze
     *
     * @constructor Creates a new PRE_FREEZE
     */
    PRE_FREEZE,

    /**
     * Common
     *
     * @constructor Creates a new COMMON
     */
    COMMON
}

/**
 * Auto register
 *
 * @constructor Creates a new AutoRegister
 * @property phase
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoRegister(val phase: RegistryPhase = RegistryPhase.COMMON)


/**
 * Auto registrar
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new AutoRegistrar
 */
object AutoRegistrar {

    /**
     * Adds the all
     *
     * @param phase
     * @param registry
     */
    fun registerAll(phase: RegistryPhase, registry: Registry<*>? = null) {
        val platform = platformService

        classScanningService.getClassesAnnotatedByWithData(AutoRegister::class.java)
            .filter { (_, data) -> phaseOf(data) == phase }
            .mapNotNull { (clazz, _) -> resolveInstance(clazz) as? Registrable }
            .filter { it.targetEnvironment == null || it.targetEnvironment == platform.environment }
            .sortedBy { it.registrationOrder }
            .forEach { it.register(registry) }
    }

    private fun phaseOf(annotationData: Map<String, Any>): RegistryPhase {
        val raw = annotationData["phase"] as? Array<*> ?: return RegistryPhase.COMMON
        val name = raw.getOrNull(1) as? String ?: return RegistryPhase.COMMON
        return RegistryPhase.valueOf(name)
    }

    private fun resolveInstance(clazz: Class<*>): Any? =
        runCatching { clazz.getField("INSTANCE").get(null) }
            .getOrElse {
                runCatching { clazz.getDeclaredConstructor().newInstance() }.getOrNull()
            }
}