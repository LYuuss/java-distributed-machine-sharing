/**
 * @author Lyes Djemaa
 * @version 1.0
 * 
 * This class implements the interface MachineService
 *  
 * It will ensure communication between executors and machines
 * 
 */
package main;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface MachineService extends Remote {

    public String tryTo(Resource R, int n) throws RemoteException;

    public void consume(Resource R, int n) throws RemoteException;

    public void abort(Resource R) throws RemoteException;

    public void produce(Resource R, int n) throws RemoteException;

    public String enumResource() throws RemoteException;
}