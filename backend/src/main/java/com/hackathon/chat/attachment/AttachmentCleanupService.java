package com.hackathon.chat.attachment;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
public class AttachmentCleanupService implements SchedulingConfigurer {

    private final AttachmentService service;
    private final Duration sweepInterval;

    public AttachmentCleanupService(AttachmentService service, AttachmentProperties props) {
        this.service = service;
        this.sweepInterval = props.sweepInterval();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedRateTask(service::sweepOrphans, sweepInterval);
    }
}
