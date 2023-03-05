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

import java.io.File;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.StringUtils;

import com.github.veithen.daemon.launcher.proto.DaemonRequest;
import com.github.veithen.daemon.launcher.proto.DaemonResponse;
import com.github.veithen.daemon.launcher.proto.DaemonResponse.ResponseCase;
import com.github.veithen.daemon.launcher.proto.InitRequest;
import com.github.veithen.daemon.launcher.proto.InitResponse;
import com.github.veithen.daemon.launcher.proto.MessageReader;
import com.github.veithen.daemon.launcher.proto.MessageWriter;
import com.github.veithen.daemon.launcher.proto.StartRequest;
import com.github.veithen.daemon.launcher.proto.StartResponse;
import com.github.veithen.daemon.launcher.proto.StopRequest;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;

public class RemoteDaemon {
    private final Logger logger;
    private final String jvm;
    private final String[] vmArgs;
    private final File workDir;
    private final List<File> launcherClasspath;
    private final List<File> daemonClasspath;
    private final List<String> testClasspath;
    private final PlexusConfiguration configuration;
    private final ExpressionEvaluator expressionEvaluator;
    private final Map<String, Integer> ports;
    private Process process;
    private Socket controlSocket;
    private MessageWriter<DaemonRequest> controlWriter;
    private MessageReader<DaemonResponse, ResponseCase> controlReader;

    public RemoteDaemon(
            Logger logger,
            String jvm,
            String[] vmArgs,
            File workDir,
            List<File> launcherClasspath,
            List<File> daemonClasspath,
            List<String> testClasspath,
            PlexusConfiguration plexusConfiguration,
            ExpressionEvaluator expressionEvaluator,
            Map<String, Integer> ports) {
        this.logger = logger;
        this.jvm = jvm;
        this.vmArgs = vmArgs;
        this.workDir = workDir;
        this.launcherClasspath = launcherClasspath;
        this.daemonClasspath = daemonClasspath;
        this.testClasspath = testClasspath;
        this.configuration = plexusConfiguration;
        this.expressionEvaluator = expressionEvaluator;
        this.ports = ports;
    }

    public Process getProcess() {
        return process;
    }

    public Map<String, Integer> startDaemon() throws Throwable {
        ServerSocket controlServerSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        try {
            controlServerSocket.setSoTimeout(100);
            List<String> cmdline = new ArrayList<>();
            cmdline.add(jvm);
            cmdline.add("-cp");
            cmdline.add(StringUtils.join(launcherClasspath.iterator(), File.pathSeparator));
            cmdline.addAll(Arrays.asList(vmArgs));
            cmdline.add("com.github.veithen.daemon.launcher.Launcher");
            cmdline.add(String.valueOf(controlServerSocket.getLocalPort()));

            if (logger.isDebugEnabled()) {
                logger.debug("Starting process with command line: " + cmdline);
            }
            process =
                    Runtime.getRuntime()
                            .exec(cmdline.toArray(new String[cmdline.size()]), null, workDir);
            new Thread(new StreamPump(process.getInputStream(), logger, "[STDOUT] ")).start();
            new Thread(new StreamPump(process.getErrorStream(), logger, "[STDERR] ")).start();
            logger.debug(
                    "Waiting for control connection on port " + controlServerSocket.getLocalPort());
            while (true) {
                try {
                    controlSocket = controlServerSocket.accept();
                    break;
                } catch (SocketTimeoutException ex) {
                    try {
                        int exitValue = process.exitValue();
                        throw new IllegalStateException(
                                "Process terminated prematurely with exit code " + exitValue);
                    } catch (IllegalThreadStateException ex2) {
                        // Process is still running; continue
                    }
                }
            }
        } finally {
            controlServerSocket.close();
        }
        logger.debug("Control connection established");
        controlWriter = new MessageWriter<DaemonRequest>(controlSocket.getOutputStream());
        controlReader =
                new MessageReader<>(
                        controlSocket.getInputStream(),
                        DaemonResponse.parser(),
                        DaemonResponse::getResponseCase);

        controlWriter.write(
                DaemonRequest.newBuilder()
                        .setInit(
                                InitRequest.newBuilder()
                                        .addAllClasspathEntry(
                                                daemonClasspath.stream()
                                                        .map(File::toString)
                                                        .collect(Collectors.toList()))
                                        .build())
                        .build());
        logger.debug("Awaiting initialization");
        InitResponse initResponse = controlReader.read(ResponseCase.INIT).getInit();
        Descriptor descriptor =
                FileDescriptor.buildFrom(initResponse.getFileDescriptor(), new FileDescriptor[0])
                        .findMessageTypeByName(initResponse.getConfigurationType());
        if (descriptor == null) {
            throw new Error("Unable to find descriptor for " + initResponse.getConfigurationType());
        }
        controlWriter.write(
                DaemonRequest.newBuilder()
                        .setStart(
                                StartRequest.newBuilder()
                                        .setConfiguration(
                                                PlexusConfigurationConverter.convert(
                                                                configuration,
                                                                expressionEvaluator,
                                                                descriptor)
                                                        .toByteString())
                                        .addAllTestClasspathEntry(testClasspath)
                                        .putAllPorts(ports)
                                        .build())
                        .build());
        logger.debug("Waiting for daemon to become ready");
        StartResponse startResponse = controlReader.read(ResponseCase.START).getStart();
        logger.debug("Daemon is ready");
        return startResponse.getPortsMap();
    }

    public void stopDaemon() throws Throwable {
        controlWriter.write(
                DaemonRequest.newBuilder().setStop(StopRequest.getDefaultInstance()).build());
        controlReader.read(ResponseCase.STOP);
        controlSocket.close();
    }
}
