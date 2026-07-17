package com.alekpeed.lifeos.ai

// The OpenAI API key baked into this build from the OPENAI_API_KEY build
// environment (a CI secret), or "" when none was provided (local/desktop/PR
// builds). Lets the app ship working out of the box without each user pasting a
// key, while a user-entered key in Settings still takes precedence.
expect fun bakedOpenAiKey(): String
