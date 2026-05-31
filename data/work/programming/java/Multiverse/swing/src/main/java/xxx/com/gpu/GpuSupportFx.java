package xxx.com.gpu;

import jcuda.*;
import jcuda.driver.*;
import jcuda.nvrtc.*;
import jcuda.runtime.JCuda;
import jcuda.jcufft.JCufft;
import jcuda.jcufft.cufftHandle;
import jcuda.jcufft.cufftType;

import static jcuda.driver.JCudaDriver.*;
import static jcuda.nvrtc.JNvrtc.*;

public class GpuSupportFx {

  private static final int MAX_FFT_SIZE = 16384;
  private static final int MAX_AUDIO_BUFFER_SIZE = 8192;

  public static CUcontext context;

  private static CUmodule kernelsModule;
  private static CUfunction normalizeAudioKernel, hannKernel, magKernel, ribbonWaveKernel;
  private static cufftHandle fftPlan;

  // --- MODIFICATION: Create dedicated buffers for each task to prevent race conditions ---
  private static CUdeviceptr d_audioInShort;
  private static CUdeviceptr d_fftInputBuffer;       // For the spectrum analyzer FFT
  private static CUdeviceptr d_liveVisNormOutput;    // For the LiveWaveformPanel normalization
  private static CUdeviceptr d_ribbonWaveInputBuffer;// For the Ribbon Wave visualization input
  private static CUdeviceptr d_fftComplexOut;
  private static CUdeviceptr d_fftMagOut;
  private static CUdeviceptr d_ribbonCoords;

