package xxx.com.llm.interactive;

import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;
import de.kherud.llama.args.NumaStrategy;
import xxx.com.llm.support.ConversationManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class LLMRunner {
  public static void main(String... args) {
    ModelParameters params =
        new ModelParameters()
            .setModel("D:/LLMs/llama-3-instruct-neurona-8b-v2-q4_k_m.gguf")
            .setGpuLayers(40)
            .setCtxSize(8192)
            .setBatchSize(2048)
            .setThreads(16)
            .setThreadsBatch(16)
            .enableFlashAttn()
            .disableLog()
            .setNuma(NumaStrategy.DISTRIBUTE)
            .enableMlock();

    try (var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        LlamaModel model = new LlamaModel(params)) {
      new ConversationManager("Grey", "Computer", "exit").start(model, reader);
    }
    catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
    }
  }
}
