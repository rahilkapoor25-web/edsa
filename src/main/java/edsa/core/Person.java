package edsa.core;

import java.util.Objects;

/**
 * Anyone the examination office knows about: a student sitting papers or a
 * faculty member invigilating them.
 */
public abstract class Person {

    private final String id;
    private final String name;
    private final String department;

    protected Person(String id, String name, String department) {
        this.id = id;
        this.name = name;
        this.department = department;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDepartment() {
        return department;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        return id.equals(((Person) other).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), id);
    }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}
