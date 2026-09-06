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
    public static Dialogue toModel(DialogueAsset a, String display) {
        String id = a.getId();
        List<String> bindings = a.npc == null ? List.of() : List.of(a.npc);
        LinkedHashMap<String, Node> nodes = new LinkedHashMap<>();
        int index = 0;
        for (DialogueAsset.NodeEntry n : a.nodes) {
            index++;
            Pos p = new Pos(display + " node " + (n.name == null ? "#" + index : n.name), 0);
            if (n.name == null || n.name.isBlank()) throw new ParseException(p, "a node has no Name");
            if (nodes.containsKey(n.name)) throw new ParseException(p, "node '" + n.name + "' is defined twice");
            nodes.put(n.name, new Node(p, n.name, statements(n.body, p)));
        }
        if (nodes.isEmpty()) throw new ParseException(new Pos(display, 0), "dialogue has no nodes");
        List<Dialogue.Start> starts = new ArrayList<>();
        for (DialogueAsset.StartEntry s : a.start) {
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
            default -> throw new ParseException(p, "unknown statement type " + s.getClass().getSimpleName());
        };
    }

    private static Statement command(Pos p, String name, String first, String second) {
        if (blank(first)) throw new ParseException(p, "<<" + name + ">> needs its first value");
        List<Text> args = new ArrayList<>();
        args.add(Text.plain(first));
        if (second != null) args.add(Text.plain(second));
        return new Statement.Command(p, name, List.copyOf(args));
    }

    private static Text text(String s, Pos p) {
        return TextParser.parse(s == null ? "" : s, p);
    }

    // ---- model -> JSON

    public static DialogueAsset toAsset(Dialogue d) {
        DialogueAsset a = new DialogueAsset(d.id());
        a.npc = d.bindings().toArray(new String[0]);
        a.speaker = d.speaker();
        a.title = d.title();
        a.scope = d.scope() == null || d.scope().equals(d.id()) ? null : d.scope();
        a.portrait = d.otherDirectives().get("portrait");
        a.on = d.otherDirectives().get("on");
        for (Dialogue.Start s : d.starts()) {
            boolean defaultStart = s.condition() == null && d.starts().size() == 1 && s.node().equals(d.nodes().keySet().iterator().next());
            if (defaultStart) continue;
            a.start.add(new DialogueAsset.StartEntry(s.node(), s.condition() == null ? null : Printer.expr(s.condition())));
        }
        for (Node n : d.nodeList()) a.nodes.add(new DialogueAsset.NodeEntry(n.name(), statements(n.body())));
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
                default -> {}
            }
        }
        JsonStatement.Command out = new JsonStatement.Command();
        out.name = cmd.name();
        out.args = args.stream().map(Printer::text).toArray(String[]::new);
        return out;
    }
}
