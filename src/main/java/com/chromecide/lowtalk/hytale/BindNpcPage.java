package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.parser.Printer;
import com.chromecide.lowtalk.ui.PageLayout;
import com.chromecide.lowtalk.ui.UiEvent;
import com.chromecide.lowtalk.ui.UiItem;
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
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The LowTalk tool on an NPC: what it says now (one row per bound dialogue, with Edit and Unbind), and a row to
 * bind another, either to this one NPC or to every NPC of its role. "This NPC" is a run-time tag on the NPC and
 * touches no file; "every role" is written into the dialogue file's npc: line, so it needs a writable file.
 */
public class BindNpcPage extends UiPage<BindNpcPage.Data> {
    private static final String LAYOUT = "Pages/LowTalk/BindNpcPage.ui";
    private static final String ROW = "Pages/LowTalk/BindNpcRow.ui";
    private static final String NONE = "$none";
    private static final String SCOPE_NPC = "npc";
    private static final String SCOPE_ROLE = "role";
    private static final String ROW_BOUND = "bound";

    /** Which control carries which value. The only place this page names anything on screen. */
    @Nonnull
    private static PageLayout layout() {
        return PageLayout.of(LAYOUT)
                .property("who", "#Who", "Text")
                .property("emptyVisible", "#Empty", "Visible")
                .property("dialogues", "#Dialogue", "Entries")
                .property("dialogue", "#Dialogue", "Value")
                .property("scopes", "#Scope", "Entries")
                .property("scope", "#Scope", "Value")
                .property("status", "#Status", "Text")
                .container("rows", "#Rows")
                    .template(ROW_BOUND, ROW)
                        .field("id", "#Id", "Text")
                        .field("how", "#How", "Text")
                        .command("EDIT", "#EditButton", UiEvent.ACTIVATE, Map.of("Id", "id"))
                        .command("UNBIND", "#UnbindButton", UiEvent.ACTIVATE, Map.of("Id", "id", "How", "bindKind"))
                        .done()
                    .done()
                .command("BIND", "#BindButton", UiEvent.ACTIVATE)
                    .reads("Dialogue", "#Dialogue", "Value")
                    .reads("Scope", "#Scope", "Value").done()
                .command("NEW", "#NewButton", UiEvent.ACTIVATE).done()
                .command("CANCEL", "#CancelButton", UiEvent.ACTIVATE).done()
                .build();
    }

    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING, false), (d, s) -> d.action = s, d -> d.action).add()
                .append(new KeyedCodec<>("Id", Codec.STRING, false), (d, s) -> d.id = s, d -> d.id).add()
                .append(new KeyedCodec<>("How", Codec.STRING, false), (d, s) -> d.how = s, d -> d.how).add()
                .append(new KeyedCodec<>("@Dialogue", Codec.STRING, false), (d, s) -> d.dialogue = s, d -> d.dialogue).add()
                .append(new KeyedCodec<>("@Scope", Codec.STRING, false), (d, s) -> d.scope = s, d -> d.scope).add()
                .build();
        private String action;
        private String id;
        private String how;
        private String dialogue;
        private String scope;
    }

    /** One current binding: the dialogue and how it is bound (role, declared @tag, or run-time tag). */
    private record Bound(Dialogue dialogue, String how, String label) {}

    private final LowTalkPlugin plugin;
    private final Ref<EntityStore> playerEntity;
    private final NpcInfo npc;
    private String status = "";

    private BindNpcPage(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef playerRef, @Nonnull Ref<EntityStore> playerEntity, @Nonnull NpcInfo npc) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC, layout());
        this.plugin = plugin;
        this.playerEntity = playerEntity;
        this.npc = npc;
    }

    public static void open(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity,
                            @Nonnull Store<EntityStore> store, @Nonnull NpcInfo npc) {
        plugin.getSessions().end(player.getUuid());
        Player p = store.getComponent(playerEntity, Player.getComponentType());
        if (p == null) return;
        p.getPageManager().openCustomPage(playerEntity, store, new BindNpcPage(plugin, player, playerEntity, npc));
    }

    @Override
    protected void populate(@Nonnull UiModel model) {
        model.set("who", npc.name() + "  (" + npc.role() + ")");
        List<Bound> bound = current();
        List<UiItem> rows = new ArrayList<>();
        for (Bound b : bound) {
            rows.add(UiItem.of(ROW_BOUND)
                    .with("id", b.dialogue().id())
                    .with("how", b.label())
                    .with("bindKind", b.how()));
        }
        model.setItems("rows", rows);
        model.set("emptyVisible", bound.isEmpty());
        List<UiOption> dialogues = new ArrayList<>();
        dialogues.add(UiOption.of(NONE, "(pick a dialogue)"));
        for (String id : plugin.getRegistry().ids()) dialogues.add(UiOption.same(id));
        model.set("dialogues", dialogues);
        model.set("dialogue", NONE);
        model.set("scopes", List.of(
                UiOption.of(SCOPE_NPC, "only this NPC"),
                UiOption.of(SCOPE_ROLE, "every " + npc.role())));
        model.set("scope", SCOPE_NPC);
        // Nothing is bound to these two dropdowns changing, so the model cannot know the creator moved them;
        // put them back to their starting choice on every draw, as this page has always done.
        model.resend("dialogue").resend("scope");
        model.set("status", status);
    }

    @Nonnull
    @Override
    protected String commandOf(@Nonnull Data data) {
        return data.action == null ? "" : data.action;
    }

    /** Every dialogue this NPC would answer with, most specific first, each with the reason it applies. */
    private List<Bound> current() {
        DialogueRegistry registry = plugin.getRegistry();
        LinkedHashMap<String, Bound> out = new LinkedHashMap<>();
        for (String tag : npc.tags()) {
            if (tag.startsWith(DialogueRegistry.BOUND_TAG_PREFIX)) {
                for (Dialogue d : registry.forTag(tag)) out.putIfAbsent(d.id(), new Bound(d, "tag", "only this NPC"));
            } else {
                for (Dialogue d : registry.forTag(tag)) out.putIfAbsent(d.id(), new Bound(d, "@" + tag, "only this NPC (@" + tag + " in the file)"));
            }
        }
        for (Dialogue d : registry.forRole(npc.role())) out.putIfAbsent(d.id(), new Bound(d, "role", "every " + npc.role()));
        return new ArrayList<>(out.values());
    }

    @Override
    protected void onCommand(@Nonnull String command, @Nonnull Data data,
                             @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        switch (command) {
            case "BIND" -> {
                Dialogue d = data.dialogue == null || NONE.equals(data.dialogue) ? null : plugin.getRegistry().byId(data.dialogue);
                if (d == null) { status = "Pick a dialogue first."; refresh(); return; }
                if (SCOPE_ROLE.equals(data.scope)) {
                    if (d.bindings().contains(npc.role())) { status = d.id() + " already applies to every " + npc.role() + "."; refresh(); return; }
                    String err = rewriteBindings(d, npc.role(), true);
                    if (err != null) { status = err; refresh(); return; }
                    playerRef.sendMessage(LowTalkCommand.msg(plugin, "npcBoundRole").param("dialogue", d.id()).param("role", npc.role()));
                } else {
                    com.chromecide.lowtalk.api.LowTalkApi.get().bindNpc(npc.id(), d.id());
                    playerRef.sendMessage(LowTalkCommand.msg(plugin, "npcBoundOne").param("dialogue", d.id()).param("npc", npc.name()));
                }
                status = "";
                refresh();
            }
            case "UNBIND" -> {
                Dialogue d = data.id == null ? null : plugin.getRegistry().byId(data.id);
                if (d == null || data.how == null) { refresh(); return; }
                switch (data.how) {
                    case "tag" -> com.chromecide.lowtalk.api.LowTalkApi.get().unbindNpc(npc.id(), d.id());
                    case "role" -> {
                        String err = rewriteBindings(d, npc.role(), false);
                        if (err != null) { status = err; refresh(); return; }
                    }
                    default -> { // a declared @tag: drop it from the file and take the tag off the NPC
                        String tag = data.how.substring(1);
                        String err = rewriteBindings(d, data.how, false);
                        if (err != null) { status = err; refresh(); return; }
                        VariableStore vs = plugin.getStore();
                        vs.removeTag(vs.npc(npc.id()), tag);
                        vs.flush();
                    }
                }
                status = "";
                playerRef.sendMessage(LowTalkCommand.msg(plugin, "npcUnbound").param("dialogue", d.id()).param("npc", npc.name()));
                refresh();
            }
            case "EDIT" -> {
                Dialogue d = data.id == null ? null : plugin.getRegistry().byId(data.id);
                if (d == null) { refresh(); return; }
                store.getExternalData().getWorld().execute(() -> {
                    if (!playerEntity.isValid()) return;
                    DialogueEditorPage.open(plugin, d, playerRef, playerEntity, store, npc);
                });
            }
            case "NEW" -> store.getExternalData().getWorld().execute(() -> {
                if (!playerEntity.isValid()) return;
                NewDialoguePage.open(plugin, playerRef, playerEntity, store, npc);
            });
            case "CANCEL" -> close();
            default -> {}
        }
    }

    /**
     * Add or remove one entry on the dialogue's npc: line and save the file. Returns a message when it cannot be
     * done: the dialogue lives in an asset pack, or the write failed.
     */
    @Nullable
    private String rewriteBindings(Dialogue d, String entry, boolean add) {
        DialogueRegistry registry = plugin.getRegistry();
        DialogueRegistry.Loaded loaded = registry.loadedFor(d.id());
        if (loaded == null) return "That dialogue is no longer loaded.";
        if (loaded.file() == null) return d.id() + " comes from an asset pack and cannot be changed here; bind this NPC only, or copy the dialogue.";
        List<String> bindings = new ArrayList<>(d.bindings());
        if (add) { if (!bindings.contains(entry)) bindings.add(entry); } else bindings.remove(entry);
        Map<String, String> other = new LinkedHashMap<>(d.otherDirectives());
        if (bindings.isEmpty()) other.put("npc", "none"); else other.remove("npc");
        Dialogue changed = new Dialogue(d.file(), d.id(), List.copyOf(bindings), d.starts(), d.speaker(), d.title(), d.scope(), other, d.includes(), d.nodes());
        try {
            String text = Printer.dialogue(changed);
            Files.writeString(loaded.file(), text, StandardCharsets.UTF_8);
            DialogueRegistry.LoadReport report = registry.loadFile(loaded.file(), text, true);
            if (!report.ok()) return "Saved, but the file did not load cleanly: " + String.join(" ", report.messages());
        } catch (IOException e) {
            return "Could not write " + loaded.file().getFileName() + ": " + e.getMessage();
        }
        return null;
    }

}
