package xxx.com.web.poster;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.*;

/**
 * Main application class for the Poster Replica.
 * This class sets up the main window and initializes the UI components.
 */
public class Poster extends JFrame {

    public Poster() {
        initUI();
    }

    private void initUI() {
        setTitle("Poster");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 700);
        setLocationRelativeTo(null);

        // Main panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create the three main panels
        RequestPanel requestPanel = new RequestPanel();
        RequestDataPanel requestDataPanel = new RequestDataPanel(requestPanel);

        // The RequestHandler is now created earlier so it can be passed to the ResponsePanel
        final RequestHandler requestHandler = new RequestHandler(requestPanel, requestDataPanel, null);
        ResponsePanel responsePanel = new ResponsePanel(requestPanel, requestHandler);
        requestHandler.setResponsePanel(responsePanel); // Set the response panel on the handler


        // Top container for request and request data panels
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.add(requestPanel, BorderLayout.NORTH);
        topPanel.add(requestDataPanel, BorderLayout.CENTER);

        // Main SplitPane to divide top and response panels
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, responsePanel);
        splitPane.setResizeWeight(0.4); // Give more initial space to the top panel
        mainPanel.add(splitPane, BorderLayout.CENTER);

        add(mainPanel);

        // Add action listener to the requestPanel to trigger the request
        requestPanel.addSendButtonListener(e -> {
            // When the send button is clicked, execute the request
            requestHandler.executeRequest();
        });
    }

    public static void main(String[] args) {
        // Set the FlatLaf look and feel for a modern UI
        FlatDarkLaf.setup();

        EventQueue.invokeLater(() -> {
            Poster ex = new Poster();
            ex.setVisible(true);
        });
    }
}