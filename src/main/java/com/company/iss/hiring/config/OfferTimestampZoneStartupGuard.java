package com.company.iss.hiring.config;

import com.company.iss.hiring.repository.HiringDecisionRepository;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.ZoneId;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OfferTimestampZoneStartupGuard implements ApplicationRunner, InitializingBean {

    private static final String TIMESTAMP_ZONE_VARIABLE = "OFFER_RESPONSE_TIMESTAMP_ZONE";
    private static final String BLANK_ZONE_MESSAGE = "OFFER_RESPONSE_TIMESTAMP_ZONE must not be blank.";
    private static final String CONFLICTING_ZONE_MESSAGE =
            "OFFER_RESPONSE_TIMESTAMP_ZONE (%s) does not have the same time-zone rules as the effective "
                    + "iss.hiring.offer-deadline.timestamp-zone (%s). Remove the higher-precedence override "
                    + "or configure it with rules equivalent to the historical zone before startup.";
    private static final String MISSING_ZONE_MESSAGE =
            "Existing hiring decisions use zone-less timestamps. Set OFFER_RESPONSE_TIMESTAMP_ZONE explicitly "
                    + "to the historical zone before startup; the Asia/Manila default is safe only when no hiring "
                    + "decisions exist. If the historical zone has fall-back overlaps, complete an approved "
                    + "timestamp backfill/storage migration before starting.";

    private final HiringDecisionRepository hiringDecisionRepository;
    private final Environment environment;
    private final ObjectProvider<OfferDeadlineProperties> offerDeadlinePropertiesProvider;

    public OfferTimestampZoneStartupGuard(
            HiringDecisionRepository hiringDecisionRepository,
            Environment environment,
            ObjectProvider<OfferDeadlineProperties> offerDeadlinePropertiesProvider
    ) {
        this.hiringDecisionRepository = hiringDecisionRepository;
        this.environment = environment;
        this.offerDeadlinePropertiesProvider = offerDeadlinePropertiesProvider;
    }

    @Override
    public void afterPropertiesSet() {
        if (environment.containsProperty(TIMESTAMP_ZONE_VARIABLE)
                && !StringUtils.hasText(environment.getProperty(TIMESTAMP_ZONE_VARIABLE))) {
            throw new IllegalStateException(BLANK_ZONE_MESSAGE);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (environment.containsProperty(TIMESTAMP_ZONE_VARIABLE)) {
            String configuredHistoricalZone = environment.getProperty(TIMESTAMP_ZONE_VARIABLE);
            if (!StringUtils.hasText(configuredHistoricalZone)) {
                return;
            }

            ZoneId historicalZone = environment.getProperty(TIMESTAMP_ZONE_VARIABLE, ZoneId.class);
            ZoneId effectiveZone = offerDeadlinePropertiesProvider.getObject().getTimestampZone();
            if (!historicalZone.getRules().equals(effectiveZone.getRules())) {
                throw new IllegalStateException(CONFLICTING_ZONE_MESSAGE.formatted(historicalZone, effectiveZone));
            }
            return;
        }

        if (hiringDecisionRepository.count() > 0) {
            throw new IllegalStateException(MISSING_ZONE_MESSAGE);
        }
    }
}
