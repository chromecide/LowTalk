package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The LowTalk tool on a prop: pick a dialogue and an optional speaker name, or unbind. */
public class BindPropPage extends InteractiveCustomUIPage<BindPropPage.Data> {
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
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
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
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        PropBindings.Binding current = plugin.getPropBindings().get(propId);
        cmd.set("#Where.Text", label);
        List<DropdownEntryInfo> dialogues = new ArrayList<>();
        dialogues.add(new DropdownEntryInfo(LocalizableString.fromString("(none)"), NONE));
        for (String id : plugin.getRegistry().ids()) dialogues.add(new DropdownEntryInfo(LocalizableString.fromString(id), id));
        cmd.set("#Dialogue.Entries", dialogues);
        cmd.set("#Dialogue.Value", current == null ? NONE : current.dialogue());
        cmd.set("#Name.Value", current == null || current.name() == null ? "" : current.name());
        List<DropdownEntryInfo> hints = new ArrayList<>();
        for (String[] h : HINTS) hints.add(new DropdownEntryInfo(LocalizableString.fromString(h[0]), h[1]));
        hints.add(new DropdownEntryInfo(LocalizableString.fromString("(no prompt)"), NONE));
        cmd.set("#Hint.Entries", hints);
        cmd.set("#Hint.Value", current == null ? DEFAULT_HINT : (current.hint() == null ? NONE : current.hint()));
        cmd.set("#UnbindButton.Visible", current != null);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BindButton",
                new EventData().append("Action", "BIND").append("@Dialogue", "#Dialogue.Value").append("@Name", "#Name.Value").append("@Hint", "#Hint.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#UnbindButton", new EventData().append("Action", "UNBIND"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", new EventData().append("Action", "CANCEL"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        if (data.action == null) return;
        PropBindings bindings = plugin.getPropBindings();
        Ref<EntityStore> prop = store.getExternalData().getRefFromUUID(propId);
        switch (data.action) {
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
            case "CANCEL" -> close();
            default -> {}
        }
    }
}
