package com.chromecide.lowtalk.api;

/**
 * What started a conversation.
 *
 * <p>A listener could see which dialogue opened and for whom, and not how. That is enough for most things
 * and wrong for anything that cares about the route: a test harness recording that a block binding works
 * cannot tell a block-opened conversation from the same dialogue opened by talking to an NPC, and a dialogue
 * bound to both is then credited to whichever check happens to watch it.
 *
 * <p>The session has always known — a block conversation carries the block's position, a role action carries
 * the NPC it fired on. This says it out loud.
 */
public enum Opener {

    /** A player used an NPC that a dialogue is bound to, by role, tag or by name. */
    NPC,

    /** A player used a block a dialogue is bound to. */
    BLOCK,

    /** A player used a prop a dialogue is bound to. */
    PROP,

    /** A trigger volume fired {@code LowTalkDialogue}. */
    TRIGGER,

    /** The player finished loading into a world and a dialogue asked for {@code on: join}. */
    JOIN,

    /** An NPC's own role opened it through {@code LowTalkOpenDialogue}, without LowTalk's use hook. */
    ROLE,

    /** The game's interaction system opened it: a choice page, or an {@code OpenCustomUI} step. */
    INTERACTION,

    /** {@code /lowtalk talk}, the dialogue browser, or anything else a creator typed. */
    COMMAND,

    /** Another plugin asked, through {@link LowTalkApi}. */
    API,

    /**
     * No conversation was opened at all. A context built only to evaluate an expression — a trigger
     * volume's condition, say — carries this, because it has a player and a dialogue and no opening.
     */
    NONE
}
