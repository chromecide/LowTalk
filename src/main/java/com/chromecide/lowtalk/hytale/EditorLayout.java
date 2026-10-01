package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.ui.PageLayout;
import com.chromecide.lowtalk.ui.UiEvent;

import javax.annotation.Nonnull;

/**
 * Which control carries which value in the dialogue editor, for every kind of row it can draw.
 *
 * <p>Separated from the page because it is long: twelve kinds of row, each with its own fields and its own
 * buttons. Keeping it here means the page is about editing a dialogue and this file is the whole of what the
 * editor knows about the screen — which is also what lets one test check every one of these names against the
 * documents that declare them.
 */
final class EditorLayout {

    static final String PAGE = "Pages/LowTalk/EditorPage.ui";
    static final String ROW_LINE = "Pages/LowTalk/EditLine.ui";
    static final String ROW_OPTION = "Pages/LowTalk/EditOption.ui";
    static final String ROW_OPTION_COND = "Pages/LowTalk/EditOptionCond.ui";
    static final String ROW_COND = "Pages/LowTalk/EditCond.ui";
    static final String ROW_COMMAND = "Pages/LowTalk/EditCommand.ui";
    static final String ROW_SET = "Pages/LowTalk/EditSet.ui";
    static final String ROW_JUMP = "Pages/LowTalk/EditJump.ui";
    static final String ROW_INPUT = "Pages/LowTalk/EditInput.ui";
    static final String ROW_WAIT = "Pages/LowTalk/EditWait.ui";
    static final String ROW_BRANCH = "Pages/LowTalk/EditBranch.ui";
    static final String ROW_BLOCK = "Pages/LowTalk/EditBlock.ui";
    static final String ROW_HEADER = "Pages/LowTalk/EditHeader.ui";

    /** Row kinds as the model names them. Two of them share a document and differ only in what Delete means. */
    static final String LINE = "line";
    static final String OPTION = "option";
    static final String OPTION_COND = "optionCond";
    static final String COND = "cond";
    static final String COMMAND = "command";
    static final String SET = "set";
    static final String JUMP = "jump";
    static final String INPUT = "input";
    static final String WAIT = "wait";
    static final String BRANCH = "branch";
    static final String BLOCK = "block";
    static final String ALTERNATIVE = "alternative";
    static final String HEADER = "header";

    /** How many named argument fields a command row has room for. */
    static final int ARG_SLOTS = 4;

    private EditorLayout() {}

    @Nonnull
    static PageLayout build() {
        PageLayout.ContainerBuilder rows = PageLayout.of(PAGE)
                .property("pageTitle", "#PageTitle", "Text")
                // Follows the passage being shown: navigating writes the new name into the box.
                .property("nodeName", "#NodeName", "Value")
                .property("nodeEntries", "#NodeJump", "Entries")
                .property("node", "#NodeJump", "Value")
                .property("crumbs", "#Crumbs", "Text")
                .property("status", "#Status", "Text")
                .property("addRowVisible", "#AddRow", "Visible")
                .property("addKindEntries", "#AddKind", "Entries")
                .property("addKind", "#AddKind", "Value")
                .command("RENAME", "#RenameButton", UiEvent.ACTIVATE).reads("Value", "#NodeName", "Value").done()
                .command("JUMP", "#NodeJump", UiEvent.VALUE_CHANGED).reads("Value", "#NodeJump", "Value").done()
                .command("ADD_KIND", "#AddKind", UiEvent.VALUE_CHANGED).reads("Value", "#AddKind", "Value").done()
                .command("ADD", "#AddButton", UiEvent.ACTIVATE).reads("Value", "#AddKind", "Value").done()
                .command("BACK", "#BackButton", UiEvent.ACTIVATE).done()
                .command("HEADER", "#HeaderButton", UiEvent.ACTIVATE).done()
                .command("DELETE_NODE", "#DeleteNodeButton", UiEvent.ACTIVATE).done()
                .command("ADD_NODE", "#AddNodeButton", UiEvent.ACTIVATE).done()
                .command("SAVE", "#SaveButton", UiEvent.ACTIVATE).done()
                .command("TEST", "#TestButton", UiEvent.ACTIVATE).done()
                .command("DISCARD", "#DiscardButton", UiEvent.ACTIVATE).done()
                .command("CLOSE", "#CloseButton", UiEvent.ACTIVATE).done()
                .container("rows", "#Rows");

        line(rows);
        option(rows);
        optionCond(rows);
        condition(rows);
        command(rows);
        set(rows);
        jump(rows);
        input(rows);
        wait(rows);
        branch(rows);
        block(rows, BLOCK, "DEL");
        block(rows, ALTERNATIVE, "ALT_DEL");
        header(rows);
        return rows.done().build();
    }

