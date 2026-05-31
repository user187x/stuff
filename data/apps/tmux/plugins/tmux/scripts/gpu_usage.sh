#!/usr/bin/env bash

export LC_ALL=en_US.UTF-8

current_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source $current_dir/utils.sh

function get_platform {
 local operating_system_platform=$(uname -s)
 local operating_system_name=""

 if [[ "$operating_system_platform" == "Darwin" ]]; then
  operating_system_name="Apple"
 elif [[ "$operating_system_platform" == "Linux" ]]; then
  operating_system_name="Linux"
 elif [[ "$operating_system_platform" =~ "CYGWIN" || "$operating_system_platform" =~ "MINGW" || "$operating_system_platform" =~ "MSYS" ]]; then
  operating_system_name="Windows"
 else
  operating_system_name="Unknown"
  return 1
 fi
 echo "$operating_system_name"
}

function get_gpu_name {
 operating_system=$(get_platform)
 gpu="UNKNOWN"

 # User defined GPU label
 gpu_label=$(get_tmux_option "@dracula-force-gpu" false)
 if [[ "$gpu_label" != "false" ]]; then
  echo "$gpu_label"
  return 0
 fi

 if [[ "$operating_system" == "Apple" ]]; then
  gpu=$(system_profiler SPDisplaysDataType | grep "Chipset Model" | head -1 | awk -F': ' '{print $2}')
 elif [[ "$operating_system" == "Linux" ]]; then
  # Redirect stderr to null to avoid lspci permission errors
  gpu_info=$(lspci -v 2>/dev/null | grep -E -i "vga|3d|display")

  if [[ "$gpu_info" =~ "AMD" ]] || [[ "$gpu_info" =~ "Radeon" ]] || [[ "$gpu_info" =~ "ATI" ]]; then
   gpu="AMD"
  elif [[ "$gpu_info" =~ "NVIDIA" ]]; then
   gpu="NVIDIA"
  elif [[ "$gpu_info" =~ "Intel" ]]; then
   gpu="INTEL"
  fi
 fi

 # Fallback check for Nvidia
 if [[ "$gpu" == "UNKNOWN" ]] && type -a nvidia-smi >/dev/null 2>&1; then
  gpu="NVIDIA"
 fi

 echo "$gpu"
}

function get_gpu_usage {
 local operating_system=$(get_platform)
 local gpu=$(get_gpu_name)
 local usage=""

 if [[ "$operating_system" == "Apple" ]]; then
  usage=$(sudo powermetrics --samplers gpu_power -n 1 -i 100 2>/dev/null | grep -i "GPU 0 GPU Busy" | awk '{print $6}')
 elif [[ "$operating_system" == "Linux" ]]; then

  # Fix: Match "NVIDIA" (UPPERCASE) from get_gpu_name
  if [[ "$gpu" == "NVIDIA" ]]; then
   usage=$(nvidia-smi --query-gpu=utilization.gpu --format=csv,noheader,nounits | awk '{ printf("%d%%", $1) }')

  elif [[ "$gpu" == "AMD" ]]; then
   # FIX: Try sysfs (amdgpu driver) first.
   # This is lighter, faster, and doesn't need sudo.
   for path in /sys/class/drm/card*/device/gpu_busy_percent; do
    if [[ -r "$path" ]]; then
     usage=$(cat "$path")
     usage="${usage}%"
     break
    fi
   done

   # Fallback to radeontop if sysfs failed
   if [[ -z "$usage" ]] && command -v radeontop >/dev/null 2>&1; then
    usage=$(radeontop -d - -l 1 | sed -n 's/.*gpu \([0-9]*\)\..*/\1%/p')
   fi

  else
   # Generic fallback for Intel/Others
   usage="$(cat /sys/class/drm/card?/device/gpu_busy_percent 2>/dev/null | head -1 | sed 's/$/%/')"
  fi
 fi

 # Default to 0% if empty to prevent math errors later
 if [[ -z "$usage" ]]; then
  usage="0%"
 fi

 echo "$usage"
}

main() {
 local refresh_rate=$(get_tmux_option "@dracula-refresh-rate" 1)
 sleep "$refresh_rate" # Fixed: Added quotes and $

 local gpu_label=$(get_tmux_option "@dracula-gpu-usage-label" "GPU")
 local percentage=$(get_gpu_usage)

 # Fixed: Added $ to variable name. Was echoing literal word "percentage"
 local number=$(echo "$percentage" | tr -d '%')

 # Ensure number is valid for bc
 if [[ -z "$number" ]]; then number="0"; fi

 local normalized=$(echo "$number" | bc 2>/dev/null | cut -d. -f1)
 if [[ -z "$normalized" ]]; then normalized="0"; fi

 echo "$gpu_label $normalized %"
}

main
