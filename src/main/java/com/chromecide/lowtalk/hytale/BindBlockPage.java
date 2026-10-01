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
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/** The LowTalk tool on a block: pick a dialogue and whether it replaces the block's own action, or unbind. */
public class BindBlockPage extends UiPage<BindBlockPage.Data> {

    private static final String LAYOUT = "Pages/LowTalk/BindBlockPage.ui";
    private static final String NONE = "$none";

    /** Which control carries which value. The only place this page names anything on screen. */
    @Nonnull
    private static PageLayout layout() {
        return PageLayout.of(LAYOUT)
                .property("where", "#Where", "Text")
                .property("dialogues", "#Dialogue", "Entries")
                .property("dialogue", "#Dialogue", "Value")
                .property("modes", "#Mode", "Entries")
                .property("mode", "#Mode", "Value")
                .property("unbindVisible", "#UnbindButton", "Visible")
                .property("editVisible", "#EditButton", "Visible")
                .command("EDIT", "#EditButton", UiEvent.ACTIVATE).done()
                .command("NEW", "#NewButton", UiEvent.ACTIVATE).done()
                .command("BIND", "#BindButton", UiEvent.ACTIVATE)
                    .reads("Dialogue", "#Dialogue", "Value")
                    .reads("Mode", "#Mode", "Value").done()
                .command("UNBIND", "#UnbindButton", UiEvent.ACTIVATE).done()
                .command("CANCEL", "#CancelButton", UiEvent.ACTIVATE).done()
                .build();
    }

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
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC, layout());
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
    protected void populate(@Nonnull UiModel model) {
        BlockBindings.Binding current = plugin.getBlockBindings().get(worldName, pos.x, pos.y, pos.z);
        model.set("where", blockId + "  @ " + pos.x + " " + pos.y + " " + pos.z);
        List<UiOption> dialogues = new ArrayList<>();
        dialogues.add(UiOption.of(NONE, "(none)"));
        for (String id : plugin.getRegistry().ids()) dialogues.add(UiOption.same(id));
        model.set("dialogues", dialogues);
        model.set("dialogue", current == null ? NONE : current.dialogue());
        model.set("modes", List.of(
                UiOption.of(BlockBindings.MODE_INSTEAD, "talk instead of the block's own action"),
                UiOption.of(BlockBindings.MODE_ALSO, "talk and let the block act as well")));
        model.set("mode", current == null ? BlockBindings.MODE_INSTEAD : current.mode());
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
        BlockBindings bindings = plugin.getBlockBindings();
        switch (command) {
            case "BIND" -> {
                if (data.dialogue == null || NONE.equals(data.dialogue) || plugin.getRegistry().byId(data.dialogue) == null) {
                    playerRef.sendMessage(LowTalkCommand.msg(plugin, "blockPickDialogue"));
                    return;
                }
                // the base block, so a door bound by its top half is the same block the use event reports
                com.hypixel.hytale.server.core.universe.world.World w = store.getExternalData().getWorld();
                org.joml.Vector3i at = BlockReads.baseAt(w, pos.x, pos.y, pos.z);
                bindings.set(worldName, at.x, at.y, at.z, data.dialogue, data.mode == null ? BlockBindings.MODE_INSTEAD : data.mode);
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
