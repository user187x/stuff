package xxx.com.lombok;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
/**
 * This class serves as a demonstration of various Lombok features.
 * It is structured like a Java unit test but instead of asserting, it prints outputs to showcase behavior.
 * Each inner class exemplifies a specific Lombok annotation or combination, with block comments explaining usage.
 * The main method demonstrates each feature imperatively by creating instances, manipulating them, and printing results.
 * This is intended for educational purposes to show how Lombok reduces boilerplate in Java codebases.
 */
public class LombokDemo {

  public static void main(String[] args) {
    // Demonstrate BasicGetterSetter
    System.out.println("=== Demonstrating @Getter and @Setter ===");
    BasicGetterSetter basic = new BasicGetterSetter();
    basic.setName("John");
    basic.setAge(30);
    System.out.println("Name: " + basic.getName()); // Expected: John
    System.out.println("Age: " + basic.getAge());   // Expected: 30

    // Demonstrate ToStringExample
    System.out.println("\n=== Demonstrating @ToString ===");
    ToStringExample toStringEx = new ToStringExample("Alice", 25);
    System.out.println(toStringEx); // Expected: ToStringExample(name=Alice, age=25)

    // Demonstrate EqualsAndHashCodeExample
    System.out.println("\n=== Demonstrating @EqualsAndHashCode ===");
    EqualsAndHashCodeExample obj1 = new EqualsAndHashCodeExample("Bob", 40);
    EqualsAndHashCodeExample obj2 = new EqualsAndHashCodeExample("Bob", 40);
    EqualsAndHashCodeExample obj3 = new EqualsAndHashCodeExample("Charlie", 50);
    System.out.println("obj1 equals obj2: " + obj1.equals(obj2)); // Expected: true
    System.out.println("obj1 equals obj3: " + obj1.equals(obj3)); // Expected: false
    System.out.println("obj1 hashCode: " + obj1.hashCode());
    System.out.println("obj2 hashCode: " + obj2.hashCode()); // Expected: same as obj1

    // Demonstrate DataExample
    System.out.println("\n=== Demonstrating @Data (combines @Getter, @Setter, @ToString, @EqualsAndHashCode) ===");
    DataExample dataEx = new DataExample();
    dataEx.setName("David");
    dataEx.setAge(35);
    System.out.println("Name: " + dataEx.getName()); // Expected: David
    System.out.println("Age: " + dataEx.getAge());   // Expected: 35
    System.out.println(dataEx); // Expected: DataExample(name=David, age=35)
    DataExample dataEx2 = new DataExample();
    dataEx2.setName("David");
    dataEx2.setAge(35);
    System.out.println("dataEx equals dataEx2: " + dataEx.equals(dataEx2)); // Expected: true

    // Demonstrate NoArgsConstructorExample
    System.out.println("\n=== Demonstrating @NoArgsConstructor ===");
    NoArgsConstructorExample noArgs = new NoArgsConstructorExample();
    noArgs.setName("Eve");
    System.out.println("Name: " + noArgs.getName()); // Expected: Eve

    // Demonstrate AllArgsConstructorExample
    System.out.println("\n=== Demonstrating @AllArgsConstructor ===");
    AllArgsConstructorExample allArgs = new AllArgsConstructorExample("Frank", 45);
    System.out.println("Name: " + allArgs.getName()); // Expected: Frank
    System.out.println("Age: " + allArgs.getAge());   // Expected: 45

    // Demonstrate RequiredArgsConstructorExample
    System.out.println("\n=== Demonstrating @RequiredArgsConstructor (for final fields) ===");
    RequiredArgsConstructorExample requiredArgs = new RequiredArgsConstructorExample("Grace");
    System.out.println("Name: " + requiredArgs.getName()); // Expected: Grace
    requiredArgs.setAge(28);
    System.out.println("Age: " + requiredArgs.getAge());   // Expected: 28

    // Demonstrate BuilderExample
    System.out.println("\n=== Demonstrating @Builder ===");
    BuilderExample builderEx = BuilderExample.builder()
        .name("Henry")
        .age(55)
        .build();
    System.out.println("Name: " + builderEx.getName()); // Expected: Henry
    System.out.println("Age: " + builderEx.getAge());   // Expected: 55

    // Demonstrate ValueExample
    System.out.println("\n=== Demonstrating @Value (immutable class, like @Data but with final fields and no setters) ===");
    ValueExample valueEx = new ValueExample("Ivy", 32);
    System.out.println("Name: " + valueEx.getName()); // Expected: Ivy
    System.out.println("Age: " + valueEx.getAge());   // Expected: 32
    System.out.println(valueEx); // Expected: ValueExample(name=Ivy, age=32)
    // Note: Cannot set values after creation, as there are no setters

    // Demonstrate Slf4jExample
    System.out.println("\n=== Demonstrating @Slf4j (for logging) ===");
    Slf4jExample slf4jEx = new Slf4jExample();
    slf4jEx.logMessage("This is a demo log message."); // Expected: Logs the message via SLF4J

    // Demonstrate UltimateLombokPojo (the "wow" example with minimal code)
    System.out.println("\n=== Demonstrating Ultimate Lombok POJO: Almost No Code Written! ===");
    // Using builder for fluent creation (generated by @Builder)
    UltimateLombokPojo ultimate = UltimateLombokPojo.builder()
        .name("Jordan")
        .age(29)
        .email("jordan@example.com")
        .build();
    // Demonstrating generated toString()
    System.out.println("toString: " + ultimate); // Expected: UltimateLombokPojo(name=Jordan, age=29, email=jordan@example.com)
    // Demonstrating generated getters (no setters since @Value makes it immutable)
    System.out.println("Name: " + ultimate.name()); // Expected: Jordan
    System.out.println("Age: " + ultimate.age());   // Expected: 29
    System.out.println("Email: " + ultimate.email()); // Expected: jordan@example.com
    // Demonstrating generated equals() and hashCode()
    UltimateLombokPojo ultimate2 = UltimateLombokPojo.builder()
        .name("Jordan")
        .age(29)
        .email("jordan@example.com")
        .build();
    System.out.println("ultimate equals ultimate2: " + ultimate.equals(ultimate2)); // Expected: true
    System.out.println("ultimate hashCode: " + ultimate.hashCode());
    System.out.println("ultimate2 hashCode: " + ultimate2.hashCode()); // Expected: same as ultimate
    // Note: No manual constructors, methods, or boilerplate written - all generated by Lombok!
  }

