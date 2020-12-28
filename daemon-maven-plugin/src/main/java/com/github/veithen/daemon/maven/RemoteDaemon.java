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
package com.github.veithen.daemon.maven;

import java.util.Arrays;

import org.codehaus.plexus.logging.Logger;

import com.github.veithen.daemon.grpc.Daemon.DaemonRequest;
import com.github.veithen.daemon.grpc.Daemon.DaemonResponse.ResponseCase;
import com.github.veithen.daemon.grpc.Daemon.Start;
import com.github.veithen.daemon.grpc.Daemon.Stop;
import com.github.veithen.daemon.grpc.DaemonLauncherGrpc;
import com.github.veithen.daemon.grpc.DaemonLauncherGrpc.DaemonLauncherStub;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class RemoteDaemon {
    private final Process process;
    private final String description;
    private final int controlPort;
    private final String daemonClass;
    private final String[] daemonArgs;
    private ManagedChannel channel;
    private StreamObserver<DaemonRequest> requestObserver;
    private DaemonResponseObserver responseObserver;

    public RemoteDaemon(
            Process process,
            String description,
            int controlPort,
            String daemonClass,
            String[] daemonArgs) {
        this.process = process;
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

    public void startDaemon(Logger logger) throws Throwable {
        logger.debug("Attempting to establish control connection on port " + controlPort);
        channel = ManagedChannelBuilder.forAddress("localhost", controlPort).usePlaintext().build();
        DaemonLauncherStub stub = DaemonLauncherGrpc.newStub(channel).withWaitForReady();
        responseObserver = new DaemonResponseObserver();
        requestObserver = stub.runDaemon(responseObserver);
        requestObserver.onNext(
                DaemonRequest.newBuilder()
                        .setStart(
                                Start.newBuilder()
                                        .setDaemonClass(daemonClass)
                                        .addAllDaemonArg(Arrays.asList(daemonArgs))
                                        .build())
                        .build());
        logger.debug("Waiting for daemon to become ready");
        responseObserver.read(ResponseCase.READY);
        logger.debug("Daemon is ready");
    }

    public void stopDaemon(Logger logger) throws Throwable {
        requestObserver.onNext(
                DaemonRequest.newBuilder().setStop(Stop.newBuilder().build()).build());
        responseObserver.read(ResponseCase.STOPPED);
        requestObserver.onCompleted();
        channel.shutdown();
    }
}
