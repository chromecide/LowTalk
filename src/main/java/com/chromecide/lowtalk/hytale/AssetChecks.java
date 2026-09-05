package com.chromecide.lowtalk.hytale;

import com.chromecide.lowtalk.model.Dialogue;
import com.chromecide.lowtalk.model.Node;
import com.chromecide.lowtalk.model.Option;
import com.chromecide.lowtalk.model.Statement;
import com.chromecide.lowtalk.model.Text;
import com.hypixel.hytale.builtin.adventure.objectives.config.ObjectiveAsset;
import com.hypixel.hytale.builtin.adventure.shop.barter.BarterShopAsset;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Reload-time checks that need the server's asset maps: item, objective, sound, and shop ids
 * named in commands must exist. Only static arguments are checked; interpolated ones are skipped.
 */
public final class AssetChecks {

    private AssetChecks() {}

    public static List<String> check(Dialogue d) {
        List<String> out = new ArrayList<>();
        for (Node n : d.nodeList()) walk(n.body(), d, out);
        return out;
    }

    private static void walk(List<Statement> body, Dialogue d, List<String> out) {
        for (Statement s : body) {
            switch (s) {
                case Statement.Command c -> checkCommand(c, d, out);
                case Statement.Choice c -> {
                    for (Option o : c.options()) walk(o.body(), d, out);
                }
                case Statement.Conditional c -> c.branches().forEach(b -> walk(b.body(), d, out));
                case Statement.Once o -> walk(o.body(), d, out);
                default -> {}
            }
        }
    }

    private static void checkCommand(Statement.Command c, Dialogue d, List<String> out) {
        if (c.args().isEmpty()) return;
        Text first = c.args().get(0);
        if (!first.isStatic()) return;
        String id = first.debugString();
        String where = "warning " + c.pos() + ": ";
        try {
            switch (c.name()) {
                case "give", "take" -> {
                    if (Item.getAssetMap().getAsset(id) == null) out.add(where + "no item called '" + id + "'");
                }
                case "objective" -> {
                    if (ObjectiveAsset.getAssetMap().getAsset(id) == null) out.add(where + "no objective called '" + id + "'");
                }
                case "sound" -> {
                    if (SoundEvent.getAssetMap().getIndex(id) == 0) out.add(where + "no sound event called '" + id + "'");
                }
                case "shop" -> {
                    if (BarterShopAsset.getAssetMap().getAsset(id) == null) out.add(where + "no barter shop called '" + id + "'");
                }
                default -> {}
            }
        } catch (RuntimeException e) {
            // Asset maps not ready (very early load); skip silently.
        }
    }
}
