package com.smartlab.service;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class PostMediaCacheTest {
    @Test void missCallsLoaderOnceAndReturnsBytes() { PostMediaCache c=cache(10,20); AtomicInteger n=new AtomicInteger(); assertThat(c.getOrLoad(1L,()->{n.incrementAndGet();return new byte[]{1};})).containsExactly((byte)1); assertThat(n).hasValue(1); }
    @Test void hitDoesNotCallLoaderAgain() { PostMediaCache c=cache(10,20); AtomicInteger n=new AtomicInteger(); c.getOrLoad(1L,()->{n.incrementAndGet();return new byte[]{1};}); c.getOrLoad(1L,()->{n.incrementAndGet();return new byte[]{2};}); assertThat(n).hasValue(1); }
    @Test void entryLargerThanPerEntryLimitIsNotCached() { PostMediaCache c=cache(1,20); AtomicInteger n=new AtomicInteger(); c.getOrLoad(1L,()->{n.incrementAndGet();return new byte[2];}); c.getOrLoad(1L,()->{n.incrementAndGet();return new byte[2];}); assertThat(n).hasValue(2); }
    @Test void lruEvictsLeastRecentlyUsedWhenTotalCapExceeded() { PostMediaCache c=cache(5,2); AtomicInteger n=new AtomicInteger(); c.getOrLoad(1L,()->{n.incrementAndGet();return new byte[1];}); c.getOrLoad(2L,()->{n.incrementAndGet();return new byte[1];}); c.getOrLoad(1L,()->{n.incrementAndGet();return new byte[1];}); c.getOrLoad(3L,()->{n.incrementAndGet();return new byte[1];}); c.getOrLoad(2L,()->{n.incrementAndGet();return new byte[1];}); assertThat(n).hasValue(4); }
    @Test void loaderFailureIsNotCachedOrLeftInFlight() { PostMediaCache c=cache(5,5); assertThatThrownBy(()->c.getOrLoad(1L,()->{throw new IllegalStateException("x");})).isInstanceOf(IllegalStateException.class); assertThat(c.getOrLoad(1L,()->new byte[]{1})).containsExactly((byte)1); }
    @Test void expiredEntryReloadsUsingInjectedClock() { MutableClock clock=new MutableClock(); PostMediaCache c=new PostMediaCache(clock,Duration.ofSeconds(1),5,5); AtomicInteger n=new AtomicInteger(); c.getOrLoad(1L,()->new byte[]{(byte)n.incrementAndGet()}); clock.advance(Duration.ofSeconds(2)); assertThat(c.getOrLoad(1L,()->new byte[]{(byte)n.incrementAndGet()})).containsExactly((byte)2); }
    @Test void concurrentSameFileMissesUseOneLoaderAndAllReceiveBytes() throws Exception { PostMediaCache c=cache(5,5); AtomicInteger n=new AtomicInteger(); CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1); ExecutorService e=Executors.newFixedThreadPool(3); Callable<byte[]> call=()->c.getOrLoad(1L,()->{n.incrementAndGet();entered.countDown(); try { release.await(); } catch(InterruptedException x){throw new RuntimeException(x);} return new byte[]{7};}); Future<byte[]> a=e.submit(call); entered.await(); Future<byte[]> b=e.submit(call), d=e.submit(call); release.countDown(); assertThat(a.get()).containsExactly((byte)7); assertThat(b.get()).containsExactly((byte)7); assertThat(d.get()).containsExactly((byte)7); assertThat(n).hasValue(1); e.shutdownNow(); }
    private static PostMediaCache cache(long entry,long total) { return new PostMediaCache(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), Duration.ofMinutes(1),entry,total); }
    private static final class MutableClock extends Clock { private Instant now=Instant.EPOCH; public ZoneOffset getZone(){return ZoneOffset.UTC;} public Clock withZone(java.time.ZoneId z){return this;} public Instant instant(){return now;} void advance(Duration d){now=now.plus(d);} }
}
