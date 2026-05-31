package xxx.com.java;

import java.util.Objects;

/**
 * This class serves as a demonstration of Java Records, introduced in Java 14 (stable in Java 16).
 * Records provide a concise syntax for declaring classes that are transparent holders for immutable data.
 * They automatically generate: private final fields, a canonical constructor, accessor methods (e.g., name() instead of getName()),
 * toString(), equals(), and hashCode() based on the components.
 * This reduces boilerplate compared to traditional classes.
 *
 * The structure mirrors the LombokDemo: inner classes/records for examples, with a main method demonstrating usage imperatively.
 * We'll start with a step-by-step progression:
 * 1. A traditional immutable POJO class with full boilerplate (for comparison).
 * 2. The equivalent basic record, showing massive reduction in code.
 * 3. A record with a compact constructor for validation (custom logic in constructor).
 * 4. A record with additional custom methods (extending functionality).
 * 5. An ultimate minimal record example to "wow" students: almost no code, yet fully functional with auto-generated methods.
 *
 * Note: Records are immutable by default (no setters). For mutable data, use regular classes.
 * Records do not support builders natively, but you can add a manual builder for fluent creation.
 * This is intended for educational purposes to teach how records simplify POJO creation.
 */
public class RecordDemo {

  public static void main(String[] args) {
    // Step 1: Demonstrate TraditionalPojo (full boilerplate for comparison)
    System.out.println("=== Step 1: Traditional Immutable POJO (Full Boilerplate) ===");
    TraditionalPojo traditional = new TraditionalPojo("John", 30);
    System.out.println("Name: " + traditional.getName()); // Expected: John
    System.out.println("Age: " + traditional.getAge());   // Expected: 30
    System.out.println(traditional); // Expected: TraditionalPojo{name='John', age=30}
    TraditionalPojo traditional2 = new TraditionalPojo("John", 30);
    System.out.println("traditional equals traditional2: " + traditional.equals(traditional2)); // Expected: true
    System.out.println("traditional hashCode: " + traditional.hashCode());
    System.out.println("traditional2 hashCode: " + traditional2.hashCode()); // Expected: same

    // Step 2: Demonstrate BasicRecord (equivalent to TraditionalPojo, but minimal code)
    System.out.println("\n=== Step 2: Basic Record (Auto-Generates Constructor, Accessors, toString, equals, hashCode) ===");
    BasicRecord basic = new BasicRecord("Alice", 25);
    System.out.println("Name: " + basic.name()); // Expected: Alice (note: accessor is name(), not getName())
    System.out.println("Age: " + basic.age());   // Expected: 25
    System.out.println(basic); // Expected: BasicRecord[name=Alice, age=25]
    BasicRecord basic2 = new BasicRecord("Alice", 25);
    System.out.println("basic equals basic2: " + basic.equals(basic2)); // Expected: true
    System.out.println("basic hashCode: " + basic.hashCode());
    System.out.println("basic2 hashCode: " + basic2.hashCode()); // Expected: same

    // Step 3: Demonstrate ValidatedRecord (with compact constructor for validation)
    System.out.println("\n=== Step 3: Record with Compact Constructor (for Custom Validation/Logic) ===");
    ValidatedRecord validated = new ValidatedRecord("Bob", 40);
    System.out.println("Name: " + validated.name()); // Expected: Bob
    System.out.println("Age: " + validated.age());   // Expected: 40
    System.out.println(validated); // Expected: ValidatedRecord[name=Bob, age=40]
    // Uncomment to see validation: ValidatedRecord invalid = new ValidatedRecord("Invalid", -1); // Throws IllegalArgumentException

    // Step 4: Demonstrate RecordWithMethod (adding custom instance methods)
    System.out.println("\n=== Step 4: Record with Custom Methods (Extending Functionality) ===");
    RecordWithMethod withMethod = new RecordWithMethod("Charlie", 50);
    System.out.println("Name: " + withMethod.name()); // Expected: Charlie
    System.out.println("Age: " + withMethod.age());   // Expected: 50
    System.out.println(withMethod); // Expected: RecordWithMethod[name=Charlie, age=50]
    System.out.println("Greeting: " + withMethod.greeting()); // Expected: Hello, Charlie

    // Step 5: Demonstrate UltimateRecord (the "wow" example: minimal code, fully functional)
    System.out.println("\n=== Step 5: Ultimate Record: Almost No Code Written! ===");
    // Using the manual builder for fluent creation (added to the record)
    UltimateRecord ultimate = UltimateRecord.builder()
        .name("David")
        .age(35)
        .email("david@example.com")
        .build();
    System.out.println("Name: " + ultimate.name()); // Expected: David
    System.out.println("Age: " + ultimate.age());   // Expected: 35
    System.out.println("Email: " + ultimate.email()); // Expected: david@example.com
    System.out.println(ultimate); // Expected: UltimateRecord[name=David, age=35, email=david@example.com]
    UltimateRecord ultimate2 = UltimateRecord.builder()
        .name("David")
        .age(35)
        .email("david@example.com")
        .build();
    System.out.println("ultimate equals ultimate2: " + ultimate.equals(ultimate2)); // Expected: true
    System.out.println("ultimate hashCode: " + ultimate.hashCode());
    System.out.println("ultimate2 hashCode: " + ultimate2.hashCode()); // Expected: same
    // Note: All constructors, accessors, toString, equals, hashCode are auto-generated.
    // The builder is manually added to show fluent creation, but the record itself is minimal.
  }

