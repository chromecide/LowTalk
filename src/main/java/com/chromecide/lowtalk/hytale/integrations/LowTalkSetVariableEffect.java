package com.chromecide.lowtalk.hytale.integrations;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.hytale.json.JsonDialogues;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerContext;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEffect;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * A trigger-volume effect that sets a LowTalk variable for the player who triggered it, so walking into a place can
 * change what NPCs say afterwards.
 * <pre>{ "Type": "LowTalkSetVariable", "Event": "Enter", "Dialogue": "village_elder", "Var": "$player.saw_ruins", "Value": "true" }</pre>
 */
public final class LowTalkSetVariableEffect extends TriggerEffect {
    public static final String TYPE_ID = "LowTalkSetVariable";

    @Nonnull
    public static final BuilderCodec<LowTalkSetVariableEffect> CODEC = BuilderCodec.builder(LowTalkSetVariableEffect.class, LowTalkSetVariableEffect::new, BASE_CODEC)
            .append(new KeyedCodec<>("Dialogue", Codec.STRING), (e, v) -> e.dialogueId = v, e -> e.dialogueId)
            .documentation("The dialogue whose variables to write (its scope).")
            .metadata(new UIEditor(new UIEditor.TextField(JsonDialogues.DATASET_DIALOGUES)))
            .add()
            .append(new KeyedCodec<>("Var", Codec.STRING), (e, v) -> e.var = v, e -> e.var)
            .documentation("The variable: $player.name for this player, $world.name for everyone.")
            .add()
            .append(new KeyedCodec<>("Value", Codec.STRING), (e, v) -> e.value = v, e -> e.value)
            .documentation("A LowTalk expression; empty means true.")
            .add()
            .build();

    private String dialogueId;
    private String var;
    private String value;

    public LowTalkSetVariableEffect() {
    }

    @Override
    public void execute(@Nonnull TriggerContext context) {
        LowTalkPlugin plugin = LowTalkPlugin.get();
        if (plugin == null) return;
        Store<EntityStore> store = context.getStore();
        Ref<EntityStore> ref = context.getEntityRef();
        if (ref == null || !ref.isValid()) return;
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) return;
        DialogueExpressions.assign(plugin, dialogueId, var, value, player);
    }
}