    private static void line(PageLayout.ContainerBuilder rows) {
        upDownDelete(rows.template(LINE, ROW_LINE)
                .field("bad", "#Bad", "Text")
                .field("speaker", "#Speaker", "Value")
                .field("text", "#Text", "Value")
                .field("button", "#Button", "Value")
                .rowCommand("LINE_SPEAKER", "#Speaker", UiEvent.VALUE_CHANGED).row("Row").readsSelf("Value", "Value").done()
                .rowCommand("LINE_TEXT", "#Text", UiEvent.VALUE_CHANGED).row("Row").readsSelf("Value", "Value").done()
                .rowCommand("LINE_BUTTON", "#Button", UiEvent.VALUE_CHANGED).row("Row").readsSelf("Value", "Value").done(),
                "DEL").done();
    }

    private static void option(PageLayout.ContainerBuilder rows) {
        upDownDelete(rows.template(OPTION, ROW_OPTION)
                .field("bad", "#Bad", "Text")
                .field("label", "#Label", "Value")
                .field("targetEntries", "#Target", "Entries")
                .field("target", "#Target", "Value")
                .rowCommand("OPT_TEXT", "#Label", UiEvent.VALUE_CHANGED).row("Row").readsSelf("Value", "Value").done()
                .rowCommand("OPT_TARGET", "#Target", UiEvent.VALUE_CHANGED).row("Row").readsSelf("Value", "Value").done()
                .rowButton("OPT_GO", "#Go", "Row")
                .rowButton("OPT_MORE", "#More", "Row"),
                "DEL").done();
    }

    private static void optionCond(PageLayout.ContainerBuilder rows) {
        rows.template(OPTION_COND, ROW_OPTION_COND)
                .field("once", "#Once", "Text")
                .rowButton("OPT_ONCE", "#Once", "Row")
                .rowButton("OPT_BODY", "#Body", "Row")
                .done();
    }

    private static void condition(PageLayout.ContainerBuilder rows) {
        conditionFields(rows.template(COND, ROW_COND).field("tag", "#Tag", "Text")).done();
    }

    private static void branch(PageLayout.ContainerBuilder rows) {
        upDown(conditionFields(rows.template(BRANCH, ROW_BRANCH)
                .field("bad", "#Bad", "Text")
                .field("tag", "#Tag", "Text")
                .field("addBranchVisible", "#AddBranch", "Visible")
                .rowButton("BRANCH_BODY", "#Body", "Row")
                .rowButton("BRANCH_ADD", "#AddBranch", "Row")
                .rowButton("BRANCH_DEL", "#Del", "Row")))
                .done();
    }

    /**
     * A condition as fields: what it is about, its own argument, a comparison and a value. The argument and the
     * value are each drawn one of three ways — a box to type in, a list to choose from, or a button that opens
     * the picker page — so each has all three and the page decides which is visible.
     */
    private static PageLayout.TemplateBuilder conditionFields(PageLayout.TemplateBuilder t) {
        return t
                .field("kindVisible", "#Kind", "Visible")
                .field("kindEntries", "#Kind", "Entries")
                .field("kind", "#Kind", "Value")
                .field("rawVisible", "#Raw", "Visible")
                .field("raw", "#Raw", "Value")
                .field("argVisible", "#Arg", "Visible")
                .field("opVisible", "#Op", "Visible")
                .field("valVisible", "#Val", "Visible")
                .field("argBoxVisible", "#V", "Visible")
                .field("argBox", "#V", "Value")
                .field("argListVisible", "#C", "Visible")
                .field("argEntries", "#C", "Entries")
                .field("argList", "#C", "Value")
                .field("argButtonVisible", "#B", "Visible")
                .field("argButton", "#B", "Text")
                .field("opEntries", "#Op", "Entries")
                .field("op", "#Op", "Value")
                .field("valBoxVisible", "#VV", "Visible")
                .field("valBox", "#VV", "Value")
                .field("valListVisible", "#VC", "Visible")
                .field("valEntries", "#VC", "Entries")
                .field("valList", "#VC", "Value")
                .field("valButtonVisible", "#VB", "Visible")
                .field("valButton", "#VB", "Text")
                .rowCommand("COND_KIND", "#Kind", UiEvent.VALUE_CHANGED).row("Row").fromField("Slot", "slot").readsSelf("Value", "Value").done()
                .rowCommand("COND_RAW", "#Raw", UiEvent.VALUE_CHANGED).row("Row").fromField("Slot", "slot").readsSelf("Value", "Value").done()
                .rowCommand("COND_ARG", "#V", UiEvent.VALUE_CHANGED).row("Row").fromField("Slot", "slot").readsSelf("Value", "Value").done()
                .rowCommand("COND_ARG_PICK", "#C", UiEvent.VALUE_CHANGED).row("Row").fromField("Slot", "slot").readsSelf("Value", "Value").done()
                .rowCommand("COND_ARG_FIND", "#B", UiEvent.ACTIVATE).row("Row").fromField("Slot", "slot").done()
                .rowCommand("COND_OP", "#Op", UiEvent.VALUE_CHANGED).row("Row").fromField("Slot", "slot").readsSelf("Value", "Value").done()
                .rowCommand("COND_VAL", "#VV", UiEvent.VALUE_CHANGED).row("Row").fromField("Slot", "slot").readsSelf("Value", "Value").done()
                .rowCommand("COND_VAL_PICK", "#VC", UiEvent.VALUE_CHANGED).row("Row").fromField("Slot", "slot").readsSelf("Value", "Value").done()
                .rowCommand("COND_VAL_FIND", "#VB", UiEvent.ACTIVATE).row("Row").fromField("Slot", "slot").done();
    }

