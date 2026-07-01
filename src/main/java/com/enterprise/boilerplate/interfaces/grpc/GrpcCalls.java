package com.enterprise.boilerplate.interfaces.grpc;

import io.grpc.stub.StreamObserver;

import java.util.function.Supplier;
final class GrpcCalls {

    private GrpcCalls() {
    }

    static <T> void handle(StreamObserver<T> responseObserver, Supplier<T> action) {
        try {
            T response = action.get();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcExceptionMapper.toStatusException(e));
        }
    }
}