  /**
   * Step 1: A traditional immutable POJO class.
   * This shows the boilerplate code records aim to eliminate: manual fields, constructor, getters, toString, equals, hashCode.
   * Without records, this is verbose and error-prone.
   */
  static class TraditionalPojo {
    private final String name;
    private final int age;

    public TraditionalPojo(String name, int age) {
      this.name = name;
      this.age = age;
    }

    public String getName() {
      return name;
    }

    public int getAge() {
      return age;
    }

    @Override
    public String toString() {
      return "TraditionalPojo{name='" + name + "', age=" + age + "}";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TraditionalPojo that = (TraditionalPojo) o;
      return age == that.age && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, age);
    }
  }

  /**
   * Step 2: Basic Record - equivalent to TraditionalPojo but with minimal syntax.
   * Declare 'record Name(Type component1, Type component2) {}'
   * Automatically gets: private final fields, constructor, accessors (name(), age()), toString, equals, hashCode.
   * Progression: From ~30 lines in TraditionalPojo to 1 line here - huge reduction!
   * Note: Accessors are component names without 'get' prefix.
   */
  record BasicRecord(String name, int age) {}

  /**
   * Step 3: Record with Compact Constructor.
   * You can add a compact constructor (no params list) for validation or transformations.
   * Progression: Builds on basic record by adding custom init logic without a full constructor body.
   * Here, we validate age >= 0. If invalid, throws exception during creation.
   */
  record ValidatedRecord(String name, int age) {
    public ValidatedRecord {
      if (age < 0) {
        throw new IllegalArgumentException("Age cannot be negative");
      }
    }
  }

  /**
   * Step 4: Record with Custom Methods.
   * Records can have additional instance/static methods, nested classes, etc.
   * Progression: Shows records aren't just data holders; you can add behavior.
   * Here, a custom greeting() method uses the components.
   */
  record RecordWithMethod(String name, int age) {
    public String greeting() {
      return "Hello, " + name;
    }
  }

  /**
   * Step 5: Ultimate Record - Minimal code for a fully functional immutable POJO.
   * Progression: Combines all benefits - auto everything, plus a manual builder for fluency.
   * Without records, this would require extensive boilerplate (like TraditionalPojo but with more fields).
   * Here, the record is 1 line; builder adds fluency (similar to Lombok @Builder).
   * Highlights: Write almost no code, get immutable data class with all standard methods.
   */
  record UltimateRecord(String name, int age, String email) {
    // Manual builder to enable fluent creation (records don't have built-in builders)
    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private String name;
      private int age;
      private String email;

      public Builder name(String name) {
        this.name = name;
        return this;
      }

      public Builder age(int age) {
        this.age = age;
        return this;
      }

      public Builder email(String email) {
        this.email = email;
        return this;
      }

      public UltimateRecord build() {
        return new UltimateRecord(name, age, email);
      }
    }
  }
}
