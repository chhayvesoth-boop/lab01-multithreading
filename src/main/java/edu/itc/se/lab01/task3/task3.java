package edu.itc.se.lab01.task3;
import java.util.ArrayDeque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class task3 {

    // 1. Order record
    public record Order(int id, String item, int qty) {}

    // Common interface
    public interface OrderQueue {
        void put(Order order) throws InterruptedException;
        Order take() throws InterruptedException;
    }

    // Adapter for java.util.concurrent queue
    public static final class BlockingOrderQueue implements OrderQueue {
        private final BlockingQueue<Order> q;
        public BlockingOrderQueue(int capacity) { 
            this.q = new ArrayBlockingQueue<>(capacity); 
        }
        @Override public void put(Order o) throws InterruptedException { q.put(o); }
        @Override public Order take() throws InterruptedException { return q.take(); }
    }

    // 2. Custom BoundedBuffer implementation
    public static class BoundedBuffer implements OrderQueue {
        private final ArrayDeque<Order> items = new ArrayDeque<>();
        private final int capacity;

        public BoundedBuffer(int capacity) { 
            this.capacity = capacity; 
        }

        @Override 
        public synchronized void put(Order order) throws InterruptedException {
            while (items.size() == capacity) {
                wait();
            }
            assert items.size() < capacity : "Buffer overflow!";
            items.addLast(order);
            notifyAll();
        }

        @Override 
        public synchronized Order take() throws InterruptedException {
            while (items.isEmpty()) {
                wait();
            }
            assert !items.isEmpty() : "Buffer underflow!";
            Order order = items.removeFirst();
            notifyAll();
            return order;
        }
    }

    // 3. Producer: produces continuous order IDs + consumer poison pills
    public static class Producer implements Runnable {
        private final int startId;
        private final OrderQueue queue;
        private final int count;
        private final int poisonPills;

        public Producer(int startId, OrderQueue queue, int count, int poisonPills) {
            this.startId = startId;
            this.queue = queue;
            this.count = count;
            this.poisonPills = poisonPills;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < count; i++) {
                    int orderId = startId + i;
                    int qty = ThreadLocalRandom.current().nextInt(1, 6);
                    queue.put(new Order(orderId, "item-" + orderId, qty));
                    Thread.sleep(ThreadLocalRandom.current().nextInt(0, 21));
                }
                for (int i = 0; i < poisonPills; i++) {
                    queue.put(new Order(-1, "POISON_PILL", 0));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // 4. Consumer: processes orders and shuts down on poison pill
    public static class Consumer implements Runnable {
        private final int consumerId;
        private final OrderQueue queue;
        private final AtomicInteger processedCounter;

        public Consumer(int consumerId, OrderQueue queue, AtomicInteger processedCounter) {
            this.consumerId = consumerId;
            this.queue = queue;
            this.processedCounter = processedCounter;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Order order = queue.take();
                    if (order.id() == -1) {
                        System.out.println("consumer-" + consumerId + " received poison pill, stopping");
                        break;
                    }
                    Thread.sleep(ThreadLocalRandom.current().nextInt(5, 31));
                    System.out.println("consumer-" + consumerId + " processed Order[id=" 
                            + order.id() + ", item=" + order.item() + ", qty=" + order.qty() + "]");
                    processedCounter.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Shared orchestrator to execute the 2 producers x 50 and 3 consumers
    public static void runSimulation(OrderQueue queue) throws InterruptedException {
        int numProducers = 2;
        int ordersPerProducer = 50;
        int numConsumers = 3;

        AtomicInteger totalProcessed = new AtomicInteger(0);
        long start = System.currentTimeMillis();

        Thread[] consumers = new Thread[numConsumers];
        for (int i = 0; i < numConsumers; i++) {
            consumers[i] = new Thread(new Consumer(i + 1, queue, totalProcessed));
            consumers[i].start();
        }

        int basePills = numConsumers / numProducers;
        int extraPills = numConsumers % numProducers;

        Thread[] producers = new Thread[numProducers];
        for (int i = 0; i < numProducers; i++) {
            int startId = i * ordersPerProducer + 1; // P1 gets 1..50, P2 gets 51..100
            int pills = basePills + (i < extraPills ? 1 : 0);
            producers[i] = new Thread(new Producer(startId, queue, ordersPerProducer, pills));
            producers[i].start();
        }

        for (Thread p : producers) p.join();
        for (Thread c : consumers) c.join();

        long duration = System.currentTimeMillis() - start;
        System.out.println("Total processed: " + totalProcessed.get() 
                + " (expected " + (numProducers * ordersPerProducer) + ") in " + duration + " ms");

        assert totalProcessed.get() == (numProducers * ordersPerProducer) : "Mismatch in processed orders!";
    }

    // 5. Run using BoundedBuffer
    public static void main(String[] args) throws InterruptedException {
        runSimulation(new BoundedBuffer(5));
    }

    // 6. Run using BlockingOrderQueue
    public static class Main2 {
        public static void main(String[] args) throws InterruptedException {
            runSimulation(new BlockingOrderQueue(5));
        }
    }
}