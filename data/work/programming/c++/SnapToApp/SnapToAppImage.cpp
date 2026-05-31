#include <array>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>

namespace fs = std::filesystem;

// Helper function to execute a shell command and capture its output
std::string exec(const char* cmd) {
  std::array<char, 128> buffer;
  std::string result;
  std::unique_ptr<FILE, decltype(&pclose)> pipe(popen(cmd, "r"), pclose);
  if (!pipe) {
    throw std::runtime_error("popen() failed!");
  }
  while (fgets(buffer.data(), buffer.size(), pipe.get()) != nullptr) {
    result += buffer.data();
  }
  // Remove trailing newline
  if (!result.empty() && result.back() == '\n') {
    result.pop_back();
  }
  return result;
}

int main(int argc, char* argv[]) {
  // 1. Configuration
  std::string app = (argc > 1) ? argv[1] : "SAMPLE";
  std::string arch = "x86_64";
  std::string build_dir = "tmp_" + app;
  std::string app_dir = app + ".AppDir";

  std::cout << "==> Packaging " << app << " into an AppImage...\n";

  // 2. Setup Working Directory
  fs::create_directories(build_dir);
  fs::current_path(build_dir);

  // 3. Download appimagetool
  if (!fs::exists("appimagetool")) {
    std::cout << "==> Downloading appimagetool...\n";
    std::string cmd =
        "wget -q --show-progress "
        "https://github.com/AppImage/appimagetool/releases/download/continuous/"
        "appimagetool-" +
        arch + ".AppImage -O appimagetool";
    std::system(cmd.c_str());
    fs::permissions(
        "appimagetool",
        fs::perms::owner_exec | fs::perms::group_exec | fs::perms::others_exec,
        fs::perm_options::add);
  }

  // 4. Download & Extract Snap Package
  bool snap_exists = false;
  for (const auto& entry : fs::directory_iterator(".")) {
    if (entry.path().extension() == ".snap") {
      snap_exists = true;
      break;
    }
  }

  if (!snap_exists) {
    std::cout << "==> Fetching Snap URL for " << app << "...\n";
    std::string cmd =
        "curl -s -H 'Snap-Device-Series: 16' "
        "\"http://api.snapcraft.io/v2/snaps/info/" +
        app +
        "\" | grep -o '\"url\": *\"[^\"]*\"' | head -n 1 | cut -d'\"' -f4";
    std::string snap_url = exec(cmd.c_str());

    if (snap_url.empty()) {
      std::cerr << "Error: Could not find Snap download URL for " << app
                << "\n";
      return 1;
    }

    std::cout << "==> Downloading Snap: " << snap_url << "\n";
    std::string wget_cmd =
        "wget -q --show-progress \"" + snap_url + "\" -O " + app + ".snap";
    std::system(wget_cmd.c_str());
  }

  if (!fs::exists("squashfs-root")) {
    std::cout << "==> Extracting Snap package...\n";
    std::system("unsquashfs -f ./*.snap > /dev/null");
  }

  // 5. Metadata Extraction (Using shell tools for regex/grep simplicity)
  std::cout << "==> Extracting metadata...\n";
  std::string snapcraft_yaml = exec("find . -name snapcraft.yaml -print -quit");
  std::string version = exec(("grep -m 1 \"^version:\" " + snapcraft_yaml +
                              " | awk '{print $2}' | tr -d '\"' | tr ' ' '-'")
                                 .c_str());

  fs::create_directories(app_dir);
  // Clear directory if it exists
  for (const auto& entry : fs::directory_iterator(app_dir)) {
    fs::remove_all(entry);
  }

  std::string desktop_file =
      exec(("find . -name \"" + app + ".desktop\" -print -quit").c_str());
  std::string appname = app;

  if (!desktop_file.empty()) {
    std::string desktop_basename = fs::path(desktop_file).filename().string();
    fs::copy(desktop_file, app_dir + "/" + desktop_basename,
             fs::copy_options::overwrite_existing);
    std::system(("sed -i \"s/^Icon=.*/Icon=" + app + "/g\" \"./" + app_dir +
                 "/" + desktop_basename + "\"")
                    .c_str());
    appname = exec(("grep -m 1 '^Name=' \"./" + app_dir + "/" +
                    desktop_basename + "\" | cut -c 6- | tr ' ' '-'")
                       .c_str());
  } else {
    std::cerr << "Error: Could not find " << app << ".desktop\n";
    return 1;
  }

  // Find and copy Icons
  std::string png_icon =
      exec(("find . -iname \"*" + app + "*.png\" -print -quit").c_str());
  std::string svg_icon =
      exec(("find . -iname \"*" + app + "*.svg\" -print -quit").c_str());

  if (!png_icon.empty())
    fs::copy(png_icon, app_dir + "/" + app + ".png",
             fs::copy_options::overwrite_existing);
  if (!svg_icon.empty())
    fs::copy(svg_icon, app_dir + "/" + app + ".svg",
             fs::copy_options::overwrite_existing);

  // 6. Build AppDir Structure
  std::cout << "==> Assembling AppDir...\n";
  std::array<std::string, 4> dirs = {"etc", "lib", "lib64", "usr"};
  for (const auto& dir : dirs) {
    if (fs::exists("squashfs-root/" + dir)) {
      fs::copy(
          "squashfs-root/" + dir, app_dir + "/" + dir,
          fs::copy_options::recursive | fs::copy_options::overwrite_existing);
    }
  }

  // 7. Generate AppRun using a C++11 Raw String Literal
  std::ofstream apprun(app_dir + "/AppRun");
  apprun << R"EOF(#!/bin/sh
HERE="$(dirname "$(readlink -f "${0}")")"
export UNION_PRELOAD=/:"${HERE}"
export LD_LIBRARY_PATH="${HERE}"/usr/lib/:"${HERE}"/usr/lib/i386-linux-gnu/:"${HERE}"/usr/lib/x86_64-linux-gnu/:"${HERE}"/lib/:"${HERE}"/lib/i386-linux-gnu/:"${HERE}"/lib/x86_64-linux-gnu/:"${LD_LIBRARY_PATH:-}"
export PATH="${HERE}"/usr/bin/:"${HERE}"/usr/sbin/:"${HERE}"/usr/games/:"${HERE}"/bin/:"${HERE}"/sbin/:"${PATH:-}"
export PYTHONPATH="${HERE}"/usr/share/pyshared/:"${HERE}"/usr/lib/python*/:"${PYTHONPATH:-}"
export PYTHONHOME="${HERE}"/usr/:"${HERE}"/usr/lib/python*/
export XDG_DATA_DIRS="${HERE}"/usr/share/:"${XDG_DATA_DIRS:-}"
export PERLLIB="${HERE}"/usr/share/perl5/:"${HERE}"/usr/lib/perl5/:"${PERLLIB:-}"
export GSETTINGS_SCHEMA_DIR="${HERE}"/usr/share/glib-2.0/schemas/:"${GSETTINGS_SCHEMA_DIR:-}"
export QT_PLUGIN_PATH="${HERE}"/usr/lib/qt4/plugins/:"${HERE}"/usr/lib/i386-linux-gnu/qt4/plugins/:"${HERE}"/usr/lib/x86_64-linux-gnu/qt4/plugins/:"${HERE}"/usr/lib32/qt4/plugins/:"${HERE}"/usr/lib64/qt4/plugins/:"${HERE}"/usr/lib/qt5/plugins/:"${HERE}"/usr/lib/i386-linux-gnu/qt5/plugins/:"${HERE}"/usr/lib/x86_64-linux-gnu/qt5/plugins/:"${HERE}"/usr/lib32/qt5/plugins/:"${HERE}"/usr/lib64/qt5/plugins/:"${QT_PLUGIN_PATH:-}"

EXEC=$(grep -m 1 -e '^Exec=.*' "${HERE}"/*.desktop | cut -d "=" -f 2- | sed -e 's|%.||g')
exec ${EXEC} "$@"
)EOF";
  apprun.close();
  fs::permissions(
      app_dir + "/AppRun",
      fs::perms::owner_exec | fs::perms::group_exec | fs::perms::others_exec,
      fs::perm_options::add);

  // 8. Build AppImage
  std::cout << "==> Compiling AppImage...\n";
  std::string output_name =
      "../" + appname + "-" + version + "-" + arch + ".AppImage";

  // Set ARCH env variable for appimagetool
  setenv("ARCH", arch.c_str(), 1);

  std::string build_cmd =
      "./appimagetool --comp zstd --mksquashfs-opt -Xcompression-level "
      "--mksquashfs-opt 20 \"./" +
      app_dir + "\" \"" + output_name + "\"";
  std::system(build_cmd.c_str());

  std::cout << "==> Success! AppImage built.\n";

  return 0;
}
