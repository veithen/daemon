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
package com.github.veithen.daemon.launcher.proto;

import java.io.IOException;
import java.io.OutputStream;

import com.google.protobuf.Message;

public class MessageWriter<T extends Message> {
    private final OutputStream out;

    public MessageWriter(OutputStream out) {
        this.out = out;
    }

    public void write(T message) throws IOException {
        message.writeDelimitedTo(out);
    }

    public void close() throws IOException {
        out.close();
    }
}
