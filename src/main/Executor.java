/**
 * 
 * @author Lyes Djemaa
 * @version 1.0
 * 
 * <p>
 * This is our class Executor
 * 
 * It will execute tirelessly a set of reaction given in argument
 * 
 * But before, in order to know where the resources are located
 * A little protocol will take place with the Machines :
 *  -The executor starts by retrieving all the machine in registry
 *     To do that in the argument args of the main method, a number (--id) will indicate how many machines there is
 *     And the address (--registry) will also be indicated
 * 
 *  -Then fill a static map<Resource, Integer > to associate each Resource a location machine
 * 
 *  -Now our executor know who to solicit to make reaction
 * 
 * In the end, our executor will execute endless reaction
 * </p>
 *  to read protocol for executing reaction @see SlaveConsumer 
 * 
 * Example of execution :
 *  *on a computer* java main.Executor --portSocket 5001 --registry "127.0.0.1" --portRegistry 1099 --machines 5 --delay 3 --reaction "A -> C" --reaction "B + C -> D"
 *  *on another one* java main.Executor --portSocket 5002 --registry "127.0.0.1" --portRegistry 1099 --machines 5 --delay 4 --reaction "C + D -> E"
 */

package main;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.net.*;
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;

public class Executor {
    
    /** Attribute map<Resource, MachineService> to locate the machine containing the resource 
     *      We assume every machines are already connected and accessible, no other we be added to the network
     *      where MachineService store the stub corresponding in the registry of class Machine @see Machine 
     * 
     *  Attribute ReactionRule[] that store reactions it will attempt to launch
     * 
     *  Attribute Random to randomly select a Reaction to launch, seed based on currentTime "good" to provide randomness
     * 
     *  Attribute ServerSocket to communicate with SlaveConsumers to perfom 2PC
     * 
     *  Attribute Logger
     *  
     *  Attribute ipAddress 
     * */
    private Map<Resource, MachineService> locations = new ConcurrentHashMap<>();
    private ReactionRule[] reactions;
    private Random rng = new Random(System.currentTimeMillis());
    private ServerSocket server;
    private final int port;
    private static final Logger logger = Logger.getLogger(Executor.class.getName());
    private final String ipAddress;


    public Executor(Map<Resource, MachineService> locations, ReactionRule[] reactions, int port) {
        this.port = port;
        this.locations = locations;
        this.reactions = reactions;
        try {
            this.server = new ServerSocket(port);
            
        } 
        catch(IOException e){
            System.err.print("Error constructor Executor" + e);
        }
        String tmp;
        try {
            tmp = InetAddress.getLocalHost().getHostAddress();
        }
        catch( UnknownHostException e){
            tmp = "Could not get ipAddress";
        }
        this.ipAddress = tmp;

    }    

    //getters
    public Map<Resource, MachineService> getLocations() { return this.locations; }
    public ReactionRule[] getReactions() { return this.reactions; }


    /**
     * select randomly a reaction present in the array
     * 
     * @return the reaction rule selected 
     * 
     */
    public ReactionRule selectReaction() {
        
        int i = this.rng.nextInt(this.reactions.length); //pick a number between 0-N where N denotes number of reactions  

        return reactions[i];
    }

    /**
    * method that will try to launch a reaction by following protocol mentioned in header
     *
    * @param rl reaction rule we're gonna try to launch
    * @return true if it could make it, false if it has to abort
    */

    public boolean attemptReaction(ReactionRule rl) {

        boolean attempt = true;

        List<ExecutorSlaveLink> links = new ArrayList<>();

        try {

            logger.info(() ->"[Exe@" + this.ipAddress + ":" + this.port                                     
                                + " Beginning phase I for " + rl);
            //Creating one SlaveConsumer per resource to consume
            for (Map.Entry<Resource, Integer> entry : rl.getToConsume().entrySet()) {
                Resource resource = entry.getKey();
                int qtyRequired = entry.getValue();
                MachineService stub = locations.get(resource);

                // start slave thread
                SlaveConsumer sl = new SlaveConsumer(resource, qtyRequired, stub, this.port);
                new Thread(sl).start();

                // wait for its TCP connection
                links.add(new ExecutorSlaveLink(this.server.accept()));
            }


//getting response from slaves 
            for (ExecutorSlaveLink link : links) {
                String ready = (String) link.in.readObject();
                logger.fine(() -> "Slave response : " + ready);
                if ("NO".equals(ready)) {
                    attempt = false;
                }
            }

            logger.info(() -> "[Exe@" + this.ipAddress + ":" + this.port 
                                 + " Ending phase I for " + rl);

            logger.info(() -> "[Exe@" + this.ipAddress + ":" +this.port
                                +" Beginning phase II for " + rl);
//deciding if we commit or abort
            String decision = attempt ? "COMMIT" : "ABORT";

            for (ExecutorSlaveLink link : links) {
                link.out.writeObject(decision);
                link.out.flush();
                link.socket.close();
            }

//If commit, trigger production on the right-hand side
            if (attempt == true) {
                //Creating one SlaveProducer per resource to produce
                for (Map.Entry<Resource, Integer> entry : rl.getToProduce().entrySet()) {
                    Resource resource = entry.getKey();
                    int qtyRequired = entry.getValue();
                    MachineService stub = locations.get(resource);

                    SlaveProducer sp = new SlaveProducer(resource, qtyRequired, stub, this.port);
                    new Thread(sp).start();
                }
                
            }
            logger.info(() -> "[Exe@" + this.ipAddress + ":" + this.port
                                +" Ending phase II for " + rl);

        } 
        catch (Exception e) {
            System.err.println("Error during attemptReaction: " + e);
            e.printStackTrace();
            attempt = false;
        }

        return attempt;
    }


