import sys
import torch
import warnings
from diffusers import AutoPipelineForText2Image

# Suppress warnings for cleaner output
warnings.filterwarnings("ignore")

prompt = sys.argv[1]

print("=> Loading base FLUX model (Schnell)...")
pipeline = AutoPipelineForText2Image.from_pretrained(
    "black-forest-labs/FLUX.1-schnell", torch_dtype=torch.bfloat16
)
# Optimizes VRAM usage by offloading to CPU
pipeline.enable_model_cpu_offload()

print("=> Loading Lustly AI LoRA...")
pipeline.load_lora_weights(
    "lustlyai/Flux_Lustly.ai_Uncensored_nsfw_v1",
    weight_name="flux_lustly-ai_v1.safetensors",
    adapter_name="v1",
)
pipeline.set_adapters(["v1"], adapter_weights=[1.0])

print(f"=> Generating image...")
image = pipeline(
    prompt=prompt,
    guidance_scale=0.0,
    height=768,
    width=768,
    num_inference_steps=4,
).images[0]

image.save("output.png")
print("=> Success! Image saved locally as output.png")
