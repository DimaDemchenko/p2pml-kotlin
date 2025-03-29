package com.novage.p2pml.interop;

public interface EventListener<T> {
    void onEvent(T data);
}
