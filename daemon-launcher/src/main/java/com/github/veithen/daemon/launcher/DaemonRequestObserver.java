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

import java.util.List;

import com.github.veithen.daemon.Daemon;
import com.github.veithen.daemon.grpc.Daemon.DaemonRequest;
import com.github.veithen.daemon.grpc.Daemon.DaemonResponse;
import com.github.veithen.daemon.grpc.Daemon.Ready;
import com.github.veithen.daemon.grpc.Daemon.Stopped;

import io.grpc.stub.StreamObserver;

final class DaemonRequestObserver implements StreamObserver<DaemonRequest> {
    private final StreamObserver<DaemonResponse> responseObserver;
    private Daemon daemon;

    DaemonRequestObserver(StreamObserver<DaemonResponse> responseObserver) {
        this.responseObserver = responseObserver;
    }

    @Override
    public void onNext(DaemonRequest request) {
        switch (request.getRequestCase()) {
            case START:
                {
                    try {
                        daemon =
                                (Daemon)
                                        Class.forName(request.getStart().getDaemonClass())
                                                .newInstance();
                        List<String> daemonArgs = request.getStart().getDaemonArgList();
                        daemon.init(
                                new DaemonContextImpl(
                                        daemonArgs.toArray(new String[daemonArgs.size()])));
                        daemon.start();
                        responseObserver.onNext(
                                DaemonResponse.newBuilder()
                                        .setReady(Ready.newBuilder().build())
                                        .build());
                    } catch (Throwable t) {
                        responseObserver.onError(t);
                    }
                    break;
                }
            case STOP:
                {
                    try {
                        daemon.stop();
                        daemon.destroy();
                        responseObserver.onNext(
                                DaemonResponse.newBuilder()
                                        .setStopped(Stopped.newBuilder().build())
                                        .build());
                    } catch (Throwable t) {
                        responseObserver.onError(t);
                    }
                }
        }
    }

    @Override
    public void onError(Throwable t) {
        t.printStackTrace();
        System.exit(1);
    }

    @Override
    public void onCompleted() {
        System.exit(0);
    }
}
