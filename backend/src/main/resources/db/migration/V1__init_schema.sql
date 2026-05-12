CREATE TABLE projects (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    description TEXT,
    environment_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE jmeter_servers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    host TEXT NOT NULL,
    ssh_port INTEGER NOT NULL,
    ssh_username TEXT NOT NULL,
    ssh_auth_type TEXT NOT NULL,
    ssh_password_encrypted TEXT,
    ssh_private_key_encrypted TEXT,
    jmeter_home TEXT NOT NULL,
    script_dir TEXT NOT NULL,
    result_dir TEXT,
    log_dir TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE TABLE project_datasources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL,
    type TEXT NOT NULL,
    name TEXT NOT NULL,
    base_url TEXT NOT NULL,
    database_name TEXT,
    username TEXT,
    password_encrypted TEXT,
    token_encrypted TEXT,
    extra_config_json TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE TABLE test_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    default_save_jtl INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE TABLE test_task_steps (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id INTEGER NOT NULL,
    step_order INTEGER NOT NULL,
    step_name TEXT NOT NULL,
    jmx_file TEXT NOT NULL,
    threads INTEGER NOT NULL,
    duration_seconds INTEGER NOT NULL,
    ramp_up_seconds INTEGER NOT NULL,
    save_jtl INTEGER NOT NULL DEFAULT 0,
    jmeter_args_json TEXT,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (task_id) REFERENCES test_tasks(id)
);

CREATE TABLE test_executions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL,
    task_id INTEGER NOT NULL,
    execution_name TEXT NOT NULL,
    trigger_type TEXT NOT NULL,
    scheduled_at TEXT,
    status TEXT NOT NULL,
    started_at TEXT,
    ended_at TEXT,
    duration_seconds INTEGER,
    current_step_order INTEGER,
    ssh_log TEXT,
    error_message TEXT,
    created_by TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(id),
    FOREIGN KEY (task_id) REFERENCES test_tasks(id)
);

CREATE TABLE test_execution_steps (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    execution_id INTEGER NOT NULL,
    task_step_id INTEGER NOT NULL,
    step_order INTEGER NOT NULL,
    jmx_file TEXT NOT NULL,
    status TEXT NOT NULL,
    started_at TEXT,
    ended_at TEXT,
    duration_seconds INTEGER,
    command TEXT,
    exit_code INTEGER,
    ssh_log TEXT,
    error_message TEXT,
    jtl_path TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (execution_id) REFERENCES test_executions(id),
    FOREIGN KEY (task_step_id) REFERENCES test_task_steps(id)
);

CREATE TABLE test_results (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL,
    execution_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    status TEXT NOT NULL,
    time_range_start TEXT NOT NULL,
    time_range_end TEXT NOT NULL,
    summary_json TEXT,
    analysis_json TEXT,
    created_by TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(id),
    FOREIGN KEY (execution_id) REFERENCES test_executions(id)
);

CREATE TABLE test_result_metrics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    result_id INTEGER NOT NULL,
    source TEXT NOT NULL,
    metric_category TEXT NOT NULL,
    metric_name TEXT NOT NULL,
    target_name TEXT,
    stat_type TEXT NOT NULL,
    value REAL NOT NULL,
    unit TEXT,
    threshold_value REAL,
    threshold_status TEXT,
    extra_tags_json TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (result_id) REFERENCES test_results(id)
);

CREATE TABLE test_reports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL,
    title TEXT NOT NULL,
    report_type TEXT NOT NULL,
    status TEXT NOT NULL,
    content_markdown TEXT,
    content_html TEXT,
    result_ids_json TEXT NOT NULL,
    created_by TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(id)
);
