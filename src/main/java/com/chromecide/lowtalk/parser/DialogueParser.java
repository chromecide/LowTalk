package com.chromecide.lowtalk.parser;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Expr;
import com.chromecide.lowtalk.model.Node;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Pos;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.model.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a .talk file into a {@link Dialogue}.
 *
 * The grammar is line-oriented. Indentation (spaces) decides which lines belong to an option's
 * body; conditionals and once-blocks are closed explicitly with endif / endonce.
 */
public final class DialogueParser {

    private static final Pattern NODE_HEADER = Pattern.compile("^==\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*$");
    private static final Pattern DIRECTIVE = Pattern.compile("^([a-z]+)\\s*:\\s*(.*)$");
    private static final Pattern SPEAKER = Pattern.compile("^([A-Za-z][A-Za-z0-9_' ]{0,30}?)\\s*:\\s+(.*)$");
    private static final Pattern OPTION = Pattern.compile("^->\\s*(.*)$");
    private static final Pattern COMMAND = Pattern.compile("^<<\\s*(.*?)\\s*>>$");
    private static final Pattern TRAILING_GUARD = Pattern.compile("^(.*?)\\s*<<\\s*(if|show if)\\s+(.+?)\\s*>>\\s*$");
    private static final Pattern SET = Pattern.compile("^set\\s+(\\$[A-Za-z0-9_.]+)\\s*=\\s*(.+)$");
    private static final Pattern INPUT = Pattern.compile("^input\\s+(\\$[A-Za-z0-9_.]+)(?:\\s+(.+))?$");

    /** One logical source line with its indentation and original line number. */
    private record SrcLine(int number, int indent, String text) {}

    private final String file;
    private final List<SrcLine> lines;
    private int idx = 0;
    private int onceCounter = 0;

    private DialogueParser(String file, List<SrcLine> lines) {
        this.file = file;
        this.lines = lines;
    }

    public static Dialogue parse(String file, String source) {
        List<SrcLine> lines = new ArrayList<>();
        String[] raw = source.split("\r?\n", -1);
        for (int i = 0; i < raw.length; i++) {
            String stripped = stripComment(raw[i]);
            if (stripped.isBlank()) continue;
            int indent = 0;
            while (indent < stripped.length() && stripped.charAt(indent) == ' ') indent++;
            if (indent < stripped.length() && stripped.charAt(indent) == '\t') {
                throw new ParseException(new Pos(file, i + 1), "tabs are not allowed for indentation; use spaces");
            }
            lines.add(new SrcLine(i + 1, indent, stripped.trim()));
        }
        return new DialogueParser(file, lines).parseFile();
    }

