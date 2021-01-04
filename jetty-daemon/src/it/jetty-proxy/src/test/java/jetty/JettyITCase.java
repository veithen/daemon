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
package jetty;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;

import org.junit.Test;

public class JettyITCase {
    @Test
    public void testHelloWorld() throws Exception {
        Proxy proxy =
                new Proxy(
                        Proxy.Type.HTTP,
                        new InetSocketAddress(
                                "localhost",
                                Integer.parseInt(System.getProperty("jetty.proxyPort"))));
        URL url =
                new URL(
                        String.format(
                                "http://localhost:%s/somefile.txt",
                                System.getProperty("jetty.httpPort")));
        URLConnection conn = url.openConnection(proxy);
        try (BufferedReader in =
                new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
            assertEquals("Test content.", in.readLine());
        }
    }
}
