/**
 * @author Lyes Djemaa
 * @version 1.0
 * 
 * This is our class ReactionRule
 * 
 * It will precisely model a reaction 
 * 
 * Reactions describe how available resources can be consumed and produced
 * 
 */

package main;


import java.util.Map;
import java.util.stream.Collectors;


public class ReactionRule {
    /** two attribute :
     *      - toConsume : Resource and their quantity to execute the reaction
     *      - toProduce : the resource it produce
    */
    private final Map<Resource, Integer> toConsume;
    private final Map<Resource, Integer> toProduce;
    
    public ReactionRule(Map<Resource, Integer> toConsume, Map<Resource, Integer> toProduce) {
        this.toConsume = toConsume;
        this.toProduce = toProduce;
    }

    //getters
    public Map<Resource, Integer> getToConsume() { return this.toConsume; }
    public Map<Resource, Integer> getToProduce() { return this.toProduce; }
    

    @Override
    public String toString(){
        String left = toConsume.entrySet().stream()
                            .map(e -> {
                                    int qty = e.getValue();
                                    Resource r = e.getKey();
                                    String coef = (qty == 1) ? "" : Integer.toString(qty);
                                    return coef + r.toString();
                                    })
                            .collect(java.util.stream.Collectors.joining(" + "));

        String right = toProduce.entrySet().stream()
                            .map(e -> {
                                    int qty = e.getValue();
                                    Resource r = e.getKey();
                                    String coef = (qty == 1) ? "" : Integer.toString(qty);
                                    return coef + r.toString();
                                    })
                            .collect(java.util.stream.Collectors.joining(" + "));


        return left + " -> " + right;
    }

}