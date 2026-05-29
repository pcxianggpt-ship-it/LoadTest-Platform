package com.loadtest.platform.cleanup;

public interface CleanupSqlExecutor {

    int execute(BusinessDatabase database, String sql);
}
