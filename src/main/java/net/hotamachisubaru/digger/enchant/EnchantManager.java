package net.hotamachisubaru.digger.enchant;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class EnchantManager {

    public void applyEfficiencyEnchant(Player player, int blocksMined) {
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (tool == null || tool.getType() == Material.AIR) {
            return; // 手に何も持っていない場合は処理を終了
        }

        if (tool.getType().toString().endsWith("_PICKAXE") && !hasAppropriateEnchant(tool, blocksMined)) {
            Enchantment enchantment = Enchantment.DIG_SPEED;

            int enchantLevel = getEnchantLevelForBlocksMined(blocksMined);

            if (enchantLevel > 0) {
                ItemMeta meta = tool.getItemMeta();
                if (meta != null) {
                    meta.addEnchant(enchantment, enchantLevel, true);
                    tool.setItemMeta(meta);
                }
            }
        }
    }

    private boolean hasAppropriateEnchant(ItemStack tool, int blocksMined) {
        int expectedLevel = getEnchantLevelForBlocksMined(blocksMined);
        return tool.getEnchantmentLevel(Enchantment.DIG_SPEED) >= expectedLevel;
    }

    private int getEnchantLevelForBlocksMined(int blocksMined) {
        if (blocksMined >= 50000) return 5;
        if (blocksMined >= 20000) return 4;
        if (blocksMined >= 12000) return 3;
        if (blocksMined >= 8000) return 2;
        return 0;
    }
}