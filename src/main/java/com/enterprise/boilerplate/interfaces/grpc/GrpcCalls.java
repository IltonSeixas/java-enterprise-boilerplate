package com.enterprise.boilerplate.interfaces.grpc;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.function.Supplier;

/**
 * Wraps a unary gRPC handler body, mapping thrown exceptions to {@link io.grpc.StatusRuntimeException}
 * via {@link GrpcExceptionMapper} so individual service implementations stay focused on their use case calls.
 */
final class GrpcCalls {

    private GrpcCalls() {
    }

    static <T> void handle(StreamObserver<T> responseObserver, Supplier<T> action) {
        try {
            T response = action.get();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            responseObserver.onError(GrpcExceptionMapper.toStatusException(e));
        }
    }
}
