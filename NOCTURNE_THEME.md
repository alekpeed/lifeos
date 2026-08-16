# Nocturne Theme

Nocturne is a first-class LifeOS graphical interface and home-screen theme. It replaces conventional launcher tiles with a single integrated environment: the room itself is navigation.

## Core principle

Every primary domain is represented by a physical object or spatial zone in the artwork. The object is the tap target. Do not place a separate button, pill, card, circle, or icon beside it merely to make it clickable.

Primary domains, using the canonical LifeOS labels:

- Operations
- Archive
- Logistics
- Discovery
- Management
- Intelligence
- People
- System

## Home-screen spatial mapping

- Archive: library / bookshelves.
- Discovery: celestial chart / telescope area.
- Logistics: illuminated map table.
- Management: central city / planning model.
- Operations: working desk / control surface.
- Intelligence: analysis desk / reading area.
- People: lounge / garden seating area.
- System: illuminated floor pool / system core.

The normalized geometry is authoritative and lives in `native/composeApp/src/commonMain/resources/nocturne_home_map.json`. `NocturneHome.kt` mirrors that geometry for runtime hit testing.

## Visual language

Nocturne is nocturnal, cinematic, warm, tactile, and architectural rather than conventionally technological. The environment should feel like an inhabited observatory-library or private command room, not a spaceship dashboard.

Use dark stone, dark wood, aged brass, leather, glass, warm practical lamps, selective moonlight, deep blue-black exterior views, and restrained gold highlights. Reflective surfaces may be used to create depth, but avoid glossy generic app-card styling.

The screen should remain visually legible at phone size. Important interactive objects must have distinct silhouettes and enough local contrast to be recognizable without explicit button chrome.

## Palette

- Void: `#05070A`
- Panel: `#10151A`
- Ivory text: `#F2E7D2`
- Gold: `#D6A84A`
- Brass: `#9D7132`
- Night blue: `#0C1A27`
- Muted text: `#9B968D`

Gold is an accent, not a fill color. Large fields should remain dark and materially textured.

## Typography

Titles may use a classical high-contrast serif. Functional labels and dynamic data should use a restrained, highly legible sans serif. Avoid excessive all-caps text except for compact section headers.

The artwork may contain the domain labels, but the interactive implementation must never depend on text pixels for navigation.

## Motion

Motion should be ambient and slow: lamp flicker, subtle reflected light, distant city light variation, faint water shimmer, or minor atmospheric movement. Do not use constant pulsing UI controls. Navigation feedback can use a brief local glow or tonal lift over the actual tapped object.

## Dynamic overlays

Live values such as completion percentage, tasks due, sync state, current focus, notifications, date/time, or other changing LifeOS information may be overlaid on reserved surfaces in the environment. These overlays should look embedded in the scene and should not interfere with the eight primary tap regions.

All variable content must remain separate from the underlying art so it can update without regenerating the image.

## Android presentation

The Nocturne home is immersive and edge-to-edge. Do not bake Android status-bar or navigation-bar graphics into the artwork. Runtime sizing respects physical cutouts while suppressing normal system chrome while the home interface is active.

The reference composition is portrait and intended for Pixel 10 Pro XL-class screens, but hit regions use normalized coordinates so the layout scales to other Android phones.

## Navigation behavior

Tapping a domain object opens that domain's canonical LifeOS module list. Selecting a module routes through the existing `Nav` and `Interfaces` layers; module data and business logic are not duplicated by the theme.

If the Nocturne binary artwork is unavailable in a build, LifeOS falls back to the functional launcher rather than becoming unusable.

## Implementation files

- `native/composeApp/src/commonMain/kotlin/com/alekpeed/lifeos/interfaces/nocturne/NocturneHome.kt`
- `native/composeApp/src/commonMain/resources/nocturne_home_map.json`
- Android image asset: `native/composeApp/src/androidMain/assets/nocturne-home.png`

Nocturne should remain visually swappable: artwork can be refined later without changing LifeOS module logic, provided the semantic object layout remains consistent or the normalized map is updated with it.
