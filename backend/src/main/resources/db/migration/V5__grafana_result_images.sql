CREATE TABLE test_result_images (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    result_id INTEGER NOT NULL,
    project_id INTEGER NOT NULL,
    image_type TEXT NOT NULL,
    title TEXT NOT NULL,
    dashboard_uid TEXT NOT NULL,
    dashboard_slug TEXT,
    panel_id INTEGER NOT NULL,
    grafana_url TEXT NOT NULL,
    file_path TEXT NOT NULL,
    content_type TEXT NOT NULL,
    file_size INTEGER NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (result_id) REFERENCES test_results(id),
    FOREIGN KEY (project_id) REFERENCES projects(id)
);
