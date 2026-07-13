// The one place interfaces are wired into the app. Each import registers
// itself with the registry as a side effect. To add a new interface:
// create js/interfaces/<id>/ implementing the contract in registry.js,
// then add one import line here. Nothing else in the app changes.

import './default/index.js';
import './vespera/index.js';
import './mobile-1/index.js';
