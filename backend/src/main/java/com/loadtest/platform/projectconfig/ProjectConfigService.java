package com.loadtest.platform.projectconfig;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loadtest.platform.common.NotFoundException;
import com.loadtest.platform.project.ProjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectConfigService {

    private static final Set<String> DATASOURCE_TYPES = Set.of("influxdb", "prometheus", "grafana");

    private final ProjectMapper projectMapper;
    private final JMeterServerMapper jMeterServerMapper;
    private final ProjectDatasourceMapper projectDatasourceMapper;

    @Transactional
    public JMeterServerResponse upsertJMeterServer(Long projectId, JMeterServerRequest request) {
        ensureProjectExists(projectId);
        String now = OffsetDateTime.now().toString();
        JMeterServer server = findJMeterServer(projectId);
        if (server == null) {
            server = new JMeterServer();
            server.setProjectId(projectId);
            server.setStatus("active");
            server.setCreatedAt(now);
        }
        server.setName(request.getName());
        server.setHost(request.getHost());
        server.setSshPort(request.getSshPort());
        server.setSshUsername(request.getSshUsername());
        server.setSshAuthType(request.getSshAuthType());
        server.setSshPasswordEncrypted(request.getSshPasswordEncrypted());
        server.setSshPrivateKeyEncrypted(request.getSshPrivateKeyEncrypted());
        server.setJmeterHome(request.getJmeterHome());
        server.setScriptDir(request.getScriptDir());
        server.setResultDir(request.getResultDir());
        server.setLogDir(request.getLogDir());
        server.setUpdatedAt(now);
        if (server.getId() == null) {
            jMeterServerMapper.insert(server);
        } else {
            jMeterServerMapper.updateById(server);
        }
        return JMeterServerResponse.from(server);
    }

    public JMeterServerResponse getJMeterServer(Long projectId) {
        ensureProjectExists(projectId);
        JMeterServer server = findJMeterServer(projectId);
        if (server == null) {
            throw new NotFoundException("jmeter server not found");
        }
        return JMeterServerResponse.from(server);
    }

    @Transactional
    public DatasourceResponse upsertDatasource(
            Long projectId,
            String datasourceType,
            DatasourceRequest request
    ) {
        ensureProjectExists(projectId);
        if (!DATASOURCE_TYPES.contains(datasourceType)) {
            throw new IllegalArgumentException("unsupported datasource type");
        }
        String now = OffsetDateTime.now().toString();
        ProjectDatasource datasource = findDatasource(projectId, datasourceType);
        if (datasource == null) {
            datasource = new ProjectDatasource();
            datasource.setProjectId(projectId);
            datasource.setType(datasourceType);
            datasource.setStatus("active");
            datasource.setCreatedAt(now);
        }
        datasource.setName(request.getName());
        datasource.setBaseUrl(request.getBaseUrl());
        datasource.setDatabaseName(request.getDatabaseName());
        datasource.setUsername(request.getUsername());
        datasource.setPasswordEncrypted(request.getPasswordEncrypted());
        datasource.setTokenEncrypted(request.getTokenEncrypted());
        datasource.setExtraConfigJson(request.getExtraConfigJson());
        datasource.setUpdatedAt(now);
        if (datasource.getId() == null) {
            projectDatasourceMapper.insert(datasource);
        } else {
            projectDatasourceMapper.updateById(datasource);
        }
        return DatasourceResponse.from(datasource);
    }

    public List<DatasourceResponse> listDatasources(Long projectId) {
        ensureProjectExists(projectId);
        LambdaQueryWrapper<ProjectDatasource> wrapper = new LambdaQueryWrapper<ProjectDatasource>()
                .eq(ProjectDatasource::getProjectId, projectId)
                .orderByAsc(ProjectDatasource::getId);
        return projectDatasourceMapper.selectList(wrapper).stream()
                .map(DatasourceResponse::from)
                .toList();
    }

    private void ensureProjectExists(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new NotFoundException("project not found");
        }
    }

    private JMeterServer findJMeterServer(Long projectId) {
        LambdaQueryWrapper<JMeterServer> wrapper = new LambdaQueryWrapper<JMeterServer>()
                .eq(JMeterServer::getProjectId, projectId)
                .last("limit 1");
        return jMeterServerMapper.selectOne(wrapper);
    }

    private ProjectDatasource findDatasource(Long projectId, String datasourceType) {
        LambdaQueryWrapper<ProjectDatasource> wrapper =
                new LambdaQueryWrapper<ProjectDatasource>()
                        .eq(ProjectDatasource::getProjectId, projectId)
                        .eq(ProjectDatasource::getType, datasourceType)
                        .last("limit 1");
        return projectDatasourceMapper.selectOne(wrapper);
    }
}
