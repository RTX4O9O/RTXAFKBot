package me.bill.fakePlayerPlugin.fakeplayer;

/**
 * Combat mode determined by the bot's current inventory.
 * The bot dynamically switches between modes based on available items.
 *
 * <p>Priority order (highest first):
 * <ol>
 *   <li>{@link #CRYSTAL} — has end crystals + obsidian (end crystal PVP)</li>
 *   <li>{@link #SWORD}   — has sword/axe (melee combat)</li>
 *   <li>{@link #FIST}    — no weapons (fist combat)</li>
 * </ol>
 */
public enum BotCombatMode {
    /** End crystal PVP — place obsidian, place crystal, detonate. */
    CRYSTAL,

    /** Sword/axe melee combat with crits, s-tap, shield. */
    SWORD,

    /** Fist combat — pure melee, no weapon. */
    FIST;

    /**
     * Returns the display name for this combat mode.
     */
    public String getDisplayName() {
        return switch (this) {
            case CRYSTAL -> "Crystal PVP";
            case SWORD   -> "Sword";
            case FIST    -> "Fist";
        };
    }
}

