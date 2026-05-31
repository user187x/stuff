#!/usr/bin/env bash

# Use portable shebang and determine script location
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
SCRIPT_NAME=$(basename "${BASH_SOURCE[0]}")
SCRIPT_PATH="${SCRIPT_DIR}/${SCRIPT_NAME}"

export UTIL_SCRIPT="$SCRIPT_PATH"

# Use ANSI codes for consistent coloring
echo -e "\033[1;37mSourced:\033[0m \033[1;32m$SCRIPT_PATH\033[0m"

###############################################
# ALIASES
###############################################

# File and directory listing
alias ll='ls -alF --color=auto'
alias lt='ls -lh --color=auto --sort=size'
alias la='ls -A --color=auto'
alias count='find . -type f -maxdepth 1 | wc -l'

# System information
alias mnt='mount | grep ^/dev/ | awk "{printf \"%-20s %s\n\", \$1, \$3}" | sort'
alias mount='mount | column -t'
alias df='df -h'
alias du='du -h'
alias meminfo='free -m -t'
alias cpuinfo='lscpu'
alias gpumeminfo='grep -i --color memory /var/log/Xorg.0.log 2>/dev/null || echo "No GPU info found"'

# Network and security
alias myip='curl -s ifconfig.me || echo "Network unavailable"'
alias ports='ss -tuln 2>/dev/null || netstat -tulanp 2>/dev/null'
alias iptlist='sudo iptables -L -n -v --line-numbers'
alias iptlistin='sudo iptables -L INPUT -n -v --line-numbers'
alias iptlistout='sudo iptables -L OUTPUT -n -v --line-numbers'
alias iptlistfw='sudo iptables -L FORWARD -n -v --line-numbers'
alias firewall='iptlist'

# Process monitoring
alias psmem='ps -eo pid,ppid,cmd,%mem --sort=-%mem | head'
alias pscpu='ps -eo pid,ppid,cmd,%cpu --sort=-%cpu | head'

# File operations
alias rm='rm -i'
alias cp='cp -iv'
alias mv='mv -iv'
alias cpv='rsync -ah --progress'

# Development tools
alias grep='grep --color=auto'
alias diff='diff --color=auto 2>/dev/null || colordiff'
alias vi='vim'
alias svi='sudo vim'
alias edit='vim'
alias gl='git log --oneline --decorate --all --graph'
alias gr='cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)"'
alias k='kubectl'
alias ve='python3 -m venv ./venv'
alias va='source ./venv/bin/activate 2>/dev/null || echo "No venv found"'

# System management
alias reload='source ~/.bashrc'
alias update='sudo yum update -y && sudo yum upgrade -y 2>/dev/null || sudo apt update && sudo apt upgrade -y'
alias root='sudo -i'
alias reboot='sudo systemctl reboot 2>/dev/null || sudo reboot'
alias poweroff='sudo systemctl poweroff 2>/dev/null || sudo poweroff'
alias shutdown='sudo systemctl poweroff 2>/dev/null || sudo shutdown'

# Time and path
alias now='date +%T'
alias nowdate='date +%d-%m-%Y'
alias path='echo -e "${PATH//:/\\n}"'

# Network monitoring (check for availability)
alias dnstop='command -v dnstop >/dev/null && dnstop -l 5 eth0 || echo "dnstop not installed"'
alias vnstat='command -v vnstat >/dev/null && vnstat -i eth0 || echo "vnstat not installed"'
alias iftop='command -v iftop >/dev/null && iftop -i eth0 || echo "iftop not installed"'
alias tcpdump='command -v tcpdump >/dev/null && tcpdump -i eth0 || echo "tcpdump not installed"'
alias ethtool='command -v ethtool >/dev/null && ethtool eth0 || echo "ethtool not installed"'

# Replace top with htop if available
alias top='command -v htop >/dev/null && htop || top'

###############################################
# FUNCTIONS
###############################################

