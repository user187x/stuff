import os
import sys
import torch
import warnings
from huggingface_hub import snapshot_download, hf_hub_download
from diffusers import AutoPipelineForText2Image

warnings.filterwarnings("ignore")

PROMPT = sys.argv[1]
OUTPUT = sys.argv[2]

PORTABLE_DIR = os.path.abspath("./portable_flux")
BASE_MODEL_DIR = os.path.join(PORTABLE_DIR, "base_model")
LORA_DIR = os.path.join(PORTABLE_DIR, "lora")
LORA_FILE = "flux_lustly-ai_v1.safetensors"

# --- PART A: SMART DOWNLOADER ---
if not os.path.exists(os.path.join(BASE_MODEL_DIR, "model_index.json")):
    print("=> [2/3] Base model not found locally. Downloading to portable folder (~24GB)...")
    snapshot_download(
        repo_id="black-forest-labs/FLUX.1-schnell",
        local_dir=BASE_MODEL_DIR,
        ignore_patterns=["*.pt", "*.bin"]
    )

if not os.path.exists(os.path.join(LORA_DIR, LORA_FILE)):
    print("=> [2/3] LoRA not found locally. Downloading to portable folder...")
    hf_hub_download(
        repo_id="lustlyai/Flux_Lustly.ai_Uncensored_nsfw_v1",
        filename=LORA_FILE,
        local_dir=LORA_DIR
    )

# --- PART B: OFFLINE GENERATOR ---
print("=> [3/3] Going strictly offline for generation...")
os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["DISABLE_TELEMETRY"] = "1"

print("   Loading base FLUX model locally...")
pipeline = AutoPipelineForText2Image.from_pretrained(
    BASE_MODEL_DIR,
    torch_dtype=torch.bfloat16,
    local_files_only=True
)
pipeline.enable_model_cpu_offload()

print("   Loading Lustly AI LoRA locally...")
pipeline.load_lora_weights(
    LORA_DIR,
    weight_name=LORA_FILE,
    adapter_name="v1",
    local_files_only=True
)
pipeline.set_adapters(["v1"], adapter_weights=[1.0])

print(f"   Generating image: '{PROMPT}'...")
image = pipeline(
    prompt=PROMPT,
    guidance_scale=0.0,
    height=768,
    width=768,
    num_inference_steps=4,
).images[0]

image.save(OUTPUT)
print(f"=> Success! Image saved locally as {OUTPUT}")
