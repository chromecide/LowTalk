package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared helpers for props: identity, the interactions component that makes the interact key reach them, and the NPC stand-in. */
public final class PropSupport {
    private PropSupport() {}

    public static boolean isProp(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        return ref.isValid() && accessor.getComponent(ref, PropComponent.getComponentType()) != null;
    }

    @Nullable
    public static UUID idOf(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        if (!ref.isValid()) return null;
        UUIDComponent c = accessor.getComponent(ref, UUIDComponent.getComponentType());
        return c == null ? null : c.getUuid();
    }

    /** True if the entity's interactions component is the one LowTalk adds to bound props. */
    public static boolean hasOurInteractions(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        Interactions i = accessor.getComponent(ref, Interactions.getComponentType());
        return i != null && PropBindings.USE_INTERACTION.equals(i.getInteractionId(InteractionType.Use));
    }

    /**
     * Give a bound prop the interact entry the game needs before it raises the use-entity event, plus the
     * "Press [key] to ..." prompt (a translation key, or null for none). Updates the prompt if the entry exists.
     */
    public static void addInteractions(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nullable String hint) {
        if (!ref.isValid()) return;
        Interactions existing = store.getComponent(ref, Interactions.getComponentType());
        if (existing == null) {
            store.putComponent(ref, Interactions.getComponentType(), ourInteractions(hint));
        } else if (PropBindings.USE_INTERACTION.equals(existing.getInteractionId(InteractionType.Use))) {
            existing.setInteractionHint(hint);
        }
    }

    /** Take the entry away again, but only if it is ours; a prop with its own interactions keeps them. */
    public static void removeInteractions(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (hasOurInteractions(ref, store)) store.tryRemoveComponent(ref, Interactions.getComponentType());
    }

    static Interactions ourInteractions(@Nullable String hint) {
        Interactions i = new Interactions(Map.of(InteractionType.Use, PropBindings.USE_INTERACTION));
        i.setInteractionHint(hint);
        return i;
    }

    /** The voice a bound prop speaks with: its binding's name, else the dialogue's speaker, else the dialogue's title. */
    public static NpcInfo speaker(@Nonnull PropBindings.Binding binding, @Nonnull com.chromecide.lowtalk.model.Dialogue d) {
        String name = binding.name();
        if (name == null) name = d.speaker();
        if (name == null) name = d.title();
        if (name == null) name = "Narrator";
        return new NpcInfo(null, NpcInfo.NONE, "none", name, Set.of());
    }

    /**
     * Safety net for props whose interactions component went missing (older save, or removed by another tool):
     * re-add it when a bound prop enters the world. The component persists with the entity, so this rarely fires.
     */
    public static final class EnsureInteractions extends HolderSystem<EntityStore> {
        private final LowTalkPlugin plugin;
        private final Query<EntityStore> query = Query.and(PropComponent.getComponentType(), UUIDComponent.getComponentType());

        public EnsureInteractions(@Nonnull LowTalkPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store) {
            UUIDComponent id = holder.getComponent(UUIDComponent.getComponentType());
            PropBindings.Binding binding = id == null ? null : plugin.getPropBindings().get(id.getUuid());
            if (binding == null) return;
            if (holder.getComponent(Interactions.getComponentType()) != null) return;
            holder.addComponent(Interactions.getComponentType(), ourInteractions(binding.hint()));
        }

        @Override
        public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store) {
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return query;
        }
    }
}
