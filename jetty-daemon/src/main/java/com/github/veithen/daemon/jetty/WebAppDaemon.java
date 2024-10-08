/*-
 * #%L
 * Daemon Tools
 * %%
 * Copyright (C) 2012 - 2024 Andreas Veithen
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.github.veithen.daemon.jetty;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletContext;

import org.eclipse.jetty.ee10.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.ClassMatcher;
import org.eclipse.jetty.util.FileID;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;

import com.github.veithen.daemon.Daemon;
import com.github.veithen.daemon.DaemonContext;

public class WebAppDaemon implements Daemon<Configuration> {
    private static final String HTTP_PORT_NAME = "http";

    private Server server;

    @Override
    public Class<Configuration> getConfigurationType() {
        return Configuration.class;
    }

    @Override
    public void init(Configuration configuration, DaemonContext daemonContext) throws Exception {
        server = new Server(daemonContext.getPort(HTTP_PORT_NAME));

        Thread.currentThread()
                .setContextClassLoader(
                        new URLClassLoader(
                                daemonContext.getTestClasspath(), getClass().getClassLoader()));
        WebAppContext context =
                new WebAppContext(
                        (Resource) null,
                        configuration.getContextPath().isEmpty()
                                ? "/"
                                : configuration.getContextPath());
        // This allows the webapp to load servlets provided by Jetty, e.g. ProxyServlet.
        context.setHiddenClassMatcher(new ClassMatcher());
        server.setHandler(context);
        ResourceFactory resourceFactory = ResourceFactory.of(context);
        List<Resource> resources = new ArrayList<>();
        for (String resourceBase : configuration.getResourceBasesList()) {
            Resource resource = resourceFactory.newResource(resourceBase);
            Path path = resource.getPath();
            // We always unpack WARs ourselves. Jetty looks for a sibling directory of the WAR file
            // with a matching name. That will exist if the WAR file was produced by
            // maven-war-plugin. In that case, this results in an attempt to access files in the
            // target directory of another module (which will cause errors if hermetic-maven-plugin
            // is used).
            if (FileID.isWebArchive(path)) {
                String name = path.getFileName().toString();
                Path unpackDir = Path.of("webapps", name.substring(0, name.length() - 4));
                if (!Files.exists(unpackDir)
                        || resource.lastModified()
                                .isAfter(Files.getLastModifiedTime(unpackDir).toInstant())) {
                    System.out.println("Unpacking " + path);
                    Files.createDirectories(unpackDir);
                    resourceFactory.newJarFileResource(resource.getURI()).copyTo(unpackDir);
                }
                resource = resourceFactory.newResource(unpackDir);
            }
            resources.add(resource);
        }
        context.setBaseResource(ResourceFactory.combine(resources));
        context.addBean(
                new AbstractLifeCycle() {
                    @Override
                    public void doStop() throws Exception {}

                    @Override
                    public void doStart() throws Exception {
                        ServletContext servletContext = context.getServletContext();
                        JettyJasperInitializer jspInit = new JettyJasperInitializer();
                        jspInit.onStartup(Collections.emptySet(), servletContext);
                        configuration
                                .getInitParametersMap()
                                .forEach(servletContext::setInitParameter);
                    }
                },
                true);

        String requestLog = configuration.getRequestLog();
        if (!requestLog.isEmpty()) {
            server.setRequestLog(
                    new CustomRequestLog(requestLog, CustomRequestLog.EXTENDED_NCSA_FORMAT));
        }
    }

    @Override
    public Map<String, Integer> start() throws Exception {
        server.start();
        return Collections.singletonMap(HTTP_PORT_NAME, server.getURI().getPort());
    }

    @Override
    public void stop() throws Exception {
        server.stop();
    }

    @Override
    public void destroy() {
        server = null;
    }
}
