/**
 * @author Lyes Djemaa
 * @version 1.0
 * 
 * Our class SlaveConsumer,
 * <p>
 * It will serve Executor by check/consume/abort on resource
 * 
 * 
 * Also it will communicate with its executor throught TCP protocol which will be detailed below :
 *  -Slave and Executor start by establish a connection
 *  -As soon as Executor create Slave, it make it start then accept its connection, the Slave has now finish its constructor method
 *  -if the resource is free, locks it immedialety and it send "YES" to the executor 
 *  -then, it waits for a message from Executor to make a decision
 *  -After receiving the message from the Executor, two message are possible : 
 *          - If the executor sends commit, it consume the resource then release the resource
 *          - If the executor sends abort, it give up on the resource by release the resource
 *  
 * </p>
 * 
 * The resource producted by the reaction will be handled by the parent as we want no duplicated production
 */

package main;

import java.net.*;
import java.io.*;
import java.util.logging.*;

public class SlaveConsumer implements Runnable {

    /**
     * four attribute :
     *      - resource : the resource we want to operate on
     *      - units : number of units we require
     *      - parent : socket to communicate with parent executor
     *      - port : port used by parent
     *      - stub : the stub that will able operation on the machine
     *      - logger : a logger
     */
    private final Resource R;
    private final int units;
    private final Socket parent;
    private final int port;
    private final MachineService stub;
    private static final Logger logger = Logger.getLogger(SlaveConsumer.class.getName());
    
    /**
     * The constructor of the class
     * All slave are created on the same device of their parent Executor 
     * So it connects locally on the port specified
     * 
     * @param R the resource we will try to operate on
     * @param units the units we will try to consume
     * @param port the port of the parent socket for communicate during the 2PC
     * @param stub to operate on the resource contained by the machine
     */
    public SlaveConsumer( Resource R, int units, MachineService stub, int port) throws IOException {
        this.R = R;
        this.units = units;
        this.port = port;
        parent = new Socket("127.0.0.1", port);
        this.stub = stub; 
    }

    /**
     * The method run
     * It follows the protocol detailed in the header
     * 
     */
    @Override
    public void run() {
        try {

            ObjectOutputStream output = new ObjectOutputStream(parent.getOutputStream());
            output.flush();

            ObjectInputStream input = new ObjectInputStream(parent.getInputStream()); 
            
            logger.fine(() -> "SlaveConsumer checking for resource " 
                                + this.R + " (" + this.units + " units)...");

            String state = this.stub.tryTo(this.R, this.units); 

            boolean tried = (state.equals("reserved") ) ? 
                                                     true:
                                                     false;
            
            logger.fine(() -> "SlaveConsumer tried to access  " + this.R 
                                + " for " + this.units + " units,"
                                + " response : " + state);


            if(state.equals("reserved"))
                logger.info( () -> "SlaveConsumer for " + this.R
                                    + " locked the resource");
                                                         
            else 
                logger.info( () -> "SlaveConsumer for " + this.R 
                                    + " could not lock the resource");

            String ready = (tried == true) ? 
                                             "YES" :
                                             "NO" ;
            output.writeObject(ready);
            output.flush();

            String action = (String) input.readObject();

            switch(action) {

                case "COMMIT":
                    stub.consume(this.R, this.units);
                    logger.info(() -> "SlaveConsumer for resource " + this.R  
                                        + " Commited, consumed it and released the lock");
                    break;

                case "ABORT":
                    if(state.equals("reserved")) {
                        stub.abort(this.R);
                        logger.info(() -> "SlaveConsumer for resource " + this.R 
                                            + " Aborted, did not consumed it and released the lock then aborted");
                    }

                    else logger.info(() -> "SlaveConsumer for resource " + this.R + " Aborted");
                    break;

                default:
            }

        }
        catch(Exception e) {
            System.err.println(e);
        }
        finally {

            try{
                this.parent.close();
                logger.fine(() -> "SlaveConsumer for resource : "+ this.R + " off");
            } catch (Exception e) {
                System.err.println(e);
            }

        }

    }
}