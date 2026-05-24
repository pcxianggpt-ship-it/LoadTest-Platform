package com.loadtest.platform.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loadtest.platform.cleanup.DeletionService;
import com.loadtest.platform.common.NotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final DeletionService deletionService;

    @Transactional
    public ProjectResponse createProject(ProjectCreateRequest request) {
        String now = OffsetDateTime.now().toString();
        Project project = new Project();
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setEnvironmentName(request.getEnvironmentName());
        project.setStatus("active");
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        projectMapper.insert(project);
        return ProjectResponse.from(project);
    }

    public List<ProjectResponse> listProjects() {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .orderByDesc(Project::getId);
        return projectMapper.selectList(wrapper).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    public ProjectResponse getProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new NotFoundException("project not found");
        }
        return ProjectResponse.from(project);
    }

    @Transactional
    public void deleteProject(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new NotFoundException("project not found");
        }
        deletionService.deleteProject(projectId);
    }
}
