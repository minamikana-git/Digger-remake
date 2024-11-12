package net.hotamachisubaru.digger.enchant

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

// クラス EnchantManager は、ツールに対してエンチャントを付与する機能を管理します
class EnchantManager {

    // プレイヤーが一定のブロックを採掘した際に、ツールに効率のエンチャントを付与する関数
    fun applyEfficiencyEnchant(player: Player, blocksMined: Int) {
        val tool = player.inventory.itemInMainHand

        // ツールが有効で、適切なエンチャントがまだついていない場合のみエンチャントを付与する
        if (isToolValidForEnchantment(tool) && !hasAppropriateEnchant(tool, blocksMined)) {
            val enchantLevel = getEnchantLevelForBlocksMined(blocksMined)

            if (enchantLevel > 0) {
                // ツールに効率エンチャントを付与
                addEnchantmentToTool(tool, Enchantment.EFFICIENCY, enchantLevel)
            }
        }
    }

    // ツールが有効かどうかを確認する関数
    private fun isToolValidForEnchantment(tool: ItemStack?): Boolean {
        // ツールが存在しており、空気ではなく、かつピッケル（PICKAXE）であれば有効とする
        return tool != null && tool.type != Material.AIR && tool.type.toString().endsWith("_PICKAXE")
    }

    // ツールにブロック数に応じた適切なエンチャントがすでに付いているかどうかを確認する関数
    private fun hasAppropriateEnchant(tool: ItemStack, blocksMined: Int): Boolean {
        val expectedLevel = getEnchantLevelForBlocksMined(blocksMined)
        // 期待されるレベル以上の効率エンチャントがついていれば、適切であるとする
        return tool.getEnchantmentLevel(Enchantment.EFFICIENCY) >= expectedLevel
    }

    // 指定したツールにエンチャントを付与する関数
    private fun addEnchantmentToTool(tool: ItemStack, enchantment: Enchantment, level: Int) {
        val meta = tool.itemMeta
        if (meta != null) {
            // 指定されたエンチャントとレベルをツールに追加する
            meta.addEnchant(enchantment, level, true)
            tool.setItemMeta(meta)
        }
    }

    // 採掘したブロック数に基づいてエンチャントレベルを決定する関数
    private fun getEnchantLevelForBlocksMined(blocksMined: Int): Int {
        // 採掘したブロック数に応じた効率エンチャントのレベルを返す
        if (blocksMined >= 80000) return 5
        if (blocksMined >= 40000) return 4
        if (blocksMined >= 20000) return 3
        if (blocksMined >= 10000) return 2
        return 0 // それ以外の場合はエンチャントを付けない
    }
}
