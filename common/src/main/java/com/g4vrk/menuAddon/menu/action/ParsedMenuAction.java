package com.g4vrk.menuAddon.menu.action;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Parsed representation of a configured menu action string.
 */
public record ParsedMenuAction(
        @NotNull String name,
        @NotNull String arguments
) {

    public static @Nullable ParsedMenuAction parse(
            final @NotNull String value
    ) {

        final String action = value.trim();

        if (action.isEmpty()) {
            return null;
        }

        int argumentStart;
        final String name;

        final char first = action.charAt(0);
        final char closing = switch (first) {
            case '[' -> ']';
            case '(' -> ')';
            case '<' -> '>';
            default -> 0;
        };

        if (closing != 0) {
            final int end = action.indexOf(closing, 1);

            if (end < 0) {
                return null;
            }

            name = action.substring(1, end).trim();
            argumentStart = end + 1;
        } else {
            final int space = action.indexOf(' ');
            final int colon = action.indexOf(':');
            final int end;

            if (space < 0) {
                end = colon < 0 ? action.length() : colon;
            } else if (colon < 0) {
                end = space;
            } else {
                end = Math.min(space, colon);
            }

            name = action.substring(0, end).trim();
            argumentStart = end;
        }

        if (name.isEmpty()) {
            return null;
        }

        while (argumentStart < action.length()) {
            final char character = action.charAt(argumentStart);

            if (character != ' ' && character != ':') {
                break;
            }

            argumentStart++;
        }

        return new ParsedMenuAction(
                name.toLowerCase(Locale.ROOT),
                action.substring(argumentStart).trim()
        );

    }

}
