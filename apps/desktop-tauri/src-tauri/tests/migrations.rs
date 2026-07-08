//! Verifies the SQLite migrations in ../migrations apply cleanly to a fresh database and
//! produce the schema described in docs/mvp-implementation-plan.md §4 (plan Milestone A
//! step 3 acceptance criterion: "fresh DB migrates to head").

use sqlx::sqlite::SqlitePoolOptions;
use sqlx::Row;

const MIGRATION_0001: &str = include_str!("../migrations/0001_initial.sql");
const MIGRATION_0002: &str = include_str!("../migrations/0002_seed_feature_flags.sql");

const EXPECTED_TABLES: &[&str] = &[
    "schema_meta",
    "language_packs",
    "learner_profiles",
    "vocabulary_items",
    "grammar_items",
    "real_speech_items",
    "dialogues",
    "lessons",
    "review_items",
    "review_results",
    "conversations",
    "ai_messages",
    "pronunciation_attempts",
    "progress_events",
    "feature_flags",
];

#[tokio::test]
async fn fresh_database_migrates_to_head() {
    let pool = SqlitePoolOptions::new()
        .connect("sqlite::memory:")
        .await
        .expect("open in-memory sqlite db");

    sqlx::raw_sql(MIGRATION_0001)
        .execute(&pool)
        .await
        .expect("migration 0001 should apply cleanly to a fresh db");
    sqlx::raw_sql(MIGRATION_0002)
        .execute(&pool)
        .await
        .expect("migration 0002 should apply cleanly after 0001");

    for table in EXPECTED_TABLES {
        let row = sqlx::query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?")
            .bind(table)
            .fetch_optional(&pool)
            .await
            .expect("query sqlite_master");
        assert!(row.is_some(), "expected table `{table}` to exist after migrating to head");
    }

    let db_version: String = sqlx::query("SELECT value FROM schema_meta WHERE key = 'db_version'")
        .fetch_one(&pool)
        .await
        .expect("schema_meta should have a db_version row")
        .get(0);
    assert_eq!(db_version, "1");

    let flag_count: i64 = sqlx::query("SELECT COUNT(*) FROM feature_flags")
        .fetch_one(&pool)
        .await
        .expect("count feature_flags rows")
        .get(0);
    assert_eq!(flag_count, 9, "expected all 9 MVP default feature flags to be seeded");

    let conversation_logging_enabled: i64 =
        sqlx::query("SELECT enabled FROM feature_flags WHERE key = 'conversation_logging'")
            .fetch_one(&pool)
            .await
            .expect("conversation_logging flag should exist")
            .get(0);
    assert_eq!(
        conversation_logging_enabled, 0,
        "conversation_logging must default to off (plan risk 7)"
    );
}

#[tokio::test]
async fn foreign_keys_prevent_orphaned_content() {
    // A single-connection pool with foreign_keys enabled on every (re)connect — PRAGMAs are
    // per-connection, so this must run on connect rather than once against the pool.
    let pool = SqlitePoolOptions::new()
        .max_connections(1)
        .after_connect(|conn, _meta| {
            Box::pin(async move {
                sqlx::query("PRAGMA foreign_keys = ON").execute(conn).await?;
                Ok(())
            })
        })
        .connect("sqlite::memory:")
        .await
        .expect("open in-memory sqlite db");
    sqlx::raw_sql(MIGRATION_0001).execute(&pool).await.unwrap();

    let result = sqlx::query(
        "INSERT INTO vocabulary_items
           (id, pack_id, item_key, lemma, translation, schema_version, created_at, updated_at)
         VALUES ('v1', 'nonexistent-pack', 'vocab.x', 'x', 'x', 1, '2026-01-01', '2026-01-01')",
    )
    .execute(&pool)
    .await;

    assert!(
        result.is_err(),
        "inserting content against a nonexistent pack_id should violate the FK constraint"
    );
}