    private static void command(PageLayout.ContainerBuilder rows) {
        PageLayout.TemplateBuilder t = rows.template(COMMAND, ROW_COMMAND)
                .field("bad", "#Bad", "Text")
                .field("name", "#Name", "Text")
                .field("argsVisible", "#Args", "Visible")
                .field("args", "#Args", "Value")
                .field("spacerVisible", "#Spacer", "Visible")
                .field("pickVisible", "#Pick", "Visible")
                .field("pickEntries", "#Pick", "Entries")
                .field("pick", "#Pick", "Value")
                .rowCommand("CMD_ARGS", "#Args", UiEvent.VALUE_CHANGED).row("Row").readsSelf("Value", "Value").done()
                .rowCommand("CMD_PICK", "#Pick", UiEvent.VALUE_CHANGED).row("Row").readsSelf("Value", "Value").done();
        // Written out rather than looped so that every control name is a literal: a name built up with
        // "#B" + slot is invisible to the check that these all exist, which is how the option buttons
        // of the dialogue window went unchecked for so long.
        t = argSlot0(t);
        t = argSlot1(t);
        t = argSlot2(t);
        t = argSlot3(t);
        upDownDelete(t, "DEL").done();
    }


    /** Argument slot 0 of a command row. */
    private static PageLayout.TemplateBuilder argSlot0(PageLayout.TemplateBuilder t) {
        return t.field("arg0Visible", "#Arg0", "Visible")
                .field("arg0Label", "#L0", "Text")
                .field("arg0BoxVisible", "#V0", "Visible")
                .field("arg0Box", "#V0", "Value")
                .field("arg0ListVisible", "#C0", "Visible")
                .field("arg0Entries", "#C0", "Entries")
                .field("arg0List", "#C0", "Value")
                .field("arg0ButtonVisible", "#B0", "Visible")
                .field("arg0Button", "#B0", "Text")
                .rowCommand("CMD_SLOT", "#V0", UiEvent.VALUE_CHANGED).row("Row").constant("Slot", "0").readsSelf("Value", "Value").done()
                .rowCommand("CMD_SLOT_PICK", "#C0", UiEvent.VALUE_CHANGED).row("Row").constant("Slot", "0").readsSelf("Value", "Value").done()
                .rowCommand("CMD_SLOT_FIND", "#B0", UiEvent.ACTIVATE).row("Row").constant("Slot", "0").done();
    }

    /** Argument slot 1 of a command row. */
    private static PageLayout.TemplateBuilder argSlot1(PageLayout.TemplateBuilder t) {
        return t.field("arg1Visible", "#Arg1", "Visible")
                .field("arg1Label", "#L1", "Text")
                .field("arg1BoxVisible", "#V1", "Visible")
                .field("arg1Box", "#V1", "Value")
                .field("arg1ListVisible", "#C1", "Visible")
                .field("arg1Entries", "#C1", "Entries")
                .field("arg1List", "#C1", "Value")
                .field("arg1ButtonVisible", "#B1", "Visible")
                .field("arg1Button", "#B1", "Text")
                .rowCommand("CMD_SLOT", "#V1", UiEvent.VALUE_CHANGED).row("Row").constant("Slot", "1").readsSelf("Value", "Value").done()
                .rowCommand("CMD_SLOT_PICK", "#C1", UiEvent.VALUE_CHANGED).row("Row").constant("Slot", "1").readsSelf("Value", "Value").done()
                .rowCommand("CMD_SLOT_FIND", "#B1", UiEvent.ACTIVATE).row("Row").constant("Slot", "1").done();
    }

