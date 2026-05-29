CREATE TABLE business_databases (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    database_type TEXT NOT NULL,
    jdbc_url TEXT NOT NULL,
    username TEXT,
    password_encrypted TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE TABLE cleanup_plans (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL,
    business_database_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    enabled INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(id),
    FOREIGN KEY (business_database_id) REFERENCES business_databases(id)
);

CREATE TABLE cleanup_plan_sqls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cleanup_plan_id INTEGER NOT NULL,
    step_order INTEGER NOT NULL,
    sql_text TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (cleanup_plan_id) REFERENCES cleanup_plans(id)
);

CREATE TABLE cleanup_runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    execution_id INTEGER NOT NULL,
    cleanup_plan_id INTEGER,
    business_database_id INTEGER,
    status TEXT NOT NULL,
    started_at TEXT NOT NULL,
    ended_at TEXT,
    error_message TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (execution_id) REFERENCES test_executions(id),
    FOREIGN KEY (cleanup_plan_id) REFERENCES cleanup_plans(id),
    FOREIGN KEY (business_database_id) REFERENCES business_databases(id)
);

CREATE TABLE cleanup_run_steps (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cleanup_run_id INTEGER NOT NULL,
    step_order INTEGER NOT NULL,
    sql_text TEXT NOT NULL,
    status TEXT NOT NULL,
    affected_rows INTEGER,
    error_message TEXT,
    started_at TEXT NOT NULL,
    ended_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (cleanup_run_id) REFERENCES cleanup_runs(id)
);

ALTER TABLE test_tasks ADD COLUMN cleanup_plan_id INTEGER;
ALTER TABLE test_executions ADD COLUMN cleanup_status TEXT NOT NULL DEFAULT 'none';
ALTER TABLE test_executions ADD COLUMN cleanup_started_at TEXT;
ALTER TABLE test_executions ADD COLUMN cleanup_ended_at TEXT;
ALTER TABLE test_executions ADD COLUMN cleanup_error_message TEXT;
