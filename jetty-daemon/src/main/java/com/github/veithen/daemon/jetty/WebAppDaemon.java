/*-
 * #%L
 * Daemon Tools
 * %%
 * Copyright (C) 2012 - 2018 Andreas Veithen
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

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * 
 * 
 * @author Andreas Veithen
 */
public class WebAppDaemon implements Daemon {
    private Server server;
    
    public void init(DaemonContext daemonContext) throws DaemonInitException, Exception {
        Options options = new Options();
        
        {
            Option option = new Option("p", true, "the HTTP port");
            option.setArgName("port");
            option.setRequired(true);
            options.addOption(option);
        }
        
        {
            Option option = new Option("r", true, "a list of resource directories");
            option.setArgName("dirs");
            option.setRequired(true);
            options.addOption(option);
        }
        
        {
            Option option = new Option("l", true, "enable request logging");
            option.setArgName("request-log");
            options.addOption(option);
        }
        
        CommandLineParser parser = new GnuParser();
        CommandLine cmdLine = parser.parse(options, daemonContext.getArguments());
        
        server = new Server(Integer.parseInt(cmdLine.getOptionValue("p")));
        HandlerCollection handlers = new HandlerCollection();
        server.setHandler(handlers);
        
        WebAppContext context = new WebAppContext(handlers, (Resource)null, "/");
        String[] resourceDirs = cmdLine.getOptionValue("r").split(File.pathSeparator);
        Resource[] resources = new Resource[resourceDirs.length];
        for (int i=0; i<resourceDirs.length; i++) {
            resources[i] = Resource.newResource(resourceDirs[i]);
        }
        context.setBaseResource(new ResourceCollection(resources));
        
        String requestLog = cmdLine.getOptionValue("l");
        if (requestLog != null) {
            RequestLogHandler requestLogHandler = new RequestLogHandler();
            requestLogHandler.setRequestLog(new NCSARequestLog(requestLog));
            handlers.addHandler(requestLogHandler);
        }
    }

    public void start() throws Exception {
        server.start();
    }

    public void stop() throws Exception {
        server.stop();
    }

    public void destroy() {
        server = null;
    }
}
