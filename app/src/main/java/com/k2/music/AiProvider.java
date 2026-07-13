package com.k2.music;

public interface AiProvider {
    interface RequestHandle {
        void cancel();
        boolean isCancelled();
    }

    interface Callback {
        void onSuccess(AiResponse response);
        void onError(AiError error);
    }

    RequestHandle send(AiRequest request, Callback callback);
}
