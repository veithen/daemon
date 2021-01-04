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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Stop all processes created by {@link StartMojo}. */
@Mojo(name = "stop-all", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class StopAllMojo extends AbstractDaemonControlMojo {
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        try {
            getDaemonManager().stopAll();
        } catch (Throwable ex) {
            throw new MojoFailureException(
                    "Errors occurred while attempting to stop processes", ex);
        }
    }
}
