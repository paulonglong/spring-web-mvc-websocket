package com.itranswarp.learnjava;

import java.io.File;

import javax.servlet.ServletContext;
import javax.sql.DataSource;

import org.apache.catalina.Context;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itranswarp.learnjava.web.ChatHandler;
import com.itranswarp.learnjava.web.ChatHandshakeInterceptor;
import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.loader.ServletLoader;
import com.mitchellbosecke.pebble.spring.extension.SpringExtension;
import com.mitchellbosecke.pebble.spring.servlet.PebbleViewResolver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
@ComponentScan
@EnableWebMvc
@EnableWebSocket
@EnableTransactionManagement
@PropertySource("classpath:/jdbc.properties")
public class AppConfig {

	public static void main(String[] args) throws Exception {
		Tomcat tomcat = new Tomcat();
		tomcat.setPort(Integer.getInteger("port", 8080));
		tomcat.getConnector();
		Context ctx = tomcat.addWebapp("", new File("src/main/webapp").getAbsolutePath());
		WebResourceRoot resources = new StandardRoot(ctx);
		resources.addPreResources(
				new DirResourceSet(resources, "/WEB-INF/classes", new File("target/classes").getAbsolutePath(), "/"));
		ctx.setResources(resources);
		tomcat.start();
		tomcat.getServer().await();
	}

	// -- Mvc configuration ---------------------------------------------------

	@Bean
	WebMvcConfigurer createWebMvcConfigurer(@Autowired HandlerInterceptor[] interceptors) {
		return new WebMvcConfigurer() {
			@Override
			public void addInterceptors(InterceptorRegistry registry) {
                for (HandlerInterceptor interceptor : interceptors) {
					registry.addInterceptor(interceptor);
				}
			}

			@Override
			public void addResourceHandlers(ResourceHandlerRegistry registry) {
				registry.addResourceHandler("/static/**").addResourceLocations("/static/");
			}
		};
	}

	@Bean
	WebSocketConfigurer createWebSocketConfigurer(@Autowired ChatHandler chatHandler,
			@Autowired ChatHandshakeInterceptor chatInterceptor) {
		return new WebSocketConfigurer() {
			@Override
			public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
				// 把URL与指定的WebSocketHandler关联，可关联多个:
				registry.addHandler(chatHandler, "/chat").addInterceptors(chatInterceptor);
			}
		};
	}

	@Bean
	ObjectMapper createObjectMapper() {
        ObjectMapper om = new ObjectMapper();
		om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		return om;
	}

	// -- pebble view configuration -------------------------------------------

	@Bean
	ViewResolver createViewResolver(@Autowired ServletContext servletContext) {
		PebbleEngine engine = new PebbleEngine.Builder().autoEscaping(true)
				// cache:
				.cacheActive(false)
				// loader:
				.loader(new ServletLoader(servletContext))
				// extension:
				.extension(new SpringExtension())
				// build:
				.build();
		PebbleViewResolver viewResolver = new PebbleViewResolver();
		viewResolver.setPrefix("/WEB-INF/templates/");
		viewResolver.setSuffix("");
		viewResolver.setPebbleEngine(engine);
		return viewResolver;
	}

	// -- jdbc configuration --------------------------------------------------

	@Value("${jdbc.url}")
	String jdbcUrl;

	@Value("${jdbc.username}")
	String jdbcUsername;

	@Value("${jdbc.password}")
	String jdbcPassword;

	@Bean
    DataSource createDataSource() {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(jdbcUrl);
		config.setUsername(jdbcUsername);
		config.setPassword(jdbcPassword);
		config.addDataSourceProperty("autoCommit", "false");
		config.addDataSourceProperty("connectionTimeout", "5");
		config.addDataSourceProperty("idleTimeout", "60");
        return new HikariDataSource(config);
	}

	@Bean
	JdbcTemplate createJdbcTemplate(@Autowired DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	@Bean
	PlatformTransactionManager createTxManager(@Autowired DataSource dataSource) {
		return new DataSourceTransactionManager(dataSource);
	}
}
