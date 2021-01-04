/*-
 * #%L
 * Daemon Tools
 * %%
 * Copyright (C) 2012 - 2021 Andreas Veithen
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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;

public abstract class AbstractStartDaemonMojo extends AbstractDaemonControlMojo {
    /** The maven project. */
    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;

    /** The current build session instance. This is used for toolchain manager API calls. */
    @Parameter(property = "session", required = true, readonly = true)
    private MavenSession session;

    /** The arguments to pass to the JVM when debug mode is enabled. */
    @Parameter(
            defaultValue = "-Xdebug -Xrunjdwp:transport=dt_socket,address=8899,server=y,suspend=y")
    private String debugArgs;

    /**
     * Indicates whether the Java process should be started in debug mode. This flag should only be
     * set from the command line.
     */
    @Parameter(property = "axis.server.debug", defaultValue = "false")
    private boolean debug;

    /** The arguments to pass to the JVM when JMX is enabled. */
    @Parameter(
            defaultValue =
                    "-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false")
    private String jmxArgs;

    /**
     * Indicates whether the Java process should be started with remote JMX enabled. This flag
     * should only be set from the command line.
     */
    @Parameter(property = "axis.server.jmx", defaultValue = "false")
    private boolean jmx;

    /**
     * Arbitrary JVM options to set on the command line. Note that this parameter uses the same
     * expression as the Surefire and Failsafe plugins. By setting the <code>argLine</code>
     * property, it is therefore possible to easily pass a common set of JVM options to all
     * processes involved in the tests. Since the JaCoCo Maven plugin also sets this property, code
     * coverage generated on the server-side will be automatically included in the analysis.
     */
    @Parameter(property = "argLine")
    private String argLine;

    @Component private MojoExecution mojoExecution;

    protected final void startDaemon(
            DaemonArtifact daemonArtifact, PlexusConfiguration configuration, File workDir)
            throws MojoExecutionException, MojoFailureException {
        Log log = getLog();

        // Compute JVM arguments
        List<String> vmArgs = new ArrayList<>();
        if (debug) {
            processVMArgs(vmArgs, debugArgs);
        }
        if (jmx) {
            processVMArgs(vmArgs, jmxArgs);
        }
        if (argLine != null) {
            processVMArgs(vmArgs, argLine);
        }
        if (log.isDebugEnabled()) {
            log.debug("Additional VM args: " + vmArgs);
        }

        try {
            getDaemonManager()
                    .startDaemon(
                            session,
                            vmArgs.toArray(new String[vmArgs.size()]),
                            workDir,
                            daemonArtifact,
                            project.getTestClasspathElements(),
                            configuration,
                            new PluginParameterExpressionEvaluator(session, mojoExecution));
        } catch (Throwable ex) {
            throw new MojoFailureException("Failed to start server", ex);
        }
    }

    private static void processVMArgs(List<String> vmArgs, String args) {
        vmArgs.addAll(Arrays.asList(args.trim().split(" +")));
    }

    protected final void doExecute() throws MojoExecutionException, MojoFailureException {
        doStartDaemon();
    }

    protected abstract void doStartDaemon() throws MojoExecutionException, MojoFailureException;
}
