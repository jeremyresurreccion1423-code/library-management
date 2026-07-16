package com.smartlibrary.config;

import com.smartlibrary.security.AuditLogoutHandler;
import com.smartlibrary.security.LoginPortalPaths;
import com.smartlibrary.security.LoginAuthenticationFailureHandler;
import com.smartlibrary.security.LoginAuthenticationSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final LoginAuthenticationSuccessHandler loginSuccessHandler;
    private final LoginAuthenticationFailureHandler loginFailureHandler;
    private final AuditLogoutHandler auditLogoutHandler;

    public SecurityConfig(
            LoginAuthenticationSuccessHandler loginSuccessHandler,
            LoginAuthenticationFailureHandler loginFailureHandler,
            AuditLogoutHandler auditLogoutHandler) {
        this.loginSuccessHandler = loginSuccessHandler;
        this.loginFailureHandler = loginFailureHandler;
        this.auditLogoutHandler = auditLogoutHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void applySecurityHeaders(HeadersConfigurer<HttpSecurity> headers) {
        headers
                .cacheControl(cache -> {})
                .frameOptions(frame -> frame.sameOrigin())
                .contentTypeOptions(contentType -> {})
                .referrerPolicy(referrer -> referrer.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; "
                                + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
                                + "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; "
                                + "img-src 'self' data: blob:; "
                                + "font-src 'self' data: https://cdn.jsdelivr.net https://fonts.gstatic.com; "
                                + "connect-src 'self'; "
                                + "media-src 'self' blob:; "
                                + "worker-src 'self' blob:; "
                                + "frame-ancestors 'self'"));
    }

    @Bean
    @Order(1)
    public SecurityFilterChain superAdminChain(HttpSecurity http) throws Exception {
        http.securityMatcher(new OrRequestMatcher(
                new AntPathRequestMatcher("/super-admin/**"),
                new AntPathRequestMatcher("/superadmin/**")));

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/super-admin/login", "/super-admin/login/process", "/super-admin/sso")
                        .permitAll()
                        .anyRequest().hasRole("SUPER_ADMIN"))
                .formLogin(form -> form
                        .loginPage(LoginPortalPaths.SUPER_ADMIN_LOGIN)
                        .loginProcessingUrl(LoginPortalPaths.SUPER_ADMIN_PROCESS)
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/super-admin/login")))
                .logout(logout -> logout
                        .logoutUrl("/super-admin/logout")
                        .addLogoutHandler(auditLogoutHandler)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/super-admin/login?logout=true")
                        .permitAll())
                .headers(this::applySecurityHeaders);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain adminChain(HttpSecurity http) throws Exception {
        http.securityMatcher(new AntPathRequestMatcher("/admin/**"));

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/css/**",
                                "/js/**",
                                "/img/**",
                                "/images/**",
                                "/uploads/**",
                                LoginPortalPaths.ADMIN_LOGIN,
                                LoginPortalPaths.ADMIN_PROCESS,
                                "/forgot-password")
                        .permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .addLogoutHandler(auditLogoutHandler)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll())
                .headers(this::applySecurityHeaders);

        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain appChain(HttpSecurity http) throws Exception {
        http.securityMatcher(new NegatedRequestMatcher(
                new OrRequestMatcher(
                        new AntPathRequestMatcher("/super-admin/**"),
                        new AntPathRequestMatcher("/superadmin/**"),
                        new AntPathRequestMatcher("/admin/**"))));

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/css/**",
                                "/js/**",
                                "/img/**",
                                "/images/**",
                                "/uploads/**",
                                "/register",
                                "/register/**",
                                "/login",
                                "/forgot-password",
                                "/search",
                                "/scan",
                                "/books/**",
                                "/api/**")
                        .permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/student/**").hasRole("STUDENT")
                        .requestMatchers("/ebook/**").authenticated()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .addLogoutHandler(auditLogoutHandler)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll())
                .headers(this::applySecurityHeaders);

        return http.build();
    }
}
