package edu.itc.se.lab01.task1;

import java.util.List;

public class Main {  
    
    // Declared static so it can be instantiated from static main
    static class Countdown extends Thread {
        String name;

        public Countdown(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            System.out.println(name);
            for (int i = 0; i < 3; i++) {
                System.out.println(i + 1);   
                }
            try {
                Thread.sleep(200);
                System.out.println("left it");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Best practice: restore interrupted flag
            }
        }
    }

    // Declared static so it can be instantiated from static main
    static class CountdownTask implements Runnable {
        String name;

        public CountdownTask(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            System.out.println(name);
            for (int i = 0; i < 3; i++) {
                System.out.println(i + 1);   
                }
            try {
                Thread.sleep(200);
                System.out.println("left it");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Best practice: restore interrupted flag
            }
        }
    }

    // Must be static to serve as the entry point
    public static void main(String[] args) throws InterruptedException {  
boolean sequential = args.length > 0 && args[0].equals("--sequential");

        Thread apollo  = new Countdown("apollo");
        Thread gemini  = new Thread(new CountdownTask("gemini"), "gemini");
        Thread mercury = new Thread(() -> new CountdownTask("mercury").run(), "mercury");
        Thread artemis = Thread.ofVirtual().name("artemis").unstarted(new CountdownTask("artemis"));

        
        List<Thread> threads = List.of(apollo, gemini, mercury, artemis);

        // TODO: start (or run) all four depending on 'sequential'
        if (sequential) {
            for (Thread t : threads) {
                t.run(); // Executes sequentially on the main thread
            }
        } else {
            for (Thread t : threads) {
                t.start(); // Runs concurrently on separate threads
            }
        }

        System.out.println("Mission control: all launched");

        // TODO: join all four
        if (!sequential) {
            for (Thread t : threads) {
                t.join();
            }
        }

        System.out.println("Mission control: all landed");

        // TODO: print name, isVirtual(), getState() of each
        for (Thread t : threads) {
            System.out.printf("%-8s virtual=%-5b state=%s%n",
                    t.getName(),
                    t.isVirtual(),
                    t.getState());
        }
    }  
}