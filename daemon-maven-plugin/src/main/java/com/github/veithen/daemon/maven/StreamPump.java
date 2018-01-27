/*-
 * #%L
 * Daemon Tools
 * %%
 * Copyright (C) 2012 - 2018 Andreas Veithen
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamPump implements Runnable {
    private final InputStream in;
    private final OutputStream out;
    
    public StreamPump(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    public void run() {
        try {
            byte[] buffer = new byte[4096];
            int c;
            while ((c = in.read(buffer)) != -1) {
                out.write(buffer, 0, c);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
