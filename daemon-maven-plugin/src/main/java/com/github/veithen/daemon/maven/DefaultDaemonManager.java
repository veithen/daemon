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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.IOUtil;
import org.eclipse.aether.util.artifact.JavaScopes;

@Component(role = DaemonManager.class, hint = "default")
public class DefaultDaemonManager implements DaemonManager {
    private static final String VERSION;

    static {
        try (InputStream in = DefaultDaemonManager.class.getResourceAsStream("version")) {
            VERSION = IOUtil.toString(in, "utf-8");
        } catch (IOException ex) {
            throw new Error(ex);
        }
    }

    private final List<RemoteDaemon> daemons = new ArrayList<>();

    @Requirement private Logger logger;
    @Requirement private ToolchainManager toolchainManager;
    @Requirement private ArtifactHandlerManager artifactHandlerManager;
    @Requirement private ProjectDependenciesResolver dependencyResolver;

    private List<File> getClassPathForArtifact(
            MavenSession session, String groupId, String artifactId, String version)
            throws DependencyResolutionException {
        MavenProject project = new MavenProject();
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        dependency.setScope(JavaScopes.RUNTIME);
        project.getDependencies().add(dependency);
        project.setRemoteArtifactRepositories(
                new ArrayList<>(session.getCurrentProject().getPluginArtifactRepositories()));
        DefaultDependencyResolutionRequest resolution =
                new DefaultDependencyResolutionRequest(project, session.getRepositorySession());
        DependencyResolutionResult resolutionResult = dependencyResolver.resolve(resolution);
        Set<Artifact> artifacts = new LinkedHashSet<>();
        RepositoryUtils.toArtifacts(
                artifacts,
                resolutionResult.getDependencyGraph().getChildren(),
                Collections.emptyList(),
                null);
        return artifacts.stream().map(Artifact::getFile).collect(Collectors.toList());
    }

    @Override
    public void startDaemon(
            String description,
            MavenSession session,
            String[] vmArgs,
            File workDir,
            File[] classpath,
            String daemonClass,
            List<String> testClasspath,
            String[] daemonArgs)
            throws Throwable {
        // Locate java executable to use
        String jvm;
        Toolchain tc = toolchainManager.getToolchainFromBuildContext("jdk", session);
        if (tc != null) {
            jvm = tc.findTool("java");
        } else {
            jvm =
                    System.getProperty("java.home")
                            + File.separator
                            + "bin"
                            + File.separator
                            + "java";
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Java executable: " + jvm);
        }

        RemoteDaemon daemon =
                new RemoteDaemon(
                        logger,
                        jvm,
                        vmArgs,
                        workDir,
                        description,
                        getClassPathForArtifact(
                                session, "com.github.veithen.daemon", "daemon-launcher", VERSION),
                        Arrays.asList(classpath),
                        daemonClass,
                        testClasspath,
                        daemonArgs);
        daemons.add(daemon);
        daemon.startDaemon();
    }

    @Override
    public void stopAll() throws Throwable {
        Throwable savedException = null;
        for (RemoteDaemon daemon : daemons) {
            if (logger.isDebugEnabled()) {
                logger.debug("Stopping " + daemon.getDescription());
            }
            boolean success;
            try {
                daemon.stopDaemon();
                success = true;
            } catch (Throwable ex) {
                if (savedException == null) {
                    savedException = ex;
                }
                success = false;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("success = " + success);
            }
            if (success) {
                daemon.getProcess().waitFor();
            } else {
                daemon.getProcess().destroy();
            }
            logger.info(daemon.getDescription() + " stopped");
        }
        // TODO: need to clear the collection because the same ServerManager instance may be used by
        // multiple projects in a reactor build;
        //       note that this means that the plugin is not thread safe (i.e. doesn't support
        // parallel builds in Maven 3)
        daemons.clear();
        if (savedException != null) {
            throw savedException;
        }
    }
}
