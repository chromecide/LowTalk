package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.LowTalkPlugin;

import com.chromecide.lowtalk.model.Dialogue;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * /lowtalk reload | list | open <id> | tag <tag> | untag <tag> | tags | vars | stop
 */
public class LowTalkCommand extends AbstractCommandCollection {

    /** Server operation: reload, tagging NPCs, thawing, the test world. Give admins lowtalk.* to cover both. */
    static final String ADMIN = "lowtalk.admin";
    /** Authoring: open any dialogue by id, list, inspect and reset variables, headless tests. */
    static final String CREATOR = "lowtalk.creator";

    public LowTalkCommand(@Nonnull LowTalkPlugin plugin) {
        super("lowtalk", "LowTalk dialogue tools");
        this.requireNoPermission();
        this.addSubCommand(new Reload(plugin));
        this.addSubCommand(new ListDialogues(plugin));
        this.addSubCommand(new Open(plugin));
        this.addSubCommand(new Tag(plugin, true));
        this.addSubCommand(new Tag(plugin, false));
        this.addSubCommand(new Tags(plugin));
        this.addSubCommand(new Vars(plugin));
        this.addSubCommand(new Reset(plugin));
        this.addSubCommand(new Thaw(plugin));
        this.addSubCommand(new TestDialogue(plugin));
        this.addSubCommand(new TestWorldCommand(plugin));
        this.addSubCommand(new Stop(plugin));
        this.addSubCommand(new Help(plugin));
        this.addSubCommand(new Info(plugin));
        this.addSubCommand(new Convert(plugin));
        this.addSubCommand(new Tool(plugin));
        this.addSubCommand(new BlockCommand(plugin));
        this.addSubCommand(new PropCommand(plugin));
    }

    /** Tab completion and did-you-mean for dialogue ids; the id argument of open, info, test and convert. */
    static DialogueIdArgument dialogueIds() {
        // Field initialisers run before the constructor stores the plugin, so look it up when suggestions are asked for.
        return new DialogueIdArgument(() -> LowTalkPlugin.get() == null ? java.util.List.of() : LowTalkPlugin.get().getRegistry().ids());
    }

    private static Message info(LowTalkPlugin plugin, String text) {
        return Message.raw(text).color(plugin.getSettings().getInfoColor());
    }

    /** A translated feedback line: {@code key} is the part after {@code server.lowtalk.msg.}; chain {@code .param(...)} for fill-ins. */
    static Message msg(LowTalkPlugin plugin, String key) {
        return Message.translation("server.lowtalk.msg." + key).color(plugin.getSettings().getInfoColor());
    }

    /** "No dialogue with id 'x'.", with a suggestion when one is close. */
    static Message noDialogue(LowTalkPlugin plugin, String id) {
        String near = com.chromecide.lowtalk.parser.Suggest.closest(id, plugin.getRegistry().ids());
        return near == null ? msg(plugin, "noDialogue").param("id", id) : msg(plugin, "noDialogueNear").param("id", id).param("near", near);
    }

    /** Re-read every dialogue file. */
    static class Reload extends CommandBase {
        private final LowTalkPlugin plugin;

        Reload(LowTalkPlugin plugin) {
            super("reload", "Re-read all dialogue files");
            this.plugin = plugin;
            this.requirePermission(ADMIN);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            DialogueRegistry.LoadReport report = plugin.reloadDialogues();
            for (String m : report.messages()) {
                context.sendMessage(info(plugin, m));
            }
            context.sendMessage(msg(plugin, "reloaded").param("loaded", report.loaded()).param("files", report.files())
                    .param("errors", report.errors()).param("warnings", report.warnings()));
        }
    }

    static class ListDialogues extends CommandBase {
        private final LowTalkPlugin plugin;

        ListDialogues(LowTalkPlugin plugin) {
            super("list", "List loaded dialogues and their bindings");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            DialogueRegistry reg = plugin.getRegistry();
            if (reg.ids().isEmpty()) {
                context.sendMessage(msg(plugin, "noDialoguesLoaded").param("folder", reg.getFolder().toString()));
                return;
            }
            for (String id : reg.ids()) {
                Dialogue d = reg.byId(id);
                context.sendMessage(info(plugin, id + "  ->  " + (d.bindings().isEmpty() ? "(command only)" : String.join(", ", d.bindings()))
                        + "  [" + d.nodes().size() + " passages]"));
            }
        }
    }

