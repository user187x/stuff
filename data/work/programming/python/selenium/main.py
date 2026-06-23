import os
import time
from dotenv import load_dotenv
from selenium import webdriver
from selenium.webdriver.chrome.service import Service

# Load environment variables from the local .env file
load_dotenv()

def main():
    # 1. Point to the chromedriver file using the environment variable
    driver_path = os.getenv('CHROMEDRIVER_PATH')
    
    if not driver_path:
        raise ValueError("CHROMEDRIVER_PATH is not set. Please check your .env file.")

    print(f"Initializing ChromeDriver at: {driver_path}")
    driver_service = Service(driver_path)

    # 2. Launch the browser
    driver = webdriver.Chrome(service=driver_service)

    try:
        # 3. View the webpage
        print("Navigating to Wikipedia...")
        driver.get("https://en.wikipedia.org/wiki/Linux")

        # Keep the browser open for 5 seconds so you can see it
        time.sleep(5)
        
    finally:
        # 4. Close the browser
        print("Cleaning up and closing browser...")
        driver.quit()

if __name__ == "__main__":
    main()
