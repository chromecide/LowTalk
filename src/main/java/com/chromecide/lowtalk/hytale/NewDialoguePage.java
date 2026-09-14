package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.chromecide.lowtalk.model.Dialogue;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shown when the LowTalk tool is used on an NPC that has no dialogue: name it, choose whether it belongs to the
 * NPC's role (every NPC of that kind) or to this one NPC (a tag), and where the file goes. Creates a small starter
 * file, loads it and opens the editor on it.
 */
public class NewDialoguePage extends InteractiveCustomUIPage<NewDialoguePage.Data> {
    private static final String LAYOUT = "Pages/LowTalk/NewDialoguePage.ui";
    private static final String BIND_ROLE = "role";
    private static final String BIND_TAG = "tag";
    private static final String BIND_NONE = "none";
    private static final String NEW_PACK = "$newpack";
    private static final String FORMAT_JSON = "json";
    private static final String FORMAT_TALK = "talk";

    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING, false), (d, s) -> d.action = s, d -> d.action).add()
                .append(new KeyedCodec<>("@Id", Codec.STRING, false), (d, s) -> d.id = s, d -> d.id).add()
                .append(new KeyedCodec<>("@Speaker", Codec.STRING, false), (d, s) -> d.speaker = s, d -> d.speaker).add()
                .append(new KeyedCodec<>("@Bind", Codec.STRING, false), (d, s) -> d.bind = s, d -> d.bind).add()
                .append(new KeyedCodec<>("@Where", Codec.STRING, false), (d, s) -> d.where = s, d -> d.where).add()
                .append(new KeyedCodec<>("@Format", Codec.STRING, false), (d, s) -> d.format = s, d -> d.format).add()
                .append(new KeyedCodec<>("@Pack", Codec.STRING, false), (d, s) -> d.pack = s, d -> d.pack).add()
                .build();
        private String action;
        private String id;
        private String speaker;
        private String bind;
        private String where;
        private String format;
        private String pack;
    }

    private final LowTalkPlugin plugin;
    /** The NPC the dialogue is for, or null when created from the browser with nothing to attach it to. */
    @Nullable
    private final NpcInfo npc;
    private final Ref<EntityStore> playerEntity;
    private final LinkedHashMap<String, Path> targets;
    /** Text above the form when not for an NPC; null for the browser's default. */
    @Nullable
    private final String intro;
    /** What to do with the new dialogue before the editor opens: bind it to the prop or block it was made for. */
    @Nullable
    private final java.util.function.Consumer<Dialogue> onCreated;
    private String status = "";

    public NewDialoguePage(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef playerRef, @Nonnull Ref<EntityStore> playerEntity, @Nullable NpcInfo npc) {
        this(plugin, playerRef, playerEntity, npc, null, null);
    }

    private NewDialoguePage(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef playerRef, @Nonnull Ref<EntityStore> playerEntity, @Nullable NpcInfo npc,
                            @Nullable String intro, @Nullable java.util.function.Consumer<Dialogue> onCreated) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.plugin = plugin;
        this.npc = npc;
        this.playerEntity = playerEntity;
        this.targets = plugin.getRegistry().creationTargets();
        this.intro = intro;
        this.onCreated = onCreated;
    }

    /** From a prop or block bind page: a dialogue attached to nothing in its file, bound by the caller once it exists. */
    public static void openFor(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity,
                               @Nonnull Store<EntityStore> store, @Nonnull String intro, @Nonnull java.util.function.Consumer<Dialogue> onCreated) {
        plugin.getSessions().end(player.getUuid());
        Player p = store.getComponent(playerEntity, Player.getComponentType());
        if (p == null) return;
        p.getPageManager().openCustomPage(playerEntity, store, new NewDialoguePage(plugin, player, playerEntity, null, intro, onCreated));
    }

    public static void open(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity,
                            @Nonnull Store<EntityStore> store, @Nonnull NpcInfo npc) {
        plugin.getSessions().end(player.getUuid());
        Player p = store.getComponent(playerEntity, Player.getComponentType());
        if (p == null) return;
        p.getPageManager().openCustomPage(playerEntity, store, new NewDialoguePage(plugin, player, playerEntity, npc));
    }

    /** From the browser: a dialogue attached to nothing (npc: none), to be bound to a prop, block, trigger or role later. */
    public static void openUnattached(@Nonnull LowTalkPlugin plugin, @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity,
                                      @Nonnull Store<EntityStore> store) {
        plugin.getSessions().end(player.getUuid());
        Player p = store.getComponent(playerEntity, Player.getComponentType());
        if (p == null) return;
        p.getPageManager().openCustomPage(playerEntity, store, new NewDialoguePage(plugin, player, playerEntity, null));
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        if (npc == null && intro != null) cmd.set("#Intro.Text", intro);
        else if (npc == null) cmd.set("#Intro.Text", LowTalkCommand.msg(plugin, "newUnattachedIntro"));
        else cmd.set("#Intro.Text", npc.name() + " (" + npc.role() + ") has no dialogue yet. Create one and it opens in the editor.");
        String suggested = npc == null || npc.role() == null ? "new_dialogue" : npc.role().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        int n = 2;
        String id = suggested;
        while (plugin.getRegistry().byId(id) != null) id = suggested + "_" + n++;
        cmd.set("#Id.Value", id);
        cmd.set("#Speaker.Value", npc == null ? "Narrator" : npc.name());
        List<DropdownEntryInfo> bind = new ArrayList<>();
        if (npc == null) {
            bind.add(new DropdownEntryInfo(LocalizableString.fromString(onCreated != null
                    ? "this prop or block (npc: none in the file)" : "nothing yet (npc: none); bind it to a prop, block, trigger or role later"), BIND_NONE));
        } else {
            bind.add(new DropdownEntryInfo(LocalizableString.fromString("every " + npc.role() + " (bind by role)"), BIND_ROLE));
            bind.add(new DropdownEntryInfo(LocalizableString.fromString("only this NPC (bind by tag)"), BIND_TAG));
        }
        cmd.set("#Bind.Entries", bind);
        cmd.set("#Bind.Value", npc == null ? BIND_NONE : BIND_ROLE);
        List<DropdownEntryInfo> where = new ArrayList<>();
        for (Map.Entry<String, Path> e : targets.entrySet()) {
            boolean server = e.getKey().isEmpty();
            where.add(new DropdownEntryInfo(
                    LocalizableString.fromString(server ? "the server's dialogues folder" : "asset pack " + e.getKey()),
                    server ? "$server" : e.getKey(),
                    LocalizableString.fromString(server
                            ? "this server only: the Asset Editor and the Node Editor cannot see it, and it does not travel with a pack"
                            : "part of the pack, so it ships with it and opens in the Asset Editor and the Node Editor too")));
        }
        where.add(new DropdownEntryInfo(LocalizableString.fromString("+ a new asset pack of my own"), NEW_PACK,
                LocalizableString.fromString("makes a pack in the server's mods folder and puts this dialogue in it")));
        cmd.set("#Where.Entries", where);
        String preferred = plugin.getRegistry().defaultCreationTarget();
        String chosen = preferred.isEmpty() ? (targets.size() > 1 ? "$server" : NEW_PACK) : preferred;
        cmd.set("#Where.Value", chosen);
        cmd.set("#PackRow.Visible", NEW_PACK.equals(chosen));
        cmd.set("#PackName.Value", "");
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Where",
                new EventData().append("Action", "WHERE").append("@Where", "#Where.Value"), false);
        List<DropdownEntryInfo> formats = new ArrayList<>();
        formats.add(new DropdownEntryInfo(LocalizableString.fromString("an asset (.json)"), FORMAT_JSON,
                LocalizableString.fromString("a form in the Asset Editor, a graph in the Node Editor, and fields in here")));
        formats.add(new DropdownEntryInfo(LocalizableString.fromString("a text file (.talk)"), FORMAT_TALK,
                LocalizableString.fromString("for writing by hand; the Asset Editor opens it as text")));
        cmd.set("#Format.Entries", formats);
        cmd.set("#Format.Value", FORMAT_JSON);
        cmd.set("#Status.Text", status);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CreateButton", new EventData().append("Action", "CREATE")
                .append("@Id", "#Id.Value").append("@Speaker", "#Speaker.Value").append("@Bind", "#Bind.Value")
                .append("@Where", "#Where.Value").append("@Format", "#Format.Value").append("@Pack", "#PackName.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", new EventData().append("Action", "CANCEL"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        if ("CANCEL".equals(data.action)) { close(); return; }
        if ("WHERE".equals(data.action)) {
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#PackRow.Visible", NEW_PACK.equals(data.where));
            sendUpdate(cmd, new UIEventBuilder(), false);
            return;
        }
        if (!"CREATE".equals(data.action)) return;
        String id = data.id == null ? "" : data.id.trim();
        if (!id.matches("[A-Za-z0-9_-]+")) { fail("The id needs letters, digits, _ or - only."); return; }
        if (plugin.getRegistry().byId(id) != null) { fail("A dialogue called " + id + " already exists."); return; }
        String where = data.where == null || data.where.equals("$server") ? "" : data.where;
        boolean makePack = NEW_PACK.equals(where);
        Path root;
        String madePack = null;
        if (makePack) {
            try {
                root = plugin.getRegistry().createPack(playerRef.getUsername(), data.pack == null ? "" : data.pack);
                madePack = root.getParent() == null ? "the new pack" : root.getParent().getParent().getFileName().toString();
            } catch (IOException e) {
                fail(e.getMessage() == null ? "Could not make the pack." : e.getMessage());
                return;
            }
        } else {
            root = targets.get(where);
        }
        if (root == null) { fail("That place is not available."); return; }
        // the server's own folder is not an asset pack, so an asset cannot live in it
        boolean asJson = !FORMAT_TALK.equals(data.format) && (makePack || !where.isEmpty());
        boolean byTag = npc != null && BIND_TAG.equals(data.bind);
        String tag = byTag ? id : null;
        String binding = npc == null ? "none" : (byTag ? "@" + tag : npc.role());
        String speaker = data.speaker == null || data.speaker.isBlank() ? (npc == null ? "Narrator" : npc.name()) : data.speaker.trim();
        String text = "npc: " + binding + "\n"
                + "speaker: " + speaker + "\n\n"
                + "== start\n"
                + "Hello there.\n"
                + "-> Goodbye\n"
                + "    <<end>>\n";
        Path file;
        Dialogue d;
        try {
            if (asJson) {
                file = plugin.getRegistry().createJson(com.chromecide.lowtalk.parser.DialogueParser.parse(id + ".talk", text, null), root);
                d = plugin.getRegistry().byId(id);
                if (d == null) { fail("The new asset did not load; see the server log."); return; }
            } else {
                file = root.resolve(id + ".talk");
                Files.createDirectories(root);
                if (Files.exists(file)) { fail(file.getFileName() + " already exists there."); return; }
                Files.writeString(file, text, StandardCharsets.UTF_8);
                DialogueRegistry.LoadReport report = plugin.getRegistry().loadFile(file, text, true);
                d = plugin.getRegistry().byId(id);
                if (!report.ok() || d == null) { fail("The new file did not load: " + String.join(" ", report.messages())); return; }
            }
        } catch (IOException e) {
            fail("Could not write the file: " + e.getMessage());
            return;
        } catch (RuntimeException e) {
            fail("Could not make the dialogue: " + e.getMessage());
            return;
        }
        if (madePack != null) playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                "Made the asset pack " + madePack + " in the server's mods folder."));
        if (byTag) {
            VariableStore vs = plugin.getStore();
            vs.addTag(vs.npc(npc.id()), tag);
            vs.flush();
        }
        if (onCreated != null) onCreated.accept(d);
        playerRef.sendMessage(byTag
                ? LowTalkCommand.msg(plugin, "createdTagged").param("file", file.getFileName().toString()).param("npc", npc.name()).param("tag", tag)
                : LowTalkCommand.msg(plugin, "created").param("file", file.getFileName().toString()));
        store.getExternalData().getWorld().execute(() -> {
            if (!playerEntity.isValid()) return;
            DialogueEditorPage.open(plugin, d, playerRef, playerEntity, store, npc == null ? BrowsePage.narrator(d) : npc);
        });
    }

    private void fail(String message) {
        status = message;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#Status.Text", status);
        sendUpdate(cmd, null, false);
    }
}
