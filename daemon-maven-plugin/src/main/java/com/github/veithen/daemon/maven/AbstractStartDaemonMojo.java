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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.DebugResolutionListener;
import org.apache.maven.artifact.resolver.ResolutionListener;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.Logger;

public abstract class AbstractStartDaemonMojo extends AbstractDaemonControlMojo
        implements LogEnabled {
    /** The maven project. */
    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;

    /** The current build session instance. This is used for toolchain manager API calls. */
    @Parameter(property = "session", required = true, readonly = true)
    private MavenSession session;

    @Component private MavenProjectBuilder projectBuilder;

    /** Local maven repository. */
    @Parameter(property = "localRepository", required = true, readonly = true)
    private ArtifactRepository localRepository;

    /** Remote repositories. */
    @Parameter(property = "project.remoteArtifactRepositories", required = true, readonly = true)
    private List<ArtifactRepository> remoteArtifactRepositories;

    @Component private ArtifactFactory artifactFactory;

    @Component private ArtifactResolver artifactResolver;

    @Component private ArtifactCollector artifactCollector;

    @Component private ArtifactMetadataSource artifactMetadataSource;

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

    @Parameter(property = "plugin.version", required = true, readonly = true)
    private String pluginVersion;

    private final Set<Artifact> additionalDependencies = new HashSet<>();
    private List<File> classpath;

    private Logger logger;

    public final void enableLogging(Logger logger) {
        this.logger = logger;
    }

    protected final void addDependency(String groupId, String artifactId, String version) {
        additionalDependencies.add(
                artifactFactory.createArtifact(
                        groupId, artifactId, version, Artifact.SCOPE_TEST, "jar"));
        classpath = null;
    }

    protected final void addDependency(String artifactId) {
        addDependency("com.github.veithen.daemon", artifactId, pluginVersion);
    }

    protected final List<File> getClasspath()
            throws ProjectBuildingException, InvalidDependencyVersionException,
                    ArtifactResolutionException, ArtifactNotFoundException {
        if (classpath == null) {
            final Log log = getLog();

            // We need dependencies in scope test. Since this is the largest scope, we don't need
            // to do any additional filtering based on dependency scope.
            Set<Artifact> projectDependencies = project.getArtifacts();

            final Set<Artifact> artifacts = new HashSet<>(projectDependencies);

            if (additionalDependencies != null) {
                for (Artifact a : additionalDependencies) {
                    if (log.isDebugEnabled()) {
                        log.debug("Resolving artifact to be added to classpath: " + a);
                    }
                    ArtifactFilter filter =
                            new ArtifactFilter() {
                                public boolean include(Artifact artifact) {
                                    String id = artifact.getDependencyConflictId();
                                    for (Artifact a2 : artifacts) {
                                        if (id.equals(a2.getDependencyConflictId())) {
                                            return false;
                                        }
                                    }
                                    return true;
                                }
                            };
                    MavenProject p =
                            projectBuilder.buildFromRepository(
                                    a, remoteArtifactRepositories, localRepository);
                    if (filter.include(p.getArtifact())) {
                        Set<Artifact> s =
                                p.createArtifacts(artifactFactory, Artifact.SCOPE_RUNTIME, filter);
                        artifacts.addAll(
                                artifactCollector
                                        .collect(
                                                s,
                                                p.getArtifact(),
                                                p.getManagedVersionMap(),
                                                localRepository,
                                                remoteArtifactRepositories,
                                                artifactMetadataSource,
                                                filter,
                                                Collections.<ResolutionListener>singletonList(
                                                        new DebugResolutionListener(logger)))
                                        .getArtifacts());
                        artifacts.add(p.getArtifact());
                    }
                }
            }

            classpath = new ArrayList<>();
            classpath.add(new File(project.getBuild().getTestOutputDirectory()));
            classpath.add(new File(project.getBuild().getOutputDirectory()));
            for (Artifact a : artifacts) {
                if (a.getArtifactHandler().isAddedToClasspath()) {
                    if (a.getFile() == null) {
                        artifactResolver.resolve(a, remoteArtifactRepositories, localRepository);
                    }
                    classpath.add(a.getFile());
                }
            }
        }

        return classpath;
    }

    protected final void startDaemon(
            String description, String daemonClass, String[] args, File workDir)
            throws MojoExecutionException, MojoFailureException {
        Log log = getLog();

        // Get class path
        List<File> classpath;
        try {
            classpath = getClasspath();
        } catch (Exception ex) {
            throw new MojoExecutionException("Failed to build classpath", ex);
        }
        if (log.isDebugEnabled()) {
            log.debug("Class path elements: " + classpath);
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

        try {
            getDaemonManager()
                    .startDaemon(
                            description,
                            session,
                            vmArgs.toArray(new String[vmArgs.size()]),
                            workDir,
                            classpath.toArray(new File[classpath.size()]),
                            daemonClass,
                            project.getTestClasspathElements(),
                            args);
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
