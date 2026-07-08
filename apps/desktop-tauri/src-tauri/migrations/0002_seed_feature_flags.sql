-- Seeds the MVP default feature flags — plan Appendix. All flaggable per spec §8.
INSERT INTO feature_flags (key, enabled, updated_at) VALUES
  ('ai_conversation', 1, '1970-01-01T00:00:00Z'),
  ('slang_mode', 1, '1970-01-01T00:00:00Z'),
  ('profanity_explanations', 1, '1970-01-01T00:00:00Z'),
  ('pronunciation_recording', 1, '1970-01-01T00:00:00Z'),
  ('conversation_logging', 0, '1970-01-01T00:00:00Z'),
  ('teacher_dashboard', 0, '1970-01-01T00:00:00Z'),
  ('cloud_sync', 0, '1970-01-01T00:00:00Z'),
  ('billing', 0, '1970-01-01T00:00:00Z'),
  ('experimental_packs', 0, '1970-01-01T00:00:00Z');
