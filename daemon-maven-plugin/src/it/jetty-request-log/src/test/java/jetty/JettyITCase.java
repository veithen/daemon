/*-
 * #%L
 * Daemon Tools
 * %%
 * Copyright (C) 2012 - 2020 Andreas Veithen
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

import static com.google.common.truth.Truth.assertThat;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.Test;

public class JettyITCase {
    private interface Action {
        void run() throws Exception;
    }

    private static void withRetry(Action action) throws Exception {
        int retries = 0;
        while (true) {
            try {
                action.run();
                return;
            } catch (AssertionError ex) {
                if (retries < 50) {
                    retries++;
                    Thread.sleep(100);
                } else {
                    throw ex;
                }
            }
        }
    }

    @Test
    public void test() throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(
                String.format("http://localhost:%s/somefile.txt", System.getProperty("jetty.httpPort"))).openConnection();
        assertThat(c.getResponseCode()).isEqualTo(200);
        c.disconnect();
    
        withRetry(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream("target/request.log"), "utf-8"))) {
                assertThat(in.readLine()).contains("GET /somefile.txt");
                assertThat(in.readLine()).isNull();
            }
        });
    }
}
