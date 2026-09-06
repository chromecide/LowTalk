package com.chromecide.lowtalk.hytale.json;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Expr;
import com.chromecide.lowtalk.model.Node;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Pos;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.model.Text;
import com.chromecide.lowtalk.parser.ExprParser;
import com.chromecide.lowtalk.parser.ParseException;
import com.chromecide.lowtalk.parser.Printer;
import com.chromecide.lowtalk.parser.TextParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts between the JSON asset shape and the runtime model, both ways, without loss. */
public final class JsonConvert {
    private JsonConvert() {}

    // ---- JSON -> model

    /**
     * @param display how the source is named in error messages, e.g. "MyPack/hello.json"
     * @throws ParseException with a position naming the node and statement index when an expression or text is bad
     */
    public static Dialogue toModel(LowTalkJson a, String display) {
        String id = a.getId();
        List<String> bindings = a.npc == null ? List.of() : List.of(a.npc);
        LinkedHashMap<String, Node> nodes = new LinkedHashMap<>();
        int index = 0;
        for (LowTalkJson.NodeEntry n : a.nodes) {
            index++;
            Pos p = new Pos(display + " node " + (n.name == null ? "#" + index : n.name), 0);
            if (n.name == null || n.name.isBlank()) throw new ParseException(p, "a node has no Name");
            if (nodes.containsKey(n.name)) throw new ParseException(p, "node '" + n.name + "' is defined twice");
            nodes.put(n.name, new Node(p, n.name, statements(n.body, p)));
        }
        if (nodes.isEmpty()) throw new ParseException(new Pos(display, 0), "dialogue has no nodes");
        List<Dialogue.Start> starts = new ArrayList<>();
        for (LowTalkJson.StartEntry s : a.start) {
            Pos p = new Pos(display + " start", 0);
            if (s.node == null || s.node.isBlank()) throw new ParseException(p, "a Start entry has no Node");
            starts.add(new Dialogue.Start(p, s.node, blank(s.when) ? null : ExprParser.parse(s.when, p)));
        }
        if (starts.isEmpty()) starts.add(new Dialogue.Start(new Pos(display, 0), nodes.keySet().iterator().next(), null));
        Map<String, String> other = new LinkedHashMap<>();
        if (!blank(a.portrait)) other.put("portrait", a.portrait);
        if (!blank(a.on)) other.put("on", a.on);
        String scope = blank(a.scope) ? id : a.scope;
        return new Dialogue(display, id, bindings, List.copyOf(starts), blank(a.speaker) ? null : a.speaker,
                blank(a.title) ? null : a.title, scope, other, List.of(), nodes);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static List<Statement> statements(List<JsonStatement> body, Pos parent) {
        List<Statement> out = new ArrayList<>();
        if (body == null) return out;
        int i = 0;
        for (JsonStatement s : body) {
            i++;
            Pos p = new Pos(parent.file(), i);
            out.add(statement(s, p));
        }
        return out;
    }

    private static Statement statement(JsonStatement s, Pos p) {
        return switch (s) {
            case JsonStatement.Say say -> new Statement.Line(p, blank(say.speaker) ? null : say.speaker, text(say.text, p));
            case JsonStatement.Choice c -> {
                List<Option> options = new ArrayList<>();
                for (JsonStatement.OptionEntry o : c.options) {
                    Pos op = new Pos(p.file() + " option \"" + o.text + "\"", p.line());
                    options.add(new Option(op, text(o.text, op),
                            blank(o.ifExpr) ? null : ExprParser.parse(o.ifExpr, op),
                            blank(o.showIf) ? null : ExprParser.parse(o.showIf, op),
                            o.once, statements(o.body, op)));
                }
                if (options.isEmpty()) throw new ParseException(p, "a Choice has no Options");
                yield new Statement.Choice(p, List.copyOf(options));
            }
            case JsonStatement.If f -> {
                List<Statement.Branch> branches = new ArrayList<>();
                for (JsonStatement.Branch b : f.branches) {
                    branches.add(new Statement.Branch(p, blank(b.when) ? null : ExprParser.parse(b.when, p), statements(b.body, p)));
                }
                if (branches.isEmpty()) throw new ParseException(p, "an If has no Branches");
                if (branches.get(0).condition() == null) throw new ParseException(p, "the first branch of an If needs a When condition");
                yield new Statement.Conditional(p, List.copyOf(branches));
            }
            case JsonStatement.Once o -> new Statement.Once(p, "once" + p.line(), statements(o.body, p));
            case JsonStatement.Random r -> {
                List<List<Statement>> alternatives = new ArrayList<>();
                for (JsonStatement.Alternative a : r.alternatives) alternatives.add(statements(a.body, p));
                yield new Statement.Random(p, List.copyOf(alternatives));
            }
            case JsonStatement.Set set -> new Statement.Set(p, ExprParser.parseVar(set.var, p), ExprParser.parse(set.value, p));
            case JsonStatement.Jump j -> {
                if (blank(j.node)) throw new ParseException(p, "a Jump has no Node");
                yield new Statement.Jump(p, j.node.trim());
            }
            case JsonStatement.End e -> new Statement.End(p);
            case JsonStatement.Input in -> new Statement.Input(p, ExprParser.parseVar(in.var, p), text(in.prompt, p));
            case JsonStatement.Wait w -> new Statement.Wait(p, ExprParser.parse(w.seconds, p));
            case JsonStatement.Command c -> {
                if (blank(c.name)) throw new ParseException(p, "a Command has no Name");
                List<Text> args = new ArrayList<>();
                if (c.args != null) for (String a : c.args) args.add(text(a, p));
                yield new Statement.Command(p, c.name.trim(), List.copyOf(args));
            }
            case JsonStatement.Give g -> command(p, "give", g.item, g.count == 1 ? null : String.valueOf(g.count));
            case JsonStatement.Take t -> command(p, "take", t.item, t.count == 1 ? null : String.valueOf(t.count));
            case JsonStatement.Sound so -> command(p, "sound", so.sound, null);
            case JsonStatement.Effect ef -> command(p, "effect", ef.effect, null);
            case JsonStatement.Cure cu -> command(p, "cure", cu.effect, null);
            case JsonStatement.Objective ob -> command(p, "objective", ob.objective, null);
            case JsonStatement.Weather w -> command(p, "weather", blank(w.weather) ? "clear" : w.weather, w.playerOnly ? "player" : null);
            case JsonStatement.Attitude at -> command(p, "attitude", at.attitude, null);
            case JsonStatement.Anim an -> command(p, "anim", an.animation, blank(an.slot) ? null : an.slot);
            case JsonStatement.Notify n -> {
                List<String> args = new ArrayList<>();
                args.add(n.text == null ? "" : n.text);
                if (!blank(n.detail)) args.add(n.detail);
                if (!blank(n.style) && !n.style.equalsIgnoreCase("default")) args.add(n.style);
                yield commandText(p, "notify", args);
            }
            case JsonStatement.Title t -> {
                List<String> args = new ArrayList<>();
                args.add(t.primary == null ? "" : t.primary);
                if (!blank(t.secondary)) args.add(t.secondary);
                if (t.major) args.add("major");
                if (t.seconds > 0) args.add(Printer.expr(new Expr.Literal(t.seconds)));
                yield commandText(p, "title", args);
            }
            case JsonStatement.Stat st -> command(p, "stat", st.stat, blank(st.value) ? "max" : st.value);
            case JsonStatement.Heal h -> blank(h.amount) ? new Statement.Command(p, "heal", List.of()) : command(p, "heal", h.amount, null);
            case JsonStatement.Learn l -> command(p, "learn", l.recipe, null);
            case JsonStatement.Teleport tp -> {
                if (blank(tp.target)) throw new ParseException(p, "a Teleport needs a Target");
                List<String> args = new ArrayList<>(List.of(tp.target.trim().split("\\s+")));
                yield commandText(p, "teleport", args);
            }
            case JsonStatement.Time tm -> command(p, "time", tm.time, blank(tm.fadeSeconds) ? null : tm.fadeSeconds);
            case JsonStatement.Reputation rp -> command(p, "reputation", rp.change, blank(rp.group) ? null : rp.group);
            case JsonStatement.NpcName nn -> commandText(p, "npc_name", List.of(nn.name == null ? "" : nn.name));
            case JsonStatement.State stt -> command(p, "state", stt.state, blank(stt.subState) ? null : stt.subState);
            case JsonStatement.Spawn sp -> {
                if (blank(sp.role)) throw new ParseException(p, "a Spawn needs a Role");
                List<String> args = new ArrayList<>();
                args.add(sp.role);
                if (sp.right != 0 || sp.up != 0 || sp.forward != 2.0) {
                    args.add(Printer.expr(new Expr.Literal(sp.right)));
                    args.add(Printer.expr(new Expr.Literal(sp.up)));
                    args.add(Printer.expr(new Expr.Literal(sp.forward)));
                }
                yield commandText(p, "spawn", args);
            }
            case JsonStatement.Despawn dn -> new Statement.Command(p, "despawn", List.of());
            case JsonStatement.Run run -> commandText(p, "run", List.of(run.command == null ? "" : run.command));
            case JsonStatement.Shop sh -> blank(sh.shop) ? new Statement.Command(p, "shop", List.of()) : command(p, "shop", sh.shop, null);
            case JsonStatement.ObjectiveLine ol -> command(p, "objective", "line", ol.line);
            case JsonStatement.ObjectiveCancel oc -> command(p, "objective", "cancel", oc.objective);
            case JsonStatement.ObjectiveTask ot -> command(p, "objective", "task", ot.task);
            case JsonStatement.Music mu -> command(p, "music", blank(mu.music) ? "clear" : mu.music, null);
            case JsonStatement.Vfx vx -> {
                if (blank(vx.particles)) throw new ParseException(p, "a Vfx needs Particles");
                List<String> args = new ArrayList<>();
                args.add(vx.particles);
                if (vx.scale != 1.0 || vx.seconds > 0) args.add(Printer.expr(new Expr.Literal(vx.scale)));
                if (vx.seconds > 0) args.add(Printer.expr(new Expr.Literal(vx.seconds)));
                yield commandText(p, "vfx", args);
            }
            case JsonStatement.Camera cam -> command(p, "camera", cam.effect, cam.intensity == 1.0 ? null : Printer.expr(new Expr.Literal(cam.intensity)));
            default -> throw new ParseException(p, "unknown statement type " + s.getClass().getSimpleName());
        };
    }

    static boolean isNotifyStyle(String a) {
        return a.equalsIgnoreCase("default") || a.equalsIgnoreCase("success") || a.equalsIgnoreCase("warning") || a.equalsIgnoreCase("danger");
    }

    /** A command whose arguments may contain interpolation, parsed as text. */
    private static Statement commandText(Pos p, String name, List<String> args) {
        List<Text> out = new ArrayList<>();
        for (String a : args) out.add(text(a, p));
        return new Statement.Command(p, name, List.copyOf(out));
    }

    private static Statement command(Pos p, String name, String first, String second) {
        if (blank(first) || (second != null && second.isBlank())) throw new ParseException(p, "<<" + name + ">> is missing a value");
        List<Text> args = new ArrayList<>();
        args.add(Text.plain(first));
        if (second != null) args.add(Text.plain(second));
        return new Statement.Command(p, name, List.copyOf(args));
    }

    private static Text text(String s, Pos p) {
        return TextParser.parse(s == null ? "" : s, p);
    }

    // ---- model -> JSON

    public static LowTalkJson toAsset(Dialogue d) {
        LowTalkJson a = new LowTalkJson(d.id());
        a.npc = d.bindings().toArray(new String[0]);
        a.speaker = d.speaker();
        a.title = d.title();
        a.scope = d.scope() == null || d.scope().equals(d.id()) ? null : d.scope();
        a.portrait = d.otherDirectives().get("portrait");
        a.on = d.otherDirectives().get("on");
        for (Dialogue.Start s : d.starts()) {
            boolean defaultStart = s.condition() == null && d.starts().size() == 1 && s.node().equals(d.nodes().keySet().iterator().next());
            if (defaultStart) continue;
            a.start.add(new LowTalkJson.StartEntry(s.node(), s.condition() == null ? null : Printer.expr(s.condition())));
        }
        for (Node n : d.nodeList()) a.nodes.add(new LowTalkJson.NodeEntry(n.name(), statements(n.body())));
        return a;
    }

    private static List<JsonStatement> statements(List<Statement> body) {
        List<JsonStatement> out = new ArrayList<>();
        for (Statement s : body) out.add(statement(s));
        return out;
    }

    private static JsonStatement statement(Statement s) {
        switch (s) {
            case Statement.Line l -> {
                JsonStatement.Say say = new JsonStatement.Say();
                say.speaker = l.speaker();
                say.text = Printer.text(l.text());
                return say;
            }
            case Statement.Choice c -> {
                JsonStatement.Choice out = new JsonStatement.Choice();
                for (Option o : c.options()) {
                    JsonStatement.OptionEntry e = new JsonStatement.OptionEntry();
                    e.text = Printer.text(o.text());
                    e.ifExpr = o.guard() == null ? null : Printer.expr(o.guard());
                    e.showIf = o.showGuard() == null ? null : Printer.expr(o.showGuard());
                    e.once = o.once();
                    e.body = statements(o.body());
                    out.options.add(e);
                }
                return out;
            }
            case Statement.Conditional c -> {
                JsonStatement.If out = new JsonStatement.If();
                for (Statement.Branch b : c.branches()) {
                    JsonStatement.Branch e = new JsonStatement.Branch();
                    e.when = b.condition() == null ? null : Printer.expr(b.condition());
                    e.body = statements(b.body());
                    out.branches.add(e);
                }
                return out;
            }
            case Statement.Once o -> {
                JsonStatement.Once out = new JsonStatement.Once();
                out.body = statements(o.body());
                return out;
            }
            case Statement.Random r -> {
                JsonStatement.Random out = new JsonStatement.Random();
                for (List<Statement> a : r.alternatives()) {
                    JsonStatement.Alternative e = new JsonStatement.Alternative();
                    e.body = statements(a);
                    out.alternatives.add(e);
                }
                return out;
            }
            case Statement.Set set -> {
                JsonStatement.Set out = new JsonStatement.Set();
                out.var = Printer.expr(set.target());
                out.value = Printer.expr(set.value());
                return out;
            }
            case Statement.Jump j -> {
                JsonStatement.Jump out = new JsonStatement.Jump();
                out.node = j.node();
                return out;
            }
            case Statement.End e -> {
                return new JsonStatement.End();
            }
            case Statement.Input in -> {
                JsonStatement.Input out = new JsonStatement.Input();
                out.var = Printer.expr(in.target());
                out.prompt = Printer.text(in.prompt());
                return out;
            }
            case Statement.Wait w -> {
                JsonStatement.Wait out = new JsonStatement.Wait();
                out.seconds = Printer.expr(w.seconds());
                return out;
            }
            case Statement.Command cmd -> {
                return typedCommand(cmd);
            }
        }
    }

    /** Known commands with static arguments become typed statements; everything else stays a generic Command. */
    private static JsonStatement typedCommand(Statement.Command cmd) {
        List<Text> args = cmd.args();
        boolean allStatic = args.stream().allMatch(Text::isStatic);
        if (allStatic) {
            String a0 = args.isEmpty() ? null : args.get(0).debugString();
            String a1 = args.size() > 1 ? args.get(1).debugString() : null;
            switch (cmd.name()) {
                case "give", "take" -> {
                    if (a0 != null && (a1 == null || a1.matches("\\d+"))) {
                        if (cmd.name().equals("give")) {
                            JsonStatement.Give g = new JsonStatement.Give();
                            g.item = a0;
                            g.count = a1 == null ? 1 : Integer.parseInt(a1);
                            return g;
                        }
                        JsonStatement.Take t = new JsonStatement.Take();
                        t.item = a0;
                        t.count = a1 == null ? 1 : Integer.parseInt(a1);
                        return t;
                    }
                }
                case "sound" -> {
                    if (a0 != null && a1 == null) {
                        JsonStatement.Sound so = new JsonStatement.Sound();
                        so.sound = a0;
                        return so;
                    }
                }
                case "effect" -> {
                    if (a0 != null && a1 == null) {
                        JsonStatement.Effect ef = new JsonStatement.Effect();
                        ef.effect = a0;
                        return ef;
                    }
                }
                case "cure" -> {
                    if (a0 != null && a1 == null) {
                        JsonStatement.Cure cu = new JsonStatement.Cure();
                        cu.effect = a0;
                        return cu;
                    }
                }
                case "objective" -> {
                    if (a0 != null && a1 == null) {
                        JsonStatement.Objective ob = new JsonStatement.Objective();
                        ob.objective = a0;
                        return ob;
                    }
                }
                case "weather" -> {
                    if (a0 != null && (a1 == null || a1.equalsIgnoreCase("player"))) {
                        JsonStatement.Weather w = new JsonStatement.Weather();
                        w.weather = a0;
                        w.playerOnly = a1 != null;
                        return w;
                    }
                }
                case "attitude" -> {
                    if (a0 != null && a1 == null) {
                        JsonStatement.Attitude at = new JsonStatement.Attitude();
                        at.attitude = a0;
                        return at;
                    }
                }
                case "anim" -> {
                    if (a0 != null) {
                        JsonStatement.Anim an = new JsonStatement.Anim();
                        an.animation = a0;
                        an.slot = a1;
                        return an;
                    }
                }
                case "stat" -> {
                    if (a0 != null) {
                        JsonStatement.Stat st = new JsonStatement.Stat();
                        st.stat = a0;
                        st.value = a1 == null ? "max" : a1;
                        return st;
                    }
                }
                case "heal" -> {
                    JsonStatement.Heal h = new JsonStatement.Heal();
                    h.amount = a0;
                    return h;
                }
                case "learn" -> {
                    if (a0 != null && a1 == null) {
                        JsonStatement.Learn l = new JsonStatement.Learn();
                        l.recipe = a0;
                        return l;
                    }
                }
                case "teleport" -> {
                    JsonStatement.Teleport tp = new JsonStatement.Teleport();
                    tp.target = String.join(" ", args.stream().map(Text::debugString).toList());
                    return tp;
                }
                case "time" -> {
                    if (a0 != null) {
                        JsonStatement.Time tm = new JsonStatement.Time();
                        tm.time = a0;
                        tm.fadeSeconds = a1;
                        return tm;
                    }
                }
                case "reputation" -> {
                    if (a0 != null) {
                        JsonStatement.Reputation rp = new JsonStatement.Reputation();
                        rp.change = a0;
                        rp.group = a1;
                        return rp;
                    }
                }
                case "state" -> {
                    if (a0 != null) {
                        JsonStatement.State stt = new JsonStatement.State();
                        stt.state = a0;
                        stt.subState = a1;
                        return stt;
                    }
                }
                case "spawn" -> {
                    if (a0 != null && (args.size() == 1 || args.size() == 4)) {
                        try {
                            JsonStatement.Spawn sp = new JsonStatement.Spawn();
                            sp.role = a0;
                            if (args.size() == 4) {
                                sp.right = Double.parseDouble(a1);
                                sp.up = Double.parseDouble(args.get(2).debugString());
                                sp.forward = Double.parseDouble(args.get(3).debugString());
                            }
                            return sp;
                        } catch (NumberFormatException ignored) {
                            // fall through to a generic command
                        }
                    }
                }
                case "despawn" -> {
                    if (args.isEmpty()) return new JsonStatement.Despawn();
                }
                case "music" -> {
                    if (a0 != null && a1 == null) {
                        JsonStatement.Music mu = new JsonStatement.Music();
                        mu.music = a0;
                        return mu;
                    }
                }
                case "vfx" -> {
                    if (a0 != null && args.size() <= 3) {
                        try {
                            JsonStatement.Vfx vx = new JsonStatement.Vfx();
                            vx.particles = a0;
                            if (a1 != null) vx.scale = Double.parseDouble(a1);
                            if (args.size() > 2) vx.seconds = Double.parseDouble(args.get(2).debugString());
                            return vx;
                        } catch (NumberFormatException ignored) {
                            // generic
                        }
                    }
                }
                case "camera" -> {
                    if (a0 != null && args.size() <= 2) {
                        try {
                            JsonStatement.Camera cam = new JsonStatement.Camera();
                            cam.effect = a0;
                            if (a1 != null) cam.intensity = Double.parseDouble(a1);
                            return cam;
                        } catch (NumberFormatException ignored) {
                            // generic
                        }
                    }
                }
                case "shop" -> {
                    if (args.size() <= 1) {
                        JsonStatement.Shop sh = new JsonStatement.Shop();
                        sh.shop = a0;
                        return sh;
                    }
                }
                default -> {}
            }
            if (cmd.name().equals("objective") && a1 != null) {
                switch (a0.toLowerCase(java.util.Locale.ROOT)) {
                    case "line" -> {
                        JsonStatement.ObjectiveLine ol = new JsonStatement.ObjectiveLine();
                        ol.line = a1;
                        return ol;
                    }
                    case "cancel" -> {
                        JsonStatement.ObjectiveCancel oc = new JsonStatement.ObjectiveCancel();
                        oc.objective = a1;
                        return oc;
                    }
                    case "task" -> {
                        JsonStatement.ObjectiveTask ot = new JsonStatement.ObjectiveTask();
                        ot.task = a1;
                        return ot;
                    }
                    case "start" -> {
                        JsonStatement.Objective ob = new JsonStatement.Objective();
                        ob.objective = a1;
                        return ob;
                    }
                    default -> {}
                }
            }
        }
        // Text-bearing commands keep their interpolation, so they are typed whether or not the arguments are static.
        List<String> printed = args.stream().map(Printer::text).toList();
        switch (cmd.name()) {
            case "notify" -> {
                if (!printed.isEmpty() && printed.size() <= 3) {
                    JsonStatement.Notify n = new JsonStatement.Notify();
                    n.text = printed.get(0);
                    for (String a : printed.subList(1, printed.size())) {
                        if (isNotifyStyle(a)) n.style = a.toLowerCase(java.util.Locale.ROOT);
                        else if (n.detail == null && !a.isEmpty()) n.detail = a;
                    }
                    return n;
                }
            }
            case "title" -> {
                if (!printed.isEmpty() && printed.size() <= 4) {
                    JsonStatement.Title t = new JsonStatement.Title();
                    t.primary = printed.get(0);
                    for (String a : printed.subList(1, printed.size())) {
                        if (a.equalsIgnoreCase("major")) t.major = true;
                        else if (a.equalsIgnoreCase("minor")) t.major = false;
                        else if (a.matches("\\d+(\\.\\d+)?")) t.seconds = Double.parseDouble(a);
                        else if (t.secondary == null && !a.isEmpty()) t.secondary = a;
                    }
                    return t;
                }
            }
            case "npc_name" -> {
                if (printed.size() == 1) {
                    JsonStatement.NpcName nn = new JsonStatement.NpcName();
                    nn.name = printed.get(0);
                    return nn;
                }
            }
            case "run" -> {
                if (printed.size() == 1) {
                    JsonStatement.Run run = new JsonStatement.Run();
                    run.command = printed.get(0);
                    return run;
                }
            }
            default -> {}
        }
        JsonStatement.Command out = new JsonStatement.Command();
        out.name = cmd.name();
        out.args = args.stream().map(Printer::text).toArray(String[]::new);
        return out;
    }
}
