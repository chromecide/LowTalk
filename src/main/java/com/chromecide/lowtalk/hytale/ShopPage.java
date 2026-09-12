package com.chromecide.lowtalk.hytale;

import com.hypixel.hytale.builtin.adventure.shop.barter.BarterPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * The game's own barter shop, opened from a dialogue by {@code <<shop>>}. The vanilla page already has a Back
 * button; this subclass only listens for the page being dismissed (Back or Escape) and hands control back to the
 * conversation, which resumes with whatever follows the command. The trades, the layout and the styling are the
 * game's, untouched.
 */
public class ShopPage extends BarterPage {

    private final Runnable onClosed;

    public ShopPage(@Nonnull PlayerRef playerRef, @Nonnull String shopId, @Nonnull Runnable onClosed) {
        super(playerRef, shopId);
        this.onClosed = onClosed;
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        super.onDismiss(ref, store);
        onClosed.run();
    }
}
