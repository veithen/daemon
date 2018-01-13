/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.veithen.daemon.maven;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.StringUtils;

/**
 * 
 * 
 * @goal start-webapp
 * @phase pre-integration-test
 * @requiresDependencyResolution test
 */
public class StartWebAppMojo extends AbstractStartWebServerMojo {
    /**
     * 
     * 
     * @parameter
     * @required
     */
    private File[] resourceBases;
    
    protected void doStartDaemon(int port) throws MojoExecutionException, MojoFailureException {
        addAxisDependency("jetty-daemon");
        startDaemon("HTTP server on port " + port, "com.github.veithen.daemon.jetty.WebAppDaemon",
                new String[] { "-p", String.valueOf(port), "-r", StringUtils.join(resourceBases, File.pathSeparator) }, new File("."));
    }
}
