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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.codehaus.plexus.logging.Logger;

public class StreamPump implements Runnable {
    private final BufferedReader in;
    private final Logger logger;
    private final String prefix;

    public StreamPump(InputStream in, Logger logger, String prefix) {
        this.in = new BufferedReader(new InputStreamReader(in));
        this.logger = logger;
        this.prefix = prefix;
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                logger.info(prefix + line);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
