/**
 * @author Lyes Djemaa
 * @version 1.0
 * 
 * A small class ResourceState 
 * it purpose is to know if a Resource is actually occupied by a Slave
 * 
 * state := 0 means that is free
 * state := 1 means that is occupied
 */
package main;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ResourceState {
        private final ReentrantLock lock = new ReentrantLock();
        private int state = 0;

        public ResourceState() {}

        public int getState() { return this.state; }
        public ReentrantLock getLock() { return this.lock; }
        public void setState(int i) { this.state = i; }
}