    /** /lowtalk help [name | commands | functions | keywords]: the format reference, in chat. */
    static class Help extends CommandBase {
        private final LowTalkPlugin plugin;
        private final OptionalArg<String> topicArg = withOptionalArg("topic", "A command, function or keyword name, or commands / functions / keywords", ArgTypes.GREEDY_STRING);

        Help(LowTalkPlugin plugin) {
            super("help", "Reference for the dialogue format");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String topic = topicArg.provided(context) ? topicArg.get(context).trim() : "";
            if (topic.isEmpty()) {
                context.sendMessage(msg(plugin, "helpIntro"));
                context.sendMessage(msg(plugin, "helpCommands").param("names", names(com.chromecide.lowtalk.parser.Reference.commands())));
                context.sendMessage(msg(plugin, "helpFunctions").param("names", names(com.chromecide.lowtalk.parser.Reference.functions())));
                context.sendMessage(msg(plugin, "helpKeywords").param("names", names(com.chromecide.lowtalk.parser.Reference.keywords())));
                context.sendMessage(msg(plugin, "helpDocs"));
                return;
            }
            java.util.List<com.chromecide.lowtalk.parser.Reference.Entry> group = switch (topic.toLowerCase(java.util.Locale.ROOT)) {
                case "commands", "command" -> com.chromecide.lowtalk.parser.Reference.commands();
                case "functions", "function" -> com.chromecide.lowtalk.parser.Reference.functions();
                case "keywords", "keyword", "syntax" -> com.chromecide.lowtalk.parser.Reference.keywords();
                default -> null;
            };
            if (group != null) {
                for (com.chromecide.lowtalk.parser.Reference.Entry e : group) context.sendMessage(info(plugin, e.line()));
                return;
            }
            com.chromecide.lowtalk.parser.Reference.Entry e = com.chromecide.lowtalk.parser.Reference.lookup(topic);
            if (e == null) {
                String near = com.chromecide.lowtalk.parser.Suggest.closest(topic, com.chromecide.lowtalk.parser.Reference.allNames());
                context.sendMessage(near == null ? msg(plugin, "nothingCalled").param("topic", topic) : msg(plugin, "nothingCalledNear").param("topic", topic).param("near", near));
                return;
            }
            context.sendMessage(info(plugin, e.line()));
        }

