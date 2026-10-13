package com.gabrielpacheco.atendente.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabrielpacheco.atendente.adapter.in.http.BodySizeLimitFilter;
import com.gabrielpacheco.atendente.adapter.in.http.ProblemResponses;
import com.gabrielpacheco.atendente.adapter.in.http.RateLimiter;
import com.gabrielpacheco.atendente.adapter.in.http.SecurityHeadersFilter;
import com.gabrielpacheco.atendente.adapter.in.web.ChatRateLimits;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class WebConfig {

    @Bean
    ProblemResponses problemResponses(ObjectMapper mapper) {
        return new ProblemResponses(mapper);
    }

    /** O primeiro filtro: até um 413 sai com os cabeçalhos de segurança. */
    @Bean
    FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> registration =
                new FilterRegistrationBean<>(new SecurityHeadersFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    FilterRegistrationBean<BodySizeLimitFilter> chatBodySizeLimitFilter(AppProperties app, ProblemResponses problems) {
        FilterRegistrationBean<BodySizeLimitFilter> registration =
                new FilterRegistrationBean<>(new BodySizeLimitFilter(app.web().maxBody().toBytes(), problems));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/api/*");
        return registration;
    }

    @Bean
    ChatRateLimits chatRateLimits(AppProperties app, Clock clock) {
        AppProperties.Web web = app.web();
        return new ChatRateLimits(
                new RateLimiter(web.messagesPerMinute(), Duration.ofMinutes(1), clock),
                new RateLimiter(web.requestsPerMinutePerIp(), Duration.ofMinutes(1), clock),
                new RateLimiter(web.sessionsPerHourPerIp(), Duration.ofHours(1), clock));
    }
}
