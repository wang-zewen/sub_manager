package com.submanager.subscriptionmanager.scheduler;

import com.submanager.subscriptionmanager.service.SubscriptionFetchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionUpdateScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionUpdateScheduler.class);

    @Autowired
    private SubscriptionFetchService subscriptionFetchService;

    /**
     * Check and update subscriptions every hour
     */
    @Scheduled(fixedRate = 3600000) // 1 hour = 3600000 milliseconds
    public void updateSubscriptions() {
        logger.info("Running scheduled subscription update task");
        try {
            subscriptionFetchService.updateAllDueSubscriptions();
        } catch (Exception e) {
            logger.error("Error in scheduled subscription update", e);
        }
    }
}
