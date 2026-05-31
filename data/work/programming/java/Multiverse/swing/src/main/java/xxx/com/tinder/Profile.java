package xxx.com.tinder;

public class Profile {

  private String name;
  private int age;
  private String bio;
  // Stores the absolute path to the image file
  private String imagePath;

  public Profile(String name, int age, String bio, String imagePath) {
    this.name = name;
    this.age = age;
    this.bio = bio;
    this.imagePath = imagePath;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getAge() {
    return age;
  }

  public void setAge(int age) {
    this.age = age;
  }

  public String getBio() {
    return bio;
  }

  public void setBio(String bio) {
    this.bio = bio;
  }

  public String getImagePath() {
    return imagePath;
  }

  public void setImagePath(String imagePath) {
    this.imagePath = imagePath;
  }

  @Override
  public String toString() {
    return name + ", " + age;
  }
}
