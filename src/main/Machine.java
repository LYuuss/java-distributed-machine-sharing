/**
 * @author Lyes Djemaa
 * @version 1.0
 * 
 * This is the class Machine, 
 * 
 * <p>
 *  It will communicate with the executors and their slaves by RMI protocol,
 *      it implements the interface MachineService in order to satisfy at best executors' demand 
 * 
 * Our machines will produce ressources which will be used to make reaction
 * 
 * 
 * Furthermore we assume each machine has contains only pairs with distinct Resource
 *      And that no two distinct Machine store the same resource
 * 
 * We require that --registry must have same address for all machines
 *  and that the --id takes successively values from 1,2,3..,n
 * </p>
 * 
 * Example of execution :
 *  *on a computer*       java Machine --registry "127.0.0.1" --portRegistry 1099 --id 1 --resource "(A,3)" --resource "(B,5)"
 *  *on another computer* java Machine --registry "127.0.0.1" --portRegistry 1099 --id 2 --resource "(C,3)" --resource "(D,0)"
 * 
 */
package main;


import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.logging.*;
import java.util.concurrent.TimeUnit;
import java.io.*;

public class Machine implements MachineService {


    /**
     *  a attribute mapping for each referenced resource, its units available
     *  each key must have a unique dedicated ResourceState 
     * 
     */
    private final Map<Resource, Integer> resources = new ConcurrentHashMap<>();

    /**
     * an attribute mapping for each referenced resource a unique state
     */
    private final Map<Resource, ResourceState> states = new ConcurrentHashMap<>();

    /** 
     * a logger
    */
    private static final Logger logger = Logger.getLogger(Machine.class.getName());
    /** 
     * Our constructor
     */
    public Machine(Map<Resource, Integer> toBeDeclared) {
        for (Map.Entry<Resource, Integer> e : toBeDeclared.entrySet()) {
            Resource R = e.getKey();
            int quantity = e.getValue();
            this.resources.put(R, quantity);
            this.states.put(R, new ResourceState());
        }
    }
    //getters
    public Map<Resource,Integer> getResources() { return this.resources; }
    public Map<Resource, ResourceState> getStates() { return this.states; }

    /**
     * List all resources with its units of the machine 
     * @return a string listing what is mentionned above, (e.g. "(A,6) (C,12) ..."
     */
    public String getInventory() {
        return resources.entrySet()
                        .stream()
                        .map(e -> "(" + e.getKey() + "," + e.getValue() + ")")
                        .collect(Collectors.joining(" "));
    } 

    /** method getStateR, return state of the given resource
     *  
     * @param R the resource we want exclusivity
     */
    public int getStateR(Resource R) {
        return (states.get(R)).getState();
    }

    /**
     * method tryTo, part of the Phase I (Prepare)
     * 
     * @param R the resource we are trying to reserve
     * @param n number of units requested by an executor 
     * @return "reserved" if the resource is free and the requested amount is available
     *             or "insufficient"/"locked" depending the situation
     *     
     */
    @Override
    public String tryTo(Resource R, int n) {
        ResourceState RS = states.get(R);
        if (RS  == null) throw new IllegalArgumentException("Unknown resource " + R);
    
        (RS.getLock()).lock();
        try {
            if (RS.getState() == 1) {
                return "locked";
            }

            int available = resources.get(R);
            if (available < n) {
                return "insufficient";
            }

            RS.setState(1);
            return "reserved";
        } 
        catch (Exception e) {
            System.err.println(e);
        } 
        finally {
            RS.getLock().unlock();
        }

        //we never reach this part of the code but compiler insisted on a return statement was needed
        return "problem";
    }
    
    /**
     * method consume, part of the Phase II(Comit or Abort)
     * 
     * we assert that param n <= units available
     * 
     * @param R, the resource we use 
     * @param n, the number of unit we consumes
     */
    @Override
    public void consume(Resource R, int n) {
        ResourceState RS = states.get(R);
        if (RS == null) throw new IllegalArgumentException("Unknown resource " + R);
    
        RS.getLock().lock();
        try {
            int available = resources.get(R);
            resources.replace(R, available - n);
            RS.setState(0);
        }
        catch(Exception e) {
            System.err.println(e);
        } 
        finally {
            RS.getLock().unlock();
        }
    }
    /**
     * method abort, part of the Phase II(Comit or Abort)
     * ONLY IF a SlaveConsumer reserved R during Phase I, release the reservation 
     * 
     * @param R Resource we wanted to consume
     */
    @Override 
    public void abort(Resource R) {
        ResourceState RS = states.get(R);
        if ( RS == null ) throw new IllegalArgumentException("Unknown resource " + R);

        RS.getLock().lock();
        RS.setState(0);
        RS.getLock().unlock(); 
    }


    /**
     * Method produce
     * 
     * When a reaction is executed, add the resource procuded 
     * 
     * We assume the resource already exist in the machine even with 0 unit
     * 
     * 
     * @param R the resource we are supplying
     * @param n number of units 
     */
    @Override
    public void produce(Resource R, int n) {
        ResourceState RS = states.get(R);
        if (RS == null) throw new IllegalArgumentException("Unknown resource " + R);

        RS.getLock().lock();
        try {

            int available = resources.get(R);
            resources.replace(R, available + n);

        } finally {
            RS.getLock().unlock();
        }
    }


    /**
     * method that enumerate the resource it can store
     * 
     * @return a string list all resource present (available or not) (e.g. "A,C,D")
     */
    @Override
    public String enumResource() {
        String result = resources.keySet()
                                .stream()
                                .map(Object::toString)
                                .collect(Collectors.joining(","));
        return result;
    }



