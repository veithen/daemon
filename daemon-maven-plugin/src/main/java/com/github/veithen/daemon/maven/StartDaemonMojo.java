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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Start a daemon. */
@Mojo(
        name = "start-daemon",
        defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST,
        requiresDependencyResolution = ResolutionScope.TEST)
public class StartDaemonMojo extends AbstractStartDaemonMojo {
    @Parameter(required = true)
    private DaemonArtifact daemonArtifact;

    /** The arguments to be passed to the main class. */
    @Parameter private String[] args;

    /** The working directory for the process. */
    @Parameter(defaultValue = "${project.build.directory}/work", required = true)
    private File workDir;

    protected void doStartDaemon() throws MojoExecutionException, MojoFailureException {
        workDir.mkdirs();
        startDaemon(/* TODO */ "Daemon", daemonArtifact, args, workDir);
    }
}