# Function to convert epoch & date
function getDate {
 if [[ $# -eq 0 ]]; then
  date +"%Y-%m-%d %H:%M:%S"
 else
  date -d @"$1" +"%Y-%m-%d %H:%M:%S" 2>/dev/null || date -r "$1" +"%Y-%m-%d %H:%M:%S" 2>/dev/null || echo "Invalid timestamp"
 fi
}

export -f getDate

# Returns the time from a specified point in the past
function ago {
    # Default values
    local days=0 hours=0 minutes=0

    # Validate and assign arguments
    [[ -n "$1" && "$1" =~ ^[0-9]+$ ]] && days="$1"
    [[ -n "$2" && "$2" =~ ^[0-9]+$ ]] && hours="$2"
    [[ -n "$3" && "$3" =~ ^[0-9]+$ ]] && minutes="$3"

    # Check if any valid time was specified
    if [[ $days -eq 0 && $hours -eq 0 && $minutes -eq 0 ]]; then
        echo "Error: At least one non-zero value for days, hours, or minutes is required" >&2
        echo "Usage: ago <days> [hours] [minutes]" >&2
        return 1
    fi

    # Calculate total seconds to subtract
    local total_seconds=$(( (days * 86400) + (hours * 3600) + (minutes * 60) ))
    
    # Try GNU date first (Linux)
    if date --version >/dev/null 2>&1; then
        date -d "@$(($(date +%s) - total_seconds))" +"%Y-%m-%d %H:%M:%S" 2>/dev/null
    # Fallback for BSD/macOS
    elif command -v date >/dev/null 2>&1; then
        date -r "$(($(date +%s) - total_seconds))" +"%Y-%m-%d %H:%M:%S" 2>/dev/null
    else
        echo "Error: date command not found" >&2
        return 1
    fi
}

export -f ago

# Returns the timestamp from a specified time in the future (days, hours, minutes from now)
function fromNow {
    # Default values
    local days=0 hours=0 minutes=0

    # Validate and assign arguments
    [[ -n "$1" && "$1" =~ ^[0-9]+$ ]] && days="$1"
    [[ -n "$2" && "$2" =~ ^[0-9]+$ ]] && hours="$2"
    [[ -n "$3" && "$3" =~ ^[0-9]+$ ]] && minutes="$3"

    # Check if any valid time was specified
    if [[ $days -eq 0 && $hours -eq 0 && $minutes -eq 0 ]]; then
        echo "Error: At least one non-zero value for days, hours, or minutes is required" >&2
        echo "Usage: fromNow <days> [hours] [minutes]" >&2
        return 1
    fi

    # Calculate total seconds to add
    local total_seconds=$(( (days * 86400) + (hours * 3600) + (minutes * 60) ))
    
    # Try GNU date first (Linux)
    if date --version >/dev/null 2>&1; then
        date -d "@$(($(date +%s) + total_seconds))" +"%Y-%m-%d %H:%M:%S" 2>/dev/null
    # Fallback for BSD/macOS
    elif command -v date >/dev/null 2>&1; then
        date -r "$(($(date +%s) + total_seconds))" +"%Y-%m-%d %H:%M:%S" 2>/dev/null
    else
        echo "Error: date command not found" >&2
        return 1
    fi
}

export -f fromNow

# Displays bash source info
function sourceInfo {
 # Find all sourced scripts
 local sourced
 sourced="$(bash -lixc exit 2>&1 | sed -n 's/^+* \(source\|\.\) //p')"
 
 # Filter and echo everything after /etc/bash.bashrc
 echo "$sourced" | awk '/\/etc\/bash\.bashrc/{f=1;next} f'
}

export -f sourceInfo

# Trims leading and trailing whitespace
function trim {
 echo "$1" | awk '{$1=$1};1'
}

export -f trim

# Uppercases entire string
function upper {
 echo "$1" | tr '[:lower:]' '[:upper:]'
}

export -f upper

# Lowercases entire string
function lower {
 echo "$1" | tr '[:upper:]' '[:lower:]'
}

export -f lower

# Displays function description
function fxdesc {
 local fx="$1"

 if [[ -z "$fx" ]]; then
  echo "Usage: fxdesc <function_name>" >&2
  return 1
 fi

 readarray -t sources < <(sourceInfo)
 local script=""

 for path in "${sources[@]}"; do
  if [[ -f "$path" ]] && grep -q "function ${fx}" "$path" 2>/dev/null; then
    script="$path"
    break
  fi
 done
 
 if [[ -z "$script" ]]; then
  echo "Function not found: $fx" >&2
  return 1
 fi

 local filename
 filename=$(basename "$script")
 local output
 output=$(grep -B 1 "function ${fx}" "$script" 2>/dev/null | head -n1 | sed 's/^# *//')

 echo "$output ($filename)"
}

export -f fxdesc

# Displays formatted function definition
function fxdef {
 local fx="$1"
 
 if [[ -z "$fx" ]]; then
  echo "Usage: fxdef <function_name>" >&2
  return 1
 fi
 
 local definition
 definition="$(fxdesc "${fx}" 2>/dev/null)"
 
 if [[ -n "$definition" ]]; then
  echo -e " ƒx → \e[1;32m$fx\e[0m \e[3;90m${definition}\e[0m"
 else
  echo -e " ƒx → \e[1;32m$fx\e[0m \e[3;90m(no description available)\e[0m"
 fi
}

export -f fxdef

# List all available functions
function lsfx {
 local output
 output=$(declare -F | awk '{print $3}' | grep -v '^_.*' | sort)

 mapfile -t funcs <<< "$output"

 for fx in "${funcs[@]}"; do
  if [[ -n "$fx" ]]; then
   fxdef "${fx}"
  fi
 done
}

export -f lsfx

# List all Java classes in jar
function lsjar {
 if [[ -z "$1" ]]; then
  echo "Usage: lsjar <jarfile>" >&2
  return 1
 fi
 
 if [[ ! -f "$1" ]]; then
  echo "Error: File '$1' not found" >&2
  return 1
 fi
 
 jar tf "$1"
}

export -f lsjar

# Run executable jar
function runjar {
 if [[ -z "$1" ]]; then
  echo "Usage: runjar <jarfile> [args...]" >&2
  return 1
 fi
 
 if [[ ! -f "$1" ]]; then
  echo "Error: File '$1' not found" >&2
  return 1
 fi
 
 java -jar "$@"
}

export -f runjar

# Initialize Java
function initjava {
 if [[ -z "$1" ]]; then
  echo "Usage: initjava <version>"
  return 1
 fi

 read -rp "Enter JDK path: " jdkHome

 if [[ ! -d "$jdkHome" ]]; then
  echo "Error: JDK path '$jdkHome' does not exist" >&2
  return 1
 fi

 export JAVA_HOME="$jdkHome"
 export PATH="$JAVA_HOME/bin:$PATH"

 echo "JAVA_HOME set to $JAVA_HOME"
 java -version

 read -rp "Enter Java formatter path (optional): " javaFormatter

 if [[ -n "$javaFormatter" ]]; then
  if [[ -f "$javaFormatter" ]]; then
   export JAVA_FORMATTER="$javaFormatter"
   echo "Java formatter set to $JAVA_FORMATTER"
  else
   echo "Warning: Java formatter path '$javaFormatter' not found"
  fi
 else
  echo "Java formatter not specified. Download from: https://github.com/google/google-java-format/releases"
 fi
}

export -f initjava

# Pretty prints Java code
function prettyJava {
 if [[ -z "$1" ]]; then
  echo "Usage: prettyJava <java_file> [formatter_path]" >&2
  return 1
 fi

 if [[ ! -f "$1" ]]; then
  echo "Error: File '$1' not found" >&2
  return 1
 fi

 local formatter=""
 
 # Check for provided formatter path
 if [[ -n "$2" ]]; then
  formatter="$2"
 # Check for environment variable
 elif [[ -n "$JAVA_FORMATTER" ]]; then
  formatter="$JAVA_FORMATTER"
 else
  echo "Error: Java formatter not found. Download from: https://github.com/google/google-java-format/releases" >&2
  return 1
 fi

 if [[ ! -f "$formatter" ]]; then
  echo "Error: Formatter '$formatter' not found" >&2
  return 1
 fi

 java -jar "$formatter" "$1"
}

export -f prettyJava

# Pretty Print JSON String
function prettyJson {
 if [[ -z "$1" ]]; then
  echo "Usage: prettyJson <json_string_or_file>" >&2
  return 1
 fi

 # If file, pretty print file
 if [[ -f "$1" ]]; then
  jq . "$1"
 else
  echo "$1" | jq .
 fi
}

export -f prettyJson

# Extract private key from P12
function privateKeyP12 {
 local p12_file="$1"

 if [[ -z "$p12_file" ]]; then
  echo "Usage: privateKeyP12 <p12_file>" >&2
  return 1
 fi

 if [[ ! -f "$p12_file" ]]; then
  echo "Error: File '$p12_file' not found" >&2
  return 1
 fi

 local outputFile="$PWD/private-key.pem"

 echo "Extracting private key from '$p12_file' to '$outputFile'..."
 openssl pkcs12 -in "$p12_file" -nocerts -nodes -out "$outputFile"

 if [[ $? -eq 0 ]]; then
  echo "Private key extracted successfully to '$outputFile'"
 else
  echo "Error extracting private key" >&2
  return 1
 fi
}

export -f privateKeyP12

# Extract public key from P12
function publicKeyP12 {
 local p12_file="$1"

 if [[ -z "$p12_file" ]]; then
  echo "Usage: publicKeyP12 <p12_file>" >&2
  return 1
 fi

 if [[ ! -f "$p12_file" ]]; then
  echo "Error: File '$p12_file' not found" >&2
  return 1
 fi

 local privateKeyFile="$PWD/temp-private-key.pem"
 local outputFile="$PWD/public-key.pem"

 echo "Extracting public key from '$p12_file' to '$outputFile'..."
 
 # First extract private key
 openssl pkcs12 -in "$p12_file" -nocerts -nodes -out "$privateKeyFile" && \
 # Then extract public key from private key
 openssl rsa -in "$privateKeyFile" -pubout -out "$outputFile" && \
 # Clean up temporary file
 rm -f "$privateKeyFile"

 if [[ $? -eq 0 ]]; then
  echo "Public key extracted successfully to '$outputFile'"
 else
  echo "Error extracting public key" >&2
  rm -f "$privateKeyFile"
  return 1
 fi
}

export -f publicKeyP12

# Extract public certificate from P12
function publicCertP12 {
 local p12_file="$1"

 if [[ -z "$p12_file" ]]; then
  echo "Usage: publicCertP12 <p12_file>" >&2
  return 1
 fi

 if [[ ! -f "$p12_file" ]]; then
  echo "Error: File '$p12_file' not found" >&2
  return 1
 fi

 local password
 read -srp "Enter P12 password: " password
 echo

 local outputFile="$PWD/public-crt.pem"

 echo "Extracting public certificate from '$p12_file' to '$outputFile'..."
 openssl pkcs12 -in "$p12_file" -nokeys -out "$outputFile" -passin pass:"$password"

 if [[ $? -eq 0 ]]; then
  echo "Public certificate extracted successfully to '$outputFile'"
 else
  echo "Error extracting public certificate" >&2
  return 1
 fi
}

export -f publicCertP12

# Extract certificate authority from P12
function certAuthP12 {
 local p12_file="$1"

 if [[ -z "$p12_file" ]]; then
  echo "Usage: certAuthP12 <p12_file>" >&2
  return 1
 fi

 if [[ ! -f "$p12_file" ]]; then
  echo "Error: File '$p12_file' not found" >&2
  return 1
 fi

 local password
 read -srp "Enter P12 password: " password
 echo

 local outputFile="$PWD/ca-cert.pem"

 echo "Extracting certificate authority from '$p12_file' to '$outputFile'..."
 openssl pkcs12 -in "$p12_file" -cacerts -nokeys -chain -out "$outputFile" -passin pass:"$password"

 if [[ $? -eq 0 ]]; then
  echo "Certificate authority extracted successfully to '$outputFile'"
 else
  echo "Error extracting certificate authority" >&2
  return 1
 fi
}

export -f certAuthP12

# Encrypts text
function encryptTxt {
 if [[ -z "$1" ]]; then
  echo "Usage: encryptTxt <text>" >&2
  return 1
 fi

 local password
 read -srp "Enter password: " password
 echo
 
 echo "$1" | openssl aes-256-cbc -a -salt -pass pass:"$password"
}

export -f encryptTxt

# Decrypts text
function decryptTxt {
 if [[ -z "$1" ]]; then
  echo "Usage: decryptTxt <encrypted_text>" >&2
  return 1
 fi

 local password
 read -srp "Enter password: " password
 echo
 
 echo "$1" | openssl aes-256-cbc -d -a -pass pass:"$password"
}

export -f decryptTxt

# Encrypts and zips a file
function encryptFile {
 if [[ $# -eq 0 ]]; then
  echo "Usage: encryptFile <filepath>" >&2
  return 1
 fi

 local file="$1"
 
 if [[ ! -f "$file" ]]; then
  echo "Error: File '$file' not found" >&2
  return 1
 fi

 local password
 read -srp "Enter password: " password
 echo
 
 local outfile="${file%.*}.zip"

 zip -e "$outfile" "$file" -P "$password"
 
 if [[ $? -eq 0 ]]; then
  echo "File encrypted successfully: $outfile"
 else
  echo "Error encrypting file" >&2
  return 1
 fi
}

export -f encryptFile

# Decrypts a zip file
function decryptFile {
 if [[ $# -eq 0 ]]; then
  echo "Usage: decryptFile <filepath>" >&2
  return 1
 fi

 local file="$1"
 
 if [[ ! -f "$file" ]]; then
  echo "Error: File '$file' not found" >&2
  return 1
 fi

 local password
 read -srp "Enter password: " password
 echo

 unzip -P "$password" "$file"
 
 if [[ $? -eq 0 ]]; then
  echo "File decrypted successfully"
 else
  echo "Error decrypting file" >&2
  return 1
 fi
}

export -f decryptFile

# Displays running clock
function clock {
 # Clear the screen and move the cursor to the top-left corner
 printf "\033[2J\033[H"

 echo "Press Ctrl+C to exit..."
 echo

 while true; do
  # Get the current time and format it
  local current_time
  current_time=$(date +"%m-%d-%Y %I:%M:%S %p")

  # Print the time, overwrite the line, and return cursor to beginning
  printf "\r%s" "$current_time"

  # Wait for one second
  sleep 1
 done
}

export -f clock