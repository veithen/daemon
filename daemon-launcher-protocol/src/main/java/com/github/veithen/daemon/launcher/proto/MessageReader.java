/*-
 * #%L
 * Daemon Tools
 * %%
 * Copyright (C) 2012 - 2022 Andreas Veithen
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
import java.io.InputStream;
import java.util.function.Function;

import com.github.veithen.daemon.ProtoOptions;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;

public class MessageReader<T extends Message, C extends Enum<C>> {
    private final InputStream in;
    private final Parser<T> parser;
    private final Function<T, C> caseProvider;
    private final ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();

    public MessageReader(InputStream in, Parser<T> parser, Function<T, C> caseProvider) {
        this.in = in;
        this.parser = parser;
        this.caseProvider = caseProvider;
        ProtoOptions.registerAllExtensions(extensionRegistry);
    }

    public T read(C expectedCase) throws IOException {
        T message = parser.parseDelimitedFrom(in, extensionRegistry);
        if (message == null) {
            throw new IOException("Unexpected end of stream");
        }
        C c = caseProvider.apply(message);
        if (c != expectedCase) {
            throw new IOException("Received unexpected message type " + c);
        }
        return message;
    }
}
