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
    }

    /** Tab completion and did-you-mean for dialogue ids; the id argument of open, info, test and convert. */
    static DialogueIdArgument dialogueIds() {
        // Field initialisers run before the constructor stores the plugin, so look it up when suggestions are asked for.
        return new DialogueIdArgument(() -> LowTalkPlugin.get() == null ? java.util.List.of() : LowTalkPlugin.get().getRegistry().ids());
    }

    private static Message info(LowTalkPlugin plugin, String text) {
        return Message.raw(text).color(plugin.getSettings().getInfoColor());
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
            context.sendMessage(info(plugin, "LowTalk: " + report.loaded() + " of " + report.files() + " dialogue file(s) loaded, "
                    + report.errors() + " error(s), " + report.warnings() + " warning(s)."));
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
                context.sendMessage(info(plugin, "No dialogues loaded. Put .talk files in " + reg.getFolder() + " and run /lowtalk reload."));
                return;
            }
            for (String id : reg.ids()) {
                Dialogue d = reg.byId(id);
                context.sendMessage(info(plugin, id + "  ->  " + (d.bindings().isEmpty() ? "(command only)" : String.join(", ", d.bindings()))
                        + "  [" + d.nodes().size() + " nodes]"));
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
                context.sendMessage(info(plugin, "LowTalk format help. /lowtalk help commands | functions | keywords lists each group; /lowtalk help <name> explains one."));
                context.sendMessage(info(plugin, "Commands: " + names(com.chromecide.lowtalk.parser.Reference.commands())));
                context.sendMessage(info(plugin, "Functions: " + names(com.chromecide.lowtalk.parser.Reference.functions())));
                context.sendMessage(info(plugin, "Keywords: " + names(com.chromecide.lowtalk.parser.Reference.keywords())));
                context.sendMessage(info(plugin, "Full docs: docs/format.md in the LowTalk repository."));
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
                context.sendMessage(info(plugin, "Nothing called '" + topic + "'." + (near == null ? "" : " Did you mean " + near + "?")));
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
                String near = com.chromecide.lowtalk.parser.Suggest.closest(id, plugin.getRegistry().ids());
                context.sendMessage(info(plugin, "No dialogue with id '" + id + "'." + (near == null ? " Try /lowtalk list." : " Did you mean " + near + "?")));
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
                    context.sendMessage(info(plugin, "No asset pack called '" + packName + "'. Writable packs: " + (names.isEmpty() ? "none (create one in the Asset Editor)" : names)));
                    return;
                }
                if (pack.isImmutable()) {
                    context.sendMessage(info(plugin, "Asset pack '" + packName + "' is read-only; create a pack in the Asset Editor first."));
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
                        context.sendMessage(info(plugin, out.getFileName() + " already exists in that pack; delete or rename it first."));
                        return;
                    }
                    com.chromecide.lowtalk.hytale.json.DialogueAsset asset = com.chromecide.lowtalk.hytale.json.JsonConvert.toAsset(d);
                    asset.setId(jsonId);
                    if (asset.scope == null && !jsonId.equals(d.scope())) asset.scope = d.scope(); // share memory with the original
                    String json = com.chromecide.lowtalk.hytale.json.JsonCodecs.DIALOGUE
                            .encode(asset, com.hypixel.hytale.codec.EmptyExtraInfo.EMPTY).asDocument()
                            .toJson(org.bson.json.JsonWriterSettings.builder().indent(true).build());
                    java.nio.file.Files.writeString(out, json + "\n", java.nio.charset.StandardCharsets.UTF_8);
                    var store = com.chromecide.lowtalk.hytale.json.JsonDialogues.store();
                    if (store != null) store.loadAssetsFromPaths(packName, java.util.List.of(out));
                    context.sendMessage(info(plugin, "Wrote " + out.getFileName() + " into " + packName + " as dialogue '" + jsonId + "'"
                            + (jsonId.equals(id) ? "." : " (the original keeps '" + id + "'; both are loaded and share the same variables, so remove one when you have chosen).")));
                } else {
                    java.nio.file.Path out = dir.resolve(id + ".talk");
                    if (java.nio.file.Files.exists(out)) {
                        context.sendMessage(info(plugin, out.getFileName() + " already exists there; delete or rename it first."));
                        return;
                    }
                    java.nio.file.Files.writeString(out, com.chromecide.lowtalk.parser.Printer.dialogue(d), java.nio.charset.StandardCharsets.UTF_8);
                    DialogueRegistry.LoadReport r = plugin.getRegistry().loadFile(out, null, true);
                    context.sendMessage(info(plugin, "Wrote " + out + (r.ok() ? "" : " (with problems, see the server log)") + "."));
                }
            } catch (java.io.IOException e) {
                context.sendMessage(info(plugin, "Could not write the file: " + e.getMessage()));
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
                String near = com.chromecide.lowtalk.parser.Suggest.closest(idArg.get(context), plugin.getRegistry().ids());
                context.sendMessage(info(plugin, "No dialogue with id '" + idArg.get(context) + "'." + (near == null ? " Try /lowtalk list." : " Did you mean " + near + "?")));
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
                String near = com.chromecide.lowtalk.parser.Suggest.closest(id, plugin.getRegistry().ids());
                context.sendMessage(info(plugin, "No dialogue with id '" + id + "'." + (near == null ? " Try /lowtalk list." : " Did you mean " + near + "?")));
                return;
            }
            plugin.getSessions().openFor(d, player, ref, store, world, lookedAtNpc(ref, store, player, plugin));
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
                context.sendMessage(info(plugin, "Give a tag name."));
                return;
            }
            NpcInfo npc = lookedAtNpc(ref, store, player, plugin);
            if (npc == null) {
                context.sendMessage(info(plugin, "Look at an NPC first."));
                return;
            }
            VariableStore vs = plugin.getStore();
            boolean changed = add ? vs.addTag(vs.npc(npc.id()), tag) : vs.removeTag(vs.npc(npc.id()), tag);
            vs.flush();
            context.sendMessage(info(plugin, changed
                    ? (add ? "Tagged " : "Untagged ") + npc.name() + " (" + npc.role() + ") " + (add ? "with" : "from") + " @" + tag
                    : npc.name() + (add ? " already has" : " does not have") + " @" + tag));
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
                context.sendMessage(info(plugin, "Look at an NPC first."));
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
                context.sendMessage(info(plugin, "No variables saved for you yet."));
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
            context.sendMessage(info(plugin, "Forgotten. " + removed + " memory file(s) removed; every NPC meets you fresh now."));
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
                context.sendMessage(info(plugin, "Look at an NPC first."));
                return;
            }
            boolean was = NpcHold.thawNow(store, npc.ref(), npc.id(), plugin.getStore());
            context.sendMessage(info(plugin, was ? npc.name() + " is free to move again." : npc.name() + " was not frozen."));
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
                context.sendMessage(info(plugin, "No dialogue with id '" + idArg.get(context) + "'. Try /lowtalk list."));
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
