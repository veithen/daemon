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
package com.github.veithen.daemon.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractDaemonControlMojo extends AbstractMojo {
    @Component private DaemonManager daemonManager;

    /**
     * Set this to <code>true</code> to skip running tests, but still compile them. This is the same
     * flag that is also used by the Surefire and Failsafe plugins.
     */
    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skipTests;

    public final DaemonManager getDaemonManager() {
        return daemonManager;
    }

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (skipTests) {
            getLog().info("Tests are skipped.");
        } else {
            doExecute();
        }
    }

    protected abstract void doExecute() throws MojoExecutionException, MojoFailureException;
}
