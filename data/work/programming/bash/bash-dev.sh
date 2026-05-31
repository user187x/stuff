#!/usr/bin/env bash

# Use portable shebang and determine script location
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
SCRIPT_NAME=$(basename "${BASH_SOURCE[0]}")
SCRIPT_PATH="${SCRIPT_DIR}/${SCRIPT_NAME}"
export DEV_SCRIPT="$SCRIPT_PATH"

# Use ANSI codes for consistent coloring
echo -e "\033[1;37mSourced:\033[0m \033[1;32m$DEV_SCRIPT\033[0m"

# Initializes the instances to setup and install required packages
function initDevEnv {

    echo "Updating development instance..."

    # Detect if running as root to avoid unnecessary sudo
    local sudo_prefix=""
    if [[ $EUID -ne 0 ]]; then
        sudo_prefix="sudo"
    fi

    # Detect package manager
    local pkg_manager=""
    local pkg_check=""
    local pkg_install=""
    local pkg_update=""
    if command -v apt >/dev/null 2>&1; then
        pkg_manager="apt"
        pkg_check="dpkg -l"
        pkg_install="apt install -y --no-install-recommends"
        pkg_update="apt update -y && apt upgrade -y"
        # Ensure non-interactive for apt
        export DEBIAN_FRONTEND=noninteractive
    elif command -v dnf >/dev/null 2>&1; then
        pkg_manager="dnf"
        pkg_check="rpm -q"
        pkg_install="dnf install -y"
        pkg_update="dnf upgrade -y"
    elif command -v yum >/dev/null 2>&1; then
        pkg_manager="yum"
        pkg_check="rpm -q"
        pkg_install="yum install -y"
        pkg_update="yum update -y"
    elif command -v pacman >/dev/null 2>&1; then
        pkg_manager="pacman"
        pkg_check="pacman -Qs"
        pkg_install="pacman -S --noconfirm"
        pkg_update="pacman -Syu --noconfirm"
    else
        echo "Error: No supported package manager found (apt, dnf, yum, or pacman)" >&2
        return 1
    fi

    echo "Detected package manager: $pkg_manager"

    # Update package lists and upgrade installed packages
    if ! ${sudo_prefix} ${pkg_update} >>/var/log/dev-install.log 2>&1; then
        echo "Error: Failed to update/upgrade packages. See /var/log/dev-install.log" >&2
        return 1
    fi

    # List of packages to install (with distro-specific names)
    local packages=(
        default-jdk      # apt: default-jdk; dnf/yum: java-17-openjdk-devel; pacman: jdk-openjdk
        curl             # Same across distros
        nmap             # Same across distros
        btop             # Same across distros
        code             # Requires repo setup for apt/dnf/yum; pacman: visual-studio-code-bin
        python3          # Same across distros
        vim              # Same across distros
        podman           # Same across distros
        terminator       # Same across distros
        newt             # apt: newt; dnf/yum: newt-devel; pacman: newt
        tmux             # Same across distros
    )

    # Adjust package names for non-apt managers
    if [[ $pkg_manager != "apt" ]]; then
        local new_packages=()
        for pkg in "${packages[@]}"; do
            case $pkg in
                default-jdk)
                    [[ $pkg_manager == "pacman" ]] && new_packages+=("jdk-openjdk") || new_packages+=("java-17-openjdk-devel")
                    ;;
                newt)
                    [[ $pkg_manager == "pacman" ]] && new_packages+=("newt") || new_packages+=("newt-devel")
                    ;;
                code)
                    [[ $pkg_manager == "pacman" ]] && new_packages+=("visual-studio-code-bin") || new_packages+=("code")
                    ;;
                *) new_packages+=("$pkg") ;;
            esac
        done
        packages=("${new_packages[@]}")
    fi

    # Special handling for VS Code repository setup (if needed)
    if [[ " ${packages[*]} " =~ " code " || " ${packages[*]} " =~ " visual-studio-code-bin " ]]; then
        if [[ $pkg_manager == "apt" && ! -f /etc/apt/sources.list.d/vscode.list ]]; then
            echo "Setting up VS Code repository for apt..."
            ${sudo_prefix} apt install -y curl gpg >>/var/log/dev-install.log 2>&1
            curl -sSL https://packages.microsoft.com/keys/microsoft.asc | ${sudo_prefix} gpg --dearmor -o /usr/share/keyrings/microsoft-archive-keyring.gpg
            echo "deb [arch=amd64 signed-by=/usr/share/keyrings/microsoft-archive-keyring.gpg] https://packages.microsoft.com/repos/code stable main" | \
                ${sudo_prefix} tee /etc/apt/sources.list.d/vscode.list >/dev/null
            ${sudo_prefix} apt update -y >>/var/log/dev-install.log 2>&1 || {
                echo "Error: Failed to set up VS Code repository" >&2
                return 1
            }
        elif [[ $pkg_manager == "dnf" && ! -f /etc/yum.repos.d/vscode.repo ]]; then
            echo "Setting up VS Code repository for dnf..."
            ${sudo_prefix} rpm --import https://packages.microsoft.com/keys/microsoft.asc
            echo -e "[code]\nname=Visual Studio Code\nbaseurl=https://packages.microsoft.com/yumrepos/vscode\nenabled=1\ngpgcheck=1\ngpgkey=https://packages.microsoft.com/keys/microsoft.asc" | \
                ${sudo_prefix} tee /etc/yum.repos.d/vscode.repo >/dev/null
            ${sudo_prefix} dnf check-update -y >>/var/log/dev-install.log 2>&1
        elif [[ $pkg_manager == "yum" && ! -f /etc/yum.repos.d/vscode.repo ]]; then
            echo "Setting up VS Code repository for yum..."
            ${sudo_prefix} rpm --import https://packages.microsoft.com/keys/microsoft.asc
            echo -e "[code]\nname=Visual Studio Code\nbaseurl=https://packages.microsoft.com/yumrepos/vscode\nenabled=1\ngpgcheck=1\ngpgkey=https://packages.microsoft.com/keys/microsoft.asc" | \
                ${sudo_prefix} tee /etc/yum.repos.d/vscode.repo >/dev/null
            ${sudo_prefix} yum check-update -y >>/var/log/dev-install.log 2>&1
        fi
    fi

    # Check for missing packages and install only those
    local to_install=()
    for pkg in "${packages[@]}"; do
        if ! ${pkg_check} "$pkg" >/dev/null 2>&1; then
            to_install+=("$pkg")
        fi
    done

    if [[ ${#to_install[@]} -gt 0 ]]; then
        echo "Installing packages: ${to_install[*]}"
        if ! ${sudo_prefix} ${pkg_install} "${to_install[@]}" >>/var/log/dev-install.log 2>&1; then
            echo "Error: Failed to install packages. See /var/log/dev-install.log" >&2
            return 1
        fi
    else
        echo "All packages are already installed"
    fi

    echo "Update and installation complete!"
}