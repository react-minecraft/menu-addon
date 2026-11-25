package com.g4vrk.menuAddon.menu.bukkit;

import com.cryptomorin.xseries.XMaterial;
import com.g4vrk.menuAddon.menu.render.MenuItemData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Converts platform-neutral menu content into a Bukkit item stack.
 */
public final class BukkitMenuItemRenderer {

    private BukkitMenuItemRenderer() {

    }

    public static @NotNull ItemStack render(
            final @NotNull MenuItemData data
    ) {

        final String configuredMaterial = data.material();
        final String materialName = configuredMaterial.startsWith("minecraft:") ? configuredMaterial.substring("minecraft:".length()) : configuredMaterial;
        final Material material = XMaterial.matchXMaterial(materialName)
                .map(XMaterial::get)
                .filter(Material::isItem)
                .orElseThrow(() -> new IllegalArgumentException("Unknown item material: " + data.material()));
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(data.name());
        meta.lore(data.lore());
        if (meta instanceof PotionMeta potionMeta && data.color() != null) {
            potionMeta.setColor(Color.fromRGB(Integer.parseInt(data.color().substring(1), 16)));
        }
        if (meta instanceof SkullMeta skullMeta && data.owner() != null) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(data.owner()));
        }

        item.setItemMeta(meta);
        return item;

    }

    public static void updateLore(
            final @NotNull ItemStack item,
            final @NotNull List<Component> lore
    ) {

        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.lore(lore);
        item.setItemMeta(meta);

    }

}
