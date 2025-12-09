# java-distributed-machine-sharing

The ReadMe of the project

Overall:
    -Every text inside {} is supposed to be defined by yourself according to how you want to design your system

    -To have more information on the console log set the level to FINE bu changing line 525 by 
        fh.setLevel(Level.FINE);

    -To have more information in executor's log file set to the level FINE by changing line 531 by 
        fh.setLevel(Level.FINE);

    -Possibility to change machines period display at line 257 (change sleep time)

First I will recommand to build a file .txt let's say "config.txt" as follow :

    -For each Machines desired, a line as follow (the order of the parameter don't matter, but here is a default way to write it):

        java main.Machine --registry "{address}" --portRegistry {Number} --id {Number} --ressource "({R}, {Number})" --resource "({R2}, {Number}) 

    Notes :
        
        - All machines must share the same registry (Must be the same as the one used when launching class Test), which means, they should share the same registry and portRegistry parameter.

        - For K machines, the id parameter must be within 1-K and each machine must have a unique id.

        - All of your Machine lines must fill the first lines, i.e. for K machines, the K first lines must be dedicated to your K machines

        - Two (or more) machines can't store the same Resource, each Resource must be uniquely stored in one machine


    -For each Executor, a line as follow (the order of the parameter don't matter, but here is a default way to write it):

        java main.Executor --portSocket {} --registry "{}" --portRegistry {Number} --machines {Number} --delay {Number} --reaction "{Reaction}" ...--reaction "{Reaction}"
    
    Notes :
        - All executor must have a unique and available parameter portSocket.

        - They share same registry and portRegistry

        - The argument machines must correspond to the number of Machines in the system, E.G. if you have K machines, first you should have used the k first lines for them, on add you should have "--machines K"

        - Resources on the left hand side of the reaction do not occur in the right hand
        side, and all the coefficients are positive

        - The delay param is in Milliseconds

An example of a config file is shown in the file "configReadMe.txt"

The file Test.java when executed takes 3 argument (the order here matter as they are not identified as in machines and executors) :
java Test {configFile} {Time} [registryPort]

    1- The name of the config file or its path
    2- The time (in second) duration of the system.
    3- (Optional) a specific port for the registry, 1099 otherwise  


With this we are now all good to launch our system

In the directory Project, compile everything with the following command :
    javac main/*.java Test.java

An example of execution:
    java Test configReadMe.txt 30 
        Will launch a system designed in configReadMe for 30 seconds using registry port 1099. 

You got now all the clue to launch a system, have fun !
