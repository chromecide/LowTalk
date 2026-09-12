package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/** The LowTalk tool on a block: pick a dialogue and whether it replaces the block's own action, or unbind. */
public class BindBlockPage extends InteractiveCustomUIPage<BindBlockPage.Data> {

    private static final String LAYOUT = "Pages/LowTalk/BindBlockPage.ui";
    private static final String NONE = "$none";

    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING, false), (d, s) -> d.action = s, d -> d.action).add()
                .append(new KeyedCodec<>("@Dialogue", Codec.STRING, false), (d, s) -> d.dialogue = s, d -> d.dialogue).add()
                .append(new KeyedCodec<>("@Mode", Codec.STRING, false), (d, s) -> d.mode = s, d -> d.mode).add()
                .build();
        private String action;
        private String dialogue;
        private String mode;
    }

    private final LowTalkPlugin plugin;
    private final String worldName;
    private final Vector3i pos;
    private final String blockId;

    private BindBlockPage(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef playerRef, @Nonnull String worldName, @Nonnull Vector3i pos, @Nonnull String blockId) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.plugin = plugin;
        this.worldName = worldName;
        this.pos = pos;
        this.blockId = blockId;
    }

    public static void open(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity,
                            @Nonnull Store<EntityStore> store, @Nonnull String worldName, @Nonnull Vector3i pos, @Nonnull String blockId) {
        Player p = store.getComponent(playerEntity, Player.getComponentType());
        if (p == null) return;
        p.getPageManager().openCustomPage(playerEntity, store, new BindBlockPage(plugin, player, worldName, pos, blockId));
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        BlockBindings.Binding current = plugin.getBlockBindings().get(worldName, pos.x, pos.y, pos.z);
        cmd.set("#Where.Text", blockId + "  @ " + pos.x + " " + pos.y + " " + pos.z);
        List<DropdownEntryInfo> dialogues = new ArrayList<>();
        dialogues.add(new DropdownEntryInfo(LocalizableString.fromString("(none)"), NONE));
        for (String id : plugin.getRegistry().ids()) dialogues.add(new DropdownEntryInfo(LocalizableString.fromString(id), id));
        cmd.set("#Dialogue.Entries", dialogues);
        cmd.set("#Dialogue.Value", current == null ? NONE : current.dialogue());
        List<DropdownEntryInfo> modes = new ArrayList<>();
        modes.add(new DropdownEntryInfo(LocalizableString.fromString("talk instead of the block's own action"), BlockBindings.MODE_INSTEAD));
        modes.add(new DropdownEntryInfo(LocalizableString.fromString("talk and let the block act as well"), BlockBindings.MODE_ALSO));
        cmd.set("#Mode.Entries", modes);
        cmd.set("#Mode.Value", current == null ? BlockBindings.MODE_INSTEAD : current.mode());
        cmd.set("#UnbindButton.Visible", current != null);
        cmd.set("#EditButton.Visible", current != null && plugin.getRegistry().byId(current.dialogue()) != null);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EditButton", new EventData().append("Action", "EDIT"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NewButton", new EventData().append("Action", "NEW"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BindButton",
                new EventData().append("Action", "BIND").append("@Dialogue", "#Dialogue.Value").append("@Mode", "#Mode.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#UnbindButton", new EventData().append("Action", "UNBIND"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", new EventData().append("Action", "CANCEL"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        if (data.action == null) return;
        BlockBindings bindings = plugin.getBlockBindings();
        switch (data.action) {
            case "BIND" -> {
                if (data.dialogue == null || NONE.equals(data.dialogue) || plugin.getRegistry().byId(data.dialogue) == null) {
                    playerRef.sendMessage(LowTalkCommand.msg(plugin, "blockPickDialogue"));
                    return;
                }
                bindings.set(worldName, pos.x, pos.y, pos.z, data.dialogue, data.mode == null ? BlockBindings.MODE_INSTEAD : data.mode);
                bindings.flush();
                close();
                playerRef.sendMessage(LowTalkCommand.msg(plugin, "blockBound").param("block", blockId).param("dialogue", data.dialogue)
                        .param("mode", BlockBindings.MODE_ALSO.equalsIgnoreCase(data.mode) ? BlockBindings.MODE_ALSO : BlockBindings.MODE_INSTEAD));
            }
            case "UNBIND" -> {
                boolean had = bindings.remove(worldName, pos.x, pos.y, pos.z);
                bindings.flush();
                close();
                playerRef.sendMessage(LowTalkCommand.msg(plugin, had ? "blockUnbound" : "blockNotBound").param("block", blockId));
            }
            case "EDIT" -> {
                BlockBindings.Binding current = bindings.get(worldName, pos.x, pos.y, pos.z);
                com.chromecide.lowtalk.model.Dialogue d = current == null ? null : plugin.getRegistry().byId(current.dialogue());
                if (d == null) return;
                store.getExternalData().getWorld().execute(() -> {
                    if (!ref.isValid()) return;
                    DialogueEditorPage.open(plugin, d, playerRef, ref, store, BrowsePage.narrator(d));
                });
            }
            case "NEW" -> store.getExternalData().getWorld().execute(() -> {
                if (!ref.isValid()) return;
                NewDialoguePage.openFor(plugin, playerRef, ref, store,
                        blockId + " has no dialogue yet. Create one: it is bound to this block (instead of its own action) and opens in the editor.", d -> {
                            bindings.set(worldName, pos.x, pos.y, pos.z, d.id(), BlockBindings.MODE_INSTEAD);
                            bindings.flush();
                        });
            });
            case "CANCEL" -> close();
            default -> {}
        }
    }
}
