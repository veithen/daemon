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
package com.github.veithen.daemon.launcher;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import com.github.veithen.daemon.Daemon;
import com.github.veithen.daemon.DaemonContext;
import com.github.veithen.daemon.launcher.proto.DaemonRequest;
import com.github.veithen.daemon.launcher.proto.DaemonRequest.RequestCase;
import com.github.veithen.daemon.launcher.proto.DaemonResponse;
import com.github.veithen.daemon.launcher.proto.InitRequest;
import com.github.veithen.daemon.launcher.proto.InitResponse;
import com.github.veithen.daemon.launcher.proto.MessageReader;
import com.github.veithen.daemon.launcher.proto.MessageWriter;
import com.github.veithen.daemon.launcher.proto.StartRequest;
import com.github.veithen.daemon.launcher.proto.StartResponse;
import com.github.veithen.daemon.launcher.proto.StopResponse;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;

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
 *   <li>The child process {@link Daemon#init(Message, DaemonContext) initializes} and {@link
 *       Daemon#start() starts} the daemon.
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
 */
public final class Launcher {
    private Launcher() {}

    private static URL[] toURLs(List<String> classpathEntries) {
        return classpathEntries.stream()
                .map(
                        s -> {
                            try {
                                return Paths.get(s).toUri().toURL();
                            } catch (MalformedURLException ex) {
                                throw new Error(ex);
                            }
                        })
                .toArray(URL[]::new);
    }

    private static <T extends Message> void initDaemon(
            Daemon<T> daemon, Object configuration, DaemonContext daemonContext) throws Exception {
        daemon.init(daemon.getConfigurationType().cast(configuration), daemonContext);
    }

    public static void main(String[] args) {
        try {
            int controlPort = Integer.parseInt(args[0]);
            Socket controlSocket = new Socket(InetAddress.getLoopbackAddress(), controlPort);
            MessageReader<DaemonRequest, RequestCase> reader =
                    new MessageReader<>(
                            controlSocket.getInputStream(),
                            DaemonRequest.parser(),
                            DaemonRequest::getRequestCase);
            MessageWriter<DaemonResponse> writer =
                    new MessageWriter<>(controlSocket.getOutputStream());

            InitRequest initRequest = reader.read(RequestCase.INIT).getInit();
            URLClassLoader classLoader =
                    new URLClassLoader(toURLs(initRequest.getClasspathEntryList()));
            Thread.currentThread().setContextClassLoader(classLoader);
            Iterator<Daemon> it = ServiceLoader.load(Daemon.class, classLoader).iterator();
            if (!it.hasNext()) {
                throw new LauncherException("Daemon class not found");
            }
            Daemon<?> daemon = it.next();
            if (it.hasNext()) {
                throw new LauncherException("More than one daemon class found");
            }
            Class<? extends Message> configurationType = daemon.getConfigurationType();
            Descriptor descriptor =
                    (Descriptor) configurationType.getMethod("getDescriptor").invoke(null);
            writer.write(
                    DaemonResponse.newBuilder()
                            .setInit(
                                    InitResponse.newBuilder()
                                            .setConfigurationType(descriptor.getFullName())
                                            .setFileDescriptor(descriptor.getFile().toProto())
                                            .build())
                            .build());

            StartRequest startRequest = reader.read(RequestCase.START).getStart();
            initDaemon(
                    daemon,
                    ((Parser<?>) configurationType.getMethod("parser").invoke(null))
                            .parseFrom(startRequest.getConfiguration()),
                    new DaemonContextImpl(
                            toURLs(startRequest.getTestClasspathEntryList()),
                            startRequest.getPortsMap()));
            Map<String, Integer> ports = daemon.start();
            writer.write(
                    DaemonResponse.newBuilder()
                            .setStart(StartResponse.newBuilder().putAllPorts(ports))
                            .build());

            reader.read(RequestCase.STOP);
            daemon.stop();
            daemon.destroy();
            writer.write(
                    DaemonResponse.newBuilder().setStop(StopResponse.getDefaultInstance()).build());
            System.exit(0);
        } catch (Throwable ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
