package com.hackathon.chat.presence;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
public class PresenceSweeper implements SchedulingConfigurer {

    private final PresenceService service;
    private final Duration sweepInterval;

    public PresenceSweeper(PresenceService service, PresenceProperties props) {
        this.service = service;
        this.sweepInterval = props.sweepInterval();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedRateTask(service::sweep, sweepInterval);
    }
}
