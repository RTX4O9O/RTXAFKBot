package me.bill.fakePlayerPlugin.fakeplayer;

/**
 * Bot behaviour archetype selected at spawn time.
 *
 * <ul>
 *   <li>{@link #AFK} — passive bot with default knockback behaviour (current system).</li>
 *   <li>{@link #PVP} — <b>[COMING SOON — unfinished]</b> PvP-oriented bot; restricted to
 *       internal developer access until the system is complete.</li>
 * </ul>
 *
 * <p>Used via {@code /fpp spawn [afk] [amount]}.
 */
public enum BotType {

    /** Standard passive / AFK bot — default behaviour, no name prefix. */
    AFK,

    /**
     * PvP bot — MC username prefixed {@code pvp_<name>}; same knockback system as AFK.
     *
     * <p><b>⚠ COMING SOON — this type is not yet finished.</b>
     * Spawning as PVP is restricted to the internal developer UUID only.
     * It will be opened to all users once the system is production-ready.
     */
    PVP;

    /**
     * Returns the {@link BotType} matching the given string (case-insensitive).
     * Returns {@link #AFK} for {@code null} or unrecognised values so the
     * default behaviour is preserved for older API callers.
     */
    public static BotType parse(String s) {
        if (s == null) return AFK;
        return "pvp".equalsIgnoreCase(s) ? PVP : AFK;
    }

    /** Returns {@code true} if {@code s} is a recognised bot-type keyword. */
    public static boolean isValid(String s) {
        if (s == null) return false;
        String lo = s.toLowerCase();
        return lo.equals("afk") || lo.equals("pvp");
    }
}




