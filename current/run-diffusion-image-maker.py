import torch
from diffusers import AutoPipelineForText2Image

# 1. Load the base FLUX model
# (You will need to accept the license on Hugging Face and log in via huggingface-cli for FLUX.1-dev)
pipeline = AutoPipelineForText2Image.from_pretrained(
    "black-forest-labs/FLUX.1-dev",
    torch_dtype=torch.bfloat16
)
# Offloads parts of the model to CPU when not in active use to save VRAM
pipeline.enable_model_cpu_offload()

# 2. Load the LoRA weights from the Lustly repository
pipeline.load_lora_weights(
    "lustlyai/Flux_Lustly.ai_Uncensored_nsfw_v1",
    weight_name="flux_lustly-ai_v1.safetensors",
    adapter_name="v1"
)
# Set the LoRA strength (1.0 is full strength)
pipeline.set_adapters(["v1"], adapter_weights=[1.0])

# 3. Generate the image
prompt = "Your text prompt goes here"

image = pipeline(
    prompt=prompt,
    guidance_scale=4.0,
    height=768,
    width=768,
    num_inference_steps=20,
).images[0]

image.save("flux_generation.png")
