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

import java.io.File;
import java.util.List;
import java.util.Map;

import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;

public interface DaemonManager {
    Map<String, Integer> startDaemon(
            MavenSession session,
            String[] vmArgs,
            File workDir,
            DaemonArtifact daemonArtifact,
            List<String> testClasspath,
            PlexusConfiguration configuration,
            ExpressionEvaluator expressionEvaluator,
            Map<String, Integer> ports)
            throws Throwable;

    void stopAll() throws Throwable;
}
