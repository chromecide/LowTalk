package com.chromecide.lowtalk.ui;

import javax.annotation.Nullable;

/**
 * Where a box sits and how big it is, in pixels from the edges of its parent. Any side may be left
 * unset.
 *
 * <p>This is the one piece of hand-computed geometry the dialogue bar needs, because a layout built
 * from {@code .ui} templates does not size itself around its contents. A backend whose layouts do size
 * themselves can ignore it, which is why it is a value in the model rather than arithmetic in the page.
 */
public record UiBox(@Nullable Integer height, @Nullable Integer left, @Nullable Integer right,
                    @Nullable Integer top, @Nullable Integer bottom) {

    public static UiBox height(int height) {
        return new UiBox(height, null, null, null, null);
    }

    public UiBox sides(int left, int right) {
        return new UiBox(height, left, right, top, bottom);
    }

    public UiBox fromTop(int top) {
        return new UiBox(height, left, right, top, null);
    }

    public UiBox fromBottom(int bottom) {
        return new UiBox(height, left, right, null, bottom);
    }
}
