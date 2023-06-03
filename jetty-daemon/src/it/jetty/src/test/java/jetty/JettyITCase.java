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
package jetty;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

import org.junit.jupiter.api.Test;

public class JettyITCase {
    @Test
    public void testHelloWorld() throws Exception {
        URL url =
                new URL(
                        String.format(
                                "http://localhost:%s/foobar/HelloWorld",
                                System.getProperty("jetty.httpPort")));
        try (BufferedReader in =
                new BufferedReader(new InputStreamReader(url.openStream(), "utf-8"))) {
            assertThat(in.readLine()).isEqualTo("Hello world!");
        }
    }

    @Test
    public void testJsp() throws Exception {
        URL url =
                new URL(
                        String.format(
                                "http://localhost:%s/foobar/test.jsp",
                                System.getProperty("jetty.httpPort")));
        try (BufferedReader in =
                new BufferedReader(new InputStreamReader(url.openStream(), "utf-8"))) {
            String line;
            while ((line = in.readLine()).isEmpty()) {
                // Just loop
            }
            assertThat(line).isEqualTo("Test");
        }
    }
}
