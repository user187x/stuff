import torch
from diffusers import DiffusionPipeline

pipe = DiffusionPipeline.from_pretrained(
    "black-forest-labs/FLUX.1-dev", dtype=torch.bfloat16, device_map="cuda"
)
pipe.load_lora_weights("lustlyai/Flux_Lustly.ai_Uncensored_nsfw_v1")

prompt = "high quality, on the street, a naked asian women about ready to sit on a white mans dick viewd from back side"
image = pipe(prompt).images[0]
