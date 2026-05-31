package xxx.com.llm.finetune;

import org.deeplearning4j.nn.conf.BackpropType;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer; // <-- IMPORT THE CORRECT LAYER
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A simple, self-contained Java class that demonstrates character-level text generation
 * using a Long Short-Term Memory (LSTM) neural network with DeepLearning4j.
 * This class emulates the basic principles of how a Large Language Model works.
 */
public class MiniLlm {

  public static void main(String[] args) {

    // --- 1. Define Training Data ---
    // In a real-world scenario, you would load this from a large text file.
    // For this example, we'll use a simple, short string.
    String trainingData = "hello world. this is a simple test. we are learning how models generate text.";

    // --- 2. Prepare the Data ---
    // We need to create a vocabulary of all unique characters and map them to integers.
    Set<Character> uniqueChars = new HashSet<>();
    for (char c : trainingData.toCharArray()) {
      uniqueChars.add(c);
    }

    List<Character> vocab = new ArrayList<>(uniqueChars);
    Map<Character, Integer> charToIndex = new HashMap<>();
    Map<Integer, Character> indexToChar = new HashMap<>();
    for (int i = 0; i < vocab.size(); i++) {
      charToIndex.put(vocab.get(i), i);
      indexToChar.put(i, vocab.get(i));
    }
    int vocabSize = vocab.size();
    System.out.println("Vocabulary Size: " + vocabSize);

    // --- 3. Create Input and Target Sequences ---
    // The network learns by predicting the next character in a sequence.
    // Input: "hell" -> Target: "ello"
    int sequenceLength = 10;
    List<INDArray> inputs = new ArrayList<>();
    List<INDArray> targets = new ArrayList<>();

    for (int i = 0; i < trainingData.length() - sequenceLength; i++) {
      INDArray inputSequence = Nd4j.zeros(1, vocabSize, sequenceLength);
      INDArray targetSequence = Nd4j.zeros(1, vocabSize, sequenceLength);

      for (int j = 0; j < sequenceLength; j++) {
        char inChar = trainingData.charAt(i + j);
        char outChar = trainingData.charAt(i + j + 1);

        inputSequence.putScalar(new int[]{0, charToIndex.get(inChar), j}, 1);
        targetSequence.putScalar(new int[]{0, charToIndex.get(outChar), j}, 1);
      }
      inputs.add(inputSequence);
      targets.add(targetSequence);
    }

    // --- 4. Configure the Neural Network ---
    // We will use an LSTM (Long Short-Term Memory) network, which is good for sequence data.
    int lstmLayerSize = 128;
    MultiLayerConfiguration config = new NeuralNetConfiguration.Builder()
        .seed(12345)
        .weightInit(WeightInit.XAVIER)
        .updater(new Adam(0.01))
        .list()
        .layer(0, new LSTM.Builder().nIn(vocabSize).nOut(lstmLayerSize)
            .activation(Activation.TANH).build())
        .layer(1, new LSTM.Builder().nIn(lstmLayerSize).nOut(lstmLayerSize)
            .activation(Activation.TANH).build())
        // --- THIS IS THE FIX ---
        // Replace DenseLayer with RnnOutputLayer and specify a loss function.
        .layer(2, new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
            .activation(Activation.SOFTMAX) // Still use SOFTMAX for classification
            .nIn(lstmLayerSize).nOut(vocabSize).build())
        .backpropType(BackpropType.TruncatedBPTT)
        .tBPTTForwardLength(sequenceLength)
        .tBPTTBackwardLength(sequenceLength)
        .build();

    MultiLayerNetwork model = new MultiLayerNetwork(config);
    model.init();

    // --- 5. Train the Model ---
    int epochs = 50;
    System.out.println("Starting training...");
    for (int i = 0; i < epochs; i++) {
      for (int j = 0; j < inputs.size(); j++) {
        DataSet dataSet = new DataSet(inputs.get(j), targets.get(j));
        model.fit(dataSet);
      }
      if ((i + 1) % 10 == 0) {
        System.out.println("Epoch " + (i + 1) + " / " + epochs);
        // After every 10 epochs, let's generate some sample text.
        System.out.println("--- Generating sample text ---");
        System.out.println(generateText(model, trainingData, charToIndex, indexToChar, 100));
        System.out.println("----------------------------\n");
      }
    }
    System.out.println("Training complete!");
  }

  /**
   * Generates text using the trained model.
   * @param model The trained MultiLayerNetwork.
   * @param trainingData The original data to pick a random starting seed from.
   * @param charToIndex Map of characters to their integer index.
   * @param indexToChar Map of integer indices to their characters.
   * @param length The length of the text to generate.
   * @return The generated text.
   */
  private static String generateText(MultiLayerNetwork model, String trainingData, Map<Character, Integer> charToIndex, Map<Integer, Character> indexToChar, int length) {
    model.rnnClearPreviousState();
    Random random = new Random();

    // Pick a random starting character from the training data as a seed.
    int startIndex = random.nextInt(trainingData.length() - 1);
    char startChar = trainingData.charAt(startIndex);

    INDArray input = Nd4j.zeros(1, charToIndex.size());
    input.putScalar(charToIndex.get(startChar), 1);

    StringBuilder generatedText = new StringBuilder(String.valueOf(startChar));

    for (int i = 0; i < length; i++) {
      INDArray output = model.rnnTimeStep(input);

      // Sample a character from the output distribution.
      // This adds some randomness instead of always picking the most likely character.
      double[] outputProb = output.data().asDouble();
      int sampledCharIndex = sampleFromDistribution(outputProb, random);

      generatedText.append(indexToChar.get(sampledCharIndex));

      // The new input is the character we just generated.
      input = Nd4j.zeros(1, charToIndex.size());
      input.putScalar(sampledCharIndex, 1);
    }

    return generatedText.toString();
  }

  /**
   * Samples an index from a probability distribution.
   */
  private static int sampleFromDistribution(double[] distribution, Random random) {
    double r = random.nextDouble();
    double sum = 0.0;
    for (int i = 0; i < distribution.length; i++) {
      sum += distribution[i];
      if (r <= sum) {
        return i;
      }
    }
    return distribution.length - 1; // Should not happen with a valid distribution
  }
}
