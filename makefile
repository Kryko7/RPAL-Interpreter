


# Compiler and JVM commands
JAVAC = javac
JAVA = java

# Target Java source files and class files
SRC_DIR = rpal
SOURCES = $(wildcard $(SRC_DIR)/*.java)
CLASSES = $(notdir $(SOURCES:.java=.class))

# Main class (replace 'MainClass' with your main class name without .java extension)
MAIN_CLASS =rpal20

# Default target, to build and run the project
all: $(MAIN_CLASS).class $(CLASSES)
	@echo "Build successful!"
	
# Rule to compile the main class separately into the root directory
$(MAIN_CLASS).class: $(MAIN_CLASS).java
	@$(JAVAC) $^

# Rule to compile Java source files in the "rpal" folder into class files
%.class: $(SRC_DIR)/%.java
	@$(JAVAC) -d . $^

# Clean up generated files
clean:
	@rm -f $(MAIN_CLASS).class $(SRC_DIR)/*.class

