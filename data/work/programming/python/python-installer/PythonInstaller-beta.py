import subprocess
import sys
import os
import venv
import shutil
import urllib.request
import stat

VENV_DIR = ".build_env"

# UPDATED: The new, actively maintained continuous release URL for appimagetool
APPIMAGETOOL_URL = "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"

# A 1x1 transparent PNG to act as a fallback icon if the user skips providing one,
# as appimagetool will fail if an AppDir lacks an icon.
FALLBACK_ICON = b'\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\nIDATx\x9cc\x00\x01\x00\x00\x05\x00\x01\r\n-\xb4\x00\x00\x00\x00IEND\xaeB`\x82'

def get_venv_commands():
    if os.name == 'nt':
        return os.path.join(VENV_DIR, "Scripts", "pip"), os.path.join(VENV_DIR, "Scripts", "pyinstaller")
    return os.path.join(VENV_DIR, "bin", "pip"), os.path.join(VENV_DIR, "bin", "pyinstaller")

def ensure_pyinstaller():
    try:
        import PyInstaller
        return [sys.executable, "-m", "PyInstaller"]
    except ImportError:
        print("⚠️ PyInstaller not found. Prompting to create virtual environment due to PEP 668...")
        response = input("Would you like me to automatically create a safe local virtual environment to install it? (y/n): ").strip().lower()
        if response not in ('y', 'yes'):
            print("❌ Setup cancelled.")
            sys.exit(1)
            
        pip_exe, pyinstaller_exe = get_venv_commands()
        if not os.path.exists(VENV_DIR):
            print(f"\n⚙️ Creating virtual environment in '{VENV_DIR}'...")
            venv.create(VENV_DIR, with_pip=True, system_site_packages=True)
            print("📦 Installing PyInstaller...")
            subprocess.check_call([pip_exe, "install", "pyinstaller"])
        
        return [pyinstaller_exe]

def ensure_appimagetool():
    tool_path = os.path.abspath("appimagetool.AppImage")
    if not os.path.exists(tool_path):
        print(f"\n⚙️ Downloading appimagetool to package the AppImage...")
        try:
            # Using custom headers to prevent GitHub from blocking the automated request
            req = urllib.request.Request(APPIMAGETOOL_URL, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req) as response, open(tool_path, 'wb') as out_file:
                shutil.copyfileobj(response, out_file)
            
            # Make it executable
            os.chmod(tool_path, os.stat(tool_path).st_mode | stat.S_IEXEC)
        except Exception as e:
            print(f"❌ Failed to download appimagetool: {e}")
            sys.exit(1)
    return tool_path

def prompt_app_info(script_path):
    print("\n--- 📝 Application Metadata (Press Enter to skip/use defaults) ---")
    default_name = os.path.splitext(os.path.basename(script_path))[0]
    
    app_name = input(f"App Name [{default_name}]: ").strip() or default_name
    version = input("Version [1.0]: ").strip() or "1.0"
    comment = input("Comment / Description []: ").strip() or ""
    categories = input("Categories (e.g., Utility;Development;) [Utility;]: ").strip() or "Utility;"
    icon_path = input("Path to icon image (.png, .svg) [Skip]: ").strip()
    
    if icon_path and not os.path.exists(icon_path):
        print(f"⚠️ Icon '{icon_path}' not found. Falling back to default blank icon.")
        icon_path = ""
        
    return app_name, version, comment, categories, icon_path

def build_appimage(pyinstaller_cmd, script_path, app_info):
    app_name, version, comment, categories, icon_path = app_info
    
    # 1. Run PyInstaller
    print(f"\n🚀 Building base executable for: {script_path}")
    build_cmd = pyinstaller_cmd + ["--noconsole", "--onefile", script_path]
    if icon_path:
        build_cmd.append(f"--icon={icon_path}")
        
    subprocess.check_call(build_cmd)
    
    # 2. Setup AppDir Structure
    print("\n📦 Structuring AppDir for AppImage creation...")
    dist_dir = os.path.abspath("dist")
    exe_name = os.path.splitext(os.path.basename(script_path))[0]
    appdir = os.path.join(dist_dir, f"{app_name}.AppDir")
    usr_bin = os.path.join(appdir, "usr", "bin")
    
    if os.path.exists(appdir):
        shutil.rmtree(appdir)
    os.makedirs(usr_bin)
    
    # Move the compiled executable into the AppDir
    compiled_exe = os.path.join(dist_dir, exe_name)
    target_exe = os.path.join(usr_bin, app_name)
    shutil.move(compiled_exe, target_exe)
    
    # 3. Handle Icon (AppImages strictly require an icon in the root of the AppDir)
    icon_ext = ".png"
    if icon_path:
        icon_ext = os.path.splitext(icon_path)[1].lower()
        shutil.copy(icon_path, os.path.join(appdir, f"{app_name}{icon_ext}"))
    else:
        # Create fallback transparent icon
        with open(os.path.join(appdir, f"{app_name}.png"), "wb") as f:
            f.write(FALLBACK_ICON)
            
    # 4. Create .desktop file
    desktop_content = f"""[Desktop Entry]
Version={version}
Name={app_name}
Comment={comment}
Exec={app_name}
Icon={app_name}
Type=Application
Terminal=false
Categories={categories}
"""
    desktop_path = os.path.join(appdir, f"{app_name}.desktop")
    with open(desktop_path, "w") as f:
        f.write(desktop_content)
        
    # Also save a copy of the .desktop file in the main dist folder for the user
    with open(os.path.join(dist_dir, f"{app_name}.desktop"), "w") as f:
        # For the standalone desktop file, we need an absolute path to the AppImage
        standalone_exec = os.path.join(dist_dir, f"{app_name}-x86_64.AppImage")
        f.write(desktop_content.replace(f"Exec={app_name}", f"Exec={standalone_exec}"))
        
    # 5. Create AppRun script
    apprun_path = os.path.join(appdir, "AppRun")
    with open(apprun_path, "w") as f:
        f.write(f'#!/bin/sh\nHERE="$(dirname "$(readlink -f "${{0}}")")"\nexec "${{HERE}}/usr/bin/{app_name}" "$@"')
    os.chmod(apprun_path, os.stat(apprun_path).st_mode | stat.S_IEXEC)
    
    # 6. Package with appimagetool
    appimagetool = ensure_appimagetool()
    print(f"\n⚙️ Compiling AppImage using AppImageTool...")
    
    env = os.environ.copy()
    env["ARCH"] = "x86_64" # Ensure architecture is set
    
    subprocess.check_call([appimagetool, appdir], cwd=dist_dir, env=env)
    
    print(f"\n🎉 Success! Output generated in the 'dist' folder:")
    print(f"   - AppImage: dist/{app_name}-x86_64.AppImage")
    print(f"   - Shortcut: dist/{app_name}.desktop")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python build_gui.py <path_to_your_gui_script.py>")
        sys.exit(1)
    
    target_script = sys.argv[1]
    if not os.path.exists(target_script):
        print(f"❌ Error: The file '{target_script}' does not exist.")
        sys.exit(1)
        
    pyinst_cmd = ensure_pyinstaller()
    app_info = prompt_app_info(target_script)
    build_appimage(pyinst_cmd, target_script, app_info)
