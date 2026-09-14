package com.company.iss.hiring.config;

import com.company.iss.hiring.repository.HiringDecisionRepository;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;

@Component
public class OfferTimestampZoneStartupGuard implements SmartInitializingSingleton, InitializingBean {

    private static final String TIMESTAMP_ZONE_VARIABLE = "OFFER_RESPONSE_TIMESTAMP_ZONE";
    private static final String BLANK_ZONE_MESSAGE = "OFFER_RESPONSE_TIMESTAMP_ZONE must not be blank.";
    private static final String CONFLICTING_ZONE_MESSAGE =
            "OFFER_RESPONSE_TIMESTAMP_ZONE (%s) does not have equivalent time-zone behavior from "
                    + "2026-01-01T00:00:00Z onward as the effective "
                    + "iss.hiring.offer-deadline.timestamp-zone (%s). Remove the higher-precedence override "
                    + "or configure it with future behavior equivalent to the historical zone before startup.";
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
    public void afterSingletonsInstantiated() {
        if (environment.containsProperty(TIMESTAMP_ZONE_VARIABLE)) {
            String configuredHistoricalZone = environment.getProperty(TIMESTAMP_ZONE_VARIABLE);
            if (!StringUtils.hasText(configuredHistoricalZone)) {
                return;
            }

            ZoneId historicalZone = environment.getProperty(TIMESTAMP_ZONE_VARIABLE, ZoneId.class);
            ZoneId effectiveZone = offerDeadlinePropertiesProvider.getObject().getTimestampZone();
            if (!haveEquivalentBehaviorInSupportedHiringRecordEra(
                    historicalZone.getRules(),
                    effectiveZone.getRules()
            )) {
                throw new IllegalStateException(CONFLICTING_ZONE_MESSAGE.formatted(historicalZone, effectiveZone));
            }
            return;
        }

        if (hiringDecisionRepository.count() > 0) {
            throw new IllegalStateException(MISSING_ZONE_MESSAGE);
        }
    }

    static boolean haveEquivalentBehaviorInSupportedHiringRecordEra(
            ZoneRules historicalRules,
            ZoneRules effectiveRules
    ) {
        if (!historicalRules.getOffset(OfferDeadlineProperties.HIRING_RECORDS_START)
                .equals(effectiveRules.getOffset(OfferDeadlineProperties.HIRING_RECORDS_START))) {
            return false;
        }

        return relevantTransitions(historicalRules).equals(relevantTransitions(effectiveRules))
                && historicalRules.getTransitionRules().equals(effectiveRules.getTransitionRules());
    }

    private static List<ZoneOffsetTransition> relevantTransitions(ZoneRules rules) {
        return rules.getTransitions().stream()
                .filter(transition -> !transition.getInstant()
                        .isBefore(OfferDeadlineProperties.HIRING_RECORDS_START))
                .toList();
    }
}
