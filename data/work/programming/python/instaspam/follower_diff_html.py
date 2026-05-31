import re
import subprocess

def extract_links_from_html(html_file):
    # Open and read the HTML file
    with open(html_file, 'r', encoding='utf-8') as file:
        html_content = file.read()

    # Use a regular expression to find all href links in anchor tags
    links = re.findall(r'href=["\'](https?://[^\s"\']+)["\']', html_content)

    return links

html_file = 'C:/Users/mrshl/Documents/Python/followers.html'
output_file = 'C:/Users/mrshl/Documents/Python/formatted-output.txt'
links = extract_links_from_html(html_file)

users = []
counter = 0

for link in links:
    
    stripped = link.strip().split('com/')
    user_name = stripped[1]

    counter += 1
    users.append(user_name)

print("Total Users : " + str(len(users)))

############# ENTRY POINT ###############

value = input("Search for which user : ")
found = False
for user in users:
    
    if user.casefold() == value.casefold():
        found = True

if found is True:
    print("User " + value + " Exists!")
else:
    print("Not Found")
    value = input("List Users (y/n): ")

    if "y" == value.casefold():
        for user in users:
            print(str(counter) + " " + user)

value = input("Write users a file (y/n): ")
if "y" == value.casefold():
    
    with open(output_file, 'w') as file:
        for item in users:
            file.write(item + '\n')
    
    print("File Written : " + output_file)
    
    subprocess.call(['notepad.exe', output_file])
print("Goodbye!")