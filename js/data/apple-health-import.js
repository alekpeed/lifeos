// Apple Health export parser -- a real, publicly documented XML schema, no
// OAuth or live API involved. Garmin/Fitbit "clean APIs where they exist"
// (the other half of the original Health-device-ingestion idea) would need
// their own research-first pass before building, same as Spotify/YouTube
// before them -- not attempted here, this is Apple Health only.
//
// Apple's export is many fine-grained <Record>/<Workout> elements (one per
// sample); this aggregates them down to this app's one-row-per-day
// HealthLogs shape (sleepHours, workoutType, workoutMinutes, waterOz,
// weight). A ONE-TIME manual import, same shape as the app's existing JSON
// backup import -- not a live sync (Apple Health has no such API for
// third-party web apps to poll anyway).

import { unzipSync, strFromU8 } from '../../vendor/fflate/fflate.module.js';

const WORKOUT_TYPE_LABELS = {
  HKWorkoutActivityTypeRunning: 'Running',
  HKWorkoutActivityTypeWalking: 'Walking',
  HKWorkoutActivityTypeCycling: 'Cycling',
  HKWorkoutActivityTypeSwimming: 'Swimming',
  HKWorkoutActivityTypeYoga: 'Yoga',
  HKWorkoutActivityTypeFunctionalStrengthTraining: 'Strength training',
  HKWorkoutActivityTypeTraditionalStrengthTraining: 'Strength training',
  HKWorkoutActivityTypeHiking: 'Hiking',
  HKWorkoutActivityTypeElliptical: 'Elliptical',
  HKWorkoutActivityTypeRowing: 'Rowing',
  HKWorkoutActivityTypeHighIntensityIntervalTraining: 'HIIT',
  HKWorkoutActivityTypeCoreTraining: 'Core training',
  HKWorkoutActivityTypeMixedCardio: 'Cardio',
};

function friendlyWorkoutType(raw) {
  if (WORKOUT_TYPE_LABELS[raw]) return WORKOUT_TYPE_LABELS[raw];
  const stripped = (raw || '').replace(/^HKWorkoutActivityType/, '');
  return stripped ? stripped.replace(/([a-z])([A-Z])/g, '$1 $2') : 'Workout';
}

async function extractXmlText(file) {
  const isZip = file.name.toLowerCase().endsWith('.zip') || file.type === 'application/zip';
  if (!isZip) return file.text();
  const buf = new Uint8Array(await file.arrayBuffer());
  const files = unzipSync(buf, { filter: (f) => f.name.endsWith('export.xml') });
  const entry = Object.keys(files).find((name) => name.endsWith('export.xml'));
  if (!entry) throw new Error('Could not find export.xml inside that zip -- make sure it\'s an unmodified Apple Health export.');
  return strFromU8(files[entry]);
}

// Apple's dates look like "2026-06-01 07:32:11 -0700" -- Date parses this
// fine. Converts to a plain calendar date via the same UTC-via-toISOString
// convention already used app-wide (todayStr() etc.); near-midnight
// samples can land on the adjacent UTC day, a known minor imprecision this
// app already accepts elsewhere rather than pulling in a timezone library.
function localDateOf(appleDateStr) {
  const d = new Date(appleDateStr);
  return Number.isNaN(d.getTime()) ? null : d.toISOString().slice(0, 10);
}

// Parses an Apple Health export (a raw export.xml File, or the .zip
// containing it) into one row per calendar day. Rows with nothing at all
// extracted are dropped rather than returned as empty placeholders.
export async function parseAppleHealthExport(file, { onStatus } = {}) {
  onStatus?.('Reading file…');
  const xmlText = await extractXmlText(file);
  onStatus?.('Parsing (large exports can take a moment)…');
  const doc = new DOMParser().parseFromString(xmlText, 'application/xml');
  if (doc.querySelector('parsererror')) {
    throw new Error('Could not parse this as Apple Health XML -- make sure it\'s an unmodified export.xml (or the .zip containing it).');
  }

  const byDate = new Map();
  const dayFor = (date) => {
    if (!byDate.has(date)) byDate.set(date, { date, sleepMinutes: 0, workoutMinutes: 0, workoutTypes: new Set(), waterMl: 0, weightKg: null, weightAt: null });
    return byDate.get(date);
  };

  // Sleep: only time actually asleep counts (both the older single
  // "Asleep" value and the newer stage-specific AsleepCore/Deep/REM all
  // match "Asleep"; "InBed" and "Awake" don't).
  for (const rec of doc.querySelectorAll('Record[type="HKCategoryTypeIdentifierSleepAnalysis"]')) {
    if (!/Asleep/i.test(rec.getAttribute('value') || '')) continue;
    const start = new Date(rec.getAttribute('startDate'));
    const end = new Date(rec.getAttribute('endDate'));
    const date = localDateOf(rec.getAttribute('startDate'));
    if (!date || Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) continue;
    dayFor(date).sleepMinutes += (end - start) / 60000;
  }

  // Water: unit is usually "mL" or "fl_oz_us" depending on device locale.
  for (const rec of doc.querySelectorAll('Record[type="HKQuantityTypeIdentifierDietaryWater"]')) {
    const date = localDateOf(rec.getAttribute('startDate'));
    const value = Number(rec.getAttribute('value'));
    if (!date || !Number.isFinite(value)) continue;
    const ml = /fl_?oz/i.test(rec.getAttribute('unit') || '') ? value * 29.5735 : value;
    dayFor(date).waterMl += ml;
  }

  // Body mass: keep the latest reading of each day, unit "kg" or "lb".
  for (const rec of doc.querySelectorAll('Record[type="HKQuantityTypeIdentifierBodyMass"]')) {
    const date = localDateOf(rec.getAttribute('startDate'));
    const value = Number(rec.getAttribute('value'));
    if (!date || !Number.isFinite(value)) continue;
    const kg = /lb/i.test(rec.getAttribute('unit') || '') ? value * 0.453592 : value;
    const entry = dayFor(date);
    const at = rec.getAttribute('startDate');
    if (!entry.weightAt || at > entry.weightAt) { entry.weightKg = kg; entry.weightAt = at; }
  }

  // Workouts: <Workout>, not <Record> -- duration attribute is already in minutes.
  for (const rec of doc.querySelectorAll('Workout')) {
    const date = localDateOf(rec.getAttribute('startDate'));
    if (!date) continue;
    const minutes = Number(rec.getAttribute('duration'));
    const entry = dayFor(date);
    if (Number.isFinite(minutes)) entry.workoutMinutes += minutes;
    entry.workoutTypes.add(friendlyWorkoutType(rec.getAttribute('workoutActivityType')));
  }

  onStatus?.('Summarizing…');
  const days = [...byDate.values()]
    .map((d) => ({
      date: d.date,
      sleepHours: d.sleepMinutes > 0 ? Math.round((d.sleepMinutes / 60) * 10) / 10 : null,
      workoutType: d.workoutTypes.size ? [...d.workoutTypes].join(' + ') : null,
      workoutMinutes: d.workoutMinutes > 0 ? Math.round(d.workoutMinutes) : null,
      waterOz: d.waterMl > 0 ? Math.round((d.waterMl / 29.5735) * 10) / 10 : null,
      weight: d.weightKg != null ? Math.round(d.weightKg * 2.20462 * 10) / 10 : null,
    }))
    .filter((d) => d.sleepHours != null || d.workoutType || d.waterOz != null || d.weight != null)
    .sort((a, b) => a.date.localeCompare(b.date));

  return days;
}
