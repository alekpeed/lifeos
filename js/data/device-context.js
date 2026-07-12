// Detects whether the app is running as the installed mobile remote (see
// LCARS_SPEC.md / PROJECT_SPEC.md's "Device philosophy" section) -- as
// opposed to a regular browser tab, or a desktop-installed PWA.
//
// Two signals, both required:
//   1. "Installed and launched as an app" -- display-mode is standalone/
//      fullscreen/minimal-ui (or iOS Safari's older navigator.standalone
//      flag), not a normal browser tab with URL bar and back button.
//   2. "Touch-primary device" -- coarse pointer, no hover capability.
// Standalone alone isn't enough: installing the PWA on a desktop (a
// perfectly normal, encouraged PWA behavior) also reports display-mode:
// standalone, and would wrongly trigger the stripped-down remote if that
// were the only check. Requiring touch-primary too means a desktop install
// always gets the full app, and only the actual phone-installed experience
// (via the TWA/Play Store package, or a plain "Add to Home Screen") gets
// the curated one. A regular browser tab -- on any device, any width --
// is deliberately never treated as the remote; only the installed-app
// launch context is.

function isStandaloneLaunch() {
  if (window.navigator.standalone === true) return true; // iOS Safari legacy flag
  return ['standalone', 'fullscreen', 'minimal-ui'].some(
    (mode) => window.matchMedia(`(display-mode: ${mode})`).matches
  );
}

function isTouchPrimaryDevice() {
  return window.matchMedia('(pointer: coarse)').matches
    && window.matchMedia('(hover: none)').matches;
}

export function isMobileRemoteContext() {
  return isStandaloneLaunch() && isTouchPrimaryDevice();
}
