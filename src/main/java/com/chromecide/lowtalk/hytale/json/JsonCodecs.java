package com.chromecide.lowtalk.hytale.json;

import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.builtin.adventure.objectives.config.ObjectiveAsset;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.lookup.CodecMapCodec;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditor;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Codecs for the JSON dialogue asset. Built here, not in the data classes, so the data classes never touch game
 * classes. The documentation strings become tooltips in the Asset Editor's form; asset validators become pickers.
 */
public final class JsonCodecs {
    private JsonCodecs() {}

    /** Polymorphic statement codec, keyed by "Type". Subtypes are registered in {@link #register()}. */
    public static final CodecMapCodec<JsonStatement> STATEMENT = new CodecMapCodec<>("Type");
    private static final ArrayCodec<JsonStatement> STATEMENTS = new ArrayCodec<>(STATEMENT, JsonStatement[]::new);

    private static final String EXPR_DOC = " A LowTalk expression, e.g. $met, not $player.done, has(\"Food_Bread\", 2), chance(0.5).";
    private static final String TEXT_DOC = " Text may use {player}, {npc}, {$var}, {expr ? a : b} and [one|of|these].";

    // ---- helpers to keep the field definitions short

    private static <T> BuilderCodec<T> statement(Class<T> cls, java.util.function.Supplier<T> make, String doc,
                                                 java.util.function.Consumer<BuilderCodec.Builder<T>> fields) {
        BuilderCodec.Builder<T> b = BuilderCodec.builder(cls, make).documentation(doc);
        fields.accept(b);
        return b.build();
    }

