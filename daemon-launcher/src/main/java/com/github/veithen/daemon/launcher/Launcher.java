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
package com.github.veithen.daemon.launcher;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;

/**
 * Main class to launch and control a {@link Daemon} implementation. This class is typically
 * executed in a child JVM and allows the parent process to control the lifecycle of the daemon
 * instance. The main method takes the following arguments:
 *
 * <ol>
 *   <li>The class name of the {@link Daemon} implementation.
 *   <li>A TCP port number to use for the control connection.
 * </ol>
 *
 * All remaining arguments are passed to the {@link Daemon} implementation.
 *
 * <p>The class uses the following protocol to allow the parent process to control the lifecycle of
 * the daemon:
 *
 * <ol>
 *   <li>The parent process spawns a new child JVM with this class as main class. It passes the
 *       class name of the {@link Daemon} implementation and the control port as arguments (see
 *       above).
 *   <li>The child process opens the specified TCP port and waits for the control connection to be
 *       established.
 *   <li>The parent process connects to the control port.
 *   <li>The child process {@link Daemon#init(DaemonContext) initializes} and {@link Daemon#start()
 *       starts} the daemon.
 *   <li>The child process sends a {@code READY} message over the control connection to the parent
 *       process.
 *   <li>When the parent process no longer needs the daemon, it sends a {@code STOP} message to the
 *       child process.
 *   <li>The child process {@link Daemon#stop() stops} and {@link Daemon#destroy() destroys} the
 *       daemon.
 *   <li>The child process sends a {@code STOPPED} message to the parent process, closes the control
 *       connection and terminates itself.
 *   <li>The parent process closes the control connection.
 * </ol>
 *
 * @author Andreas Veithen
 */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        try {
            String daemonClass = args[0];
            int controlPort = Integer.parseInt(args[1]);
            String[] daemonArgs = new String[args.length - 2];
            System.arraycopy(args, 2, daemonArgs, 0, args.length - 2);
            ServerSocket controlServerSocket = new ServerSocket();
            controlServerSocket.bind(
                    new InetSocketAddress(InetAddress.getByName("localhost"), controlPort));
            Socket controlSocket = controlServerSocket.accept();
            // We only accept a single connection; therefore we can close the ServerSocket here
            controlServerSocket.close();
            ControlConnectionReader controlIn =
                    new ControlConnectionReader(
                            new InputStreamReader(controlSocket.getInputStream(), "ASCII"));
            new Thread(controlIn).start();
            Writer controlOut = new OutputStreamWriter(controlSocket.getOutputStream(), "ASCII");
            Daemon daemon = (Daemon) Class.forName(daemonClass).newInstance();
            daemon.init(new DaemonContextImpl(daemonArgs));
            daemon.start();
            controlOut.write("READY\r\n");
            controlOut.flush();
            String request = controlIn.awaitMessage();
            if (request.equals("STOP")) {
                daemon.stop();
                daemon.destroy();
                controlIn.expectClose();
                controlOut.write("STOPPED\r\n");
                controlOut.flush();
                System.exit(0);
            } else {
                throw new LauncherException("Unexpected request: " + request);
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
