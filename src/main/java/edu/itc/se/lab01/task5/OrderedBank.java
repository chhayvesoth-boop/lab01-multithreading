package edu.itc.se.lab01.task5;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class OrderedBank {
    static final class Account {
        final int id; 
        private long balance;
        Account(int id, long balance) { this.id = id; this.balance = balance; }
        long balance() { return balance; }
        void withdraw(long a) { balance -= a; }
        void deposit(long a)  { balance += a; }
    }

    // FIX A: Always lock the account with the smaller ID first
    static void transfer(Account from, Account to, long amount) {
        Account first = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to : from;
        
        synchronized (first) {
            synchronized (second) {
                if (from.balance() >= amount) { 
                    from.withdraw(amount); 
                    to.deposit(amount); 
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new Account(i, 1_000);

        AtomicInteger completedTransfers = new AtomicInteger(0);
        Thread[] workers = new Thread[8];
        
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                // STEP 6: Loop until interrupted
                while (!Thread.currentThread().isInterrupted()) {
                    Account a = accounts[rnd.nextInt(accounts.length)];
                    Account b = accounts[rnd.nextInt(accounts.length)];
                    if (a != b) {
                        transfer(a, b, rnd.nextInt(1, 50));
                        completedTransfers.incrementAndGet();
                    }
                }
            }, "transfer-" + w);
            workers[w].start();
        }

        // STEP 6: Main thread sleeps for 2 seconds, then interrupts workers
        Thread.sleep(2000);
        for (Thread t : workers) t.interrupt();
        
        // Join with a 1-second timeout
        for (Thread t : workers) t.join(1000);

        long total = 0; 
        for (Account a : accounts) total += a.balance();
        
        System.out.println("Total Balance = " + total + " (expected 4000)");
        System.out.println("Completed Transfers = " + completedTransfers.get());
    }
}