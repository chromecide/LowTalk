"""Shared helper for the example graph generators.

A generator describes a dialogue as a tree of node(...) calls; write_graph() lays it out on the Node Editor canvas
(x by depth, y by a post-order walk, parents centred on their children), stamps a $NodeId on every object and writes
an ordinary LowTalk JSON dialogue asset with the editor's bookkeeping under $NodeEditorMetadata. The server ignores
those keys, so the file loads like any other dialogue.
"""
import json
import os
import uuid

X_STEP, Y_STEP = 520, 170
WORKSPACE_ID = "LowTalk - Dialogue"


def node(kind, fields, children=()):
    """kind is the node type id (a statement Type, or Dialogue/Start/Node/Option/Branch/Alternative);
    children is a list of (jsonKey, [child nodes]) wired through output pins."""
    return {"kind": kind, "fields": fields, "children": list(children)}


def stmt(kind, **fields):
    return node(kind, fields)


def with_body(kind, body, **fields):
    return node(kind, fields, [("Body", body)])


def option(text, body, cond="", show="", once=False):
    return with_body("Option", body, Text=text, If=cond, ShowIf=show, Once=once)


def say(text, speaker="", button=""):
    fields = {"Speaker": speaker, "Text": text}
    if button:
        fields["Button"] = button
    return stmt("Say", **fields)


def jump(target):
    return stmt("Jump", Passage=target)


def choice(options):
    return node("Choice", {}, [("Options", options)])


def dialogue(npc, speaker, title, starts, nodes, scope="", portrait="", on="", layout="", history=""):
    fields = {"Npc": npc, "Speaker": speaker, "Title": title, "Scope": scope, "Portrait": portrait, "On": on}
    if layout:
        fields["Layout"] = layout
    if history:
        fields["History"] = history
    return node("Dialogue", fields, [
        ("Start", [stmt("Start", Passage=n, When=w) for n, w in starts]),
        ("Passages", [with_body("Passage", body, Name=name) for name, body in nodes]),
    ])


def _walk_saved(obj, out):
    """Node objects of a saved graph in generator order (same tree, same walk), for carrying layouts over."""
    if not isinstance(obj, dict) or "$NodeId" not in obj:
        return
    out.append(obj)
    for key, val in obj.items():
        if key.startswith("$"):
            continue
        if isinstance(val, list):
            for item in val:
                _walk_saved(item, out)


def _saved_layout(path):
    """Positions from an existing output, in walk order, so hand-made layouts survive regeneration."""
    try:
        with open(path, encoding="utf-8-sig") as f:
            doc = json.load(f)
    except (OSError, ValueError):
        return None
    meta = doc.get("$NodeEditorMetadata") or {}
    nodes = meta.get("$Nodes") or {}
    order = []
    _walk_saved(doc, order)
    return [(o["$NodeId"], nodes.get(o["$NodeId"])) for o in order], meta


def write_graph(root, out_paths):
    positions = {}
    next_y = [0]
    saved = _saved_layout(out_paths[0]) if out_paths else None

    def layout(n, depth):
        child_ys = []
        for _, kids in n["children"]:
            for k in kids:
                child_ys.append(layout(k, depth + 1))
        if child_ys:
            y = sum(child_ys) / len(child_ys)
        else:
            y = next_y[0]
            next_y[0] += Y_STEP
        n["_pos"] = (depth * X_STEP, round(y))
        return y

    def emit(n, is_root=False):
        nid = f"{n['kind']}-{uuid.uuid4()}"
        out = {"$NodeId": nid}
        if n["kind"] not in ("Dialogue", "Start", "Passage", "Option", "Branch", "Alternative"):
            out["Type"] = n["kind"]
        out.update(n["fields"])
        for key, kids in n["children"]:
            out[key] = [emit(k) for k in kids]
        meta = {"$Position": {"$x": n["_pos"][0], "$y": n["_pos"][1]}}
        if is_root:
            meta["$Title"] = "[ROOT] Dialogue"
        positions[nid] = meta
        return out

    layout(root, 0)
    doc = emit(root, is_root=True)
    meta = {
        "$Nodes": positions,
        "$FloatingNodes": [],
        "$Links": {},
        "$Groups": [],
        "$Comments": [],
        "$WorkspaceID": WORKSPACE_ID,
    }
    # Keep a layout someone arranged by hand in the editor: same number of nodes in the same walk order means the
    # structure is unchanged, so each new node takes the saved position (and title) of its counterpart.
    if saved:
        saved_nodes, saved_meta = saved
        new_ids = list(positions.keys())
        if len(saved_nodes) == len(new_ids) and all(sp is not None for _, sp in saved_nodes):
            for new_id, (_, saved_pos) in zip(new_ids, saved_nodes):
                positions[new_id] = dict(saved_pos)
            for key in ("$Groups", "$Comments", "$FloatingNodes"):
                if saved_meta.get(key):
                    meta[key] = saved_meta[key]
            print("kept the saved layout")
        else:
            print("structure changed; laid out afresh")
    doc["$NodeEditorMetadata"] = meta
    text = json.dumps(doc, indent=2) + "\n"
    for out in out_paths:
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "w", encoding="utf-8") as f:
            f.write(text)
        print(f"wrote {out} ({len(positions)} nodes)")
    return doc
