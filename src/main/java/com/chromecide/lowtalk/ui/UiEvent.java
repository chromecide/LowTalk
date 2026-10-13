package com.chromecide.lowtalk.ui;

/**
 * The kinds of interaction a command can be bound to, named in our own terms so that a page layout
 * mentions no backend type. A renderer maps these onto whatever its backend calls them.
 */
public enum UiEvent {
    /** The control was clicked, or activated from the keyboard. */
    ACTIVATE,
    /** The control's value changed — a dropdown, a checkbox, a slider. */
    VALUE_CHANGED,
    /** A key was pressed while the control had focus. Text fields only, on the current client. */
    KEY_DOWN
}
