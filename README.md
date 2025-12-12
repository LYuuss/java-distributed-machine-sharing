# java-distributed-machine-sharing

A distributed system in Java that simulatesa network of machines sharing and transforming resources according to reaction rules
concurrently launched by different executors using Two Phase Commit Protocol (2PC).
Each machine manages a local set of resources (e.g., raw materials or data items). Certain
global rules, called reactions, describe how resources from different machines can be
combined or transformed into new resources.

A detailed readme on how to launch the system is in src/ .
