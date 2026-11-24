package com.g4vrk.menuAddon.sponge.item;

import com.g4vrk.menuAddon.menu.render.MenuItemData;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.registry.RegistryTypes;
import org.spongepowered.api.util.Color;

import java.util.Locale;

/**
 * Converts rendered menu data into Sponge item stacks.
 */
public final class SpongeMenuItemFactory {

    private SpongeMenuItemFactory() {

    }

    public static @NotNull ItemStack item(final @NotNull MenuItemData data) {

        return item(data, "");

    }

    public static @NotNull ItemStack item(
            final @NotNull MenuItemData data,
            final @NotNull String ownerName
    ) {

        final String key = data.material().contains(":") ? data.material() : "minecraft:" + data.material();
        final ItemType type = RegistryTypes.ITEM_TYPE.get().findValue(ResourceKey.resolve(key)).orElse(ItemTypes.PAPER.get());
        final ItemStack.Builder builder = ItemStack.builder()
                .itemType(type)
                .quantity(1)
                .add(Keys.CUSTOM_NAME, data.name())
                .add(Keys.LORE, data.lore());
        if (data.owner() != null) {
            builder.add(Keys.GAME_PROFILE, GameProfile.of(data.owner(), ownerName));
        }
        if (data.color() != null && isPotionMaterial(data.material())) {
            builder.add(Keys.COLOR, Color.ofRgb(Integer.parseInt(data.color().substring(1), 16)));
        }
        return builder.build();

    }

    private static boolean isPotionMaterial(final @NotNull String material) {

        final String item = material.substring(material.lastIndexOf(':') + 1).toLowerCase(Locale.ROOT);
        return item.equals("potion") || item.equals("splash_potion") || item.equals("lingering_potion")
                || item.equals("tipped_arrow");

    }

}
