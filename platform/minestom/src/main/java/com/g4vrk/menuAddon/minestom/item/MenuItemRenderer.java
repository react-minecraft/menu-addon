package com.g4vrk.menuAddon.minestom.item;

import com.g4vrk.menuAddon.menu.render.MenuItemData;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.PotionContents;
import net.minestom.server.network.player.ResolvableProfile;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Converts rendered menu item data into Minestom item stacks.
 */
public final class MenuItemRenderer {

    private MenuItemRenderer() {

    }

    public static @NotNull ItemStack item(final @NotNull MenuItemData data) {

        return item(data, "");

    }

    public static @NotNull ItemStack item(
            final @NotNull MenuItemData data,
            final @NotNull String ownerName
    ) {

        Material material = Material.fromKey(data.material());
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack stack = ItemStack.of(material)
                .with(DataComponents.CUSTOM_NAME, data.name())
                .with(DataComponents.LORE, data.lore());
        if (data.owner() != null) {
            stack = stack.with(DataComponents.PROFILE, new ResolvableProfile(
                    new ResolvableProfile.Partial(ownerName, data.owner(), List.of())
            ));
        }
        if (data.color() != null && isPotionMaterial(data.material())) {
            final MenuRgb color = MenuRgb.fromHex(data.color());
            final PotionContents contents = stack.get(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            stack = stack.with(DataComponents.POTION_CONTENTS, new PotionContents(contents.potion(), color,
                    contents.customEffects(), contents.customName()));
        }
        return stack;

    }

    private static boolean isPotionMaterial(final @NotNull String material) {

        final String item = material.substring(material.lastIndexOf(':') + 1).toLowerCase(Locale.ROOT);
        return item.equals("potion") || item.equals("splash_potion") || item.equals("lingering_potion")
                || item.equals("tipped_arrow");

    }

    /**
     * RGB color value used by potion item data.
     */
    private record MenuRgb(
            int red,
            int green,
            int blue
    ) implements RGBLike {

        private static @NotNull MenuRgb fromHex(final @NotNull String color) {

            final int rgb = Integer.parseInt(color.substring(1), 16);
            return new MenuRgb(rgb >> 16 & 0xff, rgb >> 8 & 0xff, rgb & 0xff);

        }

    }

}
