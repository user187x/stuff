import json
import subprocess
import zipfile
import os
import tkinter
from tkinter import filedialog

root = tkinter.Tk()
root.withdraw()

target_zipped_file_path = "connections/followers_and_following/followers_1.json"
output_directory = 'C:/Users/mrshl/Desktop/'
output_file_name = "followers.txt"

def extract_links_from_json(json_file):
    # Open and read the HTML file
    with open(json_file, mode='r', encoding='utf-8') as json_data:
        data = json.load(json_data) 
    
    return data

def extract_file_from_zip(json_file):

    with zipfile.ZipFile(json_file, mode="r") as zip_ref:
        zip_ref.extract(target_zipped_file_path, json_file)

json_file = filedialog.askopenfilename()
print("Using file : " + json_file)

extracted_file = extract_file_from_zip(json_file)
print(extracted_file)

# output_directory = os.path.dirname(json_file) + output_file_name

# data = extract_links_from_json(json_data)
# users = [entry['string_list_data'][0]['value'] for entry in data]
# counter = 0

# with open(output_file, 'w') as file:
#     for user in users:
#         counter += 1
#         print(user)
#         file.write(user + '\n')

# print("File Written : " + output_file)
# print("Total Followers : " + str(counter))

# subprocess.call(['notepad.exe', output_file])
