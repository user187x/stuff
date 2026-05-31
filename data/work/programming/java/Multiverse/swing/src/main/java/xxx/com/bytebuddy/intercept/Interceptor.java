package xxx.com.bytebuddy.intercept;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

public class Interceptor {

  /** A custom object that will be the return type of our method. */
  public static class GreetingResponse {

    private final String message;

    public GreetingResponse(String message) {
      this.message = message;
    }

    public String getMessage() {
      return message;
    }

    @Override
    public String toString() {
      return "GreetingResponse{message='" + message + "'}";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      GreetingResponse that = (GreetingResponse) o;
      return Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
      return Objects.hash(message);
    }
  }

  /**
   * This is the original class we want to modify. Its method now returns our custom
   * GreetingResponse object.
   */
  public static class Greeter {

    public GreetingResponse sayHello() {
      return new GreetingResponse("Hello, World!");
    }
  }

  public static void main(String[] args)
      throws InstantiationException,
          IllegalAccessException,
          NoSuchMethodException,
          InvocationTargetException {
    System.out.println("--- Demonstrating Byte Buddy Method Interception with a Custom Object ---");

    // --- Step 1: Create a dynamic subclass using Byte Buddy ---
    Class<? extends Greeter> dynamicGreeterType =
        new ByteBuddy()
            .subclass(Greeter.class)
            // We select the method to intercept, which is still named "sayHello".
            .method(ElementMatchers.named("sayHello"))
            // We define the interception logic to return a *new instance* of GreetingResponse.
            // This demonstrates returning a completely different object.
            .intercept(FixedValue.value(new GreetingResponse("Hello, Intercepted World!")))
            .make()
            .load(Interceptor.class.getClassLoader())
            .getLoaded();

    // --- Step 2: Create an instance of our new dynamic class ---
    System.out.println("\nInstantiating the dynamically created Greeter subclass...");
    Greeter dynamicGreeter = dynamicGreeterType.getDeclaredConstructor().newInstance();

    // --- Step 3: Call the intercepted method ---
    // The return type is now our custom GreetingResponse object.
    GreetingResponse result = dynamicGreeter.sayHello();
    System.out.println("Result of dynamicGreeter.sayHello(): " + result);
    System.out.println("Success! The original object return value was replaced.");

    // --- For comparison: create and call the original class ---
    System.out.println("\nFor comparison, here is the original class in action:");
    Greeter originalGreeter = new Greeter();
    System.out.println("Result of originalGreeter.sayHello(): " + originalGreeter.sayHello());
  }
}
