package com.wallet.config;

import com.wallet.security.JwtAuthFilter;
import com.wallet.security.JwtUtil;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the JWT filter for protected API routes.
 * Public routes (/api/auth/**) are skipped inside the filter itself.
 */
@Configuration
public class SecurityConfig {

    /**
     * Registers JwtAuthFilter on /api/*.
     *
     * @param jwtUtil token helper
     * @return filter registration
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilter(JwtUtil jwtUtil) {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new JwtAuthFilter(jwtUtil));
        bean.addUrlPatterns("/api/*");
        bean.setOrder(1);
        return bean;
    }
}
