package com.alekpeed.lifeos.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Keeping content out of the parts of the screen the app does not own.
//
// This is subtler here than in an ordinary app, because Life OS deliberately hides the
// system bars (`FullscreenApplication` calls `hide(systemBars())` with transient-on-swipe
// behaviour, and asks for `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`). Two consequences,
// and both were visible on a real phone in a way they never are in a build log:
//
//   · **`safeDrawing` alone is not enough at the bottom.** With the navigation bar
//     hidden its inset is zero, so nothing pads for it — but the home-swipe lane is
//     still there, physically, and the last row of tiles sits underneath it. The gesture
//     inset is the one that stays true when the bar is hidden, which is why
//     `Native.navBottomPx()` already took the max of the two for the Operations artwork.
//   · **The cutout is not the status bar.** Hiding the bars does not move the notch or
//     the punch-hole, and on the phones that put the clock up there the clock is drawn
//     over whatever the app puts underneath. `safeDrawing` still reports the cutout when
//     the bars are gone, so it is the top half of the answer.
//
// A hardcoded top spacer — which is what this replaces — cannot be right, because it is
// a guess at a measurement only the device has.
//
// Horizontal gesture insets are deliberately left out. The back-gesture lanes run down
// both edges of most phones and padding for them would inset every screen by twenty-odd
// points for a gesture that does not need the content moved, only the touches.
@Composable
fun Modifier.safeArea(): Modifier = this.windowInsetsPadding(
    WindowInsets.safeDrawing.union(WindowInsets.systemGestures.only(WindowInsetsSides.Bottom)),
)

// The bottom half on its own, for a screen that wants to reach the top edge — a reader,
// or a graphical interface with its own header art — but must still stay out of the
// swipe lane.
@Composable
fun Modifier.safeAreaBottom(): Modifier = this.windowInsetsPadding(
    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
        .union(WindowInsets.systemGestures.only(WindowInsetsSides.Bottom)),
)
