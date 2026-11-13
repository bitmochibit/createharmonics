package me.mochibit.createharmonics.foundation.services

import java.util.ServiceLoader

/**
 * Utility for loading platform-specific service implementations via Java's [ServiceLoader].
 *
 * Usage:
 * ```kotlin
 * val helper: IPlatformHelper = ServiceUtil.load(IPlatformHelper::class.java)
 * ```
 *
 * @constructor Creates a new ServiceUtil
 */
object ServiceUtil {
    /**
     * Loads the first available implementation of [serviceClass] from the classpath.
     *
     * @throws NoSuchElementException if no implementation is registered for [serviceClass].
     * @param T
     * @param serviceClass the service class
     * @return the t
     */
    fun <T> load(serviceClass: Class<T>): T =
        ServiceLoader
            .load(serviceClass, serviceClass.classLoader)
            .findFirst()
            .orElseThrow {
                NoSuchElementException(
                    "No implementation found for service '${serviceClass.name}'. " +
                        "Make sure a provider is registered under META-INF/services/${serviceClass.name}",
                )
            }
}

/**
 * Reified extension to load a service without passing a [Class] reference explicitly.
 *
 * Usage:
 * ```kotlin
 * val helper: IPlatformHelper = loadService()
 * ```
 *
 * @param T
 * @return the t
 */
inline fun <reified T> loadService(): T = ServiceUtil.load(T::class.java)
