package com.sgck.web.listener;

import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServlet;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.ServletContextResourcePatternResolver;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.sgck.common.log.DSLogger;
import com.sgck.core.platform.ServletProvider;
import com.sgck.core.platform.SubSystemGather;
import com.sgck.core.platform.SubSystemInitilizer;
import com.sgck.springAdapter.SpringBeanUtil;
import com.sgck.web.context.SGXmlWebApplicationContext;

public class InitListener implements ServletContextListener {
	static public String SUBSYSTEM_SPRING_CONFIG_NAME = "spring-main.xml";

	@Override
	public void contextInitialized(ServletContextEvent event) {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

		try {
			DSLogger.addLogger(DSLogger.defaultLoggerName, "/data/logs/sgck_web/sgck_web.log");

			ServletContext servletContext = event.getServletContext();

			SpringBeanUtil.setServletContext(servletContext);

			WebApplicationContext rootContext = WebApplicationContextUtils.getWebApplicationContext(servletContext);
			SubSystemGather subSystemGather = rootContext.getBean(SubSystemGather.class);

			ServletContextResourcePatternResolver resourceLoader = new ServletContextResourcePatternResolver(
					servletContext);
			Resource[] resources = resourceLoader.getResources("classpath*:" + SUBSYSTEM_SPRING_CONFIG_NAME);
			for (Resource resource : resources) {
				try {
					WebApplicationContext subSystemContext = new SGXmlWebApplicationContext(resource);
					((ConfigurableApplicationContext) subSystemContext).setParent(rootContext);

					configureAndRefreshWebApplicationContext((ConfigurableWebApplicationContext) subSystemContext,
							servletContext);

					Map<String, SubSystemInitilizer> beans = subSystemContext.getBeansOfType(SubSystemInitilizer.class);
					if (beans.isEmpty()) {
						DSLogger.error("SubSystem have no SubSystemInitilizer?,the resource is " + resource);
						continue;
					}

					if (beans.size() != 1) {
						DSLogger.error("SubSystem have many SystemInitilizers?,the resource is " + resource);
						continue;
					}

					SubSystemInitilizer sysInit = (SubSystemInitilizer) beans.values().toArray()[0];
					String systemName = sysInit.getSystemName();

					servletContext.setAttribute(systemName, subSystemContext);
					subSystemGather.addSubSystem(systemName, subSystemContext);

					DSLogger.info("begin to init system[" + systemName + "]");

					if (sysInit.init()) {
						DSLogger.info("OK to init system[" + systemName + "]");
					} else {
						DSLogger.error("Failed to init system[" + systemName + "]");
					}

					Map<String, ServletProvider> servlets = subSystemContext.getBeansOfType(ServletProvider.class);
					for (ServletProvider servlet : servlets.values()) {
						if (!HttpServlet.class.isAssignableFrom(servlet.getClass())) {
							DSLogger.error(servlet.toString()
									+ " is not a true HttpServlet,but it's implement ServletProvider,are you kidding me ?!");
							continue;
						}

						String uri = servlet.getURI();
						if (uri == null || uri.isEmpty()) {
							DSLogger.error(servlet.toString() + " with a null uri,ignore it...");
							continue;
						}

						if (!uri.startsWith("/")) {
							uri = "/" + uri;
						}

						uri = "/" + systemName + uri;

						ServletRegistration.Dynamic servletRegistration = servletContext
								.addServlet(servlet.getClass().getSimpleName(), (HttpServlet) servlet);
						if (servletRegistration == null) {
							servletRegistration = servletContext.addServlet(servlet.getClass().getSimpleName() + uri,
									(HttpServlet) servlet);
						}
						if (servletRegistration == null) {
							DSLogger.error(servlet.toString() + " failed to add serlvet with name: "
									+ servlet.getClass().getSimpleName() + uri);
							continue;
						}

						Set<String> existedMappings = servletRegistration.addMapping(uri);
						if (existedMappings != null && !existedMappings.isEmpty()) {
							DSLogger.error(servlet.toString() + " registered with a existed uri[" + uri + "]");
						}

						Map<String, String> initParams = servlet.getInitParams();
						if (initParams != null) {
							servletRegistration.setInitParameters(initParams);
						}

						DSLogger.info(servlet.toString() + " registered with uri[" + uri + "]");
					}
				} catch (Exception e) {
					DSLogger.error("Failed to init subsytem", e);
				}
			}
		} catch (Exception e) {
			DSLogger.error("Failed to init SGCK System ", e);
		}

		DSLogger.info("OK to init SGCK System");
	}

	protected void configureAndRefreshWebApplicationContext(ConfigurableWebApplicationContext wac, ServletContext sc) {
		// Generate default id...
		// wac.setId(ConfigurableWebApplicationContext.APPLICATION_CONTEXT_ID_PREFIX
		// +
		// ObjectUtils.getDisplayString(sc.getContextPath()));

		wac.setServletContext(sc);
		// The wac environment's #initPropertySources will be called in any case
		// when the context
		// is refreshed; do it eagerly here to ensure servlet property sources
		// are in place for
		// use in any post-processing or initialization that occurs below prior
		// to #refresh
		ConfigurableEnvironment env = wac.getEnvironment();
		if (env instanceof ConfigurableWebEnvironment) {
			((ConfigurableWebEnvironment) env).initPropertySources(sc, null);
		}
		wac.refresh();
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
	}
}
