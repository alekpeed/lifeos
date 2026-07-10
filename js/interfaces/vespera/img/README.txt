Drop hub.png here (the generated Grand Concourse art). The interface
works without it -- a CSS starfield paints until this image exists.

Labels are drawn by the app on a fixed grid (see DISTRICTS in
../index.js), not traced from the image -- so the art is free-form
atmosphere. The one thing worth asking an image tool for is keeping the
zones below clear of anything you need to read, so the code-drawn boxes
don't sit on top of painted text. Canvas is 16:9 (e.g. 1672x941).

Left column, x: 2%-26% of width. Right column, x: 74%-98% of width.
Both columns, four rows, y (top-bottom) of height:
  Row 1: 12%-22%
  Row 2: 34.5%-44.5%
  Row 3: 57%-67%
  Row 4: 79.5%-89.5%
Bottom-center, x: 38%-62%, y: 89%-97%: reserved for a UI element with no
painted counterpart (Station News) -- keep clear of key detail too.

Suggested prompt for an image tool, pasting the current hub.png as a
style/composition reference:

  "Using this image as a style and composition reference, regenerate
  the same orbital-station concourse -- same lighting, palette, mood,
  camera angle -- but remove all text labels and sign panels entirely.
  Leave those eight wall areas as plain architecture (no floating
  text, no glowing sign plates, no icons) so labels can be added in
  software afterward: two columns of four evenly-stacked zones, left
  column at 2%-26% of image width, right column at 74%-98%, each row
  spanning y 12%-22%, 34.5%-44.5%, 57%-67%, and 79.5%-89.5% of image
  height. Also keep the bottom-center area (38%-62% width, 89%-97%
  height) free of important detail. Keep the central atrium, walkways,
  and figures unchanged."
