package xxx.com.bytebuddy.modify;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FieldAccessor;

public class ClassModifier {

  public static void main(String[] args) throws Exception {
    // Assume we have an existing class to update by subclassing it
    // For this example, we'll subclass Object.class as the base class
    Class<?> dynamicClass =
        new ByteBuddy()
            .subclass(Object.class) // Replace Object.class with YourExistingClass.class if needed
            .name("DynamicClassWithAccessor")
            .defineField(
                "value", int.class, Visibility.PRIVATE) // Add a private field 'value' of type int
            .defineMethod("getValue", int.class, Visibility.PUBLIC) // Define getter method
            .intercept(FieldAccessor.ofField("value"))
            .defineMethod("setValue", void.class, Visibility.PUBLIC) // Define setter method
            .withParameters(int.class)
            .intercept(FieldAccessor.ofField("value"))
            .make()
            .load(ClassModifier.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
            .getLoaded();

    // Instantiate the new class
    Object instance = dynamicClass.getConstructor().newInstance();

    // Use the setter and getter
    dynamicClass.getMethod("setValue", int.class).invoke(instance, 42);
    int retrievedValue = (int) dynamicClass.getMethod("getValue").invoke(instance);
    System.out.println("Retrieved value: " + retrievedValue); // Outputs: Retrieved value: 42

    // Now, this instance can be passed to another object or method that accepts the base class
    // (Object or YourExistingClass)
    // For example: someMethod(instance);
  }
}
