/**
 * @author Lyes
 * @version 1.0
 * 
 * This is the class Resource
 * It will give a minimal description of our resources.
 *  
 * 
 */

package main;

import java.io.Serializable;
import java.util.Objects;

public class Resource implements Serializable {

    /**
     * Attribute for the name of the resource
     */
    private final String name;

    public Resource(String name) {
        this.name = name;
    }

    @Override
    public String toString() { return this.name; } 

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Resource)) return false;
        Resource other = (Resource) o;
        return Objects.equals(this.name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}