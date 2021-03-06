package com.jayway.awaitility;

import com.jayway.awaitility.core.ConditionTimeoutException;
import com.jayway.awaitility.core.DeadlockException;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static com.jayway.awaitility.Awaitility.await;
import static org.junit.Assert.*;

public class DeadlockDetectionTest {

    @Test(timeout = 2000L)
    public void deadlockTest() throws Exception {
        final ReentrantLock lock1 = new ReentrantLock();
        final ReentrantLock lock2 = new ReentrantLock();

        final AtomicBoolean locked = new AtomicBoolean();

        final Thread t1 = new Thread() {
            public void run() {
                lock1.lock();
                try {
                    try {
                        Thread.sleep(50);

                        // wait for lock2 or external interrupt() call
                        lock2.lockInterruptibly();
                        locked.set(true);
                    } catch (InterruptedException ignored) {
                    }
                } finally {
                    lock1.unlock();
                }
            }
        };

        final Thread t2 = new Thread(){
            public void run() {
                lock2.lock();
                try {
                    try {
                        Thread.sleep(50);

                        // wait for lock1 or external interrupt() call
                        lock1.lockInterruptibly();
                        locked.set(true);
                    } catch (InterruptedException ignored) {}
                } finally {
                    lock2.unlock();
                }
            }
        };

        // start both threads more or less at the same time to create the deadlock
        t1.start();
        t2.start();

        try {
            // wait for something that will never happen
            // i.e. something that will throw the expected ConditionTimeoutException
            await().atMost(Duration.TWO_HUNDRED_MILLISECONDS).untilTrue(locked);

            // ... and fail if the exception is not thrown
            fail("ConditionTimeoutException expected.");

        } catch (ConditionTimeoutException e) {
            // check that the thrown exception has a DeadlockException attached to it
            DeadlockException cause = (DeadlockException) e.getCause();
            assertTrue(cause instanceof DeadlockException);
            assertEquals(2, cause.getThreadInfos().length);

        } finally {
            // interrupt both threads to clean up the JVM
            t1.interrupt();
            t2.interrupt();

            // wait until both threads are finished
            await().atMost(Duration.ONE_SECOND).until(new Callable<Boolean>() {
                public Boolean call() throws Exception {
                    return !t1.isAlive() && !t2.isAlive();
                }
            });
        }
    }
}
