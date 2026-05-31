#!/usr/bin/env bash

# Use portable shebang and determine script location
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
SCRIPT_NAME=$(basename "${BASH_SOURCE[0]}")
SCRIPT_PATH="${SCRIPT_DIR}/${SCRIPT_NAME}"

export BACKUP_SCRIPT="$SCRIPT_PATH"

# Use ANSI codes for consistent coloring
echo -e "\033[1;37mSourced:\033[0m \033[1;32m$SCRIPT_PATH\033[0m"

# Back up project to Google storage
function __backupCode {

 # Remember current directory silently
 pushd . > /dev/null

 # Current epoch
 local now=$(date +"%-m-%-d-%y-%I%M-%p")
 echo "Date : $now"

 local fileName="coding-backup.zip"
 echo "File Name : $fileName"
 # Storage location
 local destination="G:/My Drive/Backup/$fileName"
 
 # Also copy it to second hard drive
 local documents="C:/Users/mrshl/Documents/$fileName"

 echo "Destination I : $destination"
 echo "Destination II: $documents"

 # Git archive the entire project
 echo "Archiving project..."

 projectPath="D:/Projects"
 cd $projectPath

 # Run git archive to save
 git archive --format=zip --output="$destination" master

 cp "$destination" "$documents"
 if [[ $? -eq 0 ]]; then
   echo "Copied redundantly : $documents"
 else
   echo "Failed to copy file to : $documents"
 fi

 # if Successful
 if [[ $? -eq 0 ]]; then

  size=$(ls -lh "$destination" | awk '{print $5}')
  echo "Backup complete!"
  echo "Saved : $destination"
  echo "Saved : $documents"
  echo "Size  : $size"

 else
  echo "Failure archiving project"
 fi

 # Return to location prior to script silently
 popd > /dev/null
}

# function to backup project work
function backup {

 echo "Backing up code base..."
 __backupCode
 
 if [[ $? -eq 0 ]]; then
  echo "Successfully archieved project from local Git"
 else
  echo "Failed archieving code"
  return 1
 fi

 echo "Sending Git project to remote repo(s)"
 git remote show cdrive
 git push cdrive
 if [[ $? -eq 0 ]]; then
  echo "Successfully sent code to 'cdrive' Git repo"
 else
  echo "Failed sending code to 'cdrive' Git repo"
 fi
 git remote show ddrive
 git push ddrive
 if [[ $? -eq 0 ]]; then
  echo "Successfully sent code to 'ddrive' Git repo"
 else
  echo "Failed sending code to 'ddrive' Git repo"
 fi
}

export -f backup
