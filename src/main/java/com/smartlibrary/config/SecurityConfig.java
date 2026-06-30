package com.smartlibrary.config;

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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
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
                                "/admin/login",
                                "/forgot-password",
                                "/reset-password")
                        .permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .failureHandler((request, response, exception) -> {
                            HttpSession session = request.getSession();
                            session.setAttribute("AUTH_ERROR", "Invalid username or password");
                            response.sendRedirect("/admin/login");
                        })
                        .successHandler((request, response, authentication) -> {
                            var principal = authentication.getPrincipal();
                            if (principal instanceof LibraryUserDetails details) {
                                if (details.getUser().getRole() != UserRole.ADMIN) {
                                    new SecurityContextLogoutHandler().logout(request, response, authentication);
                                    request.getSession().setAttribute("AUTH_ERROR", "Invalid username or password");
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
                        .logoutSuccessUrl("/admin/login")
                        .permitAll())
                .headers(headers -> headers
                        .cacheControl(cache -> {})
                        .frameOptions(frameOptions -> frameOptions.sameOrigin()));

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain appChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/css/**",
                                "/js/**",
                                "/img/**",
                                "/register",
                                "/login",
                                "/forgot-password",
                                "/reset-password",
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
                            session.setAttribute("AUTH_ERROR", "Invalid username or password");
                            response.sendRedirect("/login");
                        })
                        .successHandler((request, response, authentication) -> {
                            var principal = authentication.getPrincipal();
                            if (principal instanceof LibraryUserDetails details) {
                                if (details.getUser().getRole() == UserRole.ADMIN) {
                                    new SecurityContextLogoutHandler().logout(request, response, authentication);
                                    request.getSession().setAttribute("AUTH_ERROR", "Invalid username or password");
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
                        .logoutSuccessUrl("/login")
                        .permitAll())
                .headers(headers -> headers
                        .cacheControl(cache -> {})
                        .frameOptions(frameOptions -> frameOptions.sameOrigin()));

        return http.build();
    }
}