    /** Argument slot 2 of a command row. */
    private static PageLayout.TemplateBuilder argSlot2(PageLayout.TemplateBuilder t) {
        return t.field("arg2Visible", "#Arg2", "Visible")
                .field("arg2Label", "#L2", "Text")
                .field("arg2BoxVisible", "#V2", "Visible")
                .field("arg2Box", "#V2", "Value")
                .field("arg2ListVisible", "#C2", "Visible")
                .field("arg2Entries", "#C2", "Entries")
                .field("arg2List", "#C2", "Value")
                .field("arg2ButtonVisible", "#B2", "Visible")
                .field("arg2Button", "#B2", "Text")
                .rowCommand("CMD_SLOT", "#V2", UiEvent.VALUE_CHANGED).row("Row").constant("Slot", "2").readsSelf("Value", "Value").done()
                .rowCommand("CMD_SLOT_PICK", "#C2", UiEvent.VALUE_CHANGED).row("Row").constant("Slot", "2").readsSelf("Value", "Value").done()
                .rowCommand("CMD_SLOT_FIND", "#B2", UiEvent.ACTIVATE).row("Row").constant("Slot", "2").done();
    }

    /** Argument slot 3 of a command row. */
    private static PageLayout.TemplateBuilder argSlot3(PageLayout.TemplateBuilder t) {
        return t.field("arg3Visible", "#Arg3", "Visible")
                .field("arg3Label", "#L3", "Text")
                .field("arg3BoxVisible", "#V3", "Visible")
                .field("arg3Box", "#V3", "Value")
                .field("arg3ListVisible", "#C3", "Visible")
                .field("arg3Entries", "#C3", "Entries")
                .field("arg3List", "#C3", "Value")
                .field("arg3ButtonVisible", "#B3", "Visible")
                .field("arg3Button", "#B3", "Text")
                .rowCommand("CMD_SLOT", "#V3", UiEvent.VALUE_CHANGED).row("Row").constant("Slot", "3").readsSelf("Value", "Value").done()
                .rowCommand("CMD_SLOT_PICK", "#C3", UiEvent.VALUE_CHANGED).row("Row").constant("Slot", "3").readsSelf("Value", "Value").done()
                .rowCommand("CMD_SLOT_FIND", "#B3", UiEvent.ACTIVATE).row("Row").constant("Slot", "3").done();
    }

    private static void set(PageLayout.ContainerBuilder rows) {
        // Either box sends both values, because a set is only meaningful as a pair.
        upDownDelete(rows.template(SET, ROW_SET)
                .field("bad", "#Bad", "Text")
                .field("var", "#Var", "Value")
                .field("value", "#Value", "Value")
                .rowCommand("SET", "#Var", UiEvent.VALUE_CHANGED).row("Row")
                    .reads("Value", "#Var", "Value").reads("Value2", "#Value", "Value").done()
                .rowCommand("SET", "#Value", UiEvent.VALUE_CHANGED).row("Row")
                    .reads("Value", "#Var", "Value").reads("Value2", "#Value", "Value").done(),
                "DEL").done();
    }

    private static void jump(PageLayout.ContainerBuilder rows) {
        upDownDelete(rows.template(JUMP, ROW_JUMP)
                .field("bad", "#Bad", "Text")
                .field("nodeEntries", "#Node", "Entries")
                .field("node", "#Node", "Value")
                .rowCommand("JUMP_NODE", "#Node", UiEvent.VALUE_CHANGED).row("Row").readsSelf("Value", "Value").done()
                .rowButton("JUMP_GO", "#Go", "Row"),
                "DEL").done();
    }

