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

## District room signage (different method -- red TEXT, not corner markers)

Used for immersive per-district rooms (e.g. img/conservatory.png, the
Conservatory entry room). This supersedes an earlier CSS-projection
attempt (measuring a wall plane and mapping DOM text onto it with a
matrix3d homography) that never looked fully glued no matter how the
quad was tuned -- the fix was to stop trying to fake perspective in CSS
and have the image generator render the real signage directly.

Commissioning: ask the image tool to render the actual title, subtitle,
and every link's name + description directly onto the wall, at whatever
perspective/lighting/material it chooses -- BUT in solid red
(instructed as r255 g0 b0; expect some anti-aliased/darker-red variance
at edges and against certain backgrounds anyway, that's fine). Keep
icons in the same red so they get recolored too. Upload this source
image as consred.png (not conservatory.png -- keep the deployed baseline
untouched until the new one is ready).

Extraction + recolor pipeline (re-run per new consred.png):
1. Threshold for red: r>60 and r > g*1.6 and r > b*1.6 and g<120 and
   b<120 -- hue-based tolerance, not an exact RGB match, so it catches
   the darker/anti-aliased reds. Restrict to the actual signage region
   (e.g. x < 650) to exclude stray red-ish pixels bleeding from
   unrelated neon elsewhere in the scene.
2. Recolor every masked pixel to the target hue (teal ~193deg for rest,
   hot pink ~322deg for hover) in HSV, keeping each pixel's ORIGINAL
   brightness (max channel /255 as the V value) so the neon falloff/
   glow shape is preserved, not flattened to one flat color.
3. Add a bloom: a Gaussian-blurred copy of the mask (sigma ~2 for the
   gentle always-on rest bloom, ~5-6 for a fuzzier hover bloom),
   screen-blended under the sharp recolored text so the glow bleeds
   softly onto the surrounding wall. This is what makes the hover glow
   read as genuinely fuzzy rather than a flat color swap.
4. Base image: apply the teal recolor+bloom to the FULL image and save
   as conservatory.png (or whatever the room's `image` filename is) --
   this is the always-visible rest state, no code-drawn text at all.
5. Hover overlays: apply the pink recolor+bloom, then crop tightly
   around each interactive row (icon + name + description together,
   with padding so the blur isn't clipped) and export as a transparent
   RGBA PNG per row -- alpha = the blurred glow intensity (clipped/
   boosted so the sharp text hits full opacity and the halo fades to
   0), NOT a flat rectangle, so it composites with no visible hard edge.
6. Hover recolor -- IMPORTANT, this went through several wrong turns:
   do NOT bake a separate pink image and composite it over the teal
   base. Pink-over-teal is always a blend (muddy), and forcing hard
   alpha to avoid the blend just gives jagged sticker edges. Instead the
   per-row overlay is an OPAQUE teal crop of the base itself (pixel-
   identical, so invisible at rest), feathered at its rectangle edges
   (RGBA cosine ramp ~22px so the boundary dissolves into the base), and
   hover recolors it to pink with a CSS `filter: hue-rotate(126deg)
   brightness(1.08)` -- rotating the existing teal pixels IN PLACE.
   Because it's a recolor of one opaque layer, not a second translucent
   layer, muddiness is structurally impossible, and the neon glow + anti-
   aliased edges survive because they're the real pixels rotated. No
   saturate() boost (warms the dark wall in the crop and seams its edge)
   and no drop-shadow (glows around the rectangle, not the letters) --
   the text's own baked glow rotates to pink for free.
7. Compute each crop's position as % of the full image (left, top,
   width, height) and wire it into the district's `room.links[id]`
   config in index.js -- position by construction, no rotation or
   perspective math needed since these are pixel-for-pixel crops of the
   same photo the base image came from.
7. Bump service-worker.js's CACHE_VERSION, then actually look at
   screenshots of rest state (clean teal signage, no boxes) and hover
   state (pink fuzzy glow on just that row) at more than one viewport
   shape before shipping.

Copy caveat: whatever text the image generator rendered is now baked
into pixels -- it can drift from the district's real `name`/`tagline`/
`moduleLabel` strings in code (e.g. "AND" vs "&", wording differences)
with no way to correct it except regenerating the art. Worth a glance
at district name/tagline consistency before commissioning a new room.
