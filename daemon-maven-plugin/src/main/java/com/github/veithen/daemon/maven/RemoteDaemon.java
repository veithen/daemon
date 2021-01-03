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
package com.github.veithen.daemon.maven;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;

import org.codehaus.plexus.logging.Logger;

import com.github.veithen.daemon.launcher.proto.DaemonRequest;
import com.github.veithen.daemon.launcher.proto.DaemonResponse;
import com.github.veithen.daemon.launcher.proto.DaemonResponse.ResponseCase;
import com.github.veithen.daemon.launcher.proto.Initialize;
import com.github.veithen.daemon.launcher.proto.MessageReader;
import com.github.veithen.daemon.launcher.proto.MessageWriter;
import com.github.veithen.daemon.launcher.proto.Start;
import com.github.veithen.daemon.launcher.proto.Stop;

public class RemoteDaemon {
    private final Logger logger;
    private final String[] cmdline;
    private final File workDir;
    private final String description;
    private final int controlPort;
    private final String daemonClass;
    private final String[] daemonArgs;
    private Process process;
    private Socket controlSocket;
    private MessageWriter<DaemonRequest> controlWriter;
    private MessageReader<DaemonResponse, ResponseCase> controlReader;

    public RemoteDaemon(
            Logger logger,
            String[] cmdline,
            File workDir,
            String description,
            int controlPort,
            String daemonClass,
            String[] daemonArgs) {
        this.logger = logger;
        this.cmdline = cmdline;
        this.workDir = workDir;
        this.description = description;
        this.controlPort = controlPort;
        this.daemonClass = daemonClass;
        this.daemonArgs = daemonArgs;
    }

    public Process getProcess() {
        return process;
    }

    public String getDescription() {
        return description;
    }

    public void startDaemon() throws Throwable {
        if (logger.isDebugEnabled()) {
            logger.debug("Starting process with command line: " + Arrays.asList(cmdline));
        }
        process = Runtime.getRuntime().exec(cmdline, null, workDir);
        new Thread(new StreamPump(process.getInputStream(), logger, "[STDOUT] ")).start();
        new Thread(new StreamPump(process.getErrorStream(), logger, "[STDERR] ")).start();
        logger.debug("Attempting to establish control connection on port " + controlPort);
        while (true) {
            try {
                controlSocket = new Socket(InetAddress.getByName("localhost"), controlPort);
                break;
            } catch (IOException ex) {
                try {
                    int exitValue = process.exitValue();
                    throw new IllegalStateException(
                            "Process terminated prematurely with exit code " + exitValue);
                } catch (IllegalThreadStateException ex2) {
                    // Process is still running; continue
                }
                Thread.sleep(100);
            }
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
                        .setInitialize(Initialize.newBuilder().setDaemonClass(daemonClass).build())
                        .build());
        logger.debug("Awaiting initialization");
        controlReader.read(ResponseCase.INITIALIZED);
        controlWriter.write(
                DaemonRequest.newBuilder()
                        .setStart(
                                Start.newBuilder()
                                        .addAllDaemonArg(Arrays.asList(daemonArgs))
                                        .build())
                        .build());
        logger.debug("Waiting for daemon to become ready");
        controlReader.read(ResponseCase.READY);
        logger.debug("Daemon is ready");
    }

    public void stopDaemon() throws Throwable {
        controlWriter.write(DaemonRequest.newBuilder().setStop(Stop.getDefaultInstance()).build());
        controlReader.read(ResponseCase.STOPPED);
        controlSocket.close();
    }
}
