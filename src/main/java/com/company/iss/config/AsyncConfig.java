package com.company.iss.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (exception, method, parameters) -> {
            String message = safeMessage(exception);
            log.error(
                    "[ASYNC] Unhandled async failure method={} exception={} message=\"{}\"",
                    method.getName(),
                    exception.getClass().getSimpleName(),
                    message,
                    sanitizedThrowable(exception, message)
            );
        };
    }

    private Throwable sanitizedThrowable(Throwable exception, String message) {
        RuntimeException sanitized = new RuntimeException(
                exception.getClass().getSimpleName() + ": " + message
        );
        sanitized.setStackTrace(exception.getStackTrace());
        return sanitized;
    }

    private String safeMessage(Throwable exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "No detail available";
        }

        String message = exception.getMessage()
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll(
                        "(?i)(password|passwd|pwd|token|secret|credential)(\\s*[=:]\\s*)[^\\s&,;]+",
                        "$1$2***"
                )
                .replaceAll(
                        "(?i)([?&](?:token|code|key|secret|password)=)[^&\\s]+",
                        "$1***"
                )
                .trim();

        return message.length() <= 240 ? message : message.substring(0, 237) + "...";
    }
}
