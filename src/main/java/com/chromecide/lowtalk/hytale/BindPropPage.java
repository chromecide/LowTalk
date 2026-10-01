package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.ui.PageLayout;
import com.chromecide.lowtalk.ui.UiEvent;
import com.chromecide.lowtalk.ui.UiModel;
import com.chromecide.lowtalk.ui.UiOption;
import com.chromecide.lowtalk.ui.UiPage;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The LowTalk tool on a prop: pick a dialogue and an optional speaker name, or unbind. */
public class BindPropPage extends UiPage<BindPropPage.Data> {
    private static final String LAYOUT = "Pages/LowTalk/BindPropPage.ui";
    private static final String NONE = "$none";
    /** The prompt choices: label shown in the dropdown -> translation key the client renders with the bound key. */
    static final String[][] HINTS = {
            {"talk", "server.lowtalk.hint.talk"},
            {"read", "server.lowtalk.hint.read"},
            {"examine", "server.lowtalk.hint.examine"},
            {"listen", "server.lowtalk.hint.listen"},
            {"use", "server.lowtalk.hint.use"},
    };
    static final String DEFAULT_HINT = HINTS[0][1];

    /** Which control carries which value. The only place this page names anything on screen. */
    @Nonnull
    private static PageLayout layout() {
        return PageLayout.of(LAYOUT)
                .property("where", "#Where", "Text")
                .property("dialogues", "#Dialogue", "Entries")
                .property("dialogue", "#Dialogue", "Value")
                .property("name", "#Name", "Value")
                .property("hints", "#Hint", "Entries")
                .property("hint", "#Hint", "Value")
                .property("unbindVisible", "#UnbindButton", "Visible")
                .property("editVisible", "#EditButton", "Visible")
                .command("EDIT", "#EditButton", UiEvent.ACTIVATE).done()
                .command("NEW", "#NewButton", UiEvent.ACTIVATE).done()
                .command("BIND", "#BindButton", UiEvent.ACTIVATE)
                    .reads("Dialogue", "#Dialogue", "Value")
                    .reads("Name", "#Name", "Value")
                    .reads("Hint", "#Hint", "Value").done()
                .command("UNBIND", "#UnbindButton", UiEvent.ACTIVATE).done()
                .command("CANCEL", "#CancelButton", UiEvent.ACTIVATE).done()
                .build();
    }

    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING, false), (d, s) -> d.action = s, d -> d.action).add()
                .append(new KeyedCodec<>("@Dialogue", Codec.STRING, false), (d, s) -> d.dialogue = s, d -> d.dialogue).add()
                .append(new KeyedCodec<>("@Name", Codec.STRING, false), (d, s) -> d.name = s, d -> d.name).add()
                .append(new KeyedCodec<>("@Hint", Codec.STRING, false), (d, s) -> d.hint = s, d -> d.hint).add()
                .build();
        private String action;
        private String dialogue;
        private String name;
        private String hint;
    }

    private final LowTalkPlugin plugin;
    private final UUID propId;
    private final String label;

    private BindPropPage(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef playerRef, @Nonnull UUID propId, @Nonnull String label) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC, layout());
        this.plugin = plugin;
        this.propId = propId;
        this.label = label;
    }

    public static void open(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity,
                            @Nonnull Store<EntityStore> store, @Nonnull UUID propId, @Nonnull String label) {
        Player p = store.getComponent(playerEntity, Player.getComponentType());
        if (p == null) return;
        p.getPageManager().openCustomPage(playerEntity, store, new BindPropPage(plugin, player, propId, label));
    }

    @Override
    protected void populate(@Nonnull UiModel model) {
        PropBindings.Binding current = plugin.getPropBindings().get(propId);
        model.set("where", label);
        List<UiOption> dialogues = new ArrayList<>();
        dialogues.add(UiOption.of(NONE, "(none)"));
        for (String id : plugin.getRegistry().ids()) dialogues.add(UiOption.same(id));
        model.set("dialogues", dialogues);
        model.set("dialogue", current == null ? NONE : current.dialogue());
        model.set("name", current == null || current.name() == null ? "" : current.name());
        List<UiOption> hints = new ArrayList<>();
        for (String[] h : HINTS) hints.add(UiOption.of(h[1], h[0]));
        hints.add(UiOption.of(NONE, "(no prompt)"));
        model.set("hints", hints);
        model.set("hint", current == null ? DEFAULT_HINT : (current.hint() == null ? NONE : current.hint()));
        model.set("unbindVisible", current != null);
        model.set("editVisible", current != null && plugin.getRegistry().byId(current.dialogue()) != null);
    }

    @Nonnull
    @Override
    protected String commandOf(@Nonnull Data data) {
        return data.action == null ? "" : data.action;
    }

    @Override
    protected void onCommand(@Nonnull String command, @Nonnull Data data,
                             @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PropBindings bindings = plugin.getPropBindings();
        Ref<EntityStore> prop = store.getExternalData().getRefFromUUID(propId);
        switch (command) {
            case "BIND" -> {
                if (data.dialogue == null || NONE.equals(data.dialogue) || plugin.getRegistry().byId(data.dialogue) == null) {
                    playerRef.sendMessage(LowTalkCommand.msg(plugin, "blockPickDialogue"));
                    return;
                }
                String hint = data.hint == null || NONE.equals(data.hint) ? null : data.hint;
                bindings.set(propId, data.dialogue, data.name, hint);
                bindings.flush();
                if (prop != null) PropSupport.addInteractions(prop, store, hint);
                close();
                playerRef.sendMessage(LowTalkCommand.msg(plugin, "propBound").param("prop", label).param("dialogue", data.dialogue));
            }
            case "UNBIND" -> {
                boolean had = bindings.remove(propId);
                bindings.flush();
                if (prop != null) PropSupport.removeInteractions(prop, store);
                close();
                playerRef.sendMessage(LowTalkCommand.msg(plugin, had ? "propUnbound" : "propNotBound").param("prop", label));
            }
            case "EDIT" -> {
                PropBindings.Binding current = bindings.get(propId);
                com.chromecide.lowtalk.model.Dialogue d = current == null ? null : plugin.getRegistry().byId(current.dialogue());
                if (d == null) return;
                store.getExternalData().getWorld().execute(() -> {
                    if (!ref.isValid()) return;
                    DialogueEditorPage.open(plugin, d, playerRef, ref, store, PropSupport.speaker(current, d));
                });
            }
            case "NEW" -> store.getExternalData().getWorld().execute(() -> {
                if (!ref.isValid()) return;
                NewDialoguePage.openFor(plugin, playerRef, ref, store,
                        label + " has no dialogue yet. Create one: it is bound to this prop and opens in the editor.", d -> {
                            bindings.set(propId, d.id(), null, DEFAULT_HINT);
                            bindings.flush();
                            Ref<EntityStore> p = store.getExternalData().getRefFromUUID(propId);
                            if (p != null) PropSupport.addInteractions(p, store, DEFAULT_HINT);
                        });
            });
            case "CANCEL" -> close();
            default -> {}
        }
    }
}
