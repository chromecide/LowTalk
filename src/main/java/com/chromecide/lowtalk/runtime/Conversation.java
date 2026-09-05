package com.chromecide.lowtalk.runtime;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Node;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Pos;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.model.Text;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Walks one dialogue for one player. Call {@link #start()}, then {@link #next()},
 * {@link #choose(int)}, or {@link #answer(String)} depending on the last {@link Step}.
 * Each call returns a {@link Result}: the next step plus effects to apply before showing it.
 *
 * Not thread-safe; the host must drive it from one thread at a time.
 */
public final class Conversation {

    public record Result(Step step, List<Effect> effects) {}

    /** A body being executed and how far through it we are. */
    private static final class Frame {
        final List<Statement> body;
        int index;

        Frame(List<Statement> body) {
            this.body = body;
        }
    }

    private final Dialogue dialogue;
    private final Context ctx;
    private final Deque<Frame> stack = new ArrayDeque<>();
    private final List<Effect> effects = new ArrayList<>();
    private String currentNode;
    private Statement.Choice pendingChoice;
    private Statement.Input pendingInput;
    private boolean finished = false;

    public Conversation(@Nonnull Dialogue dialogue, @Nonnull Context ctx) {
        this.dialogue = dialogue;
        this.ctx = ctx;
    }

    public Dialogue getDialogue() {
        return dialogue;
    }

    public String getCurrentNode() {
        return currentNode;
    }

    public boolean isFinished() {
        return finished;
    }

    /** Pick the start node (first guarded start whose condition holds, else the unguarded one) and run. */
    public Result start() {
        String node = null;
        for (Dialogue.Start s : dialogue.starts()) {
            if (s.condition() == null) {
                if (node == null) node = s.node();
                continue;
            }
            boolean ok;
            try {
                ok = Values.truthy(Evaluator.eval(s.condition(), ctx));
            } catch (RuntimeError e) {
                throw e.at(s.pos());
            }
            if (ok) {
                node = s.node();
                break;
            }
        }
        if (node == null) {
            node = dialogue.nodes().keySet().iterator().next();
        }
        jumpTo(node, dialogue.starts().get(0).pos());
        return advance();
    }

    /** Called after a Say when the player presses Continue. */
    public Result next() {
        if (finished) return new Result(new Step.Finish(), List.of());
        if (pendingChoice != null) throw new RuntimeError("expected a choice, not continue");
        if (pendingInput != null) throw new RuntimeError("expected an answer, not continue");
        return advance();
    }

    /** Called after a Choose with the index of the option the player picked. */
    public Result choose(int index) {
        if (finished) return new Result(new Step.Finish(), List.of());
        if (pendingChoice == null) throw new RuntimeError("no choice is pending");
        List<Option> options = pendingChoice.options();
        if (index < 0 || index >= options.size()) throw new RuntimeError("no option " + index);
        Option o = options.get(index);
        if (!enabled(o)) throw new RuntimeError("option " + index + " is not available");
        if (o.once()) ctx.markOnce(currentNode + "#" + o.onceKey());
        pendingChoice = null;
        // The choice statement stays current in its frame so a body that does not jump or end
        // returns to the same options (hub behaviour).
        stack.push(new Frame(o.body()));
        return advance();
    }

    /** Called after an Ask with what the player typed. */
    public Result answer(String text) {
        if (finished) return new Result(new Step.Finish(), List.of());
        if (pendingInput == null) throw new RuntimeError("no input is pending");
        Statement.Input in = pendingInput;
        pendingInput = null;
        ctx.setVar(in.target().scope(), in.target().name(), text == null ? "" : text);
        stack.peek().index++;
        return advance();
    }

    // ------------------------------------------------------------------

    private void jumpTo(String node, Pos from) {
        Node n = dialogue.node(node);
        if (n == null) throw new RuntimeError(from, "jump to unknown node '" + node + "'");
        stack.clear();
        stack.push(new Frame(n.body()));
        currentNode = node;
        pendingChoice = null;
        pendingInput = null;
        ctx.markVisited(node);
    }

    /**
     * Run until something must be shown. A line is held back while jumps, sets, conditionals and
     * effects are processed, so that it can be shown together with the options or the next line
     * that follows it. Continue is only needed between two consecutive lines.
     */
    private Result advance() {
        Step.Say held = null;
        while (true) {
            Frame f = stack.peek();
            if (f == null) {
                if (held != null) return result(new Step.Say(held.speaker(), held.text(), true));
                return finish();
            }
            if (f.index >= f.body.size()) {
                stack.pop();
                continue;
            }
            Statement s = f.body.get(f.index);
            try {
                switch (s) {
                    case Statement.Line line -> {
                        if (held != null) {
                            return result(held); // leave this line for the next call
                        }
                        held = new Step.Say(speakerOf(line), Evaluator.render(line.text(), ctx));
                        f.index++;
                    }
                    case Statement.Choice c -> {
                        pendingChoice = c;
                        return result(new Step.Choose(held, shownOptions(c)));
                    }
                    case Statement.Conditional c -> {
                        f.index++;
                        for (Statement.Branch b : c.branches()) {
                            if (b.condition() == null || Values.truthy(Evaluator.eval(b.condition(), ctx))) {
                                stack.push(new Frame(b.body()));
                                break;
                            }
                        }
                    }
                    case Statement.Once o -> {
                        f.index++;
                        String key = currentNode + "#" + o.key();
                        if (!ctx.onceDone(key)) {
                            ctx.markOnce(key);
                            stack.push(new Frame(o.body()));
                        }
                    }
                    case Statement.Random r -> {
                        f.index++;
                        if (!r.alternatives().isEmpty()) {
                            stack.push(new Frame(r.alternatives().get(Evaluator.pick(r.alternatives().size(), ctx))));
                        }
                    }
                    case Statement.Wait w -> {
                        f.index++;
                        double seconds = Math.max(0.0, Values.number(Evaluator.eval(w.seconds(), ctx)));
                        return result(new Step.Wait(held, seconds));
                    }
                    case Statement.Set set -> {
                        f.index++;
                        ctx.setVar(set.target().scope(), set.target().name(), Evaluator.eval(set.value(), ctx));
                    }
                    case Statement.Jump j -> jumpTo(j.node(), j.pos());
                    case Statement.End e -> {
                        if (held != null) {
                            // Show the last line; the next call finds the end again.
                            return result(new Step.Say(held.speaker(), held.text(), true));
                        }
                        return finish();
                    }
                    case Statement.Input in -> {
                        if (held != null) {
                            return result(held);
                        }
                        pendingInput = in;
                        return result(new Step.Ask(Evaluator.render(in.prompt(), ctx)));
                    }
                    case Statement.Command cmd -> {
                        f.index++;
                        List<String> args = new ArrayList<>(cmd.args().size());
                        for (Text t : cmd.args()) args.add(Evaluator.render(t, ctx));
                        effects.add(new Effect(cmd.pos(), cmd.name(), List.copyOf(args)));
                    }
                }
            } catch (RuntimeError e) {
                throw e.at(s.pos());
            }
        }
    }

    private Result finish() {
        finished = true;
        stack.clear();
        pendingChoice = null;
        pendingInput = null;
        return result(new Step.Finish());
    }

    private Result result(Step step) {
        List<Effect> out = List.copyOf(effects);
        effects.clear();
        return new Result(step, out);
    }

    private String speakerOf(Statement.Line line) {
        if (line.speaker() != null) return line.speaker();
        if (dialogue.speaker() != null) return dialogue.speaker();
        return Values.text(ctx.call("npc", List.of()));
    }

    private List<Step.Shown> shownOptions(Statement.Choice c) {
        List<Step.Shown> out = new ArrayList<>();
        List<Option> options = c.options();
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            if (o.once() && ctx.onceDone(currentNode + "#" + o.onceKey())) {
                continue;
            }
            if (o.guard() != null && !Values.truthy(Evaluator.eval(o.guard(), ctx))) {
                continue;
            }
            boolean enabled = o.showGuard() == null || Values.truthy(Evaluator.eval(o.showGuard(), ctx));
            out.add(new Step.Shown(i, Evaluator.render(o.text(), ctx), enabled));
        }
        return out;
    }

    private boolean enabled(Option o) {
        if (o.once() && ctx.onceDone(currentNode + "#" + o.onceKey())) return false;
        if (o.guard() != null && !Values.truthy(Evaluator.eval(o.guard(), ctx))) return false;
        return o.showGuard() == null || Values.truthy(Evaluator.eval(o.showGuard(), ctx));
    }
}
