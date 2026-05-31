#include <stdio.h>

// This program uses preprocessor directives to compile different code
// depending on the operating system. This allows it to gather system
// specifications on Windows, macOS, and Linux.

// --- PLATFORM-SPECIFIC INCLUDES ---
// By placing the includes at the top level, we avoid compiler errors.
// The preprocessor will only include the headers for the target OS.

#if defined(_WIN32) || defined(_WIN64)
    #include <windows.h>
    #include <lmcons.h> // For UNLEN constant
    #include <initguid.h> // MUST be included before dxgi.h for IID_... definitions
    #include <dxgi.h>   // For GPU information
    #pragma comment(lib, "dxgi.lib") // Link against the DXGI library
#elif defined(__APPLE__) || defined(__MACH__)
    #include <sys/types.h>
    #include <sys/sysctl.h>
    #include <unistd.h>      // for gethostname
    #include <sys/utsname.h> // for uname
    #include <sys/mount.h>   // For getfsstat
#elif defined(__linux__)
    #include <sys/utsname.h>
    #include <unistd.h>      // for sysconf, gethostname
    #include <string.h>      // for strncmp, strchr
    #include <ctype.h>       // for isspace
    #include <stdlib.h>      // for atoll
    #include <sys/statvfs.h> // For filesystem stats
#endif

// Define a function that will contain the platform-specific code.
void printSystemSpecs();

int main() {
    // Call the function to print the specs.
    printSystemSpecs();
    return 0;
}

// Helper function to format bytes into a more readable format (GB)
double bytesToGB(long long bytes) {
    return (double)bytes / (1024 * 1024 * 1024);
}


