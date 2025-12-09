/**
 * @author Lyes Djemaa
 * @version 1.0
 * 
 * A small class ExecutorSlaveLink
 * 
 * to prevent deadlock (between SlaveConsumer and Executor) during 2PC using Objects ObjectOutputStream, ObjectInputStream
 */
package main;

import java.net.*;
import java.io.*;

class ExecutorSlaveLink {
    final Socket socket;
    final ObjectOutputStream out;
    final ObjectInputStream in;

    ExecutorSlaveLink(Socket socket) throws IOException {

            this.socket = socket;
            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.out.flush();
            this.in = new ObjectInputStream(socket.getInputStream());

    }
}