    private static void input(PageLayout.ContainerBuilder rows) {
        upDownDelete(rows.template(INPUT, ROW_INPUT)
                .field("bad", "#Bad", "Text")
                .field("var", "#Var", "Value")
                .field("prompt", "#Prompt", "Value")
                .field("kindVisible", "#Kind", "Visible")
                .field("kindEntries", "#Kind", "Entries")
                .field("kind", "#Kind", "Value")
                .rowCommand("INPUT", "#Var", UiEvent.VALUE_CHANGED).row("Row")
                    .reads("Value", "#Var", "Value").reads("Value2", "#Prompt", "Value").done()
                .rowCommand("INPUT", "#Prompt", UiEvent.VALUE_CHANGED).row("Row")
                    .reads("Value", "#Var", "Value").reads("Value2", "#Prompt", "Value").done()
                .rowCommand("INPUT_KIND", "#Kind", UiEvent.VALUE_CHANGED).row("Row").readsSelf("Value", "Value").done(),
                "DEL").done();
    }

    private static void wait(PageLayout.ContainerBuilder rows) {
        upDownDelete(rows.template(WAIT, ROW_WAIT)
                .field("bad", "#Bad", "Text")
                .field("seconds", "#Seconds", "Value")
                .rowCommand("WAIT", "#Seconds", UiEvent.VALUE_CHANGED).row("Row").readsSelf("Value", "Value").done(),
                "DEL").done();
    }

    /** once/random/end share a document; only what Delete means differs, so they are two kinds of row. */
    private static void block(PageLayout.ContainerBuilder rows, String kind, String deleteCommand) {
        upDownDelete(rows.template(kind, ROW_BLOCK)
                .field("bad", "#Bad", "Text")
                .field("tag", "#Tag", "Text")
                .field("summary", "#Summary", "Text")
                .field("bodyVisible", "#Body", "Visible")
                .field("addAltVisible", "#AddAlt", "Visible")
                .rowButton("BLOCK_BODY", "#Body", "Row")
                .rowButton("ALT_ADD", "#AddAlt", "Row"),
                deleteCommand).done();
    }

    private static void header(PageLayout.ContainerBuilder rows) {
        rows.template(HEADER, ROW_HEADER)
                .field("bindings", "#Bindings", "Value")
                .field("npcEntries", "#NpcPick", "Entries")
                .field("npcPick", "#NpcPick", "Value")
                .field("speaker", "#Speaker", "Value")
                .field("dialogTitle", "#DialogTitle", "Value")
                .field("startEntries", "#StartNode", "Entries")
                .field("start", "#StartNode", "Value")
                .field("onEntries", "#On", "Entries")
                .field("on", "#On", "Value")
                .field("portrait", "#Portrait", "Value")
                .field("scope", "#Scope", "Value")
                .field("layoutEntries", "#Layout", "Entries")
                .field("layout", "#Layout", "Value")
                .field("historyEntries", "#History", "Entries")
                .field("history", "#History", "Value")
                .rowCommand("H_BINDINGS", "#Bindings", UiEvent.VALUE_CHANGED).readsSelf("Value", "Value").done()
                .rowCommand("H_NPC_PICK", "#NpcPick", UiEvent.VALUE_CHANGED).readsSelf("Value", "Value").done()
                .rowCommand("H_SPEAKER", "#Speaker", UiEvent.VALUE_CHANGED).readsSelf("Value", "Value").done()
                .rowCommand("H_TITLE", "#DialogTitle", UiEvent.VALUE_CHANGED).readsSelf("Value", "Value").done()
                .rowCommand("H_START", "#StartNode", UiEvent.VALUE_CHANGED).readsSelf("Value", "Value").done()
                .rowCommand("H_ON", "#On", UiEvent.VALUE_CHANGED).readsSelf("Value", "Value").done()
                .rowCommand("H_PORTRAIT", "#Portrait", UiEvent.VALUE_CHANGED).readsSelf("Value", "Value").done()
                .rowCommand("H_SCOPE", "#Scope", UiEvent.VALUE_CHANGED).readsSelf("Value", "Value").done()
                .rowCommand("H_LAYOUT", "#Layout", UiEvent.VALUE_CHANGED).readsSelf("Value", "Value").done()
                .rowCommand("H_HISTORY", "#History", UiEvent.VALUE_CHANGED).readsSelf("Value", "Value").done()
                .done();
    }

    /** Every statement row carries the same three buttons at its right edge. */
    private static PageLayout.TemplateBuilder upDownDelete(PageLayout.TemplateBuilder t, String deleteCommand) {
        return upDown(t).rowButton(deleteCommand, "#Del", "Row");
    }

    private static PageLayout.TemplateBuilder upDown(PageLayout.TemplateBuilder t) {
        return t.field("upVisible", "#Up", "Visible")
                .field("downVisible", "#Down", "Visible")
                .rowButton("UP", "#Up", "Row")
                .rowButton("DOWN", "#Down", "Row");
    }
}