  /**
   * Initializes the CUDA context, compiles all kernels, and pre-allocates GPU memory.
   * Call this once at application startup.
   */
  public static void init() {

    JCuda.setExceptionsEnabled(true);
    JCudaDriver.setExceptionsEnabled(true);
    JNvrtc.setExceptionsEnabled(true);

    cuInit(0);
    CUdevice device = new CUdevice();
    cuDeviceGet(device, 0);
    context = new CUcontext();
    cuCtxCreate(context, 0, device);

    // --- Compile All Kernels At Once ---
    String allKernelsSource =
        """
        extern "C" {
            // For gpuNormalizeAudio
            __global__ void normalizeAudio(const short *in, float *out, int n) {
                int idx = blockIdx.x * blockDim.x + threadIdx.x;
                if (idx < n) {
                    out[idx] = in[idx] / 32768.0f; // Normalize short to [-1.0, 1.0]
                }
            }

            // For gpuFftMagnitudes: Applies a Hann window to reduce spectral leakage
            __global__ void applyHann(float* x, int n) {
                int i = blockIdx.x * blockDim.x + threadIdx.x;
                if (i < n) {
                    const float pi = 3.14159265358979323846f;
                    float w = 0.5f * (1.0f - cosf(2.0f * pi * i / (n - 1)));
                    x[i] *= w;
                }
            }

            // For gpuFftMagnitudes: Converts complex FFT output to magnitude
            __global__ void complexToMagnitudeScale(const float2* inComplex, float* outMag, int nComplex, float scale) {
                int i = blockIdx.x * blockDim.x + threadIdx.x;
                if (i < nComplex) {
                    float re = inComplex[i].x;
                    float im = inComplex[i].y;
                    float mag = sqrtf(re*re + im*im) * scale;
                    outMag[i] = mag;
                }
            }

            __global__ void ribbonWaveKernel(const float* samples, float2* coords, int numSamples, int numRibbons, int w, int h, int x_off, int y_mid) {

                int ribbonIdx = blockIdx.x; // Each block handles one ribbon
                int sampleIdx = threadIdx.x; // Each thread in a block handles one sample/vertex

                if (ribbonIdx < numRibbons && sampleIdx < numSamples) {

                    // Calculate vertex for the top path of the ribbon
                    float scale_top = 1.0f - ((float)ribbonIdx / numRibbons);
                    float scaledHeight_top = samples[sampleIdx] * (h / 2.0f) * scale_top;
                    double step_top = (double)w / (numSamples - 1);

                    float x_top = x_off + (float)(sampleIdx * step_top);
                    float y_top = y_mid - scaledHeight_top;

                    // The output array is structured as [top_ribbon0, bottom_ribbon0, top_ribbon1, bottom_ribbon1, ...]
                    int top_coord_idx = (ribbonIdx * 2 * numSamples) + sampleIdx;
                    coords[top_coord_idx] = make_float2(x_top, y_top);

                    // Calculate vertex for the bottom path of the ribbon
                    float scale_bottom = 1.0f - ((float)ribbonIdx / numRibbons);
                    float scaledHeight_bottom = samples[sampleIdx] * (h / 2.0f) * scale_bottom;
                    double step_bottom = (double)w / (numSamples - 1);

                    float x_bottom = x_off + (float)(sampleIdx * step_bottom);
                    float y_bottom = y_mid + scaledHeight_bottom;

                    int bottom_coord_idx = (ribbonIdx * 2 * numSamples) + numSamples + sampleIdx;
                    coords[bottom_coord_idx] = make_float2(x_bottom, y_bottom);
                }
            }

        } // extern "C"
        """;

    String ptx = compilePtx(allKernelsSource);
    kernelsModule = new CUmodule();
    cuModuleLoadData(kernelsModule, ptx);

    // Load functions (this is correct)
    normalizeAudioKernel = new CUfunction(); /* ... load ... */
    hannKernel = new CUfunction();           /* ... load ... */
    magKernel = new CUfunction();            /* ... load ... */
    ribbonWaveKernel = new CUfunction();     /* ... load ... */
    cuModuleGetFunction(normalizeAudioKernel, kernelsModule, "normalizeAudio");
    cuModuleGetFunction(hannKernel, kernelsModule, "applyHann");
    cuModuleGetFunction(magKernel, kernelsModule, "complexToMagnitudeScale");
    cuModuleGetFunction(ribbonWaveKernel, kernelsModule, "ribbonWaveKernel");

    // --- MODIFICATION: Allocate all the new dedicated buffers ---
    d_audioInShort = new CUdeviceptr();
    d_fftInputBuffer = new CUdeviceptr();
    d_liveVisNormOutput = new CUdeviceptr();
    d_ribbonWaveInputBuffer = new CUdeviceptr();
    d_fftComplexOut = new CUdeviceptr();
    d_fftMagOut = new CUdeviceptr();
    d_ribbonCoords = new CUdeviceptr();

    cuMemAlloc(d_audioInShort, (long) MAX_AUDIO_BUFFER_SIZE * Sizeof.SHORT);
    cuMemAlloc(d_fftInputBuffer, (long) MAX_FFT_SIZE * Sizeof.FLOAT);
    cuMemAlloc(d_liveVisNormOutput, (long) MAX_AUDIO_BUFFER_SIZE * Sizeof.FLOAT);
    cuMemAlloc(d_ribbonWaveInputBuffer, (long) MAX_AUDIO_BUFFER_SIZE * Sizeof.FLOAT);
    cuMemAlloc(d_fftComplexOut, (long) (MAX_FFT_SIZE / 2 + 1) * 2L * Sizeof.FLOAT);
    cuMemAlloc(d_fftMagOut, (long) (MAX_FFT_SIZE / 2 + 1) * Sizeof.FLOAT);
    cuMemAlloc(d_ribbonCoords, (long) 16 * MAX_AUDIO_BUFFER_SIZE * 2 * (2 * Sizeof.FLOAT));

    fftPlan = new cufftHandle();
    JCufft.cufftPlan1d(fftPlan, MAX_FFT_SIZE, cufftType.CUFFT_R2C, 1);
  }

  public static void cleanup() {
    if (context != null) {
      // Free all buffers
      cuMemFree(d_audioInShort);
      cuMemFree(d_fftInputBuffer);
      cuMemFree(d_liveVisNormOutput);
      cuMemFree(d_ribbonWaveInputBuffer);
      cuMemFree(d_fftComplexOut);
      cuMemFree(d_fftMagOut);
      cuMemFree(d_ribbonCoords);
      JCufft.cufftDestroy(fftPlan);
      cuCtxDestroy(context);
      context = null;
    }
  }

  /**
   * Normalizes audio for the LIVE VISUALIZER. Uses its own dedicated buffer.
   */
  public static void gpuNormalizeAudio(short[] samples, float[] output) {
    if (context == null) throw new IllegalStateException("CUDA not initialized.");
    int length = samples.length;
    if (length > MAX_AUDIO_BUFFER_SIZE) throw new IllegalArgumentException("Sample count exceeds pre-allocated buffer size.");

    cuMemcpyHtoD(d_audioInShort, Pointer.to(samples), (long) length * Sizeof.SHORT);

    Pointer kernelParams = Pointer.to(Pointer.to(d_audioInShort), Pointer.to(d_liveVisNormOutput), Pointer.to(new int[]{length}));
    cuLaunchKernel(normalizeAudioKernel, (length + 255) / 256, 1, 1, 256, 1, 1, 0, null, kernelParams, null);

    cuMemcpyDtoH(Pointer.to(output), d_liveVisNormOutput, (long) length * Sizeof.FLOAT);
  }