    /**
     * The main method of this class, instance a Machine and its resource
     * @param args an array of string indicating each Resource the machine 
     */
    public static void main(String[] args) {
        try {
            String addressRegistry = parseAddress(args);
            int portRegistry = parsePortRegistry(args);
            String id = parseID(args);
            int id_numeric = Integer.parseInt(id);

            Machine M = new Machine(parsePairs(args));

            MachineService stub =
                    (MachineService) UnicastRemoteObject.exportObject(M, 0);

            Registry registry;

            try {
                registry = LocateRegistry.getRegistry(addressRegistry, portRegistry);
                registry.list();
            } 
            catch (Exception e) {
                throw new IllegalArgumentException("No registry at the address given for --registry");
            }

            registry.rebind("Machine" + id_numeric, stub);

            configureLogging(id_numeric);

            logger.info(() -> "Machine exported with id " + id_numeric
                                + " and Resources: " + M.enumResource());

            while(true) {
                logger.info(() -> "Machine"+id 
                                + " maintaining : " + M.getInventory());
                TimeUnit.SECONDS.sleep(10);
            }

        } 
        catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage();
            System.exit(1);
        } 
        catch (Exception e) {
            System.err.println("Machine exception: " + e);
            e.printStackTrace();
            printUsage();
            System.exit(1);
        }
    }

    /**
     * ----------------------------------------------------
     * Methods to smooth the initialisation of our machines
     * ----------------------------------------------------
     */

    /**
     * method that will extract registry address given in argument
     * @param args argument of main
     * @return address of the registry as a string
     * @throws IllegalArgumentException
     */
    public static String parseAddress(String[] args) {

        for (int i = 0; i < args.length; ++i) {
            if ("--registry".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing args after --registry");
                }
                return args[++i];
            }
        }
        throw new IllegalArgumentException("You must provide --registry \"<address>\"");

    }

    /**
     * method that extract the port from parameter args
     * the port arg give the port each executor try to own when initializing its socket
     * @param args the argument of main
     * @return the port associated to the address registry
     * @throws IllegalArgumentException
     */
    public static int parsePortRegistry(String[] args) {

        for (int i = 0; i < args.length; ++i) {
            if ("--portRegistry".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing args after --portRegistry");
                }
                return Integer.parseInt(args[++i]);
            }
        }
        throw new IllegalArgumentException("You must provide --portRegistry <value>");

    }


    /**
     * A parsing method to extract the id of the machine 
     * 
     * Example args :
     *  --id 5
     * 
     * @param args the args we gave when execute the program
     * @return a string containing only the ID (a number)
     * @throws IllegalArgumentException 
     */
    public static String parseID(String[] args) {

        for (int i = 0; i < args.length; ++i) {
            if ("--id".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing args after --id");
                }
                return args[i + 1];
            }
        }
        throw new IllegalArgumentException("You must provide --id <value>");

    }

    /** 
     * A parsing method to retrieve all resource and their quantity
     *  then compute the associated map
     * 
     * Example args:
     *   --resource "(A,3)" --resource "(B,5)"
     * @param args the argument of the method main
     * @return map for which each resource has its quantity associated
     */
   public static Map<Resource, Integer> parsePairs ( String[] args) {
        Map<Resource, Integer> res = new ConcurrentHashMap<>();

        int l = args.length;

        for(int i = 0; i < l; ++i) {
            if(args[i].equals("--resource")) {

                if( i + 1 == l )
                    throw new IllegalArgumentException("Missing args after --resource");
                ++i;
                
                String trimmed = args[i].trim();
                if (!trimmed.startsWith("(") || !trimmed.endsWith(")")) {
                    throw new IllegalArgumentException("Bad resource format: " 
                                                        + trimmed 
                                                        + " (expected: (NAME,UNITS))");
                }

                String inside = trimmed.substring(1, trimmed.length() - 1); // drop '(' and ')'
                String[] parts = inside.split(",");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Bad resource format: " 
                                                        + trimmed 
                                                        + " (expected: (NAME,UNITS))");
                }

                String name = parts[0].trim();
                String quantity_str = parts[1].trim();

                int quantity;
                try {
                    quantity = Integer.parseInt(quantity_str);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Units must be an integer in " + trimmed);
                }

                Resource r = new Resource(name);
                res.put(r, quantity);

            }
        }

        return res;

   }

    /**
     * An Error handling method, to remind user
     */
    public static void printUsage() {

        System.err.println("Usage :");
        System.err.println(" java main.Machine --registry <address> --portRegistry <value> --id <number> --resource \"(A,3)\" --resource \"(B,5)\" ...");
    }

    /**
     * static method that configure the Machine logger
     * @param id the unique id of the machine to identify the file log
     */
    private static void configureLogging(int id) {
        Logger root = Logger.getLogger("");
        root.setLevel(Level.FINE);

        for (Handler h : root.getHandlers()) {
            root.removeHandler(h);
        }
// a class to help having an output like : "DATE" INFO : "..." in the log
        Formatter myFormatter = new Formatter() {
            @Override
            public String format(LogRecord r) {
                java.util.Date d = new java.util.Date(r.getMillis());
                String date = String.format("%1$tF %1$tT", d);
                return String.format("%s: %s %s%n",
                                    r.getLevel().getName(),
                                    date,
                                    formatMessage(r));
            }
        };

        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.INFO);
        ch.setFormatter(myFormatter);
        root.addHandler(ch);

        try {
            FileHandler fh = new FileHandler("systemMachine"+id+".log", true);
            fh.setLevel(Level.INFO);
            fh.setFormatter(myFormatter);
            root.addHandler(fh);
        } catch (IOException e) {
            System.err.println("Could not set up file logging for Machine: " + e);
        }
    }

    
}
