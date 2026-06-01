use rfd::{MessageDialog, MessageLevel};

// ==========================================
// 1. DATA: The equivalent of a Java "Class"
// ==========================================
// Rust does not have 'class'. Data (structs) and Behavior (impl) are strictly separated.
pub struct WindowGreeter {
    title: String,
    messages: Vec<String>, // Vec is Rust's equivalent to Java's ArrayList
}

// ==========================================
// 2. BEHAVIOR: Methods attached to the struct
// ==========================================
impl WindowGreeter {
    // There are no built-in constructors in Rust. 
    // By convention, we create a static method called `new` that returns `Self`.
    pub fn new(title: &str) -> Self {
        Self {
            title: title.to_string(), // Convert string slice (&str) to owned String
            messages: Vec::new(),
        }
    }

    // `&mut self` is required to modify the struct's data.
    // In Java, methods can mutate object state by default. In Rust, you must explicitly ask for mutable permission.
    pub fn add_message(&mut self, msg: &str) {
        self.messages.push(msg.to_string());
    }

    // `&self` means this method only READS data. It cannot modify the struct.
    pub fn pop_all_standard(&self) {
        println!("[*] Using standard iteration...");
        
        // Iteration Technique 1: Standard For-Loop using `.iter()`
        // `.iter()` borrows the list. If we didn't use it, the for-loop would "consume" and destroy the list!
        for (index, msg) in self.messages.iter().enumerate() {
            let display_text = format!("Message {} of {}:\n{}", index + 1, self.messages.len(), msg);

            MessageDialog::new()
                .set_level(MessageLevel::Info)
                .set_title(&self.title)
                .set_description(&display_text)
                .show(); // Blocks execution until the user clicks OK
        }
    }

    pub fn pop_summary_functional(&self) {
        println!("[*] Using functional iteration...");
        
        // Iteration Technique 2: Functional / Stream API style
        // Similar to Java Streams: map(), filter(), collect()
        let combined_messages: String = self.messages
            .iter()
            .map(|m| format!("- {}", m)) // Closure (Lambda): formats each string
            .collect::<Vec<String>>()    // Gathers them into a new list
            .join("\n");                 // Joins them with newlines

        MessageDialog::new()
            .set_level(MessageLevel::Warning)
            .set_title("Functional Summary")
            .set_description(&combined_messages)
            .show();
    }
}

// ==========================================
// 3. EXECUTION: The Entry Point
// ==========================================
fn main() {
    // `mut` is required here because we are going to call `add_message`, 
    // which modifies the state of the greeter.
    let mut greeter = WindowGreeter::new("Rust System Alerts");

    // Adding data
    greeter.add_message("Welcome! This is a native OS window.");
    greeter.add_message("Rust handles memory without a Garbage Collector.");
    greeter.add_message("Check out the functional iteration next!");

    // Calling methods
    greeter.pop_all_standard();
    greeter.pop_summary_functional();

    println!("✅ All windows closed. Exiting safely.");
}