  /**
   * Demonstrates basic @Getter and @Setter annotations.
   * Lombok generates getter and setter methods for the fields, reducing boilerplate.
   * Without Lombok, you'd manually write public String getName() { return name; } and public void setName(String name) { this.name = name; }
   */
  @Getter
  @Setter
  static class BasicGetterSetter {
    private String name;
    private int age;
  }

  /**
   * Demonstrates @ToString annotation.
   * Lombok generates a toString() method that includes all fields.
   * Without Lombok, you'd override toString() manually: return "ToStringExample(name=" + name + ", age=" + age + ")";
   * Note: This annotation must be placed at the class level, not on methods.
   * It does not require parameters by default, but optional ones like exclude, include, callSuper, etc., can customize the output (e.g., @ToString(exclude = "age")).
   */
  @ToString
  @Getter  // Added for auto-generated getters; optional but reduces more boilerplate
  static class ToStringExample {
    private String name;
    private int age;

    public ToStringExample(String name, int age) {
      this.name = name;
      this.age = age;
    }

    // No need for manual toString() - Lombok generates it
    // No need for manual getters if using @Getter
  }

  /**
   * Demonstrates @EqualsAndHashCode annotation.
   * Lombok generates equals() and hashCode() methods based on fields.
   * Without Lombok, you'd override equals() with field comparisons (e.g., using Objects.equals(this.name, other.name))
   * and hashCode() using Objects.hash(name, age).
   * Note: This annotation must be placed at the class level, not on methods.
   * It does not require parameters by default, but optional ones like exclude, callSuper, etc., can be used for customization.
   */
  @EqualsAndHashCode
  @Getter  // Added for auto-generated getters; removes need for manual ones
  static class EqualsAndHashCodeExample {
    private String name;
    private int age;

    public EqualsAndHashCodeExample(String name, int age) {
      this.name = name;
      this.age = age;
    }

    // No need for manual equals() or hashCode() - Lombok generates them
    // No need for manual getters - @Getter handles them
  }

  /**
   * Demonstrates @Data annotation.
   * This is a convenience annotation that bundles @Getter, @Setter, @ToString, @EqualsAndHashCode, and @RequiredArgsConstructor.
   * Ideal for POJOs where you want all the basics without writing any boilerplate.
   */
  @Data
  static class DataExample {
    private String name;
    private int age;
  }

  /**
   * Demonstrates @NoArgsConstructor.
   * Lombok generates a no-argument constructor.
   * Without Lombok, you'd write: public NoArgsConstructorExample() {}
   */
  @NoArgsConstructor
  @Getter
  @Setter
  static class NoArgsConstructorExample {
    private String name;
  }

  /**
   * Demonstrates @AllArgsConstructor.
   * Lombok generates a constructor with all fields as arguments.
   * Without Lombok, you'd write: public AllArgsConstructorExample(String name, int age) { this.name = name; this.age = age; }
   */
  @AllArgsConstructor
  @Getter
  static class AllArgsConstructorExample {
    private String name;
    private int age;
  }

  /**
   * Demonstrates @RequiredArgsConstructor.
   * Lombok generates a constructor for all final fields (or @NonNull fields).
   * Useful for dependency injection or ensuring required fields are set at creation.
   */
  @RequiredArgsConstructor
  @Getter
  @Setter
  static class RequiredArgsConstructorExample {
    private final String name;
    private int age;
  }

  /**
   * Demonstrates @Builder.
   * Lombok generates a builder pattern for creating instances fluently.
   * Without Lombok, you'd need a static inner Builder class with methods like name(String) and build().
   */
  @Builder
  @Getter
  static class BuilderExample {
    private String name;
    private int age;
  }

  /**
   * Demonstrates @Value.
   * Similar to @Data but makes the class immutable: fields are final, no setters, and includes @ToString, @EqualsAndHashCode, @Getter.
   * Great for value objects like DTOs.
   */
  @Value
  static class ValueExample {
    String name;
    int age;
  }

  /**
   * Demonstrates @Slf4j.
   * Lombok generates a static logger field: private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Slf4jExample.class);
   * Useful for adding logging without boilerplate.
   */
  @Slf4j
  static class Slf4jExample {
    public void logMessage(String message) {
      log.info(message);
    }
  }


  /**
     * Demonstrates the ultimate minimal POJO with Lombok: Almost no code written!
     * By combining @Value (for immutability, getters, toString, equals, hashCode) and @Builder (for fluent creation),
     * we get a fully functional immutable class with builder pattern, constructors, and all standard methods generated.
     * Without Lombok, this would require hundreds of lines of boilerplate code for constructors, getters, equals, hashCode, toString, and a builder class.
     * Here, we just declare the fields and annotations - Lombok handles everything else!
     * Note: @Value implies final fields, no setters, @Getter, @ToString, @EqualsAndHashCode, and @AllArgsConstructor.
     * @Builder adds the fluent builder.
     */
    @Builder record UltimateLombokPojo(String name,int age, String email) {}
}
