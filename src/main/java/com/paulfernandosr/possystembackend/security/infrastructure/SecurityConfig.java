package com.paulfernandosr.possystembackend.security.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final UserDetailsService userDetailsService;
    private final SecurityAuthFilter jsonWebTokenAuthFilter;
    private final SecurityAuthEntryPoint securityAuthEntryPoint;
    private final SecurityAccessDeniedHandler securityAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(corsConfigurationSource()))
                .exceptionHandling(exceptionHandlingConfigurer -> exceptionHandlingConfigurer
                        .authenticationEntryPoint(securityAuthEntryPoint)
                        .accessDeniedHandler(securityAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/landing/**").permitAll()
                        .requestMatchers("/images/**").permitAll()
                        .requestMatchers("/files/manual-pdfs/**").permitAll()
                        .requestMatchers("/files/community/**").permitAll()
                        .requestMatchers("/files/company-community/**").permitAll()
                        .requestMatchers("/whatsapp-center/media/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/catalog/departments").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/catalog/provinces").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/catalog/districts").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/product-offers/**")
                        .hasAnyAuthority("MANAGE_PRODUCT_OFFERS", "MANAGE_PRODUCT_OFFERS_EDIT")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/product-offers/**")
                        .hasAuthority("MANAGE_PRODUCT_OFFERS_EDIT")
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/product-offers/**")
                        .hasAuthority("MANAGE_PRODUCT_OFFERS_EDIT")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/product-offers/**")
                        .hasAuthority("MANAGE_PRODUCT_OFFERS_EDIT")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/community/**")
                        .hasAuthority("MANAGE_FORUMS_VIEW")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/community/groups")
                        .hasAuthority("MANAGE_FORUM_GROUPS")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/community/groups/**")
                        .hasAuthority("MANAGE_FORUM_GROUPS")
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/community/groups/**")
                        .hasAuthority("MANAGE_FORUM_GROUPS")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/community/groups/**")
                        .hasAuthority("MANAGE_FORUM_GROUPS")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/community/posts")
                        .hasAuthority("MANAGE_FORUM_POSTS")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/community/posts/*/comments")
                        .hasAuthority("MANAGE_FORUM_POSTS")
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/community/posts/*/status")
                        .hasAuthority("MANAGE_FORUM_STATUS")
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/community/posts/*/pinned")
                        .hasAuthority("MANAGE_FORUM_STATUS")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/community/**")
                        .hasAuthority("MANAGE_FORUM_DELETE")
                        .requestMatchers("/community/**")
                        .hasAuthority("MANAGE_FORUMS_VIEW")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/company-community/**")
                        .hasAuthority("MANAGE_COMPANY_COMMUNITY_VIEW")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/company-community/categories")
                        .hasAuthority("MANAGE_COMPANY_COMMUNITY_GROUPS")
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/company-community/categories/**")
                        .hasAuthority("MANAGE_COMPANY_COMMUNITY_GROUPS")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/company-community/categories/**")
                        .hasAuthority("MANAGE_COMPANY_COMMUNITY_GROUPS")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/company-community/posts")
                        .hasAuthority("MANAGE_COMPANY_COMMUNITY_POSTS")
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/company-community/posts/**")
                        .hasAuthority("MANAGE_COMPANY_COMMUNITY_POSTS")
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/company-community/posts/**")
                        .hasAuthority("MANAGE_COMPANY_COMMUNITY_POSTS")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/company-community/**")
                        .hasAuthority("MANAGE_COMPANY_COMMUNITY_DELETE")
                        .requestMatchers("/company-community/**")
                        .hasAuthority("MANAGE_COMPANY_COMMUNITY_VIEW")
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/motorcycle-availability/contract-prefill")
                    .access(new WebExpressionAuthorizationManager("hasAuthority('MANAGE_MOTORCYCLE_AVAILABILITY_VIEW') and hasAuthority('MANAGE_CONTRACTS')"))
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/motorcycle-availability/**")
                        .hasAuthority("MANAGE_MOTORCYCLE_AVAILABILITY_VIEW")
                        .requestMatchers("/community-notifications/**")
                        .authenticated()
//                        .requestMatchers("/permissions/**").hasAnyAuthority("MANAGE_PERMISSIONS")
//                        .requestMatchers("/products/**").hasAnyAuthority("MANAGE_PRODUCTS")
                        .anyRequest().authenticated())
                .sessionManagement(smc -> smc.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jsonWebTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();

        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());

        return authProvider;
    }
}