void printSystemSpecs() {
    printf("--- System Specifications ---\n");

// --- WINDOWS IMPLEMENTATION ---
#if defined(_WIN32) || defined(_WIN64)
    // --- Basic Info ---
    char computerName[MAX_COMPUTERNAME_LENGTH + 1];
    DWORD computerNameSize = sizeof(computerName);
    GetComputerNameA(computerName, &computerNameSize);
    printf("Operating System: Windows\n");
    printf("Computer Name:    %s\n", computerName);

    // --- CPU Info ---
    HKEY hKey;
    char cpuBrand[256] = "N/A";
    DWORD bufferSize = sizeof(cpuBrand);
    if (RegOpenKeyExA(HKEY_LOCAL_MACHINE, "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", 0, KEY_READ, &hKey) == ERROR_SUCCESS) {
        if(RegQueryValueExA(hKey, "ProcessorNameString", NULL, NULL, (LPBYTE)cpuBrand, &bufferSize) == ERROR_SUCCESS){
            printf("Processor:        %s\n", cpuBrand);
        }
        RegCloseKey(hKey);
    }

    SYSTEM_INFO sysInfo;
    GetSystemInfo(&sysInfo);
    printf("Cores:            %u\n", sysInfo.dwNumberOfProcessors);


    // --- RAM Info ---
    MEMORYSTATUSEX memInfo;
    memInfo.dwLength = sizeof(memInfo);
    GlobalMemoryStatusEx(&memInfo);
    printf("RAM:              %.2f GB\n", bytesToGB(memInfo.ullTotalPhys));


    // --- GPU Info ---
    printf("\n--- GPUs ---\n");
    IDXGIFactory* pFactory;
    if (SUCCEEDED(CreateDXGIFactory(&IID_IDXGIFactory, (void**)&pFactory))) {
        IDXGIAdapter* pAdapter;

        for (UINT i = 0; pFactory->lpVtbl->EnumAdapters(pFactory, i, &pAdapter) != DXGI_ERROR_NOT_FOUND; ++i) {
            DXGI_ADAPTER_DESC desc;
            if (SUCCEEDED(pAdapter->lpVtbl->GetDesc(pAdapter, &desc))) {
                // The GPU name is a wide char string, needs conversion for printf
                char gpuName[128];
                wcstombs(gpuName, desc.Description, sizeof(gpuName));
                printf("GPU %u Name:       %s\n", i, gpuName);
                printf("GPU %u VRAM:       %.2f GB\n", i, bytesToGB(desc.DedicatedVideoMemory));
            }
            pAdapter->lpVtbl->Release(pAdapter);
        }
        pFactory->lpVtbl->Release(pFactory);
    } else {
        printf("GPU Info:         Could not initialize DXGI.\n");
    }

    // --- Storage Info ---
    printf("\n--- Storage ---\n");
    char driveStrings[256];
    if (GetLogicalDriveStringsA(sizeof(driveStrings) - 1, driveStrings)) {
        char *drive = driveStrings;
        while (*drive) {
            ULARGE_INTEGER freeBytes, totalBytes, totalFreeBytes;
            char fileSystemName[MAX_PATH + 1] = {0};

            if (GetDiskFreeSpaceExA(drive, &freeBytes, &totalBytes, &totalFreeBytes) &&
                GetVolumeInformationA(drive, NULL, 0, NULL, NULL, NULL, fileSystemName, MAX_PATH + 1))
            {
                printf("Drive:            %s\n", drive);
                printf("  File System:    %s\n", fileSystemName);
                printf("  Storage:        %.2f GB Free of %.2f GB\n", bytesToGB(totalFreeBytes.QuadPart), bytesToGB(totalBytes.QuadPart));
            }
            drive += strlen(drive) + 1; // Move to the next drive string
        }
    }


// --- MACOS IMPLEMENTATION ---
#elif defined(__APPLE__) || defined(__MACH__)
    struct utsname osInfo;
    uname(&osInfo);
    char computerName[256];
    gethostname(computerName, sizeof(computerName));
    printf("Operating System: %s %s\n", osInfo.sysname, osInfo.release);
    printf("Computer Name:    %s\n", computerName);

    // --- CPU Info ---
    char cpuBrand[256];
    size_t cpuBrandSize = sizeof(cpuBrand);
    sysctlbyname("machdep.cpu.brand_string", &cpuBrand, &cpuBrandSize, NULL, 0);
    printf("Processor:        %s\n", cpuBrand);
    int coreCount;
    size_t coreCountSize = sizeof(coreCount);
    sysctlbyname("hw.ncpu", &coreCount, &coreCountSize, NULL, 0);
    printf("Cores:            %d\n", coreCount);

    // --- RAM Info ---
    long long memSize;
    size_t memSizeSize = sizeof(memSize);
    sysctlbyname("hw.memsize", &memSize, &memSizeSize, NULL, 0);
    printf("RAM:              %.2f GB\n", bytesToGB(memSize));

    // --- GPU Info ---
    printf("\n--- GPUs ---\n");
    FILE* gpuPipe = popen("system_profiler SPDisplaysDataType | grep -E 'Chipset Model|VRAM'", "r");
    if (gpuPipe) {
        char line[256];
        while (fgets(line, sizeof(line), gpuPipe)) {
            printf("%s", line); // Directly print the parsed lines
        }
        pclose(gpuPipe);
    }

    // --- Storage Info ---
    printf("\n--- Storage ---\n");
    struct statfs* mounts;
    int num_mounts = getfsstat(NULL, 0, MNT_NOWAIT);
    if (num_mounts > 0) {
        mounts = malloc(sizeof(struct statfs) * num_mounts);
        getfsstat(mounts, sizeof(struct statfs) * num_mounts, MNT_NOWAIT);
        for (int i = 0; i < num_mounts; i++) {
             // Only show user-relevant filesystems
            if (strncmp(mounts[i].f_mntfromname, "/dev/disk", 9) == 0) {
                long long totalSize = (long long)mounts[i].f_blocks * mounts[i].f_bsize;
                long long freeSize = (long long)mounts[i].f_bfree * mounts[i].f_bsize;
                printf("Drive:            %s mounted on %s\n", mounts[i].f_mntfromname, mounts[i].f_mntonname);
                printf("  File System:    %s\n", mounts[i].f_fstypename);
                printf("  Storage:        %.2f GB Free of %.2f GB\n", bytesToGB(freeSize), bytesToGB(totalSize));
            }
        }
        free(mounts);
    }


// --- LINUX IMPLEMENTATION ---
#elif defined(__linux__)
    struct utsname osInfo;
    uname(&osInfo);
    char computerName[256];
    gethostname(computerName, sizeof(computerName));
    printf("Operating System: %s %s\n", osInfo.sysname, osInfo.release);
    printf("Computer Name:    %s\n", computerName);

    // --- CPU Info ---
    char cpuBrand[256] = "N/A";
    FILE* cpuFile = fopen("/proc/cpuinfo", "r");
    if (cpuFile) {
        char line[256];
        while (fgets(line, sizeof(line), cpuFile)) {
            if (strncmp(line, "model name", 10) == 0) {
                char* colon = strchr(line, ':');
                if (colon) {
                    char* model = colon + 2; // Skip ':' and space
                    char* newline = strchr(model, '\n');
                    if (newline) *newline = '\0';
                    strncpy(cpuBrand, model, sizeof(cpuBrand) - 1);
                    cpuBrand[sizeof(cpuBrand) - 1] = '\0';
                    break;
                }
            }
        }
        fclose(cpuFile);
    }
    printf("Processor:        %s\n", cpuBrand);
    printf("Cores:            %ld\n", sysconf(_SC_NPROCESSORS_ONLN));

    // --- RAM Info ---
    long long totalMem = 0;
    FILE* memFile = fopen("/proc/meminfo", "r");
    if (memFile) {
        char line[256];
        if(fgets(line, sizeof(line), memFile)) { // First line is MemTotal
             sscanf(line, "MemTotal: %lld kB", &totalMem);
             totalMem *= 1024; // Convert to bytes
        }
        fclose(memFile);
    }
    printf("RAM:              %.2f GB\n", bytesToGB(totalMem));

    // --- GPU Info ---
    printf("\n--- GPUs ---\n");
    // Use lspci to find VGA compatible controllers
    FILE* pciPipe = popen("lspci | grep 'VGA\\|3D'", "r");
    if (pciPipe) {
        char line[256];
        while (fgets(line, sizeof(line), pciPipe)) {
            char* deviceName = strchr(line, ':');
            if (deviceName) {
                printf("GPU:              %s", deviceName + 2); // Skip ':' and space
            }
        }
        pclose(pciPipe);
    }

    // --- Storage Info ---
    printf("\n--- Storage ---\n");
    FILE* mountsFile = fopen("/proc/mounts", "r");
    if (mountsFile) {
        char line[256];
        char device[256], mountPoint[256], fsType[256];
        while(fgets(line, sizeof(line), mountsFile)) {
            sscanf(line, "%s %s %s", device, mountPoint, fsType);
            // Only show common physical device filesystems
            if (strncmp(device, "/dev/", 5) == 0 && (
                strcmp(fsType, "ext4") == 0 || strcmp(fsType, "ext3") == 0 ||
                strcmp(fsType, "vfat") == 0 || strcmp(fsType, "ntfs") == 0 ||
                strcmp(fsType, "btrfs") == 0 || strcmp(fsType, "xfs") == 0
            )) {
                struct statvfs stats;
                if (statvfs(mountPoint, &stats) == 0) {
                    long long totalSize = (long long)stats.f_blocks * stats.f_frsize;
                    long long freeSize = (long long)stats.f_bfree * stats.f_frsize;
                    printf("Drive:            %s mounted on %s\n", device, mountPoint);
                    printf("  File System:    %s\n", fsType);
                    printf("  Storage:        %.2f GB Free of %.2f GB\n", bytesToGB(freeSize), bytesToGB(totalSize));
                }
            }
        }
        fclose(mountsFile);
    }

#else
    printf("Unsupported Operating System.\n");
#endif

    printf("---------------------------\n");
}

