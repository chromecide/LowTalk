package com.chromecide.lowtalk.hytale.integrations;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.model.Dialogue;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerContext;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEffect;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * A trigger-volume effect, registered with the game's TriggerVolumesPlugin as type "LowTalkDialogue", so map makers
 * can open a dialogue when a player walks into a volume drawn with the in-game Trigger Volume Tool. JSON:
 * <pre>{ "Type": "LowTalkDialogue", "Event": "Enter", "Dialogue": "cave_warning" }</pre>
 * The dialogue runs without an NPC; its {@code speaker:} directive names the voice.
 */
public final class LowTalkTriggerEffect extends TriggerEffect {
    public static final String TYPE_ID = "LowTalkDialogue";

    @Nonnull
    public static final BuilderCodec<LowTalkTriggerEffect> CODEC = BuilderCodec.builder(LowTalkTriggerEffect.class, LowTalkTriggerEffect::new, BASE_CODEC)
            .append(new KeyedCodec<>("Dialogue", Codec.STRING), (e, v) -> e.dialogueId = v, e -> e.dialogueId)
            .documentation("The LowTalk dialogue to open, by id (file name without extension).")
            .metadata(new com.hypixel.hytale.codec.schema.metadata.ui.UIEditor(new com.hypixel.hytale.codec.schema.metadata.ui.UIEditor.TextField(com.chromecide.lowtalk.hytale.json.JsonDialogues.DATASET_DIALOGUES)))
            .add()
            .build();

    private String dialogueId;

    public LowTalkTriggerEffect() {
    }

    @Override
    public void execute(@Nonnull TriggerContext context) {
        LowTalkPlugin plugin = LowTalkPlugin.get();
        if (plugin == null || dialogueId == null) return;
        Store<EntityStore> store = context.getStore();
        Ref<EntityStore> ref = context.getEntityRef();
        if (ref == null || !ref.isValid()) return;
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return; // only players talk
        Dialogue d = plugin.getRegistry().byId(dialogueId);
        if (d == null) {
            plugin.getLogger().at(Level.WARNING).atMostEvery(30, java.util.concurrent.TimeUnit.SECONDS)
                    .log("Trigger volume asks for dialogue '%s', which is not loaded", dialogueId);
            return;
        }
        if (plugin.getSessions().get(playerRef.getUuid()) != null) return; // already in a conversation
        World world = store.getExternalData().getWorld();
        plugin.getSessions().openFor(d, playerRef, ref, store, world, null);
    }
}