  /**
   * Computes FFT for the SPECTRUM ANALYZER. Uses its own dedicated buffer.
   */
  public static float[] gpuFftMagnitudes(float[] samples, boolean applyHann) {
    if (context == null) throw new IllegalStateException("CUDA not initialized.");
    int n = samples.length;
    int N = 1;
    while (N < n) N <<= 1;
    if (N > MAX_FFT_SIZE) throw new IllegalArgumentException("FFT size exceeds max size.");

    float[] frame = (N == n) ? samples : java.util.Arrays.copyOf(samples, N);
    cuMemcpyHtoD(d_fftInputBuffer, Pointer.to(frame), (long) N * Sizeof.FLOAT);

    if (applyHann) {
      Pointer hannParams = Pointer.to(Pointer.to(d_fftInputBuffer), Pointer.to(new int[]{N}));
      cuLaunchKernel(hannKernel, (N + 255) / 256, 1, 1, 256, 1, 1, 0, null, hannParams, null);
    }

    JCufft.cufftExecR2C(fftPlan, d_fftInputBuffer, d_fftComplexOut);

    float scale = 2.0f / N;
    int complexBins = N / 2 + 1;
    Pointer magParams = Pointer.to(Pointer.to(d_fftComplexOut), Pointer.to(d_fftMagOut), Pointer.to(new int[]{complexBins}), Pointer.to(new float[]{scale}));
    cuLaunchKernel(magKernel, (complexBins + 255) / 256, 1, 1, 256, 1, 1, 0, null, magParams, null);

    float[] magnitudes = new float[complexBins];
    cuMemcpyDtoH(Pointer.to(magnitudes), d_fftMagOut, (long) complexBins * Sizeof.FLOAT);
    return magnitudes;
  }

  /**
   * Calculates vertices for the RIBBON WAVE. Uses its own dedicated buffer.
   */
  public static void gpuCalculateRibbonWave(float[] samples, float[] outputCoords, int w, int h, int x_off, int y_mid, int numRibbons) {
    if (context == null) throw new IllegalStateException("CUDA not initialized.");
    int numSamples = samples.length;
    if (numSamples > MAX_AUDIO_BUFFER_SIZE) throw new IllegalArgumentException("Sample count exceeds pre-allocated buffer size.");

    cuMemcpyHtoD(d_ribbonWaveInputBuffer, Pointer.to(samples), (long) numSamples * Sizeof.FLOAT);

    Pointer kernelParams = Pointer.to(
        Pointer.to(d_ribbonWaveInputBuffer), Pointer.to(d_ribbonCoords),
        Pointer.to(new int[]{numSamples}), Pointer.to(new int[]{numRibbons}),
        Pointer.to(new int[]{w}), Pointer.to(new int[]{h}),
        Pointer.to(new int[]{x_off}), Pointer.to(new int[]{y_mid})
    );
    cuLaunchKernel(ribbonWaveKernel, numRibbons, 1, 1, numSamples, 1, 1, 0, null, kernelParams, null);

    long size = (long) numRibbons * numSamples * 2 * (2 * Sizeof.FLOAT);
    cuMemcpyDtoH(Pointer.to(outputCoords), d_ribbonCoords, size);
  }

  /** Helper to compile a CUDA source string to PTX using NVRTC. */
  private static String compilePtx(String kernelSource) {
    nvrtcProgram program = new nvrtcProgram();
    nvrtcCreateProgram(program, kernelSource, "kernels.cu", 0, null, null);
    String[] options = new String[]{"--gpu-architecture=compute_52"}; // Target a reasonable architecture
    try {
      nvrtcCompileProgram(program, options.length, options);
    } catch (CudaException e) {
      String[] log = new String[1];
      nvrtcGetProgramLog(program, log);
      System.err.println("NVRTC Compilation Failed:\n" + log[0]);
      throw e;
    }
    String[] ptx = new String[1];
    nvrtcGetPTX(program, ptx);
    nvrtcDestroyProgram(program);
    return ptx[0];
  }
}
