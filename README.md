# Concurrency Experiment: `while` vs `if` and `notifyAll()` vs `notify()`

### 1. Replacing `while` with `if` (Spurious Wakeups & Lost Conditions)
* **What breaks:** Thrown assertions/exceptions (`NoSuchElementException` or capacity breaches).
* **Why:** In Java, when a thread unblocks from `wait()`, it does not run immediately; it moves to the blocked set waiting to reacquire the monitor lock. By the time it acquires the lock, another thread may have altered the queue state. 
  - If a consumer checks `if (items.isEmpty()) wait();`, another consumer may have acquired the lock first and taken the only available item. The unblocked consumer resumes without re-evaluating the condition and attempts `items.removeFirst()` on an empty deque, violating the assertion and throwing an exception.
  - A `while` loop re-checks the guard predicate upon waking to guard against race conditions and spurious wakeups.

### 2. Replacing `notifyAll()` with `notify()` (Signal Stealing & Deadlock)
* **What breaks:** The system hangs indefinitely (deadlock/livelock) before reaching 100 processed items.
* **Why:** A single object monitor lock manages a unified wait-set containing both waiting producers and waiting consumers. `notify()` arbitrarily selects one single thread to wake up:
  1. Assume the buffer is at capacity (5/5) and both producers are waiting.
  2. A consumer takes an item and calls `notify()`.
  3. Instead of waking an awaiting producer, `notify()` wakes another consumer.
  4. That consumer finds the remaining items consumed (or buffer empty) and immediately goes back to `wait()`.
  5. The signal intended for a blocked producer is effectively lost ("signal stealing").
  6. Soon, all consumer and producer threads end up parked in `wait()`, with no active thread left to call `notify()`.
