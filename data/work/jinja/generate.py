import getpass
from jinja2 import Template


def main():
 # Prompt for username
 username = input('Enter NiFi Cluster Username: ')

 # Prompt securely for password
 password = getpass.getpass('Enter NiFi Cluster Password: ')

 # Read the template file
 with open('jinja-template.yaml', 'r') as file:
  template = Template(file.read())

 # Render the template with the provided variables
 rendered_yaml = template.render(nifi_username=username, nifi_password=password)

 # Write the result to the final configuration file
 with open('nifi-cluster.yaml', 'w') as file:
  file.write(rendered_yaml)

 print('✅ Successfully generated nifi-cluster.yaml')


if __name__ == '__main__':
 main()
