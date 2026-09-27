package com.ikrai.project.job;

import com.ikrai.project.service.reminder.ReminderService;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Profile("!test")
public class ReminderJob {

    private final ReminderService reminderService;

    public ReminderJob(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(cron = "0 0 8 * * ?", zone = "Asia/Shanghai")
    public void daily() {
        reminderService.scan(LocalDate.now());
    }
}
