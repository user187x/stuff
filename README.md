# 🛠️ dev.environment

![Status: Work in Progress](https://img.shields.io/badge/Status-Work_in_Progress-orange.svg)
![Target: Ubuntu](https://img.shields.io/badge/Target-Ubuntu-E95420?logo=ubuntu&logoColor=white)
![Terminal: Kitty | Tmux | Vim](https://img.shields.io/badge/Terminal-Kitty_%7C_Tmux_%7C_Vim-blue)

A monolithic, "zero-to-hero" automation project designed to provision and reproduce a highly customized, consistent developer environment. Whether deploying locally on a host machine, within a Virtual Machine, orchestrating a Cloud instance, or spinning up a Docker container, this framework ensures absolute environment parity.

> **⚠️ DEVELOPMENT STATUS:** The `init` script is currently under heavy construction[cite: 2]. While the foundational bash configurations and utilities are functional, the automated restoration pipelines (for software, packages, and desktop settings) are still being wired up[cite: 2]. Proceed with caution.

## ✨ Features

### 🚀 Automated Bootstrapping (`init`)
The core of the project is the interactive `init` script, which orchestrates the deployment of the environment[cite: 2]. Once completed, it will handle:
* **System Integrity:** Pre-flight checks to map resource paths and automatically resolve broken symlinks[cite: 2].
* **Asset Provisioning:** Unpacking and placing custom fonts, audio files, and profile images into their respective `XDG` directories[cite: 2].
* **Environment Parity:** Restoring previously installed APT packages, custom software binaries, and GNOME desktop configurations via `gsettings`[cite: 2].

### 💻 Terminal & Shell Mastery
The shell environment is heavily optimized for a TUI-centric workflow, utilizing a custom `.bashrc` and modularized bash configurations[cite: 1, 2].
* **Dynamic Prompts:** Context-aware prompts that visually indicate command success or failure via color-coded exit status tracking[cite: 1].
* **Auditory Feedback:** Terminal events are tied to specific audio cues (e.g., successful operations, terminal open/close events)[cite: 1].
* **Drop-in Replacements:** Fallback logic that automatically aliases modern Rust-based CLI tools like `eza` (for `ls`) and `bat` (for `cat`) if they are installed on the system[cite: 1].

### 🧰 Custom Utilities (`bash/functions`)
A massive library of developer-focused bash functions built directly into the environment[cite: 1]:
* **`fzearch`:** A powerful, `fzf` and `whiptail` driven interactive file browser for previewing, opening, editing, or deleting files on the fly[cite: 1].
* **PKI Management:** Dedicated `pki-details` and `pki-analyze` functions to visually inspect certificates, map chains of trust, identify expired keys, and dynamically match CSRs to Private Keys[cite: 1].
* **Data Manipulation:** Tools to split and seamlessly combine large files (`spliter-combiner`) with built-in SHA256 integrity hashing[cite: 1].
* **Kubernetes Orchestration:** Streamlined Minikube management tools for rapidly spinning up clusters with specific resource constraints and GitOps-ready addons[cite: 1].
* **Quality of Life:** Quick-access functions for system clipboards (Wayland & X11 support)[cite: 1], QR code generation[cite: 1], and interactive `shfmt`/`ruff` code formatting[cite: 1].

## 📂 Project Structure

```text
.
├── init                  # Primary deployment and bootstrapping script (WIP)
├── bash/                 # Modularized shell configurations
│   ├── aliases           # Git, K8s, and system aliases
│   ├── completions       # Custom tab-completions (kubectl, toilet, etc.)
│   ├── functions         # Core library of TUI tools and utilities
│   └── bashrc            # Base environment variables and hooks
└── resources/            # External assets (Not strictly tracked in this view)
    ├── os/               # GNOME gsettings backups and software manifests
    ├── software/         # Configs for Kitty, Tmux, and Vim
    └── updates/          # Fonts, audio files, and profile avatars
```

## 🏗️ Getting Started (Future State)

*Note: Instructions reflect the intended workflow once the `init` script reaches maturity.*

1. Clone this repository to your target machine (Host, VM, or Cloud Instance):
```bash
   git clone <repository-url> ~/dev.environment
   cd ~/dev.environment
   ```
2. Execute the initialization script:
```bash
   ./init
   ```
3. Follow the interactive TUI prompts to selectively restore your desired components (e.g., packages, dotfiles, desktop settings).
4. Restart your terminal session to apply all variables and hooks[cite: 2].

## 📝 License
[Insert License Here]