    /**
     * the main method of the class
     *  it starts by parsing the arguments, then initialize a executor and infinitely launch reaction
     * @param args the parameter to initialize the executor
     * @throws Exception
     */
    public static void main(String[] args) {
        try {
            int portSocket = parsePortSocket(args);
            String addressRegistry = parseAddress(args);
            int portRegistry = parsePortRegistry(args);
            int nb = parseMachines(args);
            int delay = parseDelay(args);

            Map<Resource, MachineService> locs = lookUpMachine(addressRegistry, portRegistry, nb);

            ReactionRule[] reacts = parseReaction(args);

            Executor executor = new Executor(locs, reacts, portSocket);

            
            configureLogging(portSocket);

            logger.info(() -> "[Exe@"+ executor.ipAddress + ":" +portSocket
                                +" Ready ! Reaction rules available :" + Arrays.toString(reacts) );

            logger.fine("Starting...");

            int total_attempt = 0;
            int successful_attempt = 0;

            while(true) {
                ReactionRule attempt = executor.selectReaction();
                logger.info(() -> "[Exe@" + executor.ipAddress + ":" +portSocket 
                                    +" Trying to make : " + attempt);
                try {
                    boolean result = executor.attemptReaction(attempt);

                    final int ta = ++total_attempt;
                    final int sa = (result) ? ++successful_attempt : successful_attempt;


                    logger.info(() ->"[Exe@" + executor.ipAddress + ":" + portSocket
                                        +" Reaction attempted : " + attempt 
                                        + " result : " + result);

                    final float ratio = (float) sa / ta;

                    logger.info( () -> "[Exe@" + executor.ipAddress + ":" + portSocket
                                            +" Overall ratio at attempt " + ta + " : " + ratio);
//waiting time before launching next reaction 
                    TimeUnit.MILLISECONDS.sleep(delay);
                } 
                catch(Exception e) {
                    System.err.println("Error during 2PC" + e);
                    System.exit(1);
                }

            }

        }  
        catch( Exception e) {
            System.err.println("Executor exception: " + e);
            e.printStackTrace();
            printUsage();
            System.exit(1);
        }
    }

//----------------------------------------------------------------------------------------------------------------------
// Methods to smooth initialization of Executor
//--------------------------------------------------------------------------------------------------------------------------
 
 
    /**
     * method that extract the port from parameter args
     *  indicate the port each executor try to own when initializing its socket
     * @param args the argument of main
     * @return the port of the socket
     * @throws IllegalArgumentException
     */
    public static int parsePortSocket(String[] args) {

        for (int i = 0; i < args.length; ++i) {
            if ("--portSocket".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing args after --portSocket");
                }
                return Integer.parseInt(args[++i]);
            }
        }
        throw new IllegalArgumentException("You must provide --portSocket <value>");

    }



    /**
     * method that will extract registry address given in argument
     * @param args argument of main
     * @return address of the registry as a string
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
     * indicate the port registry of the addressRegistry
     * @param args the argument of main
     * @return the port of the registry
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
     * static method 
     *  it simply retrieve the nb associate to "--machines" which correspond 
     * @param args
     * @return the number of machine as a string
     */
    public static int parseMachines(String[] args) {

        for (int i = 0; i < args.length; ++i) {
            if ("--machines".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing args after --machines");
                }
                return Integer.parseInt(args[++i]);
            }
        }
        throw new IllegalArgumentException("You must provide --machines <value>");

    }

    /**
     * method that extract the delay parameter from args
     * the delay arg give the time D each executor waits before launching another reaction
     * @param args the argument of main
     * @return the time D
     * @throws IllegalArgumentException
     */
    public static int parseDelay(String[] args) {

        for (int i = 0; i < args.length; ++i) {
            if ("--delay".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing args after --delay");
                }
                return Integer.parseInt(args[++i]);
            }
        }
        throw new IllegalArgumentException("You must provide --delay <value>");

    }

     /**
     * static method to retrieve all machines connected to the network 
     *  then store the information about what they contain into a map 
     * @param address registry's host address
     * @param nb number of machines connected
     * @return a map where for each resource keeps its container machine (we recall that no two distinct machines can store same resource)
     * @throws IllegalArgumentException
     * @throws Exception
     */
    public static Map<Resource, MachineService> lookUpMachine(String address, int port, int nb) {
        Map<Resource, MachineService> result = new ConcurrentHashMap<>();
        try{
            Registry registry = LocateRegistry.getRegistry(address, port);
       

            for (int i =1; i <= nb; ++i ) {
                MachineService stub = (MachineService) registry.lookup("Machine"+i);

                if( stub == null) {
                    throw new IllegalArgumentException("Machine " + i + " not registered");
                }

                String allResources = stub.enumResource();
                for ( String name : allResources.split(",")){
                    Resource resource = new Resource(name);

                    result.put(resource, stub);
                }
            }
        }
        catch(Exception e) {
            System.err.println("Executor exception: " + e);
            e.printStackTrace();
            printUsage();
            System.exit(1);
        }

        return result;
    }

    /**
     * static method that will, for each reaction (e.g. A + B -> C)
     *  instance a ReactionRule and add it to the returned ArrayList
     * @param args
     * @return an array of reaction rule
     * @throws IllegalArgumentException
     */
    public static ReactionRule[] parseReaction(String[] args) {
        List<ReactionRule> result = new ArrayList<>();

        for ( int i = 0; i < args.length; ++i) {
            if ( "--reaction".equals(args[i]) ) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing args after --reaction");
                }

                String sides[] = args[++i].split("->");
                if (sides.length != 2) throw new IllegalArgumentException("Wrong format reaction : " + args[i]);
 
                Map<Resource, Integer> leftHand = parseSide(sides[0]);
                Map<Resource, Integer> rightHand = parseSide(sides[1]);

                ReactionRule rl = new ReactionRule(leftHand, rightHand);
                result.add(rl);
            }
        }

        return result.toArray(new ReactionRule[0]);
    }

    /**
     * static method to parse left hand operand of reaction into a map 
     *      e.g. "2A + B" parses into map : {("A", 2);("B", 1)}
     * 
     * @param operands raw leftside of reaction
     * @return a map repertorying resource and quantity needed
     * @throws IllegalArgumentException
     */
    public static Map<Resource, Integer> parseSide(String operands) {
        Map<Resource, Integer> result = new ConcurrentHashMap<>();

        String[] parts = operands.split("\\+");
        for( String term : parts) {
            term = term.trim();
            if( term.isEmpty() ) throw new IllegalArgumentException("Empty term in reaction side");

            // remove all spaces inside a term (to handle "2 A" as well)
            term = term.replaceAll("\\s+", "");

            // extract coefficient at the beginning (if any)
            int i = 0;
            while (i < term.length() && Character.isDigit(term.charAt(i))) {
                i++;
            }

            final int coeff;
            final String name;

            if (i == 0) {
                // no leading digits => coefficient = 1, whole term is the name
                coeff = 1;
                name = term;
            } 
            else {
                coeff = Integer.parseInt(term.substring(0, i));
                name = term.substring(i);
            }

            if (name.isEmpty()) {
                throw new IllegalArgumentException("Missing resource name in term: \"" + term + "\"");

            }

            Resource r = new Resource(name);
            result.put(r,coeff);

        }

        return result;
    }

    
    public static void printUsage() {

        System.err.println("Usage :");
        System.err.println("  java main.Executor --portSocket <number> --registry \"address\" --portRegistry <value> --machines <number> --delay <number> --reaction \"3A + B -> C\" --reaction \"B + 2C -> 2D\" ...");
    }

    /**
     * static method that configure the Executor logger
     * @param port the unique portSocket associated to the executor, enable to identify the file log
     */
    private static void configureLogging(int port) {
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
            FileHandler fh = new FileHandler("systemExecutor"+port+".log", true);
            fh.setLevel(Level.INFO);
            fh.setFormatter(myFormatter);
            root.addHandler(fh);
        } catch (IOException e) {
            System.err.println("Could not set up file logging for Executor: " + e);
        }
    }


}


/**
 * Questions ? :
 *  - machines keep producing primary resource ? 
 *  - how I keep the lock on resource until commit/abort ?
 *  - who should execute the produce method ?  
 *  
 */



