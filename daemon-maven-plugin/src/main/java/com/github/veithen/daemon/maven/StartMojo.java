/*-
 * #%L
 * Daemon Tools
 * %%
 * Copyright (C) 2012 - 2023 Andreas Veithen
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;

/** Start a daemon. */
@Mojo(
        name = "start",
        defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST,
        requiresDependencyResolution = ResolutionScope.TEST)
public final class StartMojo extends AbstractDaemonControlMojo {
    /** The maven project. */
    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;

    /** The current build session instance. This is used for toolchain manager API calls. */
    @Parameter(property = "session", required = true, readonly = true)
    private MavenSession session;

    @Parameter(required = true)
    private DaemonArtifact daemonArtifact;

    @Parameter(required = true)
    private PlexusConfiguration daemonConfiguration;

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

    @Parameter private Port[] ports;

    /**
     * If this flag is set to <code>true</code>, then the execution of the goal will block after the
     * server has been started. This is useful if one wants to manually test some services deployed
     * on the server or if one wants to run the integration tests from an IDE. The flag should only
     * be set using the command line, but not in the POM.
     */
    // Note: this feature is implemented using a flag (instead of a distinct goal) to make sure that
    // the server is configured in exactly the same way as in a normal integration test execution.
    @Parameter(property = "axis.server.foreground", defaultValue = "false")
    private boolean foreground;

    /** The working directory for the process. */
    @Parameter(defaultValue = "${project.build.directory}/work", required = true)
    private File workDir;

    @Component private MojoExecution mojoExecution;

    @Override
    protected final void doExecute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();

        // Use the plugin group ID and version as default values for the daemon artifact.
        if (daemonArtifact.getGroupId() == null) {
            daemonArtifact.setGroupId(mojoExecution.getPlugin().getGroupId());
        }
        if (daemonArtifact.getVersion() == null
                && daemonArtifact.getGroupId().equals(mojoExecution.getPlugin().getGroupId())) {
            daemonArtifact.setVersion(mojoExecution.getPlugin().getVersion());
        }

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

        Map<String, Integer> portsIn = new HashMap<>();
        if (foreground) {
            for (Port port : ports) {
                if (port.getForeground() != 0) {
                    portsIn.put(port.getName(), port.getForeground());
                }
            }
        }

        workDir.mkdirs();
        Map<String, Integer> portsOut;
        try {
            portsOut =
                    getDaemonManager()
                            .startDaemon(
                                    session,
                                    vmArgs.toArray(new String[vmArgs.size()]),
                                    workDir,
                                    daemonArtifact,
                                    project.getTestClasspathElements(),
                                    daemonConfiguration,
                                    new PluginParameterExpressionEvaluator(session, mojoExecution),
                                    portsIn);
        } catch (Throwable ex) {
            throw new MojoFailureException("Failed to start server", ex);
        }

        for (Port port : ports) {
            int portNumber = portsOut.getOrDefault(port.getName(), -1);
            if (portNumber == -1) {
                throw new MojoFailureException("Unknown port " + port.getName());
            }
            if (foreground && port.getForeground() != 0 && portNumber != port.getForeground()) {
                throw new MojoFailureException(
                        "Daemon failed to allocate the expected port number for port "
                                + port.getName());
            }
            project.getProperties()
                    .setProperty(port.getPropertyName(), Integer.toString(portNumber));
        }

        if (foreground) {
            log.info("Server started in foreground mode. Press CRTL-C to stop.");
            Object lock = new Object();
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException ex) {
                    // Set interrupt flag and continue
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static void processVMArgs(List<String> vmArgs, String args) {
        vmArgs.addAll(Arrays.asList(args.trim().split(" +")));
    }
}
