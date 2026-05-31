package xxx.com.gpu;

import jcuda.*;
import jcuda.driver.*;
import jcuda.nvrtc.*;
import jcuda.runtime.JCuda;

import java.util.stream.IntStream;

import static jcuda.driver.JCudaDriver.*;
import static jcuda.nvrtc.JNvrtc.*;

public class GpuInvoker {

  private static class CudaDriver {

    int N = 1024;
    float[] h_A = new float[N * N];
    float[] h_B = new float[N * N];
    float[] h_C = new float[N * N];
    int blockSize = 16;
    int gridSize = (N + blockSize - 1) / blockSize;

    CUdevice device = new CUdevice();
    CUcontext context = new CUcontext();
    CUmodule module = new CUmodule();
    CUfunction function = new CUfunction();
    CUdeviceptr d_A = new CUdeviceptr();
    CUdeviceptr d_B = new CUdeviceptr();
    CUdeviceptr d_C = new CUdeviceptr();
    Pointer kernelParams = null;

    public final String ptxCode = compilePtx();

    public CudaDriver() {

      for (int i = 0; i < N * N; i++) {
        h_A[i] = (float) Math.random();
        h_B[i] = (float) Math.random();
      }

      if (ptxCode == null) {
        System.err.println("Kernel compilation failed.");
        System.exit(1);
      }

      init();
    }

    public void init() {

      cuInit(0);
      device = new CUdevice();
      cuDeviceGet(device, 0);
      context = new CUcontext();
      cuCtxCreate(context, 0, device);

      module = new CUmodule();
      cuModuleLoadData(module, ptxCode);
      function = new CUfunction();
      cuModuleGetFunction(function, module, "matrixMulKernel");

      cuMemAlloc(d_A, (long) N * N * Sizeof.FLOAT);
      cuMemAlloc(d_B, (long) N * N * Sizeof.FLOAT);
      cuMemAlloc(d_C, (long) N * N * Sizeof.FLOAT);

      cuMemcpyHtoD(d_A, Pointer.to(h_A), (long) N * N * Sizeof.FLOAT);
      cuMemcpyHtoD(d_B, Pointer.to(h_B), (long) N * N * Sizeof.FLOAT);

      blockSize = 16;
      gridSize = (N + blockSize - 1) / blockSize;
      kernelParams = Pointer.to(
          Pointer.to(d_A),
          Pointer.to(d_B),
          Pointer.to(d_C),
          Pointer.to(new int[]{N}));
    }

    /** Compile CUDA source to PTX with NVRTC and return the PTX string. */
    private static String compilePtx() {

      JNvrtc.setExceptionsEnabled(true);
      nvrtcProgram program = new nvrtcProgram();
      nvrtcCreateProgram(program, CudaKernels.KERNEL_SOURCE, "matrixMul.cu", 0, null, null);

      String[] options = new String[] { };
      nvrtcCompileProgram(program, options.length, options);

      String[] logStr = new String[1];
      nvrtcGetProgramLog(program, logStr);

      if (logStr[0] != null && !logStr[0].trim().isEmpty()) {
        System.out.println("NVRTC Compile Log:\n" + logStr[0]);
      }

      String[] ptxStr = new String[1];
      nvrtcGetPTX(program, ptxStr);

      nvrtcDestroyProgram(program);
      return ptxStr[0];
    }

    public void run(int iterations) {

      IntStream.range(0, iterations).forEach(i -> {
        cuLaunchKernel(function, gridSize, gridSize, 1, blockSize, blockSize, 1, 0, null, kernelParams, null);
      });

      // Wait for all kernels to complete
      cuCtxSynchronize();

      // Copy result from device (GPU) back to host (CPU)
      cuMemcpyDtoH(Pointer.to(h_C), d_C, (long) N * N * Sizeof.FLOAT);
    }

    public void cleanup() {

      cuMemFree(d_A);
      cuMemFree(d_B);
      cuMemFree(d_C);

      if (module != null) {
        cuModuleUnload(module);
      }

      if (context != null) {
        cuCtxDestroy(context);
      }
    }
  }

  /** One-off run: multiply two N×N matrices on GPU and print first 5×5 of result. */
  public static void runSingleMultiplication() {

    System.out.println("Starting Single Multiplcation test");
    long startTime = System.nanoTime();

    CudaDriver cuda = new CudaDriver();
    cuda.run(1);

    System.out.println("CUDA matrix multiplication complete. First 5x5 sub-matrix of C:");
    for (int i = 0; i < 5; i++) {
      for (int j = 0; j < 5; j++) {
        System.out.printf("%.6f ", cuda.h_C[i * cuda.N + j]);
      }
      System.out.println();
    }
  }

  /** Stress test: run N×N multiplication repeatedly to load GPU. */
  public static void stressTestGPU(int iterations) {

    System.out.println("Starting GPU stress test with " + iterations + " iterations...");
    long startTime = System.nanoTime();

    CudaDriver cuda = new CudaDriver();
    cuda.run(iterations);

    long endTime = System.nanoTime();
    double seconds = (endTime - startTime) / 1e9;

    System.out.printf("Completed %d iterations in %.3f seconds (%.3f sec/iter)%n",iterations, seconds, seconds / iterations);

    // Ensure resources are always cleaned up
    cuda.cleanup();
  }

  public static void askUser(){

    java.util.Scanner scanner = new java.util.Scanner(System.in);

    System.out.println("Choose mode:");
    System.out.println("1 - Run single matrix multiplication");
    System.out.println("2 - Run stress test");
    System.out.print("Enter choice: ");
    int choice = scanner.nextInt();

    if (choice == 1) {
      runSingleMultiplication();
    }
    else if (choice == 2) {

      System.out.print("Enter number of iterations for stress test: ");
      int iterations = scanner.nextInt();

      stressTestGPU(iterations);
    }
    else {
      System.out.println("Invalid choice.");
    }

    scanner.close();
  }

  public static void main(String[] args) {

    JCuda.setExceptionsEnabled(true);
    JCudaDriver.setExceptionsEnabled(true);
    JNvrtc.setExceptionsEnabled(true);

    askUser();
  }
}
