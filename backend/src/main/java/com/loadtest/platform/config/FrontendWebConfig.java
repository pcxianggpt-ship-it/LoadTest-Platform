package com.loadtest.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class FrontendWebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String path : new String[] {
                "/",
                "/projects/{projectId}",
                "/projects/{projectId}/tasks/new",
                "/projects/{projectId}/executions",
                "/tasks",
                "/tasks/new",
                "/tasks/{taskId}/edit",
                "/cleanup",
                "/executions",
                "/results",
                "/results/{resultId}",
                "/reports",
                "/reports/{reportId}"
        }) {
            registry.addViewController(path).setViewName("forward:/index.html");
        }
    }
}