    private static <T> void body(BuilderCodec.Builder<T> b, String key, java.util.function.BiConsumer<T, List<JsonStatement>> set,
                                 java.util.function.Function<T, List<JsonStatement>> get, String doc) {
        b.<JsonStatement[]>append(new KeyedCodec<>(key, STATEMENTS), (t, v) -> set.accept(t, v == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(v))),
                        t -> get.apply(t) == null ? null : get.apply(t).toArray(new JsonStatement[0]))
                .documentation(doc).add();
    }

    // ---- statements

    public static final BuilderCodec<JsonStatement.Say> SAY = statement(JsonStatement.Say.class, JsonStatement.Say::new,
            "A spoken line. Consecutive lines get a Continue button; the last line before options is shown with them.", b -> {
                b.append(new KeyedCodec<>("Speaker", Codec.STRING), (s, v) -> s.speaker = v, s -> s.speaker)
                        .documentation("Who says it. Leave empty for the dialogue's default speaker (the NPC's name).").add();
                b.append(new KeyedCodec<>("Text", Codec.STRING), (s, v) -> s.text = v, s -> s.text)
                        .addValidator(Validators.nonNull()).metadata(new UIEditor(new UIEditor.MultilineTextField()))
                        .documentation("What is said." + TEXT_DOC).add();
            });

    public static final BuilderCodec<JsonStatement.OptionEntry> OPTION = statement(JsonStatement.OptionEntry.class, JsonStatement.OptionEntry::new,
            "One button the player can pick. Its Body runs when chosen; if the body does not Jump or End, the options are shown again.", b -> {
                b.append(new KeyedCodec<>("Text", Codec.STRING), (o, v) -> o.text = v, o -> o.text)
                        .addValidator(Validators.nonNull()).documentation("Button text." + TEXT_DOC).add();
                b.append(new KeyedCodec<>("If", Codec.STRING), (o, v) -> o.ifExpr = v, o -> o.ifExpr)
                        .documentation("Hide the option unless this is true." + EXPR_DOC).add();
                b.append(new KeyedCodec<>("ShowIf", Codec.STRING), (o, v) -> o.showIf = v, o -> o.showIf)
                        .documentation("Show the option greyed out unless this is true." + EXPR_DOC).add();
                b.append(new KeyedCodec<>("Once", Codec.BOOLEAN), (o, v) -> o.once = v, o -> o.once)
                        .documentation("Hide the option for good once the player has picked it.").add();
                body(b, "Body", (o, v) -> o.body = v, o -> o.body, "What happens when this option is chosen.");
            });

    public static final BuilderCodec<JsonStatement.Choice> CHOICE = statement(JsonStatement.Choice.class, JsonStatement.Choice::new,
            "A set of options shown together as buttons (at most eight).", b -> {
                b.<JsonStatement.OptionEntry[]>append(new KeyedCodec<>("Options", new ArrayCodec<>(OPTION, JsonStatement.OptionEntry[]::new)),
                                (c, v) -> c.options = v == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(v)),
                                c -> c.options.toArray(new JsonStatement.OptionEntry[0]))
                        .addValidator(Validators.nonNull()).documentation("The buttons, in order.").add();
            });

    public static final BuilderCodec<JsonStatement.Branch> BRANCH = statement(JsonStatement.Branch.class, JsonStatement.Branch::new,
            "One branch of an If. The first needs a When; a last branch without When is the else.", b -> {
                b.append(new KeyedCodec<>("When", Codec.STRING), (x, v) -> x.when = v, x -> x.when)
                        .documentation("Condition for this branch; leave empty on the last branch for an else." + EXPR_DOC).add();
                body(b, "Body", (x, v) -> x.body = v, x -> x.body, "Runs when the condition holds.");
            });

    public static final BuilderCodec<JsonStatement.If> IF = statement(JsonStatement.If.class, JsonStatement.If::new,
            "if / elseif / else. Branches are tried in order.", b -> {
                b.<JsonStatement.Branch[]>append(new KeyedCodec<>("Branches", new ArrayCodec<>(BRANCH, JsonStatement.Branch[]::new)),
                                (f, v) -> f.branches = v == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(v)),
                                f -> f.branches.toArray(new JsonStatement.Branch[0]))
                        .addValidator(Validators.nonNull()).documentation("The branches, in order.").add();
            });

    public static final BuilderCodec<JsonStatement.Once> ONCE = statement(JsonStatement.Once.class, JsonStatement.Once::new,
            "Runs the first time this player reaches it with this NPC, and never again.", b ->
                    body(b, "Body", (o, v) -> o.body = v, o -> o.body, "Runs once."));

    public static final BuilderCodec<JsonStatement.Alternative> ALTERNATIVE = statement(JsonStatement.Alternative.class, JsonStatement.Alternative::new,
            "One alternative of a Random block.", b -> body(b, "Body", (a, v) -> a.body = v, a -> a.body, "Statements for this alternative."));

    public static final BuilderCodec<JsonStatement.Random> RANDOM = statement(JsonStatement.Random.class, JsonStatement.Random::new,
            "Runs exactly one alternative, chosen at random each time.", b -> {
                b.<JsonStatement.Alternative[]>append(new KeyedCodec<>("Alternatives", new ArrayCodec<>(ALTERNATIVE, JsonStatement.Alternative[]::new)),
                                (r, v) -> r.alternatives = v == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(v)),
                                r -> r.alternatives.toArray(new JsonStatement.Alternative[0]))
                        .addValidator(Validators.nonNull()).documentation("At least two alternatives.").add();
            });

    public static final BuilderCodec<JsonStatement.Set> SET = statement(JsonStatement.Set.class, JsonStatement.Set::new,
            "Store a value in a variable.", b -> {
                b.append(new KeyedCodec<>("Var", Codec.STRING), (s, v) -> s.var = v, s -> s.var).addValidator(Validators.nonNull())
                        .documentation("The variable: $x (this player with this NPC), $player.x, $npc.x, $world.x or $tmp.x.").add();
                b.append(new KeyedCodec<>("Value", Codec.STRING), (s, v) -> s.value = v, s -> s.value).addValidator(Validators.nonNull())
                        .documentation("The value." + EXPR_DOC).add();
            });

    public static final BuilderCodec<JsonStatement.Jump> JUMP = statement(JsonStatement.Jump.class, JsonStatement.Jump::new,
            "Continue at another node.", b ->
                    b.append(new KeyedCodec<>("Node", Codec.STRING), (j, v) -> j.node = v, j -> j.node).addValidator(Validators.nonNull())
                            .documentation("Name of the node to continue at.").add());

    public static final BuilderCodec<JsonStatement.End> END = statement(JsonStatement.End.class, JsonStatement.End::new,
            "Close the window.", b -> {});

    public static final BuilderCodec<JsonStatement.Input> INPUT = statement(JsonStatement.Input.class, JsonStatement.Input::new,
            "Ask the player to type something and store it.", b -> {
                b.append(new KeyedCodec<>("Var", Codec.STRING), (i, v) -> i.var = v, i -> i.var).addValidator(Validators.nonNull())
                        .documentation("Where to store the text, e.g. $tmp.answer.").add();
                b.append(new KeyedCodec<>("Prompt", Codec.STRING), (i, v) -> i.prompt = v, i -> i.prompt)
                        .documentation("Shown above the text box." + TEXT_DOC).add();
            });

    public static final BuilderCodec<JsonStatement.Wait> WAIT = statement(JsonStatement.Wait.class, JsonStatement.Wait::new,
            "Pause before the next line; the previous line shows with no Continue button.", b ->
                    b.append(new KeyedCodec<>("Seconds", Codec.STRING), (w, v) -> w.seconds = v, w -> w.seconds).addValidator(Validators.nonNull())
                            .documentation("How long, in seconds (0 to 30); may be an expression.").add());

    public static final BuilderCodec<JsonStatement.Command> COMMAND = statement(JsonStatement.Command.class, JsonStatement.Command::new,
            "Any LowTalk command by name, e.g. notify, title, heal, stat, learn, teleport, time, npc_name, spawn, despawn, reputation, attitude, anim, run.", b -> {
                b.append(new KeyedCodec<>("Name", Codec.STRING), (c, v) -> c.name = v, c -> c.name).addValidator(Validators.nonNull())
                        .metadata(new UIEditor(new UIEditor.TextField(JsonDialogues.DATASET_COMMANDS)))
                        .documentation("Command name, as in <<name ...>>. See /lowtalk help commands.").add();
                b.append(new KeyedCodec<>("Args", Codec.STRING_ARRAY), (c, v) -> c.args = v == null ? new String[0] : v, c -> c.args)
                        .documentation("Arguments, one per entry, as they would appear in the <<...>>." + TEXT_DOC).add();
            });

    public static final BuilderCodec<JsonStatement.Give> GIVE = statement(JsonStatement.Give.class, JsonStatement.Give::new,
            "Put items in the player's inventory.", b -> {
                b.append(new KeyedCodec<>("Item", Codec.STRING), (g, v) -> g.item = v, g -> g.item)
                        .addValidator(Validators.nonNull()).addValidator(Item.VALIDATOR_CACHE.getValidator()).documentation("The item.").add();
                b.append(new KeyedCodec<>("Count", Codec.INTEGER), (g, v) -> g.count = v, g -> g.count).documentation("How many (default 1).").add();
            });

    public static final BuilderCodec<JsonStatement.Take> TAKE = statement(JsonStatement.Take.class, JsonStatement.Take::new,
            "Remove items from the player's inventory; fails the option if they lack them, so guard with has().", b -> {
                b.append(new KeyedCodec<>("Item", Codec.STRING), (g, v) -> g.item = v, g -> g.item)
                        .addValidator(Validators.nonNull()).addValidator(Item.VALIDATOR_CACHE.getValidator()).documentation("The item.").add();
                b.append(new KeyedCodec<>("Count", Codec.INTEGER), (g, v) -> g.count = v, g -> g.count).documentation("How many (default 1).").add();
            });

    public static final BuilderCodec<JsonStatement.Sound> SOUND = statement(JsonStatement.Sound.class, JsonStatement.Sound::new,
            "Play a sound at the NPC.", b ->
                    b.append(new KeyedCodec<>("Sound", Codec.STRING), (s, v) -> s.sound = v, s -> s.sound)
                            .addValidator(Validators.nonNull()).addValidator(SoundEvent.VALIDATOR_CACHE.getValidator()).documentation("The sound event.").add());

    public static final BuilderCodec<JsonStatement.Effect> EFFECT = statement(JsonStatement.Effect.class, JsonStatement.Effect::new,
            "Apply an entity effect to the player.", b ->
                    b.append(new KeyedCodec<>("Effect", Codec.STRING), (e, v) -> e.effect = v, e -> e.effect)
                            .addValidator(Validators.nonNull()).addValidator(EntityEffect.VALIDATOR_CACHE.getValidator()).documentation("The effect.").add());

    public static final BuilderCodec<JsonStatement.Cure> CURE = statement(JsonStatement.Cure.class, JsonStatement.Cure::new,
            "Remove an entity effect from the player.", b ->
                    b.append(new KeyedCodec<>("Effect", Codec.STRING), (e, v) -> e.effect = v, e -> e.effect)
                            .addValidator(Validators.nonNull()).addValidator(EntityEffect.VALIDATOR_CACHE.getValidator()).documentation("The effect.").add());

    public static final BuilderCodec<JsonStatement.Objective> OBJECTIVE = statement(JsonStatement.Objective.class, JsonStatement.Objective::new,
            "Start an objective for the player. For cancel, line or task use a Command named objective.", b ->
                    b.append(new KeyedCodec<>("Objective", Codec.STRING), (o, v) -> o.objective = v, o -> o.objective)
                            .addValidator(Validators.nonNull()).addValidator(ObjectiveAsset.VALIDATOR_CACHE.getValidator()).documentation("The objective.").add());

    public static final BuilderCodec<JsonStatement.Weather> WEATHER = statement(JsonStatement.Weather.class, JsonStatement.Weather::new,
            "Change the weather for the world, or for this player only.", b -> {
                b.append(new KeyedCodec<>("Weather", Codec.STRING), (w, v) -> w.weather = v, w -> w.weather)
                        .addValidator(Validators.nonNull()).metadata(new UIEditor(new UIEditor.TextField(JsonDialogues.DATASET_WEATHERS)))
                        .documentation("A weather id, or \"clear\" to return to the natural sky.").add();
                b.append(new KeyedCodec<>("PlayerOnly", Codec.BOOLEAN), (w, v) -> w.playerOnly = v, w -> w.playerOnly)
                        .documentation("Only this player sees it.").add();
            });

    private static UIEditor pick(String dataSet) {
        return new UIEditor(new UIEditor.TextField(dataSet));
    }

    public static final BuilderCodec<JsonStatement.Attitude> ATTITUDE = statement(JsonStatement.Attitude.class, JsonStatement.Attitude::new,
            "Set this NPC's attitude toward the player for a while.", b ->
                    b.append(new KeyedCodec<>("Attitude", Codec.STRING), (a, v) -> a.attitude = v, a -> a.attitude).addValidator(Validators.nonNull())
                            .metadata(pick(JsonDialogues.DATASET_ATTITUDES)).documentation("ignore, hostile, neutral, friendly or revered.").add());

    public static final BuilderCodec<JsonStatement.Anim> ANIM = statement(JsonStatement.Anim.class, JsonStatement.Anim::new,
            "Play an animation on the NPC.", b -> {
                b.append(new KeyedCodec<>("Animation", Codec.STRING), (a, v) -> a.animation = v, a -> a.animation).addValidator(Validators.nonNull())
                        .metadata(pick(JsonDialogues.DATASET_ANIMATIONS)).documentation("Animation name; must exist on the NPC's model.").add();
                b.append(new KeyedCodec<>("Slot", Codec.STRING), (a, v) -> a.slot = v, a -> a.slot)
                        .metadata(pick(JsonDialogues.DATASET_ANIMATION_SLOTS)).documentation("Emote by default; Status is what the game uses for greetings.").add();
            });

    public static final BuilderCodec<JsonStatement.Notify> NOTIFY = statement(JsonStatement.Notify.class, JsonStatement.Notify::new,
            "A toast notification in the corner of the screen.", b -> {
                b.append(new KeyedCodec<>("Text", Codec.STRING), (n, v) -> n.text = v, n -> n.text).addValidator(Validators.nonNull())
                        .documentation("Main text." + TEXT_DOC).add();
                b.append(new KeyedCodec<>("Detail", Codec.STRING), (n, v) -> n.detail = v, n -> n.detail).documentation("Smaller second line.").add();
                b.append(new KeyedCodec<>("Style", Codec.STRING), (n, v) -> n.style = v, n -> n.style)
                        .metadata(pick(JsonDialogues.DATASET_NOTIFY_STYLES)).documentation("success, warning or danger; empty for the default look.").add();
            });

    public static final BuilderCodec<JsonStatement.Title> TITLE = statement(JsonStatement.Title.class, JsonStatement.Title::new,
            "A cinematic title across the screen.", b -> {
                b.append(new KeyedCodec<>("Primary", Codec.STRING), (t, v) -> t.primary = v, t -> t.primary).addValidator(Validators.nonNull())
                        .documentation("Big text." + TEXT_DOC).add();
                b.append(new KeyedCodec<>("Secondary", Codec.STRING), (t, v) -> t.secondary = v, t -> t.secondary).documentation("Smaller text underneath.").add();
                b.append(new KeyedCodec<>("Major", Codec.BOOLEAN), (t, v) -> t.major = v, t -> t.major).documentation("The larger title style.").add();
                b.append(new KeyedCodec<>("Seconds", Codec.DOUBLE), (t, v) -> t.seconds = v, t -> t.seconds).documentation("How long it stays; 0 for the default (3).").add();
            });

    public static final BuilderCodec<JsonStatement.Stat> STAT = statement(JsonStatement.Stat.class, JsonStatement.Stat::new,
            "Add to, set, or max out an entity stat.", b -> {
                b.append(new KeyedCodec<>("Stat", Codec.STRING), (s, v) -> s.stat = v, s -> s.stat).addValidator(Validators.nonNull())
                        .metadata(pick(JsonDialogues.DATASET_STATS)).documentation("The stat, e.g. Health, Stamina.").add();
                b.append(new KeyedCodec<>("Value", Codec.STRING), (s, v) -> s.value = v, s -> s.value).addValidator(Validators.nonNull())
                        .documentation("+20 to add, -5 to take, 50 to set, or max.").add();
            });

    public static final BuilderCodec<JsonStatement.Heal> HEAL = statement(JsonStatement.Heal.class, JsonStatement.Heal::new,
            "Restore health.", b ->
                    b.append(new KeyedCodec<>("Amount", Codec.STRING), (h, v) -> h.amount = v, h -> h.amount).documentation("Empty for a full heal.").add());

    public static final BuilderCodec<JsonStatement.Learn> LEARN = statement(JsonStatement.Learn.class, JsonStatement.Learn::new,
            "Teach the player a crafting recipe.", b ->
                    b.append(new KeyedCodec<>("Recipe", Codec.STRING), (l, v) -> l.recipe = v, l -> l.recipe).addValidator(Validators.nonNull())
                            .metadata(pick(JsonDialogues.DATASET_RECIPES)).documentation("The recipe id.").add());

    public static final BuilderCodec<JsonStatement.Teleport> TELEPORT = statement(JsonStatement.Teleport.class, JsonStatement.Teleport::new,
            "Move the player; ends the conversation.", b ->
                    b.append(new KeyedCodec<>("Target", Codec.STRING), (t, v) -> t.target = v, t -> t.target).addValidator(Validators.nonNull())
                            .metadata(pick(JsonDialogues.DATASET_WARPS)).documentation("A warp name, or coordinates as \"x y z\".").add());

    public static final BuilderCodec<JsonStatement.Time> TIME = statement(JsonStatement.Time.class, JsonStatement.Time::new,
            "Set the time of day, or pause and resume the clock.", b -> {
                b.append(new KeyedCodec<>("Time", Codec.STRING), (t, v) -> t.time = v, t -> t.time).addValidator(Validators.nonNull())
                        .metadata(pick(JsonDialogues.DATASET_TIMES)).documentation("dawn, noon, dusk, midnight, an hour 0-24, pause or resume.").add();
                b.append(new KeyedCodec<>("FadeSeconds", Codec.STRING), (t, v) -> t.fadeSeconds = v, t -> t.fadeSeconds)
                        .documentation("Fade to the new time over this many seconds; empty for instant.").add();
            });

    public static final BuilderCodec<JsonStatement.Reputation> REPUTATION = statement(JsonStatement.Reputation.class, JsonStatement.Reputation::new,
            "Change the player's standing with a reputation group.", b -> {
                b.append(new KeyedCodec<>("Change", Codec.STRING), (r, v) -> r.change = v, r -> r.change).addValidator(Validators.nonNull())
                        .documentation("+10 or -5.").add();
                b.append(new KeyedCodec<>("Group", Codec.STRING), (r, v) -> r.group = v, r -> r.group)
                        .metadata(pick(JsonDialogues.DATASET_REPUTATION_GROUPS)).documentation("Empty for this NPC's own group.").add();
            });

    public static final BuilderCodec<JsonStatement.NpcName> NPC_NAME = statement(JsonStatement.NpcName.class, JsonStatement.NpcName::new,
            "Rename this NPC (kept with the NPC).", b ->
                    b.append(new KeyedCodec<>("Name", Codec.STRING), (n, v) -> n.name = v, n -> n.name).addValidator(Validators.nonNull())
                            .documentation("The new name, or clear to remove it.").add());

    public static final BuilderCodec<JsonStatement.State> STATE = statement(JsonStatement.State.class, JsonStatement.State::new,
            "Put this NPC's role into one of its states.", b -> {
                b.append(new KeyedCodec<>("State", Codec.STRING), (s, v) -> s.state = v, s -> s.state).addValidator(Validators.nonNull())
                        .documentation("State name from the role JSON.").add();
                b.append(new KeyedCodec<>("SubState", Codec.STRING), (s, v) -> s.subState = v, s -> s.subState).documentation("Optional sub-state.").add();
            });

    public static final BuilderCodec<JsonStatement.Spawn> SPAWN = statement(JsonStatement.Spawn.class, JsonStatement.Spawn::new,
            "Spawn an NPC near the player, facing them.", b -> {
                b.append(new KeyedCodec<>("Role", Codec.STRING), (s, v) -> s.role = v, s -> s.role).addValidator(Validators.nonNull())
                        .metadata(pick(JsonDialogues.DATASET_ROLES)).documentation("The NPC role to spawn.").add();
                b.append(new KeyedCodec<>("Right", Codec.DOUBLE), (s, v) -> s.right = v, s -> s.right).documentation("Blocks to the player's right.").add();
                b.append(new KeyedCodec<>("Up", Codec.DOUBLE), (s, v) -> s.up = v, s -> s.up).documentation("Blocks up.").add();
                b.append(new KeyedCodec<>("Forward", Codec.DOUBLE), (s, v) -> s.forward = v, s -> s.forward).documentation("Blocks in front of the player (default 2).").add();
            });

    public static final BuilderCodec<JsonStatement.Despawn> DESPAWN = statement(JsonStatement.Despawn.class, JsonStatement.Despawn::new,
            "End the conversation and retire this NPC.", b -> {});

    public static final BuilderCodec<JsonStatement.Run> RUN = statement(JsonStatement.Run.class, JsonStatement.Run::new,
            "Run a server command as the console. Never include text the player typed.", b ->
                    b.append(new KeyedCodec<>("Command", Codec.STRING), (r, v) -> r.command = v, r -> r.command).addValidator(Validators.nonNull())
                            .documentation("The command, e.g. /give {player} Food_Bread 1.").add());

    public static final BuilderCodec<JsonStatement.Shop> SHOP = statement(JsonStatement.Shop.class, JsonStatement.Shop::new,
            "Open a barter shop; ends the conversation.", b ->
                    b.append(new KeyedCodec<>("Shop", Codec.STRING), (s, v) -> s.shop = v, s -> s.shop)
                            .metadata(pick(JsonDialogues.DATASET_SHOPS)).documentation("Empty for this NPC's own shop.").add());

    public static final BuilderCodec<JsonStatement.ObjectiveLine> OBJECTIVE_LINE = statement(JsonStatement.ObjectiveLine.class, JsonStatement.ObjectiveLine::new,
            "Start an objective line (a chain of objectives).", b ->
                    b.append(new KeyedCodec<>("Line", Codec.STRING), (o, v) -> o.line = v, o -> o.line).addValidator(Validators.nonNull())
                            .addValidator(com.hypixel.hytale.builtin.adventure.objectives.config.ObjectiveLineAsset.VALIDATOR_CACHE.getValidator())
                            .documentation("The objective line.").add());

    public static final BuilderCodec<JsonStatement.ObjectiveCancel> OBJECTIVE_CANCEL = statement(JsonStatement.ObjectiveCancel.class, JsonStatement.ObjectiveCancel::new,
            "Abandon one of the player's active objectives.", b ->
                    b.append(new KeyedCodec<>("Objective", Codec.STRING), (o, v) -> o.objective = v, o -> o.objective).addValidator(Validators.nonNull())
                            .addValidator(ObjectiveAsset.VALIDATOR_CACHE.getValidator()).documentation("The objective.").add());

    public static final BuilderCodec<JsonStatement.ObjectiveTask> OBJECTIVE_TASK = statement(JsonStatement.ObjectiveTask.class, JsonStatement.ObjectiveTask::new,
            "Advance a talk-to-this-NPC task of an active objective.", b ->
                    b.append(new KeyedCodec<>("Task", Codec.STRING), (o, v) -> o.task = v, o -> o.task).addValidator(Validators.nonNull())
                            .documentation("The task id from the objective's task set.").add());

    // ---- the asset

    public static final BuilderCodec<DialogueAsset.StartEntry> START = statement(DialogueAsset.StartEntry.class, DialogueAsset.StartEntry::new,
            "A start rule: begin at Node, if When holds (or unconditionally).", b -> {
                b.append(new KeyedCodec<>("Node", Codec.STRING), (s, v) -> s.node = v, s -> s.node).addValidator(Validators.nonNull())
                        .documentation("Node to begin at.").add();
                b.append(new KeyedCodec<>("When", Codec.STRING), (s, v) -> s.when = v, s -> s.when)
                        .documentation("Only when this is true; guarded starts are tried before the unguarded one." + EXPR_DOC).add();
            });

    public static final BuilderCodec<DialogueAsset.NodeEntry> NODE = statement(DialogueAsset.NodeEntry.class, DialogueAsset.NodeEntry::new,
            "A named node: a stretch of conversation that Jump and Start refer to by name.", b -> {
                b.append(new KeyedCodec<>("Name", Codec.STRING), (n, v) -> n.name = v, n -> n.name).addValidator(Validators.nonNull())
                        .documentation("Letters, digits and underscores.").add();
                body(b, "Body", (n, v) -> n.body = v, n -> n.body, "The node's statements, in order.");
            });

    public static final AssetBuilderCodec<String, DialogueAsset> DIALOGUE = AssetBuilderCodec.builder(
                    DialogueAsset.class, DialogueAsset::new, Codec.STRING,
                    (a, k) -> a.id = k, a -> a.id, (a, d) -> a.data = d, a -> a.data)
            .documentation("A LowTalk dialogue: what an NPC says and the choices the player gets. Same model as a .talk file.")
            .append(new KeyedCodec<>("Npc", new ArrayCodec<>(Codec.STRING, String[]::new).metadata(new UIEditor(new UIEditor.TextField(JsonDialogues.DATASET_NPCS)))),
                    (a, v) -> a.npc = v == null ? new String[0] : v, a -> a.npc)
            .documentation("Which NPCs use this dialogue: role ids (Kweebec_Merchant) or @tags set with /lowtalk tag. Empty means command only.").add()
            .append(new KeyedCodec<>("Speaker", Codec.STRING), (a, v) -> a.speaker = v, a -> a.speaker)
            .documentation("Default speaker name for lines without one. Defaults to the NPC's name.").add()
            .append(new KeyedCodec<>("Title", Codec.STRING), (a, v) -> a.title = v, a -> a.title)
            .documentation("Window title. Defaults to the speaker.").add()
            .append(new KeyedCodec<>("Scope", Codec.STRING), (a, v) -> a.scope = v, a -> a.scope)
            .documentation("Variable namespace shared with other dialogues that declare the same scope. Defaults to this dialogue's id.").add()
            .append(new KeyedCodec<>("Portrait", Codec.STRING), (a, v) -> a.portrait = v, a -> a.portrait)
            .documentation("Image beside the text, a path inside Common/UI/Custom of any pack.").add()
            .append(new KeyedCodec<>("On", Codec.STRING), (a, v) -> a.on = v, a -> a.on)
            .documentation("\"join\" opens this dialogue by itself when a player joins.").add()
            .<DialogueAsset.StartEntry[]>append(new KeyedCodec<>("Start", new ArrayCodec<>(START, DialogueAsset.StartEntry[]::new)),
                    (a, v) -> a.start = v == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(v)), a -> a.start.toArray(new DialogueAsset.StartEntry[0]))
            .documentation("Where to begin. Empty means the first node.").add()
            .<DialogueAsset.NodeEntry[]>append(new KeyedCodec<>("Nodes", new ArrayCodec<>(NODE, DialogueAsset.NodeEntry[]::new)),
                    (a, v) -> a.nodes = v == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(v)), a -> a.nodes.toArray(new DialogueAsset.NodeEntry[0]))
            .addValidator(Validators.nonNull()).documentation("The nodes. The first is the default start.").add()
            .build();

    private static boolean registered = false;

    /** Register the statement subtypes with the polymorphic codec. Call once, before the asset store is registered. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        STATEMENT.register("Say", JsonStatement.Say.class, SAY);
        STATEMENT.register("Choice", JsonStatement.Choice.class, CHOICE);
        STATEMENT.register("If", JsonStatement.If.class, IF);
        STATEMENT.register("Once", JsonStatement.Once.class, ONCE);
        STATEMENT.register("Random", JsonStatement.Random.class, RANDOM);
        STATEMENT.register("Set", JsonStatement.Set.class, SET);
        STATEMENT.register("Jump", JsonStatement.Jump.class, JUMP);
        STATEMENT.register("End", JsonStatement.End.class, END);
        STATEMENT.register("Input", JsonStatement.Input.class, INPUT);
        STATEMENT.register("Wait", JsonStatement.Wait.class, WAIT);
        STATEMENT.register("Command", JsonStatement.Command.class, COMMAND);
        STATEMENT.register("Give", JsonStatement.Give.class, GIVE);
        STATEMENT.register("Take", JsonStatement.Take.class, TAKE);
        STATEMENT.register("Sound", JsonStatement.Sound.class, SOUND);
        STATEMENT.register("Effect", JsonStatement.Effect.class, EFFECT);
        STATEMENT.register("Cure", JsonStatement.Cure.class, CURE);
        STATEMENT.register("Objective", JsonStatement.Objective.class, OBJECTIVE);
        STATEMENT.register("Weather", JsonStatement.Weather.class, WEATHER);
        STATEMENT.register("Attitude", JsonStatement.Attitude.class, ATTITUDE);
        STATEMENT.register("Anim", JsonStatement.Anim.class, ANIM);
        STATEMENT.register("Notify", JsonStatement.Notify.class, NOTIFY);
        STATEMENT.register("Title", JsonStatement.Title.class, TITLE);
        STATEMENT.register("Stat", JsonStatement.Stat.class, STAT);
        STATEMENT.register("Heal", JsonStatement.Heal.class, HEAL);
        STATEMENT.register("Learn", JsonStatement.Learn.class, LEARN);
        STATEMENT.register("Teleport", JsonStatement.Teleport.class, TELEPORT);
        STATEMENT.register("Time", JsonStatement.Time.class, TIME);
        STATEMENT.register("Reputation", JsonStatement.Reputation.class, REPUTATION);
        STATEMENT.register("NpcName", JsonStatement.NpcName.class, NPC_NAME);
        STATEMENT.register("State", JsonStatement.State.class, STATE);
        STATEMENT.register("Spawn", JsonStatement.Spawn.class, SPAWN);
        STATEMENT.register("Despawn", JsonStatement.Despawn.class, DESPAWN);
        STATEMENT.register("Run", JsonStatement.Run.class, RUN);
        STATEMENT.register("Shop", JsonStatement.Shop.class, SHOP);
        STATEMENT.register("ObjectiveLine", JsonStatement.ObjectiveLine.class, OBJECTIVE_LINE);
        STATEMENT.register("ObjectiveCancel", JsonStatement.ObjectiveCancel.class, OBJECTIVE_CANCEL);
        STATEMENT.register("ObjectiveTask", JsonStatement.ObjectiveTask.class, OBJECTIVE_TASK);
    }
}
