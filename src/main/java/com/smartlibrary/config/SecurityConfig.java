package com.smartlibrary.config;

import com.smartlibrary.config.LibraryProperties;
import com.smartlibrary.model.UserRole;
import com.smartlibrary.security.LibraryUserDetails;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final LibraryProperties libraryProperties;

    public SecurityConfig(LibraryProperties libraryProperties) {
        this.libraryProperties = libraryProperties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain superAdminChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/super-admin/**");

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/super-admin/login", "/super-admin/sso")
                        .permitAll()
                        .anyRequest().hasRole("SUPER_ADMIN"))
                .formLogin(form -> form
                        .loginPage("/super-admin/login")
                        .loginProcessingUrl("/super-admin/login")
                        .failureHandler((request, response, exception) -> {
                            HttpSession session = request.getSession();
                            session.setAttribute("AUTH_ERROR", "Invalid Super Admin credentials.");
                            response.sendRedirect("/super-admin/login");
                        })
                        .successHandler((request, response, authentication) -> {
                            boolean isSuperAdmin = authentication.getAuthorities().stream()
                                    .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
                            if (!isSuperAdmin) {
                                new SecurityContextLogoutHandler().logout(request, response, authentication);
                                response.sendRedirect("/super-admin/login");
                                return;
                            }
                            response.sendRedirect("/super-admin");
                        })
                        .permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/super-admin/login")))
                .logout(logout -> logout
                        .logoutUrl("/super-admin/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/super-admin/login?logout=true")
                        .permitAll())
                .headers(headers -> headers
                        .cacheControl(cache -> {})
                        .frameOptions(frameOptions -> frameOptions.sameOrigin()));

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain adminChain(HttpSecurity http) throws Exception {
        http.securityMatcher(new OrRequestMatcher(
                new AntPathRequestMatcher("/admin/**"),
                new AntPathRequestMatcher("/admin/login")));

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/css/**",
                                "/js/**",
                                "/img/**",
                                "/uploads/**",
                                "/admin/login",
                                "/forgot-password")
                        .permitAll()
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .failureHandler((request, response, exception) -> {
                            HttpSession session = request.getSession();
                            session.setAttribute("AUTH_ERROR", "Invalid username or password.");
                            response.sendRedirect("/admin/login");
                        })
                        .successHandler((request, response, authentication) -> {
                            var principal = authentication.getPrincipal();
                            if (principal instanceof LibraryUserDetails details) {
                                if (details.getUser().getRole() != UserRole.ADMIN) {
                                    new SecurityContextLogoutHandler().logout(request, response, authentication);
                                    request.getSession().setAttribute("AUTH_ERROR", "Invalid username or password.");
                                    response.sendRedirect("/admin/login");
                                    return;
                                }
                            }
                            response.sendRedirect("/admin");
                        })
                        .permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/admin/login")))
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/admin/login?logout=true")
                        .permitAll())
                .headers(headers -> headers
                        .cacheControl(cache -> {})
                        .frameOptions(frameOptions -> frameOptions.sameOrigin()));

        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain appChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/css/**",
                                "/js/**",
                                "/img/**",
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
                        .failureHandler((request, response, exception) -> {
                            HttpSession session = request.getSession();
                            session.setAttribute("AUTH_ERROR", "Invalid username or password.");
                            response.sendRedirect("/login");
                        })
                        .successHandler((request, response, authentication) -> {
                            var principal = authentication.getPrincipal();
                            if (principal instanceof LibraryUserDetails details) {
                                if (details.getUser().getRole() == UserRole.ADMIN
                                        || details.getUser().getRole() == UserRole.SUPER_ADMIN) {
                                    new SecurityContextLogoutHandler().logout(request, response, authentication);
                                    request.getSession().setAttribute("AUTH_ERROR", "Invalid username or password.");
                                    response.sendRedirect("/login");
                                    return;
                                }
                                if (details.getUser().getRole() == UserRole.TEACHER) {
                                    new SecurityContextLogoutHandler().logout(request, response, authentication);
                                    request.getSession().setAttribute("AUTH_ERROR",
                                            "Teacher accounts must sign in via the Attendance System: "
                                                    + libraryProperties.getAttendanceLoginUrl());
                                    response.sendRedirect("/login");
                                    return;
                                }
                            }
                            response.sendRedirect("/redirect-home");
                        })
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll())
                .headers(headers -> headers
                        .cacheControl(cache -> {})
                        .frameOptions(frameOptions -> frameOptions.sameOrigin()));

        return http.build();
    }
}
