package com.smartlab.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class PostMediaCache {
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final long MAX_ENTRY_BYTES = 5L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 32L * 1024 * 1024;
    private final Clock clock;
    private final Duration ttl;
    private final long maxEntryBytes;
    private final long maxTotalBytes;
    private final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private final ConcurrentHashMap<Long, CompletableFuture<byte[]>> inFlight = new ConcurrentHashMap<>();
    private long totalBytes;

    public PostMediaCache() { this(Clock.systemUTC(), TTL, MAX_ENTRY_BYTES, MAX_TOTAL_BYTES); }
    PostMediaCache(Clock clock, Duration ttl, long maxEntryBytes, long maxTotalBytes) {
        this.clock = clock; this.ttl = ttl; this.maxEntryBytes = maxEntryBytes; this.maxTotalBytes = maxTotalBytes;
    }
    public byte[] getOrLoad(Long fileId, Supplier<byte[]> loader) {
        synchronized (entries) {
            Entry entry = entries.get(fileId);
            if (entry != null && entry.expiresAt <= clock.millis()) { totalBytes -= entry.bytes.length; entries.remove(fileId); entry = null; }
            if (entry != null) return entry.bytes;
        }
        CompletableFuture<byte[]> created = new CompletableFuture<>();
        CompletableFuture<byte[]> future = inFlight.putIfAbsent(fileId, created);
        if (future == null) {
            future = created;
            try {
                byte[] bytes = loader.get();
                if (bytes.length <= maxEntryBytes) put(fileId, bytes);
                created.complete(bytes);
            } catch (Throwable error) { created.completeExceptionally(error); }
            finally { inFlight.remove(fileId, created); }
        }
        try { return future.join(); }
        catch (java.util.concurrent.CompletionException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            if (error.getCause() instanceof Error fatal) throw fatal;
            throw error;
        }
    }
    private void put(Long id, byte[] bytes) {
        synchronized (entries) {
            while (!entries.isEmpty() && totalBytes + bytes.length > maxTotalBytes) {
                Entry evicted = entries.remove(entries.keySet().iterator().next()); totalBytes -= evicted.bytes.length;
            }
            if (bytes.length <= maxTotalBytes) { entries.put(id, new Entry(bytes, clock.millis() + ttl.toMillis())); totalBytes += bytes.length; }
        }
    }
    private record Entry(byte[] bytes, long expiresAt) {}
}
