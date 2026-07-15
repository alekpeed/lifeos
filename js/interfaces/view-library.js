// The canonical module-view library. Every module's view is a pure render
// function `(canvas, ctx, requestRerender) => Promise<void>` that talks to
// the app exclusively through ctx — which is what makes the views
// interface-agnostic even though they were born inside Test Mode: any
// interface can host them inside its own chrome. This file is the one
// shared source of truth for "module id → view", consumed by Test Mode
// (js/interfaces/default/), the spatial interface (js/interfaces/spatial-1/),
// and mobile-1, so the registry's "interfaces never import each other" rule
// holds — all of them import this neutral library instead of reaching into
// one another.
//
// The view files themselves still live under default/views/ (moving 40+
// files would churn every import and service-worker path for zero
// behavioral gain); what's shared is this map, not Test Mode's chrome.

import { renderDashboard } from './default/views/dashboard.js';
import { renderPaper } from './default/views/paper.js';
import { renderSettings } from './default/views/settings.js';
import { renderTasks } from './default/views/tasks.js';
import { renderPlaces } from './default/views/places.js';
import { renderLinks } from './default/views/links.js';
import { renderEducation } from './default/views/education.js';
import { renderBooks } from './default/views/books.js';
import { renderRecipes } from './default/views/recipes.js';
import { renderFinance } from './default/views/finance.js';
import { renderDocuments } from './default/views/documents.js';
import { renderContacts } from './default/views/contacts.js';
import { renderMilestones } from './default/views/milestones.js';
import { renderSearch } from './default/views/search.js';
import { renderTools } from './default/views/tools.js';
import { renderHabits } from './default/views/habits.js';
import { renderHealth } from './default/views/health.js';
import { renderPhotos } from './default/views/photos.js';
import { renderSharebox } from './default/views/sharebox.js';
import { renderMuseum } from './default/views/museum.js';
import { renderTimeCapsules } from './default/views/timecapsules.js';
import { renderCollections } from './default/views/collections.js';
import { renderPacking } from './default/views/packing.js';
import { renderQuartermaster } from './default/views/quartermaster.js';
import { renderSkillTree } from './default/views/skilltree.js';
import { renderEntropy } from './default/views/entropy.js';
import { renderStationCat } from './default/views/stationcat.js';
import { renderGhostDays } from './default/views/ghostdays.js';
import { renderThemeFromPhoto } from './default/views/themefromphoto.js';
import { renderRabbitHoles } from './default/views/rabbitholes.js';
import { renderAlmanac } from './default/views/almanac.js';
import { renderKnowledge } from './default/views/knowledge.js';
import { renderOrrery } from './default/views/orrery.js';
import { renderTimeMachine } from './default/views/timemachine.js';
import { renderQrSync } from './default/views/qrsync.js';
import { renderAssistant } from './default/views/assistant.js';
import { renderIdeas } from './default/views/ideas.js';
import { renderRecall } from './default/views/recall.js';
import { renderNotifications } from './default/views/notifications.js';
import { renderAsk } from './default/views/ask.js';
import { renderBriefing } from './default/views/briefing.js';
import { renderCommand } from './default/views/command.js';

export const VIEWS = {
  dashboard: renderDashboard,
  paper: renderPaper,
  settings: renderSettings,
  tasks: renderTasks,
  places: renderPlaces,
  links: renderLinks,
  education: renderEducation,
  books: renderBooks,
  recipes: renderRecipes,
  finance: renderFinance,
  documents: renderDocuments,
  contacts: renderContacts,
  milestones: renderMilestones,
  search: renderSearch,
  tools: renderTools,
  habits: renderHabits,
  health: renderHealth,
  photos: renderPhotos,
  sharebox: renderSharebox,
  museum: renderMuseum,
  timecapsules: renderTimeCapsules,
  collections: renderCollections,
  packing: renderPacking,
  quartermaster: renderQuartermaster,
  skilltree: renderSkillTree,
  entropy: renderEntropy,
  stationcat: renderStationCat,
  ghostdays: renderGhostDays,
  themefromphoto: renderThemeFromPhoto,
  rabbitholes: renderRabbitHoles,
  almanac: renderAlmanac,
  knowledge: renderKnowledge,
  orrery: renderOrrery,
  timemachine: renderTimeMachine,
  qrsync: renderQrSync,
  assistant: renderAssistant,
  ideas: renderIdeas,
  recall: renderRecall,
  notifications: renderNotifications,
  ask: renderAsk,
  briefing: renderBriefing,
  command: renderCommand,
};
