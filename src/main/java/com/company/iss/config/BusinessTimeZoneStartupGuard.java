package com.company.iss.config;

import com.company.iss.dashboard.config.FollowUpSlaProperties;
import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.schedule.repository.ScheduleRepository;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;

@Component
public class BusinessTimeZoneStartupGuard implements SmartInitializingSingleton, InitializingBean {

    static final String BUSINESS_ZONE_VARIABLE = "BUSINESS_TIME_ZONE";
    static final String BUSINESS_ZONE_PROPERTY = "iss.business-time.zone";
    static final String FOLLOW_UP_ZONE_VARIABLE = "INTERVIEW_FOLLOW_UP_TIMESTAMP_ZONE";
    static final String REMINDER_ZONE_VARIABLE = "INTERVIEW_REMINDER_BUSINESS_ZONE";

    private static final String MISSING_ZONE_MESSAGE =
            "Existing schedules use zone-less appointment and business timestamps. Set BUSINESS_TIME_ZONE or "
                    + "iss.business-time.zone explicitly to their historical zone before startup; the Asia/Manila "
                    + "default is safe only when no schedules exist. Existing values are not reinterpreted or "
                    + "backfilled.";

    private final ScheduleRepository scheduleRepository;
    private final Environment environment;
    private final ObjectProvider<BusinessTimeProperties> businessPropertiesProvider;
    private final ObjectProvider<FollowUpSlaProperties> followUpPropertiesProvider;
    private final ObjectProvider<NotificationRuntimeProperties> notificationPropertiesProvider;

    public BusinessTimeZoneStartupGuard(
            ScheduleRepository scheduleRepository,
            Environment environment,
            ObjectProvider<BusinessTimeProperties> businessPropertiesProvider,
            ObjectProvider<FollowUpSlaProperties> followUpPropertiesProvider,
            ObjectProvider<NotificationRuntimeProperties> notificationPropertiesProvider
    ) {
        this.scheduleRepository = scheduleRepository;
        this.environment = environment;
        this.businessPropertiesProvider = businessPropertiesProvider;
        this.followUpPropertiesProvider = followUpPropertiesProvider;
        this.notificationPropertiesProvider = notificationPropertiesProvider;
    }

    @Override
    public void afterPropertiesSet() {
        requireNonBlankWhenPresent(BUSINESS_ZONE_VARIABLE);
        requireNonBlankWhenPresent(FOLLOW_UP_ZONE_VARIABLE);
        requireNonBlankWhenPresent(REMINDER_ZONE_VARIABLE);
        if (hasDirectCanonicalProperty()
                && !StringUtils.hasText(environment.getProperty(BUSINESS_ZONE_PROPERTY))) {
            throw new IllegalStateException(BUSINESS_ZONE_PROPERTY + " must not be blank.");
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        ZoneId canonicalZone = businessPropertiesProvider.getObject().getZone();
        validateEquivalent("follow-up", followUpPropertiesProvider.getObject().getTimestampZone(), canonicalZone);
        validateEquivalent(
                "reminder",
                notificationPropertiesProvider.getObject().getReminders().zoneId(),
                canonicalZone
        );

        if (!hasExplicitCanonicalZone() && scheduleRepository.count() > 0) {
            throw new IllegalStateException(MISSING_ZONE_MESSAGE);
        }
    }

    private void requireNonBlankWhenPresent(String variable) {
        if (environment.containsProperty(variable) && !StringUtils.hasText(environment.getProperty(variable))) {
            throw new IllegalStateException(variable + " must not be blank.");
        }
    }

    private boolean hasExplicitCanonicalZone() {
        return environment.containsProperty(BUSINESS_ZONE_VARIABLE) || hasDirectCanonicalProperty();
    }

    private boolean hasDirectCanonicalProperty() {
        if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            return false;
        }
        for (PropertySource<?> propertySource : configurableEnvironment.getPropertySources()) {
            if ("configurationProperties".equals(propertySource.getName())) {
                continue;
            }
            if (propertySource.getProperty(BUSINESS_ZONE_PROPERTY) == null) {
                continue;
            }
            Origin origin = OriginLookup.getOrigin(propertySource, BUSINESS_ZONE_PROPERTY);
            return !isPackagedApplicationDefault(origin);
        }
        return false;
    }

    private boolean isPackagedApplicationDefault(Origin origin) {
        return origin != null
                && origin.toString().startsWith("class path resource [application.properties]");
    }

    private void validateEquivalent(String use, ZoneId effectiveZone, ZoneId canonicalZone) {
        if (!haveEquivalentBehaviorInSupportedBusinessRecordEra(
                effectiveZone.getRules(), canonicalZone.getRules())) {
            throw new IllegalStateException(
                    "The effective %s time zone (%s) must be equivalent to canonical business time zone (%s) "
                            .formatted(use, effectiveZone, canonicalZone)
                            + "for zone-less historical timestamps."
            );
        }
    }

    static boolean haveEquivalentBehaviorInSupportedBusinessRecordEra(
            ZoneRules historicalRules,
            ZoneRules effectiveRules
    ) {
        if (!historicalRules.getOffset(BusinessTimeProperties.BUSINESS_RECORDS_START)
                .equals(effectiveRules.getOffset(BusinessTimeProperties.BUSINESS_RECORDS_START))) {
            return false;
        }
        return relevantTransitions(historicalRules).equals(relevantTransitions(effectiveRules))
                && historicalRules.getTransitionRules().equals(effectiveRules.getTransitionRules());
    }

    private static List<ZoneOffsetTransition> relevantTransitions(ZoneRules rules) {
        return rules.getTransitions().stream()
                .filter(transition -> !transition.getInstant()
                        .isBefore(BusinessTimeProperties.BUSINESS_RECORDS_START))
                .toList();
    }
}