        private static String names(java.util.List<com.chromecide.lowtalk.parser.Reference.Entry> entries) {
            StringBuilder sb = new StringBuilder();
            for (com.chromecide.lowtalk.parser.Reference.Entry e : entries) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(e.name());
            }
            return sb.toString();
        }
    }

    /**
     * /lowtalk convert json <id> <pack> writes a loaded dialogue as a JSON asset into an asset pack, where the Asset
     * Editor's form can edit it. /lowtalk convert talk <id> <pack|server> writes .talk text into a pack, or into the
     * plugin's own dialogues folder when the pack is the word "server". Pack names may contain spaces.
     */
    static class Convert extends AbstractCommandCollection {
        Convert(LowTalkPlugin plugin) {
            super("convert", "Write a dialogue as .json (form editor) or .talk (text)");
            this.requirePermission(CREATOR);
            this.addSubCommand(new ConvertTo(plugin, "json"));
            this.addSubCommand(new ConvertTo(plugin, "talk"));
        }
    }

    static class ConvertTo extends CommandBase {
        private final LowTalkPlugin plugin;
        private final String format;
        private final RequiredArg<String> idArg = withRequiredArg("dialogue", "Dialogue id", dialogueIds());
        private final RequiredArg<String> packArg;

        ConvertTo(LowTalkPlugin plugin, String format) {
            super(format, format.equals("json") ? "Write a dialogue as a JSON asset into a pack" : "Write a dialogue as .talk text into a pack, or 'server' for the plugin folder");
            this.plugin = plugin;
            this.format = format;
            this.packArg = withRequiredArg("pack", format.equals("json") ? "Asset pack name, e.g. JP:My Pack" : "Asset pack name, or server", ArgTypes.GREEDY_STRING);
            this.requirePermission(CREATOR);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = idArg.get(context);
            Dialogue d = plugin.getRegistry().byId(id);
            if (d == null) {
                context.sendMessage(noDialogue(plugin, id));
                return;
            }
            String packName = packArg.get(context).trim();
            if (packName.length() > 1 && packName.startsWith("\"") && packName.endsWith("\"")) packName = packName.substring(1, packName.length() - 1);
            java.nio.file.Path dir;
            if (format.equals("talk") && packName.equalsIgnoreCase("server")) {
                dir = plugin.getRegistry().getFolder();
                packName = null;
            } else {
                com.hypixel.hytale.assetstore.AssetPack pack = com.hypixel.hytale.server.core.asset.AssetModule.get().getAssetPack(packName);
                if (pack == null) {
                    StringBuilder names = new StringBuilder();
                    for (com.hypixel.hytale.assetstore.AssetPack ap : com.hypixel.hytale.server.core.asset.AssetModule.get().getAssetPacks()) {
                        if (ap.isImmutable()) continue;
                        if (!names.isEmpty()) names.append(", ");
                        names.append(ap.getName());
                    }
                    context.sendMessage(names.isEmpty() ? msg(plugin, "noPackNone").param("pack", packName) : msg(plugin, "noPack").param("pack", packName).param("packs", names.toString()));
                    return;
                }
                if (pack.isImmutable()) {
                    context.sendMessage(msg(plugin, "packReadOnly").param("pack", packName));
                    return;
                }
                dir = pack.getRoot().resolve(DialogueRegistry.PACK_DIR);
            }
            try {
                java.nio.file.Files.createDirectories(dir);
                if (format.equals("json")) {
                    // Assets are named Capitalised_Words; this also keeps the copy's id clear of the .talk original.
                    String jsonId = assetName(id);
                    java.nio.file.Path out = dir.resolve(jsonId + ".json");
                    if (java.nio.file.Files.exists(out)) {
                        context.sendMessage(msg(plugin, "fileExists").param("file", out.getFileName().toString()));
                        return;
                    }
                    com.chromecide.lowtalk.hytale.json.LowTalkJson asset = com.chromecide.lowtalk.hytale.json.JsonConvert.toAsset(d);
                    asset.setId(jsonId);
                    if (asset.scope == null && !jsonId.equals(d.scope())) asset.scope = d.scope(); // share memory with the original
                    String json = com.chromecide.lowtalk.hytale.json.JsonCodecs.DIALOGUE
                            .encode(asset, com.hypixel.hytale.codec.EmptyExtraInfo.EMPTY).asDocument()
                            .toJson(org.bson.json.JsonWriterSettings.builder().indent(true).build());
                    java.nio.file.Files.writeString(out, json + "\n", java.nio.charset.StandardCharsets.UTF_8);
                    var store = com.chromecide.lowtalk.hytale.json.JsonDialogues.store();
                    if (store != null) store.loadAssetsFromPaths(packName, java.util.List.of(out));
                    context.sendMessage(jsonId.equals(id)
                            ? msg(plugin, "wroteJson").param("file", out.getFileName().toString()).param("pack", packName).param("id", jsonId)
                            : msg(plugin, "wroteJsonKept").param("file", out.getFileName().toString()).param("pack", packName).param("id", jsonId).param("original", id));
                } else {
                    java.nio.file.Path out = dir.resolve(id + ".talk");
                    if (java.nio.file.Files.exists(out)) {
                        context.sendMessage(msg(plugin, "fileExists").param("file", out.getFileName().toString()));
                        return;
                    }
                    java.nio.file.Files.writeString(out, com.chromecide.lowtalk.parser.Printer.dialogue(d), java.nio.charset.StandardCharsets.UTF_8);
                    DialogueRegistry.LoadReport r = plugin.getRegistry().loadFile(out, null, true);
                    context.sendMessage(msg(plugin, r.ok() ? "wrote" : "wroteProblems").param("file", out.toString()));
                }
            } catch (java.io.IOException e) {
                context.sendMessage(msg(plugin, "writeFailed").param("error", String.valueOf(e.getMessage())));
            }
        }
    }

    /** village_elder -> Village_Elder, the way the game names assets. */
    static String assetName(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append('_');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.isEmpty() ? id : sb.toString();
    }

    /** /lowtalk info <id>: nodes, options, variables and unreachable nodes of a loaded dialogue. */
    static class Info extends CommandBase {
        private final LowTalkPlugin plugin;
        private final RequiredArg<String> idArg = withRequiredArg("dialogue", "Dialogue id", dialogueIds());

        Info(LowTalkPlugin plugin) {
            super("info", "Outline of a loaded dialogue");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            Dialogue d = plugin.getRegistry().byId(idArg.get(context));
            if (d == null) {
                context.sendMessage(noDialogue(plugin, idArg.get(context)));
                return;
            }
            for (String line : com.chromecide.lowtalk.parser.Outline.of(d).lines()) context.sendMessage(info(plugin, line));
        }
    }

    /** Open a dialogue by id with the NPC you are looking at (or with no NPC). */
    static class Open extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;
        private final RequiredArg<String> idArg = withRequiredArg("dialogue", "Dialogue id", dialogueIds());

        Open(LowTalkPlugin plugin) {
            super("open", "Open a dialogue by id");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            String id = idArg.get(context);
            Dialogue d = plugin.getRegistry().byId(id);
            if (d == null) {
                context.sendMessage(noDialogue(plugin, id));
                return;
            }
            plugin.getSessions().openFor(d, player, ref, store, world, lookedAtNpc(ref, store, player, plugin));
        }
    }

    /** Bind a dialogue to the block you are looking at, or list or remove bindings. */
    static class BlockCommand extends AbstractCommandCollection {
        BlockCommand(LowTalkPlugin plugin) {
            super("block", "Dialogues bound to placed blocks");
            this.requirePermission(CREATOR);
            this.addSubCommand(new BlockBind(plugin));
            this.addSubCommand(new BlockUnbind(plugin));
            this.addSubCommand(new BlockList(plugin));
        }
    }

    /** The block the player is looking at, within reach, with its type id; null when none. */
    @Nullable
    static Object[] lookedAtBlock(Ref<EntityStore> playerEntity, Store<EntityStore> store, World world) {
        org.joml.Vector3i pos = com.hypixel.hytale.server.core.util.TargetUtil.getTargetBlock(playerEntity, 6.0, store);
        if (pos == null) return null;
        com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk chunk =
                world.getChunkIfLoaded(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType type = chunk == null ? null : chunk.getBlockType(pos.x, pos.y, pos.z);
        return new Object[] {pos, type};
    }

    static class BlockBind extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;
        private final RequiredArg<String> idArg = withRequiredArg("dialogue", "Dialogue id", dialogueIds());
        private final OptionalArg<String> modeArg = withOptionalArg("mode", "instead (default) or also", ArgTypes.STRING);

        BlockBind(LowTalkPlugin plugin) {
            super("bind", "Bind a dialogue to the block you are looking at");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            String id = idArg.get(context);
            if (plugin.getRegistry().byId(id) == null) {
                context.sendMessage(noDialogue(plugin, id));
                return;
            }
            String mode = modeArg.provided(context) ? modeArg.get(context).trim().toLowerCase() : BlockBindings.MODE_INSTEAD;
            if (!BlockBindings.isMode(mode)) {
                context.sendMessage(msg(plugin, "blockMode"));
                return;
            }
            Object[] target = lookedAtBlock(ref, store, world);
            if (target == null) {
                context.sendMessage(msg(plugin, "lookAtBlock"));
                return;
            }
            org.joml.Vector3i pos = (org.joml.Vector3i) target[0];
            com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType type = (com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType) target[1];
            String blockId = type == null ? "?" : String.valueOf(type.getId());
            if (type == null || type.getInteractions() == null || !type.getInteractions().containsKey(com.hypixel.hytale.protocol.InteractionType.Use)) {
                context.sendMessage(msg(plugin, "blockNoUse").param("block", blockId));
                return;
            }
            plugin.getBlockBindings().set(world.getName(), pos.x, pos.y, pos.z, id, mode);
            plugin.getBlockBindings().flush();
            context.sendMessage(msg(plugin, "blockBound").param("block", blockId).param("dialogue", id).param("mode", mode));
        }
    }

    static class BlockUnbind extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        BlockUnbind(LowTalkPlugin plugin) {
            super("unbind", "Remove the dialogue from the block you are looking at");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            Object[] target = lookedAtBlock(ref, store, world);
            if (target == null) {
                context.sendMessage(msg(plugin, "lookAtBlock"));
                return;
            }
            org.joml.Vector3i pos = (org.joml.Vector3i) target[0];
            String blockId = target[1] == null ? "?" : String.valueOf(((com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType) target[1]).getId());
            boolean had = plugin.getBlockBindings().remove(world.getName(), pos.x, pos.y, pos.z);
            plugin.getBlockBindings().flush();
            context.sendMessage(msg(plugin, had ? "blockUnbound" : "blockNotBound").param("block", blockId));
        }
    }

    static class BlockList extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        BlockList(LowTalkPlugin plugin) {
            super("list", "List every block binding");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            context.sendMessage(msg(plugin, "blockBindings").param("count", plugin.getBlockBindings().size()));
            for (String line : plugin.getBlockBindings().describeAll()) context.sendMessage(info(plugin, line));
        }
    }

    static class PropCommand extends AbstractCommandCollection {
        PropCommand(LowTalkPlugin plugin) {
            super("prop", "Dialogues bound to props");
            this.requirePermission(CREATOR);
            this.addSubCommand(new PropList(plugin));
            this.addSubCommand(new PropUnbind(plugin));
        }
    }

    static class PropList extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        PropList(LowTalkPlugin plugin) {
            super("list", "List every prop binding");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            context.sendMessage(msg(plugin, "propBindings").param("count", plugin.getPropBindings().size()));
            for (String line : plugin.getPropBindings().describeAll()) context.sendMessage(info(plugin, line));
        }
    }

    /** Drop a binding by UUID, for props that no longer exist; the tool's Unbind covers props still in the world. */
    static class PropUnbind extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;
        private final RequiredArg<String> idArg = withRequiredArg("uuid", "Prop entity UUID, from /lowtalk prop list", ArgTypes.STRING);

        PropUnbind(LowTalkPlugin plugin) {
            super("unbind", "Remove a prop binding by UUID");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            java.util.UUID id;
            try {
                id = java.util.UUID.fromString(idArg.get(context).trim());
            } catch (IllegalArgumentException e) {
                context.sendMessage(msg(plugin, "propBadUuid"));
                return;
            }
            boolean had = plugin.getPropBindings().remove(id);
            plugin.getPropBindings().flush();
            Ref<EntityStore> prop = store.getExternalData().getRefFromUUID(id);
            if (prop != null) com.chromecide.lowtalk.hytale.PropSupport.removeInteractions(prop, store);
            context.sendMessage(msg(plugin, had ? "propUnbound" : "propNotBound").param("prop", id.toString()));
        }
    }

    /** Tag or untag the NPC you are looking at. */
    static class Tag extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;
        private final boolean add;
        private final RequiredArg<String> tagArg = withRequiredArg("tag", "Tag name, without the @", ArgTypes.STRING);

        Tag(LowTalkPlugin plugin, boolean add) {
            super(add ? "tag" : "untag", add ? "Tag the NPC you are looking at" : "Remove a tag from the NPC you are looking at");
            this.plugin = plugin;
            this.add = add;
            this.requirePermission(ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            String tag = tagArg.get(context).trim();
            if (tag.startsWith("@")) tag = tag.substring(1);
            if (tag.isEmpty()) {
                context.sendMessage(msg(plugin, "tagNeedsName"));
                return;
            }
            NpcInfo npc = lookedAtNpc(ref, store, player, plugin);
            if (npc == null) {
                context.sendMessage(msg(plugin, "lookAtNpc"));
                return;
            }
            VariableStore vs = plugin.getStore();
            boolean changed = add ? vs.addTag(vs.npc(npc.id()), tag) : vs.removeTag(vs.npc(npc.id()), tag);
            vs.flush();
            context.sendMessage(msg(plugin, changed ? (add ? "tagged" : "untagged") : (add ? "tagAlready" : "tagMissing"))
                    .param("npc", npc.name()).param("role", String.valueOf(npc.role())).param("tag", tag));
        }
    }

    static class Tags extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        Tags(LowTalkPlugin plugin) {
            super("tags", "Show the tags and bound dialogues of the NPC you are looking at");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            NpcInfo npc = lookedAtNpc(ref, store, player, plugin);
            if (npc == null) {
                context.sendMessage(msg(plugin, "lookAtNpc"));
                return;
            }
            context.sendMessage(info(plugin, npc.name() + "  role=" + npc.role() + "  tags=" + npc.tags() + "  id=" + npc.id()));
            for (Dialogue d : plugin.getRegistry().candidates(npc.role(), npc.tags())) {
                context.sendMessage(info(plugin, "  dialogue: " + d.id()));
            }
        }
    }

    /** Show your own saved variables. */
    static class Vars extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        Vars(LowTalkPlugin plugin) {
            super("vars", "Show your saved dialogue variables");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            VariableStore vs = plugin.getStore();
            Map<String, Map<String, Object>> scopes = vs.snapshotScopes(vs.player(player.getUuid()));
            if (scopes.isEmpty()) {
                context.sendMessage(msg(plugin, "noVars"));
                return;
            }
            scopes.forEach((scope, vars) -> vars.forEach((k, v) ->
                    context.sendMessage(info(plugin, scope + ": $" + k + " = " + v))));
        }
    }

    /** Forget everything every dialogue knows about you. For testing. */
    static class Reset extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        Reset(LowTalkPlugin plugin) {
            super("reset", "Forget everything every dialogue knows about you");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            plugin.getSessions().end(player.getUuid());
            int removed = plugin.getStore().resetPlayer(player.getUuid());
            context.sendMessage(msg(plugin, "forgotten").param("count", removed));
        }
    }

    /** Free an NPC that was left frozen. */
    static class Thaw extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        Thaw(LowTalkPlugin plugin) {
            super("thaw", "Unfreeze the NPC you are looking at");
            this.plugin = plugin;
            this.requirePermission(ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            NpcInfo npc = lookedAtNpc(ref, store, player, plugin);
            if (npc == null) {
                context.sendMessage(msg(plugin, "lookAtNpc"));
                return;
            }
            boolean was = NpcHold.thawNow(store, npc.ref(), npc.id(), plugin.getStore());
            context.sendMessage(msg(plugin, was ? "thawed" : "notFrozen").param("npc", npc.name()));
        }
    }

    /**
     * /lowtalk test <dialogue> [apply] [choice ...]
     * Plays a dialogue headlessly with the NPC you are looking at (or none) and prints every step.
     * Effects are listed unless the first token is "apply".
     */
    static class TestDialogue extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;
        private final RequiredArg<String> idArg = withRequiredArg("dialogue", "Dialogue id", dialogueIds());
        private final OptionalArg<String> scriptArg = withOptionalArg("script", "'apply' to run effects for real, then choices: numbers or text prefixes", ArgTypes.GREEDY_STRING);

        TestDialogue(LowTalkPlugin plugin) {
            super("test", "Play a dialogue from the console with scripted choices");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            Dialogue d = plugin.getRegistry().byId(idArg.get(context));
            if (d == null) {
                context.sendMessage(msg(plugin, "noDialogue").param("id", idArg.get(context)));
                return;
            }
            java.util.List<String> tokens = new java.util.ArrayList<>();
            if (scriptArg.provided(context)) {
                for (String t : scriptArg.get(context).trim().split("\\s+")) if (!t.isEmpty()) tokens.add(t);
            }
            boolean apply = !tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("apply");
            if (apply) tokens.remove(0);
            NpcInfo npc = lookedAtNpc(ref, store, player, plugin);
            java.util.UUID npcId = npc == null ? new java.util.UUID(0L, 0L) : npc.id();
            String npcName = npc == null ? (d.speaker() != null ? d.speaker() : "Narrator") : npc.name();
            TestRunner runner = new TestRunner(plugin, d, player, world, npcId, npcName, apply, line -> {
                context.sendMessage(info(plugin, line));
                plugin.getLogger().at(java.util.logging.Level.INFO).log("[test] %s", line);
            });
            runner.run(tokens);
        }
    }

    /** /lowtalk testworld build | go */
    static class TestWorldCommand extends AbstractCommandCollection {
        TestWorldCommand(LowTalkPlugin plugin) {
            super("testworld", "Build or visit the LowTalk test corridor");
            this.requirePermission(ADMIN);
            this.addSubCommand(new TestWorldBuild(plugin));
            this.addSubCommand(new TestWorldGo(plugin));
            this.addSubCommand(new TestWorldRespawn(plugin));
            this.addSubCommand(new TestWorldProbe(plugin));
            this.addSubCommand(new TestWorldLeave(plugin));
        }
    }

    static class TestWorldLeave extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        TestWorldLeave(LowTalkPlugin plugin) {
            super("leave", "Return to the main world with your old game mode and unload the test world");
            this.plugin = plugin;
            this.requirePermission(ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            TestWorld.leave(plugin, player, reporter(plugin, player));
        }
    }

    static class TestWorldProbe extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        TestWorldProbe(LowTalkPlugin plugin) {
            super("probe", "Report why the nearest NPC does or does not react to you");
            this.plugin = plugin;
            this.requirePermission(ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            TestWorld.probe(ref, store, player, world, reporter(plugin, player));
        }
    }

    static class TestWorldRespawn extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        TestWorldRespawn(LowTalkPlugin plugin) {
            super("respawn", "Reset your test state, respawn the corridor's NPCs and return to the entrance");
            this.plugin = plugin;
            this.requirePermission(ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            java.util.function.Consumer<String> out = reporter(plugin, player);
            plugin.getSessions().end(player.getUuid());
            TestWorld.resetPlayer(plugin, ref, store, player, out);
            TestWorld.respawn(plugin, out);
            TestWorld.teleport(plugin, player, out);
        }
    }

    static java.util.function.Consumer<String> reporter(LowTalkPlugin plugin, PlayerRef player) {
        return line -> {
            player.sendMessage(info(plugin, line));
            plugin.getLogger().at(java.util.logging.Level.INFO).log("[testworld] %s", line);
        };
    }

    static class TestWorldBuild extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        TestWorldBuild(LowTalkPlugin plugin) {
            super("build", "Create the test world and build the corridor of stations");
            this.plugin = plugin;
            this.requirePermission(ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            plugin.getRegistry().copyTestDialogues();
            DialogueRegistry.LoadReport report = plugin.reloadDialogues(); // the stations' dialogues must be loaded before anyone talks to them
            if (!report.ok()) context.sendMessage(msg(plugin, "someNotLoaded").param("messages", String.join(" ", report.messages())));
            TestWorld.build(plugin, reporter(plugin, player));
        }
    }

    static class TestWorldGo extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        TestWorldGo(LowTalkPlugin plugin) {
            super("go", "Teleport to the test corridor");
            this.plugin = plugin;
            this.requirePermission(ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            TestWorld.teleport(plugin, player, reporter(plugin, player));
        }
    }

    /** /lowtalk tool: put the dialogue editing tool in your hand, like /triggervolume tool does for its tool. */
    static class Tool extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        Tool(LowTalkPlugin plugin) {
            super("tool", "Get the LowTalk tool: click an NPC with it to edit its dialogue in place");
            this.plugin = plugin;
            this.requirePermission(CREATOR);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            com.hypixel.hytale.server.core.inventory.InventoryComponent.Hotbar hotbar =
                    store.getComponent(ref, com.hypixel.hytale.server.core.inventory.InventoryComponent.Hotbar.getComponentType());
            if (hotbar == null) {
                context.sendMessage(msg(plugin, "noHotbar"));
                return;
            }
            hotbar.getInventory().setItemStackForSlot(hotbar.getActiveSlot(), new com.hypixel.hytale.server.core.inventory.ItemStack(DialogueEditorPage.TOOL_ITEM));
            context.sendMessage(msg(plugin, "toolInHand"));
        }
    }

    static class Stop extends AbstractPlayerCommand {
        private final LowTalkPlugin plugin;

        Stop(LowTalkPlugin plugin) {
            super("stop", "Leave your current conversation");
            this.plugin = plugin;
            this.requireNoPermission();
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            plugin.getSessions().end(player.getUuid());
        }
    }

    static NpcInfo lookedAtNpc(Ref<EntityStore> playerEntity, Store<EntityStore> store, PlayerRef player, LowTalkPlugin plugin) {
        return NpcInfo.lookedAt(playerEntity, store, player, plugin.getStore());
    }
}
