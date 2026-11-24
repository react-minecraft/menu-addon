package com.g4vrk.menuAddon.fabric.item;

import com.g4vrk.menuAddon.menu.render.MenuItemData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

/**
 * Converts rendered menu content to native Fabric items and text.
 */
public final class FabricMenuItemRenderer {

    private FabricMenuItemRenderer() {

    }

    public static @NotNull ItemStack item(
            final @NotNull MenuItemData data
    ) {

        final Identifier key = Identifier.parse(data.material());
        final Item material = BuiltInRegistries.ITEM.get(key)
                .map(reference -> reference.value())
                .orElse(Items.STONE);
        final ItemStack stack = new ItemStack(material == Items.AIR ? Items.STONE : material);
        stack.set(DataComponents.CUSTOM_NAME, toMinecraft(data.name()));
        stack.set(DataComponents.LORE, new ItemLore(data.lore().stream()
                .<Component>map(FabricMenuItemRenderer::toMinecraft)
                .toList()));
        if (data.owner() != null) {
            stack.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(data.owner()));
        }
        if (data.color() != null && isPotionMaterial(data.material())) {
            final PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(contents.potion(),
                    Optional.of(Integer.parseInt(data.color().substring(1), 16)),
                    contents.customEffects(), contents.customName()));
        }
        return stack;

    }

    public static @NotNull MutableComponent toMinecraft(
            final @NotNull net.kyori.adventure.text.Component component
    ) {

        MutableComponent result = Component.empty().withStyle(toMinecraft(component.style()));
        if (component instanceof net.kyori.adventure.text.TextComponent text) {
            result.append(Component.literal(text.content()));
        }
        for (final net.kyori.adventure.text.Component child : component.children()) {
            result.append(toMinecraft(child));
        }
        return result;

    }

    private static @NotNull Style toMinecraft(
            final net.kyori.adventure.text.format.Style style
    ) {

        Style result = Style.EMPTY;
        if (style.color() != null) {
            result = result.withColor(TextColor.fromRgb(style.color().value()));
        }
        if (style.decoration(TextDecoration.BOLD) != TextDecoration.State.NOT_SET) {
            result = result.withBold(style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE);
        }
        if (style.decoration(TextDecoration.ITALIC) != TextDecoration.State.NOT_SET) {
            result = result.withItalic(style.decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE);
        }
        if (style.decoration(TextDecoration.UNDERLINED) != TextDecoration.State.NOT_SET) {
            result = result.withUnderlined(style.decoration(TextDecoration.UNDERLINED) == TextDecoration.State.TRUE);
        }
        if (style.decoration(TextDecoration.STRIKETHROUGH) != TextDecoration.State.NOT_SET) {
            result = result.withStrikethrough(style.decoration(TextDecoration.STRIKETHROUGH)
                    == TextDecoration.State.TRUE);
        }
        if (style.decoration(TextDecoration.OBFUSCATED) != TextDecoration.State.NOT_SET) {
            result = result.withObfuscated(style.decoration(TextDecoration.OBFUSCATED)
                    == TextDecoration.State.TRUE);
        }
        return result;

    }

    private static boolean isPotionMaterial(final @NotNull String material) {

        final String item = material.substring(material.lastIndexOf(':') + 1).toLowerCase(Locale.ROOT);
        return item.equals("potion") || item.equals("splash_potion") || item.equals("lingering_potion")
                || item.equals("tipped_arrow");

    }

}
