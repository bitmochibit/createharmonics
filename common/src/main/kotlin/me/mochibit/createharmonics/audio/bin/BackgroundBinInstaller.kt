package me.mochibit.createharmonics.audio.bin

import kotlinx.coroutines.Dispatchers
import me.mochibit.createharmonics.BuildConfig
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import me.mochibit.createharmonics.registry.ModLang
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("KotlinConstantConditions")
object BackgroundBinInstaller {
    private val installationInProgress = AtomicBoolean(false)

    fun isAutoInstallAllowed(): Boolean = !BuildConfig.IS_CURSEFORGE

    /**
     * Check if installation is currently in progress
     */
    fun isInstalling(): Boolean = installationInProgress.get()

    /**
     * Start background installation of missing libraries
     */
    fun startBackgroundInstallation() {
        if (!isAutoInstallAllowed()) {
            Logger.warn("Automatic installation is disabled on ${BuildConfig.PLATFORM}")
            return
        }

        if (!installationInProgress.compareAndSet(false, true)) {
            Logger.warn("Library installation already in progress")
            return
        }

        Logger.info("Starting background library installation...")

        launchModCoroutine(Dispatchers.IO) {
            try {
                BinStatusManager.LibraryType.entries.forEach { libraryType ->
                    if (!BinStatusManager.isLibraryInstalled(libraryType)) {
                        installLibrary(libraryType)
                    } else {
                        BinStatusManager.updateStatus(
                            libraryType,
                            status = BinStatusManager.Status.ALREADY_INSTALLED,
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.err("Background installation failed: ${e.message}")
                e.printStackTrace()
            } finally {
                installationInProgress.set(false)
            }
        }
    }

    private fun installLibrary(libraryType: BinStatusManager.LibraryType) {
        Logger.info("Installing ${libraryType.displayName}...")
        BinStatusManager.updateStatus(
            libraryType,
            status = BinStatusManager.Status.DOWNLOADING,
            progress = 0.0f,
        )

        try {
            val success =
                BinInstaller.install(
                    provider = libraryType.provider,
                    providerName = libraryType.displayName,
                ) { statusString, progress, speed ->
                    val status =
                        when (statusString) {
                            "downloading" -> BinStatusManager.Status.DOWNLOADING
                            "extracting" -> BinStatusManager.Status.EXTRACTING
                            "completed" -> BinStatusManager.Status.INSTALLED
                            "already_installed" -> BinStatusManager.Status.ALREADY_INSTALLED
                            "failed" -> BinStatusManager.Status.FAILED
                            else -> BinStatusManager.Status.PENDING
                        }
                    BinStatusManager.updateStatus(
                        libraryType,
                        status = status,
                        progress = progress,
                        speed = speed,
                    )
                }

            if (success) {
                Logger.info("${libraryType.displayName} installed successfully")
                BinStatusManager.updateStatus(
                    libraryType,
                    status = BinStatusManager.Status.INSTALLED,
                    progress = 1.0f,
                )
                showToast(libraryType, success = true)
            } else {
                Logger.err("${libraryType.displayName} installation failed")
                BinStatusManager.updateStatus(
                    libraryType,
                    status = BinStatusManager.Status.FAILED,
                    progress = 0.0f,
                )
                showToast(libraryType, success = false)
            }
        } catch (e: Exception) {
            Logger.err("Error installing ${libraryType.displayName}: ${e.message}")
            e.printStackTrace()
            BinStatusManager.updateStatus(
                libraryType,
                status = BinStatusManager.Status.ERROR,
                progress = 0.0f,
            )
            showToast(libraryType, success = false)
        }
    }

    private fun showToast(
        library: BinStatusManager.LibraryType,
        success: Boolean,
    ) {
        Minecraft.getInstance().execute {
            val minecraft = Minecraft.getInstance()
            val toastManager = minecraft.toasts

            if (success) {
                toastManager.addToast(
                    SystemToast.multiline(
                        minecraft,
                        SystemToast.SystemToastIds.TUTORIAL_HINT,
                        Component.literal(library.displayName).append(
                            ModLang.translate("gui.library_installer.toast.success_title").component(),
                        ),
                        ModLang.translate("gui.library_installer.toast.success_desc").component(),
                    ),
                )
            } else {
                toastManager.addToast(
                    SystemToast.multiline(
                        minecraft,
                        SystemToast.SystemToastIds.TUTORIAL_HINT,
                        Component.literal(library.displayName).append(
                            ModLang.translate("gui.library_installer.toast.failure_title").component(),
                        ),
                        ModLang.translate("gui.library_installer.toast.failure_desc").component(),
                    ),
                )
            }
        }
    }
}
