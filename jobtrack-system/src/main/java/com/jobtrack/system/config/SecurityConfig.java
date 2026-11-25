package com.jobtrack.system.config;

import com.jobtrack.system.security.JwtAuthenticationFilter;
import com.jobtrack.system.security.ResourceScopeAuthorizationManager;
import com.jobtrack.system.security.RestAccessDeniedHandler;
import com.jobtrack.system.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final AppProperties appProperties;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 本项目使用 JWT 过滤器认证，不提供表单登录。
     * 显式声明一个空的 UserDetailsService，避免 Spring Boot 生成随机的内存账号。
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("JWT authentication only");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/status",
                                "/api/auth/captcha",
                                "/api/auth/public-key",
                                "/api/auth/forgot-password/**",
                                "/api/tokens/validate",
                                "/api/avatar/**",
                                "/api/health",
                                "/api/config")
                        .permitAll()
                        // 管理员接口按功能拆分权限：授权、在线用户、用户管理。
                        // 授权码不会获得 ROLE_*，因此扩展授权码无法访问这里。
                        .requestMatchers(
                                "/api/admin/permissions",
                                "/api/admin/permissions/**",
                                "/api/admin/users/*/permissions",
                                "/api/admin/users/*/permissions/**")
                        .hasAnyRole("ADMIN", "ADMIN_CONSOLE", "ADMIN_PERMISSIONS")
                        .requestMatchers("/api/admin/online/**")
                        .hasAnyRole("ADMIN", "ADMIN_CONSOLE", "ADMIN_ONLINE")
                        .requestMatchers("/api/admin/operations", "/api/admin/operations/**")
                        .hasAnyRole("ADMIN", "ADMIN_CONSOLE", "ADMIN_OPERATIONS")
                        .requestMatchers("/api/admin/users/**")
                        .hasAnyRole("ADMIN", "ADMIN_CONSOLE", "ADMIN_USERS")
                        .requestMatchers("/api/admin/**")
                        .hasAnyRole("ADMIN", "ADMIN_CONSOLE", "ADMIN_USERS",
                                "ADMIN_ONLINE", "ADMIN_OPERATIONS", "ADMIN_PERMISSIONS")
                        // 授权码的数据范围（resources）在这里生效：
                        // 同时校验 SCOPE_*（读/写）与 RES_*（可访问的数据范围）。
                        // 网页端 JWT 登录用户拥有全部 SCOPE_* 与 RES_*，不受影响。
                        .requestMatchers("/api/applications", "/api/applications/**",
                                "/api/application/**", "/api/trash", "/api/trash/**")
                        .access(ResourceScopeAuthorizationManager.of("applications"))
                        .requestMatchers("/api/resumes", "/api/resumes/**", "/api/resume/**")
                        .access(ResourceScopeAuthorizationManager.of("resumes"))
                        .requestMatchers("/api/stats", "/api/stats/**")
                        .access(ResourceScopeAuthorizationManager.of("statistics"))
                        .requestMatchers("/api/account/profile", "/api/account/contact",
                                "/api/account/avatar")
                        .access(ResourceScopeAuthorizationManager.of("profile"))
                        // 其余授权码权限由 SCOPE_* 权限控制：读接口需要 read/readwrite，
                        // 写接口需要 write/readwrite；JWT 登录用户拥有全部 SCOPE。
                        .requestMatchers(HttpMethod.GET, "/api/**")
                        .hasAnyAuthority("SCOPE_read", "SCOPE_readwrite")
                        .requestMatchers("/api/**")
                        .hasAnyAuthority("SCOPE_write", "SCOPE_readwrite")
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = appProperties.getCors().getAllowedOrigins();
        // 使用 origin patterns 以支持通配符；浏览器扩展来源始终放行。
        List<String> patterns = new java.util.ArrayList<>(
                origins == null ? List.of() : origins);
        if (patterns.isEmpty()) {
            patterns = List.of("*");
        } else if (!patterns.contains("*")) {
            patterns.add("chrome-extension://*");
        }
        configuration.setAllowedOriginPatterns(patterns);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
