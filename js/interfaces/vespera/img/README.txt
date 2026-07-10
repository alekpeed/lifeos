Drop hub.png here (the generated Grand Concourse art). The interface
works without it -- a CSS starfield paints until this image exists.

The real, final district names and taglines are painted directly into the
signage in the art -- the app does not draw its own label boxes over it.
Each plaque in the hub is an invisible click target clipped (via CSS
clip-path) to that sign's real shape, so it must be commissioned with a
red-corner-marker method so those shapes can be measured precisely.

## Commissioning brief for a new/replacement hub.png

Ask the image tool for the concourse art WITH the real signage already
baked in -- correct final text, correct perspective, correct lighting and
material for each of the 9 signs (8 district doors + Station News) -- plus
small solid red squares marking each sign's 4 real corners, so the
positions can be extracted programmatically afterward and the markers
inpainted back out before shipping.

Suggested prompt, pasting the current hub.png as a style/composition
reference and listing the real district names/taglines (pull these from
DISTRICTS in ../index.js so the art never drifts from what's actually
built):

  "Using this image as a style and composition reference, regenerate the
  same orbital-station concourse -- same lighting, palette, mood, camera
  angle. Render each of the 9 signs with its real final text fully
  integrated into the scene (correct perspective, correct material,
  correct lighting/glow for that sign's position) -- not a flat overlay.
  [list each district's name + tagline + icon here]. Then, additionally,
  mark each sign's 4 real corners with a small solid red square (#FF0000,
  about 10px, flat color, no glow, no anti-aliasing, no gradient) placed
  exactly at that corner -- 36 markers total (9 signs x 4 corners). Keep
  the central atrium, walkways, and figures unchanged."

## Extraction process (re-run this for any new hub.png)

1. Detect markers: threshold the image for near-pure red pixels
   (r>180, g<90, b<90), then connected-component label them (e.g.
   scipy.ndimage.label), filtering to blobs of roughly 15-400px area to
   reject noise. Expect exactly 36 candidates (9 signs x 4 corners) --
   if the count is off, the color threshold or size filter needs
   adjusting for that image before continuing.
2. Group markers into signs: don't trust naive distance/k-means
   clustering on raw (x,y) -- signs are wide/short, so within-sign
   diagonal distances can rival between-sign gaps. Instead band
   candidates by x-coordinate (signs are well-separated into left-
   column / right-column / bottom-center regions), then within each
   band sort by y and pair consecutive points. Visually confirm the
   grouping by rendering labeled markers on the image before trusting
   it -- pure numeric reasoning is not reliable enough on its own.
3. Compute each sign's quadrilateral corners (TL/TR/BR/BL, which may be
   a tilted parallelogram, not axis-aligned). Convert to CSS values:
   `cx`/`cy`/`w`/`h` as percentages of the full image (the bounding
   box), plus a `clip-path: polygon(...)` expressed as percentages
   RELATIVE TO THAT BOUNDING BOX (not the full image), tracing the
   corners in TL -> TR -> BR -> BL order. Verify the corner order
   produces a valid, non-self-intersecting polygon (e.g. via the
   shoelace formula) before using it.
4. Inpaint the markers out of the shipped image: dilate the marker mask
   a couple of iterations, then replace each marker's padded bounding
   box with the median color of its surrounding non-marker ring.
   Verify visually via zoomed crops of a few signs -- no residue or
   halo should remain.
5. Update the `hotspot: { cx, cy, w, h, clip }` value for each district
   in DISTRICTS (../index.js) with the measured numbers. Station News
   has no painted counterpart and keeps `hotspot: null`.
6. Bump service-worker.js's CACHE_VERSION, then actually look at a
   screenshot of the rendered hub (rest state: no visible boxes, just
   the art; hover state: glow traces the real sign shape) before
   shipping -- automated tests passing is not sufficient confirmation
   for a visual change like this.

This whole pipeline is tied to whatever image is currently in hub.png --
a new image needs the process re-run from step 1, not a hand-tweak of the
existing numbers.
