package xxx.com.gpu;

public final class CudaKernels {

  public final static String KERNEL_SOURCE =
      """
        extern "C" {
          __global__ void matrixMulKernel(const float *A, const float *B, float *C, int n) {
          
            int row = blockIdx.y * blockDim.y + threadIdx.y;
            int col = blockIdx.x * blockDim.x + threadIdx.x;
            float sum = 0.0f;
              
            if (row < n && col < n) {
              for (int i = 0; i < n; ++i) {
                sum += A[row * n + i] * B[i * n + col];
               }
              
              C[row * n + col] = sum;
            }
          }
        }
      """;
}
