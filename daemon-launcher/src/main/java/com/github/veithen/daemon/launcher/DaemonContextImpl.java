/*-
 * #%L
 * Daemon Tools
 * %%
 * Copyright (C) 2012 - 2025 Andreas Veithen-Knowles
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
package com.github.veithen.daemon.launcher;

import java.net.URL;
import java.util.Map;

import com.github.veithen.daemon.DaemonContext;

final class DaemonContextImpl implements DaemonContext {
    private final URL[] testClasspath;
    private final Map<String, Integer> ports;

    public DaemonContextImpl(URL[] testClasspath, Map<String, Integer> ports) {
        this.testClasspath = testClasspath;
        this.ports = ports;
    }

    @Override
    public URL[] getTestClasspath() {
        return testClasspath;
    }

    @Override
    public int getPort(String name) {
        return ports.getOrDefault(name, 0);
    }
}
