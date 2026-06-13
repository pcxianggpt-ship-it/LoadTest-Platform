package com.loadtest.platform.grafana;

import com.loadtest.platform.projectconfig.ProjectDatasource;

public interface GrafanaImageClient {

    GrafanaImageData renderPanel(ProjectDatasource datasource, GrafanaRenderRequest request);
}
