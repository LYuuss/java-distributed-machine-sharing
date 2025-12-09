/**
 * 
 * @author Lyes
 * @version 1.0
 * 
 * This is our class SlaveProducer,
 * <p>
 * it will simply add some Resource and die through stub of the machine implicated
 * </p>
 * 
 */
package main;


import java.net.*;
import java.io.*;
import java.util.logging.*;

public class SlaveProducer implements Runnable {
    /**
     * Our class contains 3 attributes :
     *      - R : the resource we produce
     *      - units : the number of units we are adding
     *      - stub : to operate on the Resource contained by the machine
     *      - logger : a logger
     *      - id : the port of the Executor parent 
     */
    private final Resource R;
    private final int units;
    private final MachineService stub;
    private static final Logger logger = Logger.getLogger(SlaveProducer.class.getName());
    private final int id;

    /**
     * Initialize our SlaveProducer
     * 
     * @param R the resource we produce
     * @param units the number of units we are adding
     * @param stub to operate on the Resource contained by the machine
     */
    public SlaveProducer(Resource R, int units, MachineService stub, int port) throws IOException {
        this.R = R;
        this.units = units;
        this.stub = stub;
        this.id = port;
    }
    /**
     * method that performs the producing step from 2PC
     */
    @Override
    public void run() {
        try {
            logger.info(() -> "SlaveProducer"+this.id + " start producing " 
                                + this.units + " of " + this.R);
            this.stub.produce(this.R, this.units);
            logger.info(() -> "SlaveProducer"+this.id + " produced " 
                                + this.units + " of " + this.R);
        } 
        catch(Exception e) {
            System.err.println(e);
        }

    }
}