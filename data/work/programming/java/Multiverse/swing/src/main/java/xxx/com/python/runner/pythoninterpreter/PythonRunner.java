package xxx.com.python.runner.pythoninterpreter;

import org.python.util.PythonInterpreter;

public class PythonRunner {
  public static void main(String[] args) {
    try (PythonInterpreter pythonInterpreter = new PythonInterpreter()) {
      pythonInterpreter.exec("print('Hello Python World!')");
    }
  }
}
