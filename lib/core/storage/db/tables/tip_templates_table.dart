abstract final class TipTemplatesTable {
  static const tableName = 'tip_templates';

  static const createSql =
      '''
    CREATE TABLE $tableName (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      is_builtin INTEGER NOT NULL DEFAULT 0,
      background_type TEXT NOT NULL,
      background_value TEXT NOT NULL,
      primary_color TEXT NOT NULL,
      title_text TEXT NOT NULL,
      subtitle_text TEXT NOT NULL,
      image_path TEXT,
      show_skip_button INTEGER NOT NULL DEFAULT 1,
      layout_json TEXT,
      sort_order INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL
    )
  ''';
}
