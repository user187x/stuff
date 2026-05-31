# Compiler settings
CXX = g++
CXXFLAGS = -std=c++17 -O3

# Formatter settings
FORMATTER = clang-format
FORMAT_FLAGS = -i -style=Google

# Target executable name
TARGET = SnapToAppImage

# Source files
SRCS = SnapToAppImage.cpp

# Default rule executed when running 'make'
# It will format the code first, then build the target
all: format $(TARGET)

# Rule to format the source code
format:
	$(FORMATTER) $(FORMAT_FLAGS) $(SRCS)

# Rule to build the target
$(TARGET): $(SRCS)
	$(CXX) $(CXXFLAGS) $(SRCS) -o $(TARGET)

# Rule to clean the workspace
clean:
	rm -f $(TARGET)

# Phony targets to prevent conflicts with files named "all", "clean", or "format"
.PHONY: all clean format
