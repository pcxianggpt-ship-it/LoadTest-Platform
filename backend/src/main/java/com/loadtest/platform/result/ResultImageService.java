package com.loadtest.platform.result;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loadtest.platform.common.NotFoundException;
import com.loadtest.platform.grafana.GrafanaImageClient;
import com.loadtest.platform.grafana.GrafanaImageData;
import com.loadtest.platform.grafana.GrafanaRenderRequest;
import com.loadtest.platform.projectconfig.ProjectDatasource;
import com.loadtest.platform.projectconfig.ProjectDatasourceMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResultImageService {

    private final TestResultMapper testResultMapper;
    private final TestResultImageMapper testResultImageMapper;
    private final ProjectDatasourceMapper projectDatasourceMapper;
    private final GrafanaImageClient grafanaImageClient;
    private final ObjectMapper objectMapper;

    @Value("${loadtest.grafana.image-dir:./data/grafana-images}")
    private String imageDir;

    @Transactional
    public ResultImageResponse exportGrafanaImage(Long resultId, ResultImageRequest request) {
        TestResult result = getResult(resultId);
        ProjectDatasource datasource = datasource(result.getProjectId());
        GrafanaDatasourceConfig config = parseConfig(datasource.getExtraConfigJson(), false);
        String dashboardUid = firstNonBlank(request.getDashboardUid(), config.dashboardUid());
        if (dashboardUid == null) {
            throw new IllegalArgumentException("grafana dashboardUid is required");
        }
        Integer width = request.getWidth() == null ? defaultInt(config.width(), 1200) : request.getWidth();
        Integer height = request.getHeight() == null ? defaultInt(config.height(), 700) : request.getHeight();
        Integer orgId = request.getOrgId() == null ? config.orgId() : request.getOrgId();
        String dashboardSlug = firstNonBlank(request.getDashboardSlug(), config.dashboardSlug(), "_");
        String theme = firstNonBlank(request.getTheme(), config.theme(), "light");
        GrafanaRenderRequest renderRequest = new GrafanaRenderRequest(
                dashboardUid,
                dashboardSlug,
                request.getPanelId(),
                toEpochMillis(result.getTimeRangeStart()),
                toEpochMillis(result.getTimeRangeEnd()),
                orgId,
                width,
                height,
                theme
        );

        GrafanaImageData imageData = grafanaImageClient.renderPanel(datasource, renderRequest);
        TestResultImage image = saveGrafanaImage(result, imageData, "grafana_panel",
                firstNonBlank(request.getTitle(), "Grafana Panel " + request.getPanelId()), dashboardUid,
                dashboardSlug, request.getPanelId(), width, height);
        return ResultImageResponse.from(image);
    }

    public List<ResultImageResponse> exportConfiguredGrafanaImages(Long resultId) {
        TestResult result = getResult(resultId);
        ProjectDatasource datasource = findDatasource(result.getProjectId());
        if (datasource == null) {
            return List.of();
        }
        GrafanaDatasourceConfig config = parseConfig(datasource.getExtraConfigJson(), true);
        if (config.panels() == null || config.panels().isEmpty()) {
            throw new IllegalArgumentException("grafana panels are required");
        }

        List<ResolvedGrafanaPanel> panels = config.panels().stream()
                .map(panel -> resolvePanel(config, panel))
                .toList();
        Set<String> imageTypes = panels.stream()
                .map(ResolvedGrafanaPanel::imageType)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        deleteImagesByTypes(result.getId(), imageTypes);

        return panels.stream()
                .map(panel -> {
                    GrafanaRenderRequest renderRequest = new GrafanaRenderRequest(
                            panel.dashboardUid(),
                            panel.dashboardSlug(),
                            panel.panelId(),
                            toEpochMillis(result.getTimeRangeStart()),
                            toEpochMillis(result.getTimeRangeEnd()),
                            panel.orgId(),
                            panel.width(),
                            panel.height(),
                            panel.theme()
                    );
                    GrafanaImageData imageData = grafanaImageClient.renderPanel(datasource, renderRequest);
                    return ResultImageResponse.from(saveGrafanaImage(result, imageData, panel.imageType(),
                            panel.title(), panel.dashboardUid(), panel.dashboardSlug(), panel.panelId(),
                            panel.width(), panel.height()));
                })
                .toList();
    }

    public List<ResultImageResponse> listImages(Long resultId) {
        if (testResultMapper.selectById(resultId) == null) {
            throw new NotFoundException("result not found");
        }
        return images(resultId).stream().map(ResultImageResponse::from).toList();
    }

    public ImageContent imageContent(Long imageId) {
        TestResultImage image = testResultImageMapper.selectById(imageId);
        if (image == null) {
            throw new NotFoundException("result image not found");
        }
        Path path = rootDir().resolve(image.getFilePath()).normalize();
        if (!path.startsWith(rootDir()) || !Files.isRegularFile(path)) {
            throw new NotFoundException("result image file not found");
        }
        return new ImageContent(new FileSystemResource(path), image.getContentType());
    }

    public void deleteImagesByResult(Long resultId) {
        for (TestResultImage image : images(resultId)) {
            deleteImageFile(image);
        }
        testResultImageMapper.delete(new LambdaQueryWrapper<TestResultImage>()
                .eq(TestResultImage::getResultId, resultId));
    }

    private TestResult getResult(Long resultId) {
        TestResult result = testResultMapper.selectById(resultId);
        if (result == null) {
            throw new NotFoundException("result not found");
        }
        if ("failed".equals(result.getStatus())) {
            throw new IllegalArgumentException("failed result cannot export grafana image");
        }
        return result;
    }

    private List<TestResultImage> images(Long resultId) {
        return testResultImageMapper.selectList(new LambdaQueryWrapper<TestResultImage>()
                .eq(TestResultImage::getResultId, resultId)
                .orderByDesc(TestResultImage::getId));
    }

    private ProjectDatasource datasource(Long projectId) {
        ProjectDatasource datasource = findDatasource(projectId);
        if (datasource == null) {
            throw new NotFoundException("grafana datasource not found");
        }
        return datasource;
    }

    private ProjectDatasource findDatasource(Long projectId) {
        return projectDatasourceMapper.selectOne(new LambdaQueryWrapper<ProjectDatasource>()
                .eq(ProjectDatasource::getProjectId, projectId)
                .eq(ProjectDatasource::getType, "grafana")
                .last("limit 1"));
    }

    private GrafanaDatasourceConfig parseConfig(String json, boolean strict) {
        if (json == null || json.isBlank()) {
            return new GrafanaDatasourceConfig(null, null, null, null, null, null, null);
        }
        try {
            return objectMapper.readValue(json, GrafanaDatasourceConfig.class);
        } catch (JsonProcessingException exception) {
            if (!strict) {
                return new GrafanaDatasourceConfig(null, null, null, null, null, null, null);
            }
            throw new IllegalArgumentException("invalid grafana config json", exception);
        }
    }

    private ResolvedGrafanaPanel resolvePanel(GrafanaDatasourceConfig config, GrafanaPanelConfig panel) {
        String dashboardUid = firstNonBlank(panel.dashboardUid(), config.dashboardUid());
        if (dashboardUid == null) {
            throw new IllegalArgumentException("grafana dashboardUid is required");
        }
        if (panel.panelId() == null) {
            throw new IllegalArgumentException("grafana panelId is required");
        }
        String dashboardSlug = firstNonBlank(panel.dashboardSlug(), config.dashboardSlug(), "_");
        Integer orgId = panel.orgId() == null ? config.orgId() : panel.orgId();
        int width = panel.width() == null ? defaultInt(config.width(), 1200) : panel.width();
        int height = panel.height() == null ? defaultInt(config.height(), 700) : panel.height();
        String theme = firstNonBlank(panel.theme(), config.theme(), "light");
        String imageType = firstNonBlank(panel.imageType(), "grafana_panel");
        String title = firstNonBlank(panel.title(), "Grafana Panel " + panel.panelId());
        return new ResolvedGrafanaPanel(dashboardUid, dashboardSlug, panel.panelId(), orgId, width, height,
                theme, imageType, title);
    }

    private TestResultImage saveGrafanaImage(
            TestResult result,
            GrafanaImageData imageData,
            String imageType,
            String title,
            String dashboardUid,
            String dashboardSlug,
            Integer panelId,
            Integer width,
            Integer height
    ) {
        String now = OffsetDateTime.now().toString();
        String extension = extension(imageData.contentType());
        Path relativePath = Path.of(String.valueOf(result.getProjectId()), String.valueOf(result.getId()),
                "panel-" + panelId + "-" + System.currentTimeMillis() + extension);
        Path absolutePath = rootDir().resolve(relativePath).normalize();
        try {
            Files.createDirectories(absolutePath.getParent());
            Files.write(absolutePath, imageData.content());
        } catch (IOException exception) {
            throw new IllegalStateException("failed to save grafana image", exception);
        }

        TestResultImage image = new TestResultImage();
        image.setResultId(result.getId());
        image.setProjectId(result.getProjectId());
        image.setImageType(imageType);
        image.setTitle(title);
        image.setDashboardUid(dashboardUid);
        image.setDashboardSlug(dashboardSlug);
        image.setPanelId(panelId);
        image.setGrafanaUrl(imageData.renderUrl());
        image.setFilePath(relativePath.toString().replace('\\', '/'));
        image.setContentType(imageData.contentType());
        image.setFileSize((long) imageData.content().length);
        image.setWidth(width);
        image.setHeight(height);
        image.setCreatedAt(now);
        testResultImageMapper.insert(image);
        return image;
    }

    private void deleteImagesByTypes(Long resultId, Set<String> imageTypes) {
        if (imageTypes.isEmpty()) {
            return;
        }
        List<TestResultImage> oldImages = testResultImageMapper.selectList(new LambdaQueryWrapper<TestResultImage>()
                .eq(TestResultImage::getResultId, resultId)
                .in(TestResultImage::getImageType, imageTypes));
        for (TestResultImage image : oldImages) {
            deleteImageFile(image);
        }
        testResultImageMapper.delete(new LambdaQueryWrapper<TestResultImage>()
                .eq(TestResultImage::getResultId, resultId)
                .in(TestResultImage::getImageType, imageTypes));
    }

    private String toEpochMillis(String time) {
        return String.valueOf(OffsetDateTime.parse(time).toInstant().toEpochMilli());
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String extension(String contentType) {
        if (contentType != null && contentType.toLowerCase().contains("jpeg")) {
            return ".jpg";
        }
        if (contentType != null && contentType.toLowerCase().contains("webp")) {
            return ".webp";
        }
        return ".png";
    }

    private Path rootDir() {
        return Path.of(imageDir).toAbsolutePath().normalize();
    }

    private void deleteImageFile(TestResultImage image) {
        Path path = rootDir().resolve(image.getFilePath()).normalize();
        if (!path.startsWith(rootDir())) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private record GrafanaDatasourceConfig(
            String dashboardUid,
            String dashboardSlug,
            Integer orgId,
            Integer width,
            Integer height,
            String theme,
            List<GrafanaPanelConfig> panels
    ) {
    }

    private record GrafanaPanelConfig(
            String title,
            String dashboardUid,
            String dashboardSlug,
            Integer panelId,
            Integer orgId,
            Integer width,
            Integer height,
            String theme,
            String imageType
    ) {
    }

    private record ResolvedGrafanaPanel(
            String dashboardUid,
            String dashboardSlug,
            Integer panelId,
            Integer orgId,
            Integer width,
            Integer height,
            String theme,
            String imageType,
            String title
    ) {
    }

    public record ImageContent(Resource resource, String contentType) {
    }
}
