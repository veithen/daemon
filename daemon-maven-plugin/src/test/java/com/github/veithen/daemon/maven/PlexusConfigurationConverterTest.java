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
package com.github.veithen.daemon.maven;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import java.io.File;

import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.junit.jupiter.api.Test;

import com.google.protobuf.Message;

public class PlexusConfigurationConverterTest {
    @Test
    public void test() throws Exception {
        Message message =
                PlexusConfigurationConverter.convert(
                        new XmlPlexusConfiguration(
                                Xpp3DomBuilder.build(
                                        PlexusConfigurationConverterTest.class.getResourceAsStream(
                                                "sample_config.xml"),
                                        "utf-8")),
                        new NullExpressionEvaluator(new File("/my/basedir")),
                        Configuration.getDescriptor());
        assertThat(message)
                .isEqualTo(
                        Configuration.newBuilder()
                                .setSomeInt(1234)
                                .setSomeString("test")
                                .addSomeRepeatedStrings("value1")
                                .addSomeRepeatedStrings("value2")
                                .setSomeMessage(SomeMessage.newBuilder().setValue("test").build())
                                .setSomeFile("/my/basedir/src/test/foobar")
                                .putSomeMap("key1", true)
                                .putSomeMap("key2", false)
                                .build());
    }
}
