package com.squared.locus.controller;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final UserRateLimitInterceptor userRateLimitInterceptor;

    public WebConfig(UserRateLimitInterceptor userRateLimitInterceptor) {
        this.userRateLimitInterceptor = userRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userRateLimitInterceptor);
    }
}