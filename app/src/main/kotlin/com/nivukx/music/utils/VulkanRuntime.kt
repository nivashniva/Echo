package com.nivukx.music.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import timber.log.Timber

/**
 * Runtime GPU capability probe.
 *
 * Android does not expose a supported API for forcing the private HWUI renderer
 * (OpenGL/Vulkan) for an individual application. This object therefore avoids
 * hidden system properties and instead makes the Vulkan capability explicit while
 * keeping the app on the platform's hardware-accelerated rendering path.
 */
object VulkanRuntime {
    data class Capabilities(
        val supported: Boolean,
        val level: Int?,
        val version: Int?,
    )

    @Volatile
    private var capabilities: Capabilities? = null

    fun initialize(context: Context): Capabilities {
        capabilities?.let { return it }

        val packageManager = context.packageManager
        val detected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val levelFeature = packageManager.hasSystemFeature(
                PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL
            )
            val versionFeature = packageManager.hasSystemFeature(
                PackageManager.FEATURE_VULKAN_HARDWARE_VERSION
            )
            val level = packageManager
                .getSystemAvailableFeatures()
                .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL }
                ?.version
            val version = packageManager
                .getSystemAvailableFeatures()
                .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
                ?.version

            Capabilities(
                supported = levelFeature || versionFeature,
                level = level,
                version = version,
            )
        } else {
            Capabilities(
                supported = false,
                level = null,
                version = null,
            )
        }

        capabilities = detected
        Timber.tag("VulkanRuntime").i(
            "GPU renderer capability: vulkanSupported=${detected.supported}, " +
                "hardwareLevel=${detected.level ?: -1}, version=${detected.version ?: -1}"
        )
        return detected
    }

    fun current(): Capabilities? = capabilities
}
