package com.chromecide.lowtalk.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.Dirty;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Tell the game an entity has changed, so the change is still there after a restart.
 *
 * <p>An entity is written out only when its {@code Dirty} component says so: {@code EntitySavingSystem}
 * and {@code EntitySection} both skip anything not marked, and a component written straight into the store
 * marks nothing. Spawning marks the entity ({@code new Dirty(section, reason == AddReason.SPAWN)}), which
 * is why a name given to an NPC as it is created survives and a name given to it later did not.
 *
 * <p>That is what made {@code <<npc_name>>} lie: the nameplate changed in front of you and reverted at the
 * next restart, while the comment above it said "persists with the NPC". It had been that way since the
 * feature shipped, because nothing had ever restarted a server and looked.
 *
 * <p>{@code Entity.markNeedsSave()} does the same thing and is deprecated for removal, so this goes
 * through the component.
 *
 * <p>This lives in its own class rather than beside its first caller because the rule is subtle, easy to
 * forget, and applies to every component LowTalk adds or removes on an entity it did not spawn. Freezing
 * missed it for exactly that reason.
 */
public final class EntitySaving {

    private EntitySaving() {}

    /** Mark {@code ref} as needing a save. Safe to call on an invalid ref or an entity with no Dirty. */
    public static void markForSaving(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!ref.isValid()) return;
        Dirty dirty = store.getComponent(ref, Dirty.getComponentType());
        if (dirty != null) dirty.markDirty();
    }
}
