package edu.itc.se.lab01.task5;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class TryLockBank {
    static final class Account {
        final int id; 
        private long balance;
        final ReentrantLock lock = new ReentrantLock(); // Replaces intrinsic monitor
        
        Account(int id, long balance) { this.id = id; this.balance = balance; }
        long balance() { return balance; }
        void withdraw(long a) { balance -= a; }
        void deposit(long a)  { balance += a; }
    }

    static final AtomicInteger retryCount = new AtomicInteger(0);

    // FIX B: tryLock with timeouts and random back-off
    static boolean transfer(Account from, Account to, long amount) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (from.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                    try {
                        if (to.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                            try {
                                if (from.balance() < amount) {
                                    return false;
                                }
                                from.withdraw(amount);
                                to.deposit(amount);
                                return true;
                            } finally {
                                to.lock.unlock();
                            }
                        }
                    } finally {
                        from.lock.unlock();
                    }
                }
                
                // Back-off if we couldn't get both locks
                retryCount.incrementAndGet();
                Thread.sleep(rnd.nextInt(1, 10)); // Random sleep prevents livelock
                
            } catch (InterruptedException e) {
                // STEP 6: Restore the interrupt flag when a blocking call throws
                Thread.currentThread().interrupt();
            }
        }
        return false;
    }

    public static void main(String[] args) throws InterruptedException {
        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new Account(i, 1_000);

        AtomicInteger completedTransfers = new AtomicInteger(0);
        Thread[] workers = new Thread[8];
        
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                while (!Thread.currentThread().isInterrupted()) {
                    Account a = accounts[rnd.nextInt(accounts.length)];
                    Account b = accounts[rnd.nextInt(accounts.length)];
                    if (a != b) {
                        if (transfer(a, b, rnd.nextInt(1, 50))) {
                            completedTransfers.incrementAndGet();
                        }
                    }
                }
            }, "transfer-" + w);
            workers[w].start();
        }

        Thread.sleep(2000);
        for (Thread t : workers) t.interrupt();
        for (Thread t : workers) t.join();

        long total = 0; 
        for (Account a : accounts) total += a.balance();
        
        System.out.println("Total Balance = " + total + " (expected 4000)");
        System.out.println("Completed Transfers = " + completedTransfers.get());
        System.out.println("Total Lock Retries  = " + retryCount.get());
    }
}