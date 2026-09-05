package com.chromecide.lowtalk.hytale.integrations;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.NpcInfo;
import com.chromecide.lowtalk.model.Dialogue;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * A choice interaction for the game's choice framework (shop pages and anything else built on ChoiceBasePage), so
 * a shop entry can lead into a LowTalk dialogue:
 * <pre>"Interactions": [ { "Type": "LowTalkDialogue", "Dialogue": "haggle" } ]</pre>
 */
public final class LowTalkChoiceInteraction extends ChoiceInteraction {
    public static final String TYPE_ID = "LowTalkDialogue";

    @Nonnull
    public static final BuilderCodec<LowTalkChoiceInteraction> CODEC = BuilderCodec.builder(LowTalkChoiceInteraction.class, LowTalkChoiceInteraction::new, ChoiceInteraction.BASE_CODEC)
            .append(new KeyedCodec<>("Dialogue", Codec.STRING), (s, v) -> s.dialogueId = v, s -> s.dialogueId)
            .add()
            .build();

    private String dialogueId;

    public LowTalkChoiceInteraction() {
    }

    @Override
    public void run(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef) {
        LowTalkPlugin plugin = LowTalkPlugin.get();
        if (plugin == null || dialogueId == null) return;
        Dialogue d = plugin.getRegistry().byId(dialogueId);
        if (d == null) {
            plugin.getLogger().at(Level.WARNING).log("Choice asks for dialogue '%s', which is not loaded", dialogueId);
            return;
        }
        World world = store.getExternalData().getWorld();
        NpcInfo npc = NpcInfo.lookedAt(ref, store, playerRef, plugin.getStore());
        plugin.getSessions().openFor(d, playerRef, ref, store, world, npc);
    }
}
