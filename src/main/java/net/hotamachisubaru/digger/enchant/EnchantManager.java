package net.hotamachisubaru.digger.enchant;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class EnchantManager {

    public void applyEfficiencyEnchant(Player player, int blocksMined) {
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (isToolValidForEnchantment(tool) && !hasAppropriateEnchant(tool, blocksMined)) {
            int enchantLevel = getEnchantLevelForBlocksMined(blocksMined);

            if (enchantLevel > 0) {
                addEnchantmentToTool(tool, Enchantment.EFFICIENCY, enchantLevel);
            }
        }
    }

    private boolean isToolValidForEnchantment(ItemStack tool) {
        return tool != null && tool.getType() != Material.AIR && tool.getType().toString().endsWith("_PICKAXE");
    }

    private boolean hasAppropriateEnchant(ItemStack tool, int blocksMined) {
        int expectedLevel = getEnchantLevelForBlocksMined(blocksMined);
        return tool.getEnchantmentLevel(Enchantment.EFFICIENCY) >= expectedLevel;
    }

    private void addEnchantmentToTool(ItemStack tool, Enchantment enchantment, int level) {
        ItemMeta meta = tool.getItemMeta();
        if (meta != null) {
            meta.addEnchant(enchantment, level, true);
            tool.setItemMeta(meta);
        }
    }

    private int getEnchantLevelForBlocksMined(int blocksMined) {
        if (blocksMined >= 80000) return 5;
        if (blocksMined >= 40000) return 4;
        if (blocksMined >= 20000) return 3;
        if (blocksMined >= 10000) return 2;
        return 0;
    }
}