    /** Remove a # comment unless the # is inside quotes. */
    static String stripComment(String line) {
        boolean inQuote = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '\\') i++;
                else if (c == quote) inQuote = false;
            } else if (c == '"' || c == '\'') {
                inQuote = true;
                quote = c;
            } else if (c == '#') {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private Pos pos(SrcLine l) {
        return new Pos(file, l.number());
    }

    private Dialogue parseFile() {
        List<String> bindings = new ArrayList<>();
        List<Dialogue.Start> starts = new ArrayList<>();
        String speaker = null;
        String title = null;
        String scope = null;
        Map<String, String> other = new LinkedHashMap<>();

        // Header: directives until the first node.
        while (idx < lines.size()) {
            SrcLine l = lines.get(idx);
            if (NODE_HEADER.matcher(l.text()).matches()) break;
            Matcher m = DIRECTIVE.matcher(l.text());
            if (!m.matches()) {
                throw new ParseException(pos(l), "expected a 'key: value' directive or a '== node' header, got: " + l.text());
            }
            String key = m.group(1);
            String value = m.group(2).trim();
            switch (key) {
                case "npc" -> {
                    if (value.isEmpty()) throw new ParseException(pos(l), "npc: needs a role id or @tag");
                    bindings.add(value);
                }
                case "start" -> starts.add(parseStart(value, pos(l)));
                case "speaker" -> speaker = value;
                case "title" -> title = value;
                case "scope" -> scope = value;
                default -> other.put(key, value);
            }
            idx++;
        }

        LinkedHashMap<String, Node> nodes = new LinkedHashMap<>();
        while (idx < lines.size()) {
            SrcLine header = lines.get(idx);
            Matcher m = NODE_HEADER.matcher(header.text());
            if (!m.matches()) {
                throw new ParseException(pos(header), "expected '== node_name', got: " + header.text());
            }
            String name = m.group(1);
            if (nodes.containsKey(name)) {
                throw new ParseException(pos(header), "node '" + name + "' is defined twice (first at " + nodes.get(name).pos() + ")");
            }
            idx++;
            List<Statement> body = parseBlock(0, null);
            nodes.put(name, new Node(pos(header), name, body));
        }
        if (nodes.isEmpty()) {
            throw new ParseException(new Pos(file, 1), "dialogue has no nodes");
        }

        String id = file;
        int slash = Math.max(id.lastIndexOf('/'), id.lastIndexOf('\\'));
        if (slash >= 0) id = id.substring(slash + 1);
        if (id.endsWith(".talk")) id = id.substring(0, id.length() - 5);
        if (scope == null) scope = id;
        if (starts.isEmpty()) {
            starts.add(new Dialogue.Start(new Pos(file, 1), nodes.keySet().iterator().next(), null));
        }
        return new Dialogue(file, id, List.copyOf(bindings), List.copyOf(starts), speaker, title, scope, other, nodes);
    }

    private Dialogue.Start parseStart(String value, Pos pos) {
        int when = indexOfWord(value, "when");
        if (when < 0) {
            String node = value.trim();
            if (node.isEmpty()) throw new ParseException(pos, "start: needs a node name");
            return new Dialogue.Start(pos, node, null);
        }
        String node = value.substring(0, when).trim();
        String cond = value.substring(when + 4).trim();
        if (node.isEmpty() || cond.isEmpty()) throw new ParseException(pos, "start: expects 'node when condition'");
        return new Dialogue.Start(pos, node, ExprParser.parse(cond, pos));
    }

    private static int indexOfWord(String s, String word) {
        Matcher m = Pattern.compile("\\b" + word + "\\b").matcher(s);
        return m.find() ? m.start() : -1;
    }

    /**
     * Parse statements until a line with indent below {@code minIndent}, a node header,
     * or one of the {@code closers} (e.g. elseif/else/endif) at any indent.
     */
    private List<Statement> parseBlock(int minIndent, List<String> closers) {
        List<Statement> out = new ArrayList<>();
        List<Option> pendingOptions = null;
        Pos pendingPos = null;

        while (idx < lines.size()) {
            SrcLine l = lines.get(idx);
            if (NODE_HEADER.matcher(l.text()).matches()) break;
            if (l.indent() < minIndent) break;
            String keyword = commandKeyword(l.text());
            if (closers != null && keyword != null && closers.contains(keyword)) break;
            if (keyword != null && (keyword.equals("endif") || keyword.equals("elseif") || keyword.equals("else") || keyword.equals("endonce"))) {
                throw new ParseException(pos(l), "'" + keyword + "' without a matching opener");
            }

            Matcher opt = OPTION.matcher(l.text());
            if (opt.matches()) {
                if (pendingOptions == null) {
                    pendingOptions = new ArrayList<>();
                    pendingPos = pos(l);
                }
                pendingOptions.add(parseOption(l, opt.group(1)));
                continue;
            }
            if (pendingOptions != null) {
                out.add(new Statement.Choice(pendingPos, List.copyOf(pendingOptions)));
                pendingOptions = null;
            }
            out.add(parseStatement(l, minIndent));
        }
        if (pendingOptions != null) {
            out.add(new Statement.Choice(pendingPos, List.copyOf(pendingOptions)));
        }
        return out;
    }

    private Option parseOption(SrcLine l, String rest) {
        idx++;
        Pos p = pos(l);
        Expr guard = null;
        Expr showGuard = null;
        String textPart = rest;
        Matcher g = TRAILING_GUARD.matcher(rest);
        if (g.matches()) {
            textPart = g.group(1);
            Expr e = ExprParser.parse(g.group(3), p);
            if (g.group(2).equals("if")) guard = e;
            else showGuard = e;
        }
        if (textPart.isBlank()) {
            throw new ParseException(p, "option has no text");
        }
        Text text = TextParser.parse(textPart.trim(), p);
        // Body: following lines indented deeper than the arrow.
        List<Statement> body = parseBlock(l.indent() + 1, null);
        return new Option(p, text, guard, showGuard, body);
    }

    private Statement parseStatement(SrcLine l, int minIndent) {
        Pos p = pos(l);
        Matcher cmd = COMMAND.matcher(l.text());
        if (cmd.matches()) {
            return parseCommand(l, cmd.group(1), minIndent);
        }
        if (l.text().startsWith("<<") || l.text().endsWith(">>")) {
            throw new ParseException(p, "malformed command, expected <<...>>: " + l.text());
        }
        idx++;
        Matcher sp = SPEAKER.matcher(l.text());
        if (sp.matches() && !l.text().startsWith("http")) {
            return new Statement.Line(p, sp.group(1).trim(), TextParser.parse(sp.group(2), p));
        }
        String text = l.text();
        if (text.startsWith("\\")) text = text.substring(1); // escaped leading char, e.g. \-> or \Speaker:
        return new Statement.Line(p, null, TextParser.parse(text, p));
    }

    private static String commandKeyword(String text) {
        Matcher m = COMMAND.matcher(text);
        if (!m.matches()) return null;
        String inner = m.group(1).trim();
        int sp = inner.indexOf(' ');
        return sp < 0 ? inner : inner.substring(0, sp);
    }

    private Statement parseCommand(SrcLine l, String inner, int minIndent) {
        Pos p = pos(l);
        String keyword = inner.contains(" ") ? inner.substring(0, inner.indexOf(' ')) : inner;
        String rest = inner.substring(keyword.length()).trim();

        switch (keyword) {
            case "if" -> {
                return parseConditional(l, rest, minIndent);
            }
            case "once" -> {
                idx++;
                if (!rest.isEmpty()) throw new ParseException(p, "<<once>> takes no arguments");
                List<Statement> body = parseBlock(minIndent, List.of("endonce"));
                expectCloser(p, "endonce");
                return new Statement.Once(p, "once" + (onceCounter++), body);
            }
            case "set" -> {
                idx++;
                Matcher m = SET.matcher(inner);
                if (!m.matches()) throw new ParseException(p, "expected <<set $var = expression>>");
                return new Statement.Set(p, ExprParser.parseVar(m.group(1), p), ExprParser.parse(m.group(2), p));
            }
            case "jump" -> {
                idx++;
                if (rest.isEmpty() || rest.contains(" ")) throw new ParseException(p, "expected <<jump node_name>>");
                return new Statement.Jump(p, rest);
            }
            case "end" -> {
                idx++;
                if (!rest.isEmpty()) throw new ParseException(p, "<<end>> takes no arguments");
                return new Statement.End(p);
            }
            case "input" -> {
                idx++;
                Matcher m = INPUT.matcher(inner);
                if (!m.matches()) throw new ParseException(p, "expected <<input $var \"prompt\">>");
                Text prompt = m.group(2) == null ? Text.plain("") : parseStringArg(m.group(2), p);
                return new Statement.Input(p, ExprParser.parseVar(m.group(1), p), prompt);
            }
            default -> {
                idx++;
                if (!keyword.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    throw new ParseException(p, "bad command name: " + keyword);
                }
                return new Statement.Command(p, keyword, splitArgs(rest, p));
            }
        }
    }

    private Statement parseConditional(SrcLine l, String firstCond, int minIndent) {
        Pos p = pos(l);
        if (firstCond.isEmpty()) throw new ParseException(p, "<<if>> needs a condition");
        List<Statement.Branch> branches = new ArrayList<>();
        idx++;
        Expr cond = ExprParser.parse(firstCond, p);
        List<Statement> body = parseBlock(minIndent, List.of("elseif", "else", "endif"));
        branches.add(new Statement.Branch(p, cond, body));
        boolean sawElse = false;
        while (true) {
            if (idx >= lines.size()) throw new ParseException(p, "<<if>> is never closed with <<endif>>");
            SrcLine c = lines.get(idx);
            String kw = commandKeyword(c.text());
            if (kw == null) throw new ParseException(p, "<<if>> is never closed with <<endif>>");
            String inner = COMMAND.matcher(c.text()).replaceAll("$1").trim();
            String rest = inner.substring(kw.length()).trim();
            Pos cp = pos(c);
            switch (kw) {
                case "elseif" -> {
                    if (sawElse) throw new ParseException(cp, "<<elseif>> after <<else>>");
                    if (rest.isEmpty()) throw new ParseException(cp, "<<elseif>> needs a condition");
                    idx++;
                    Expr e = ExprParser.parse(rest, cp);
                    branches.add(new Statement.Branch(cp, e, parseBlock(minIndent, List.of("elseif", "else", "endif"))));
                }
                case "else" -> {
                    if (sawElse) throw new ParseException(cp, "second <<else>>");
                    if (!rest.isEmpty()) throw new ParseException(cp, "<<else>> takes no condition (use <<elseif>>)");
                    sawElse = true;
                    idx++;
                    branches.add(new Statement.Branch(cp, null, parseBlock(minIndent, List.of("elseif", "else", "endif"))));
                }
                case "endif" -> {
                    idx++;
                    return new Statement.Conditional(p, List.copyOf(branches));
                }
                default -> throw new ParseException(cp, "unexpected <<" + kw + ">> inside <<if>>");
            }
        }
    }

    private void expectCloser(Pos opener, String closer) {
        if (idx >= lines.size()) throw new ParseException(opener, "<<" + closer.substring(3) + ">> is never closed with <<" + closer + ">>");
        SrcLine c = lines.get(idx);
        if (!closer.equals(commandKeyword(c.text()))) {
            throw new ParseException(opener, "expected <<" + closer + ">> but found: " + c.text());
        }
        idx++;
    }

    /** Split command arguments on whitespace, honouring quotes; each becomes interpolatable Text. */
    static List<Text> splitArgs(String rest, Pos p) {
        List<Text> args = new ArrayList<>();
        int i = 0;
        int n = rest.length();
        while (i < n) {
            char c = rest.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '"' || c == '\'') {
                int start = i + 1;
                int j = start;
                StringBuilder sb = new StringBuilder();
                boolean closed = false;
                while (j < n) {
                    char d = rest.charAt(j);
                    if (d == '\\' && j + 1 < n) {
                        sb.append(rest.charAt(j + 1));
                        j += 2;
                    } else if (d == c) {
                        closed = true;
                        j++;
                        break;
                    } else {
                        sb.append(d);
                        j++;
                    }
                }
                if (!closed) throw new ParseException(p, "unterminated quoted argument");
                args.add(TextParser.parse(sb.toString(), p));
                i = j;
            } else {
                int j = i;
                while (j < n && !Character.isWhitespace(rest.charAt(j))) j++;
                args.add(TextParser.parse(rest.substring(i, j), p));
                i = j;
            }
        }
        return args;
    }

    private static Text parseStringArg(String s, Pos p) {
        List<Text> parts = splitArgs(s, p);
        if (parts.size() != 1) throw new ParseException(p, "expected one quoted string, got: " + s);
        return parts.get(0);
    }
}
