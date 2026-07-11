use tauri_plugin_sql::{Migration, MigrationKind};

/// DB name used with the sql plugin's sqlite: connection string. Schema lives in
/// ../migrations (plan §4); each file here is one forward-only migration, run in order,
/// tracked in the plugin's own migration-history table.
const DB_NAME: &str = "polyglotai.db";

fn migrations() -> Vec<Migration> {
    vec![
        Migration {
            version: 1,
            description: "initial_schema",
            sql: include_str!("../migrations/0001_initial.sql"),
            kind: MigrationKind::Up,
        },
        Migration {
            version: 2,
            description: "seed_feature_flags",
            sql: include_str!("../migrations/0002_seed_feature_flags.sql"),
            kind: MigrationKind::Up,
        },
        Migration {
            version: 3,
            description: "pronunciation_rules",
            sql: include_str!("../migrations/0003_pronunciation_rules.sql"),
            kind: MigrationKind::Up,
        },
        Migration {
            version: 4,
            description: "grammar_ladders",
            sql: include_str!("../migrations/0004_grammar_ladders.sql"),
            kind: MigrationKind::Up,
        },
    ]
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(
            tauri_plugin_sql::Builder::default()
                .add_migrations(&format!("sqlite:{DB_NAME}"), migrations())
                .build(),
        )
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
