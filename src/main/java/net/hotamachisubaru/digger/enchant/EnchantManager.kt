package net.hotamachisubaru.digger.enchant

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class EnchantManager {
    fun applyEfficiencyEnchant(player: Player, blocksMined: Int) {
        val tool = player.inventory.itemInMainHand

        if (isToolValidForEnchantment(tool) && !hasAppropriateEnchant(tool, blocksMined)) {
            val enchantLevel = getEnchantLevelForBlocksMined(blocksMined)

            if (enchantLevel > 0) {
                addEnchantmentToTool(tool, Enchantment.EFFICIENCY, enchantLevel)
            }
        }
    }

    private fun isToolValidForEnchantment(tool: ItemStack?): Boolean {
        return tool != null && tool.type != Material.AIR && tool.type.toString().endsWith("_PICKAXE")
    }

    private fun hasAppropriateEnchant(tool: ItemStack, blocksMined: Int): Boolean {
        val expectedLevel = getEnchantLevelForBlocksMined(blocksMined)
        return tool.getEnchantmentLevel(Enchantment.EFFICIENCY) >= expectedLevel
    }

    private fun addEnchantmentToTool(tool: ItemStack, enchantment: Enchantment, level: Int) {
        val meta = tool.itemMeta
        if (meta != null) {
            meta.addEnchant(enchantment, level, true)
            tool.setItemMeta(meta)
        }
    }

    private fun getEnchantLevelForBlocksMined(blocksMined: Int): Int {
        if (blocksMined >= 80000) return 5
        if (blocksMined >= 40000) return 4
        if (blocksMined >= 20000) return 3
        if (blocksMined >= 10000) return 2
        return 0
    }
}
