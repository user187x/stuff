import subprocess
import sys
import os
import venv

VENV_DIR = ".build_env"

def get_venv_commands():
    """Returns the correct paths for pip and pyinstaller based on the OS."""
    if os.name == 'nt':
        pip_exe = os.path.join(VENV_DIR, "Scripts", "pip")
        pyinstaller_exe = os.path.join(VENV_DIR, "Scripts", "pyinstaller")
    else:
        pip_exe = os.path.join(VENV_DIR, "bin", "pip")
        pyinstaller_exe = os.path.join(VENV_DIR, "bin", "pyinstaller")
    return pip_exe, pyinstaller_exe

def ensure_pyinstaller():
    """Checks for PyInstaller, creating a local venv if system pip is locked."""
    try:
        import PyInstaller
        print("✅ PyInstaller is available in the current environment.")
        # Return the command to run PyInstaller globally
        return [sys.executable, "-m", "PyInstaller"]
    except ImportError:
        print("⚠️ PyInstaller not found.")
        print("Your system likely prevents global pip installations (PEP 668).")
        
        # Prompt the user for confirmation
        response = input("Would you like me to automatically create a safe local virtual environment to install it? (y/n): ").strip().lower()
        
        if response not in ('y', 'yes'):
            print("❌ Setup cancelled. Please install PyInstaller manually.")
            sys.exit(1)
            
        pip_exe, pyinstaller_exe = get_venv_commands()
        
        if not os.path.exists(VENV_DIR):
            print(f"\n⚙️ Creating virtual environment in '{VENV_DIR}'...")
            # system_site_packages=True is CRITICAL so PyInstaller can still find
            # your system-level GUI bindings (like GTK4 / PyGObject).
            venv.create(VENV_DIR, with_pip=True, system_site_packages=True)
            
            print("📦 Installing PyInstaller into the virtual environment (this may take a moment)...")
            try:
                subprocess.check_call([pip_exe, "install", "pyinstaller"])
                print("✅ PyInstaller installed successfully.")
            except subprocess.CalledProcessError as e:
                print(f"❌ Failed to install PyInstaller inside the virtual environment: {e}")
                sys.exit(1)
        else:
            print(f"✅ Virtual environment '{VENV_DIR}' already exists.")
            
        # Return the command to run the localized PyInstaller
        return [pyinstaller_exe]

def build_executable(script_path):
    """Compiles the given Python script into a windowed executable."""
    if not os.path.exists(script_path):
        print(f"❌ Error: The file '{script_path}' does not exist.")
        sys.exit(1)

    pyinstaller_cmd = ensure_pyinstaller()
    
    print(f"\n🚀 Building executable for: {script_path}")
    
    # PyInstaller arguments:
    # --noconsole : Prevents the command prompt from appearing
    # --onefile   : Bundles everything into a single executable file
    command = pyinstaller_cmd + [
        "--noconsole",
        "--onefile",
        script_path
    ]
    
    try:
        subprocess.check_call(command)
        print("\n🎉 Success! Your executable is located in the 'dist' folder.")
    except subprocess.CalledProcessError as e:
        print(f"\n❌ An error occurred during the build process: {e}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python build_gui.py <path_to_your_gui_script.py>")
        sys.exit(1)
    
    target_script = sys.argv[1]
    build_executable(target_script)
