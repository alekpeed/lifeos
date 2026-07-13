// Two separate device-context questions, both used by shell.js:
//
// isTouchPrimaryDevice() -- "is this a phone/touch-first device," full
// stop, regardless of install state. A browser tab counts. Used to decide
// interface SUITABILITY: Vespera's spatial hub genuinely doesn't work on a
// small touchscreen, installed or not, so anything touch-primary falls
// back to the plain Equator interface instead (see vespera/index.js's
// `touchSafe: false`) -- still the FULL 43-module app, just not Vespera.
//
// isMobileRemoteContext() -- "is this the installed mobile remote"
// specifically (see MOBILE_INTERFACES_SPEC.md / PROJECT_SPEC.md's "Device
// philosophy" section), a strictly narrower question used to decide module
// SCOPE (the curated ~21-module set vs. the full app). Two signals, both
// required:
//   1. "Installed and launched as an app" -- display-mode is standalone/
//      fullscreen/minimal-ui (or iOS Safari's older navigator.standalone
//      flag), not a normal browser tab with URL bar and back button.
//   2. isTouchPrimaryDevice() above.
// Standalone alone isn't enough: installing the PWA on a desktop (a
// perfectly normal, encouraged PWA behavior) also reports display-mode:
// standalone, and would wrongly trigger the curated module set if that
// were the only check. A regular browser tab -- on any device, any width --
// deliberately keeps the full module list even on a touch-primary phone;
// only the installed-app launch context narrows it. This is the
// escape hatch back to full functionality from a phone that isn't set up
// as the dedicated on-the-go remote.

function isStandaloneLaunch() {
  if (window.navigator.standalone === true) return true; // iOS Safari legacy flag
  return ['standalone', 'fullscreen', 'minimal-ui'].some(
    (mode) => window.matchMedia(`(display-mode: ${mode})`).matches
  );
}

export function isTouchPrimaryDevice() {
  return window.matchMedia('(pointer: coarse)').matches
    && window.matchMedia('(hover: none)').matches;
}

export function isMobileRemoteContext() {
  return isStandaloneLaunch() && isTouchPrimaryDevice();
}
