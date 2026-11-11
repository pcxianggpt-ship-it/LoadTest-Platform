package com.loadtest.platform.projectconfig;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loadtest.platform.common.NotFoundException;
import com.loadtest.platform.project.ProjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
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
    private final ProjectConnectionTester connectionTester;

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
        boolean sameConnection = sameSshConnection(server, request);
        server.setSshPasswordEncrypted(credential(
                request.getSshPasswordEncrypted(), server.getSshPasswordEncrypted(), sameConnection
        ));
        server.setSshPrivateKeyEncrypted(credential(
                request.getSshPrivateKeyEncrypted(), server.getSshPrivateKeyEncrypted(), sameConnection
        ));
        server.setName(request.getName());
        server.setHost(request.getHost());
        server.setSshPort(sshPort(request));
        server.setSshUsername(request.getSshUsername());
        server.setSshAuthType(request.getSshAuthType());
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

    public ProjectConnectionTester.Result testJMeterServer(Long projectId, JMeterServerRequest request) {
        ensureProjectExists(projectId);
        JMeterServer saved = findJMeterServer(projectId);
        String password = credential(
                request.getSshPasswordEncrypted(),
                saved == null ? null : saved.getSshPasswordEncrypted(),
                sameSshConnection(saved, request)
        );
        return connectionTester.testJMeterServer(request, sshPort(request), password);
    }

    public JMeterServerResponse getJMeterServer(Long projectId) {
        ensureProjectExists(projectId);
        JMeterServer server = findJMeterServer(projectId);
        if (server == null) {
            throw new NotFoundException("jmeter server not found");
        }
        return JMeterServerResponse.from(server);
    }

    public List<String> listJmxFiles(Long projectId) {
        ensureProjectExists(projectId);
        JMeterServer server = findJMeterServer(projectId);
        if (server == null) {
            throw new NotFoundException("jmeter server not found");
        }
        Path scriptDir = Path.of(server.getScriptDir());
        if (!Files.isDirectory(scriptDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(scriptDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(fileName -> fileName.toLowerCase().endsWith(".jmx"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException exception) {
            throw new IllegalArgumentException("failed to read jmx script directory");
        }
    }

    @Transactional
    public DatasourceResponse upsertDatasource(
            Long projectId,
            String datasourceType,
            DatasourceRequest request
    ) {
        ensureProjectExists(projectId);
        validateDatasourceType(datasourceType);
        String now = OffsetDateTime.now().toString();
        ProjectDatasource datasource = findDatasource(projectId, datasourceType);
        if (datasource == null) {
            datasource = new ProjectDatasource();
            datasource.setProjectId(projectId);
            datasource.setType(datasourceType);
            datasource.setStatus("active");
            datasource.setCreatedAt(now);
        }
        applyDatasourceRequest(datasource, request);
        datasource.setUpdatedAt(now);
        if (datasource.getId() == null) {
            projectDatasourceMapper.insert(datasource);
        } else {
            projectDatasourceMapper.updateById(datasource);
        }
        return DatasourceResponse.from(datasource);
    }

    public ProjectConnectionTester.Result testDatasource(Long projectId, String datasourceType, DatasourceRequest request) {
        ensureProjectExists(projectId);
        validateDatasourceType(datasourceType);
        ProjectDatasource datasource = "grafana".equals(datasourceType) ? findDatasource(projectId, datasourceType) : null;
        if (datasource == null) {
            datasource = new ProjectDatasource();
        }
        applyDatasourceRequest(datasource, request);
        return connectionTester.testDatasource(datasourceType, datasource);
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

    private void validateDatasourceType(String datasourceType) {
        if (!DATASOURCE_TYPES.contains(datasourceType)) {
            throw new IllegalArgumentException("unsupported datasource type");
        }
    }

    private void applyDatasourceRequest(ProjectDatasource datasource, DatasourceRequest request) {
        boolean sameConnection = Objects.equals(datasource.getBaseUrl(), request.getBaseUrl())
                && Objects.toString(datasource.getUsername(), "").equals(Objects.toString(request.getUsername(), ""));
        datasource.setPasswordEncrypted(credential(
                request.getPasswordEncrypted(), datasource.getPasswordEncrypted(), sameConnection
        ));
        datasource.setTokenEncrypted(credential(
                request.getTokenEncrypted(), datasource.getTokenEncrypted(), sameConnection
        ));
        datasource.setName(request.getName());
        datasource.setBaseUrl(request.getBaseUrl());
        datasource.setDatabaseName(request.getDatabaseName());
        datasource.setUsername(request.getUsername());
        datasource.setExtraConfigJson(request.getExtraConfigJson());
    }

    private boolean sameSshConnection(JMeterServer server, JMeterServerRequest request) {
        return server != null
                && Objects.equals(server.getHost(), request.getHost())
                && Objects.equals(server.getSshPort(), sshPort(request))
                && Objects.equals(server.getSshUsername(), request.getSshUsername())
                && Objects.equals(server.getSshAuthType(), request.getSshAuthType());
    }

    private int sshPort(JMeterServerRequest request) {
        return request.getSshPort() == null ? 22 : request.getSshPort();
    }

    private String credential(String supplied, String saved, boolean sameConnection) {
        return supplied != null ? supplied : sameConnection ? saved : "";
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
