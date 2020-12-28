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
package com.github.veithen.daemon.maven;

import com.github.veithen.daemon.grpc.Daemon.DaemonResponse;
import com.github.veithen.daemon.grpc.Daemon.DaemonResponse.ResponseCase;

import io.grpc.stub.StreamObserver;

final class DaemonResponseObserver implements StreamObserver<DaemonResponse> {
    private interface ResponseProvider {
        DaemonResponse get() throws Throwable;
    }

    private ResponseProvider responseProvider;

    private synchronized void setResponse(ResponseProvider responseProvider) {
        while (this.responseProvider != null) {
            try {
                wait();
            } catch (InterruptedException ex) {
                Thread.interrupted();
                return;
            }
        }
        this.responseProvider = responseProvider;
        notifyAll();
    }

    @Override
    public void onNext(DaemonResponse response) {
        setResponse(() -> response);
    }

    @Override
    public void onError(Throwable t) {
        setResponse(
                () -> {
                    throw t;
                });
    }

    @Override
    public void onCompleted() {
        setResponse(() -> null);
    }

    synchronized DaemonResponse read(ResponseCase expectedResponseCase) throws Throwable {
        while (this.responseProvider == null) {
            wait();
        }
        ResponseProvider responseProvider = this.responseProvider;
        this.responseProvider = null;
        notifyAll();
        DaemonResponse response = responseProvider.get();
        if (response == null) {
            throw new IllegalStateException("Unexpected end of stream");
        }
        if (response.getResponseCase() != expectedResponseCase) {
            throw new IllegalStateException("Unexpected response");
        }
        return response;
    }
}
