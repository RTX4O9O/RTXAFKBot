package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.UUID;

/**
 * Immutable snapshot of a bot that is physically running on another server
 * in the proxy network.
 *
 * <p>Received via the {@code BOT_SPAWN} plugin-messaging subchannel and stored
 * in {@link RemoteBotCache}.  Contains every field needed to create a virtual
 * tab-list entry on the receiving server — including the full skin texture so
 * the bot shows the correct skin even without a local Mojang lookup.
 *
 * <p>When skin data is unavailable (e.g. during DB-backed startup sync where
 * textures are not stored) both {@link #skinValue()} and {@link #skinSignature()}
 * are empty strings.  The bot will still appear in the tab list with the
 * default Steve/Alex skin.
 */
public record RemoteBotEntry(
        /** ID of the originating server (from {@code Config.serverId()}). */
        String serverId,
        /** Bot's UUID — used as the unique key in the tab-list packet. */
        UUID uuid,
        /** Minecraft profile name (max 16 chars). */
        String name,
        /** Fully formatted display name with prefix/suffix/colours applied. */
        String displayName,
        /** Profile name with sort-prefix prepended (e.g. {@code {00_BotName}}). */
        String packetProfileName,
        /** Base64-encoded Mojang texture value, or {@code ""} if unavailable. */
        String skinValue,
        /** Base64-encoded Mojang texture signature, or {@code ""} if unavailable. */
        String skinSignature
) {
    /** Returns {@code true} when valid skin texture data is present. */
    public boolean hasSkin() {
        return skinValue != null && !skinValue.isBlank();
    }
}

