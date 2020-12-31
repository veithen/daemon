/*-
 * #%L
 * Daemon Tools
 * %%
 * Copyright (C) 2012 - 2020 Andreas Veithen
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
package com.github.veithen.daemon.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.StringUtils;

@Mojo(
        name = "start-webapp",
        defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST,
        requiresDependencyResolution = ResolutionScope.TEST)
public class StartWebAppMojo extends AbstractStartWebServerMojo {
    @Parameter(required = true)
    private File[] resourceBases;

    @Parameter() private File requestLog;

    protected void doStartDaemon(int port) throws MojoExecutionException, MojoFailureException {
        addDependency("jetty-daemon");
        List<String> args =
                new ArrayList<>(
                        Arrays.asList(
                                "-p",
                                String.valueOf(port),
                                "-r",
                                StringUtils.join(resourceBases, File.pathSeparator)));
        if (requestLog != null) {
            args.add("-l");
            args.add(requestLog.getAbsolutePath());
        }
        startDaemon(
                "HTTP server on port " + port,
                "com.github.veithen.daemon.jetty.WebAppDaemon",
                args.toArray(new String[args.size()]),
                new File("."));
    }
}
