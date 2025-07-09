package net.hotamachisubaru.digger.util

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

object SchedulerUtil {
    @JvmStatic
    fun runAtPlayer(player: Player, plugin: JavaPlugin, task: Runnable) {
        try {
            val scheduler = player.javaClass.getMethod("getScheduler").invoke(player)
            scheduler.javaClass.getMethod("run", JavaPlugin::class.java, Runnable::class.java)
                .invoke(scheduler, plugin, task)
        } catch (e: Exception) {
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }
}