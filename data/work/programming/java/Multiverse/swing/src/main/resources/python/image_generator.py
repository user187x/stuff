import torch
from diffusers import StableDiffusionPipeline
import sys

def generate_image(prompt, output_path):
    """
    Generates an image based on a text prompt using a pre-trained Stable Diffusion model.
    """
    print("Loading Stable Diffusion model... This might take some time and download data on the first run.")

    # Check if CUDA is available for GPU acceleration
    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"Using device: {device}")

    # Load the pre-trained pipeline
    pipe = StableDiffusionPipeline.from_pretrained("runwayml/stable-diffusion-v1-5")
    pipe = pipe.to(device)

    print(f"Generating image for prompt: '{prompt}'")
    # Generate the image
    with torch.no_grad():
        image = pipe(prompt).images[0]

    # Save the image
    image.save(output_path)
    print(f"Image successfully saved to {output_path}")


if __name__ == "__main__":
    # This block executes when the script is run directly from the command line
    if len(sys.argv) != 3:
        print("Usage: python image_generator.py \"<prompt>\" <output_path>")
        sys.exit(1)

    # Get the prompt and output path from the command-line arguments
    prompt_arg = sys.argv[1]
    output_path_arg = sys.argv[2]

    try:
        generate_image(prompt_arg, output_path_arg)
    except Exception as e:
        print(f"An error occurred during image generation: {e}")
        sys.exit(1)
