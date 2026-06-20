package dev.ysm.architectury.event;

/**
 * Stub for dev.ysm.architectury.event.EventFactory.
 */
public class EventFactory {
    public static <T> Event<T> createEventResult() {
        return new Event<>();
    }
}
