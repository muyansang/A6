package selector;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import selector.SelectionModel.SelectionState;
import scissors.ScissorsSelectionModel;

/**
 * A graphical application for selecting and extracting regions of images.
 */
public class SelectorApp implements PropertyChangeListener {

    /**
     * Our application window.  Disposed when application exits.
     */
    private final JFrame frame;

    /**
     * Component for displaying the current image and selection tool.
     */
    private final ImagePanel imgPanel;

    /**
     * The current state of the selection tool.  Must always match the model used by `imgPanel`.
     */
    private SelectionModel model;

    // New in A6
    /**
     * Progress bar to indicate the progress of a model that needs to do long calculations in a
     * "processing" state.
     */
    private JProgressBar processingProgress;

    /* Components whose state must be changed during the selection process. */
    private JMenuItem saveItem;
    private JMenuItem undoItem;
    private JButton cancelButton;
    private JButton undoButton;
    private JButton resetButton;
    private JButton finishButton;
    private final JLabel statusLabel;


    /**
     * Construct a new application instance.  Initializes GUI components, so must be invoked on the
     * Swing Event Dispatch Thread.  Does not show the application window (call `start()` to do
     * that).
     */
    public SelectorApp() {
        // Initialize application window
        frame = new JFrame("Selector");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Add status bar
        statusLabel = new JLabel();
        frame.add(statusLabel, BorderLayout.PAGE_END);


        // Add image component with scrollbars
        imgPanel = new ImagePanel();
        JScrollPane scrollPane = new JScrollPane(imgPanel);
        scrollPane.setPreferredSize(new Dimension(400,700));
        frame.add(scrollPane, BorderLayout.CENTER);

        // Add menu bar
        frame.setJMenuBar(makeMenuBar());

        // Add control buttons
        frame.add(makeControlPanel(), BorderLayout.LINE_END);

        // New in A6: Add progress bar
        processingProgress = new JProgressBar();
        frame.add(processingProgress, BorderLayout.PAGE_START);

        // Controller: Set initial selection tool and update components to reflect its state
        setSelectionModel(new PointToPointSelectionModel(true));

    }

    /**
     * Create and populate a menu bar with our application's menus and items and attach listeners.
     * Should only be called from constructor, as it initializes menu item fields.
     */
    private JMenuBar makeMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // Create and populate File menu
        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);
        JMenuItem openItem = new JMenuItem("Open...");
        fileMenu.add(openItem);
        saveItem = new JMenuItem("Save...");
        fileMenu.add(saveItem);
        JMenuItem closeItem = new JMenuItem("Close");
        fileMenu.add(closeItem);
        JMenuItem exitItem = new JMenuItem("Exit");
        fileMenu.add(exitItem);

        // Create and populate Edit menu
        JMenu editMenu = new JMenu("Edit");
        menuBar.add(editMenu);
        undoItem = new JMenuItem("Undo");
        editMenu.add(undoItem);

        //KeyEvent Stroke_ctrl
        KeyStroke saveKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK);
        KeyStroke undoStroke= KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK);
        KeyStroke openStroke= KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK);
        KeyStroke exitStroke= KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.CTRL_DOWN_MASK);
        KeyStroke closeStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0);

        // Controller: Attach menu item listeners
        openItem.addActionListener(e -> openImage());
        openItem.setAccelerator(openStroke);

        closeItem.addActionListener(e -> imgPanel.setImage(null));
        closeItem.setAccelerator(closeStroke);

        saveItem.addActionListener(e -> saveSelection());
        saveItem.setAccelerator(saveKeyStroke);

        exitItem.addActionListener(e -> frame.dispose());
        exitItem.setAccelerator(exitStroke);

        undoItem.addActionListener(e -> model.undo());
        undoItem.setAccelerator(undoStroke);

        return menuBar;
    }

    /**
     * Return a panel containing buttons for controlling image selection.  Should only be called
     * from constructor, as it initializes button fields.
     */
    private JPanel makeControlPanel() {
        JPanel buttonsPanel = new JPanel ();
        GridLayout layout = new GridLayout(0, 1);
        buttonsPanel.setLayout(layout);

        cancelButton = new JButton("Cancel");
        buttonsPanel.add(cancelButton);
        cancelButton.addActionListener(e -> model.cancelProcessing());

        undoButton = new JButton("Undo");
        buttonsPanel.add(undoButton);
        undoButton.addActionListener(e -> model.undo());

        resetButton = new JButton("Reset");
        buttonsPanel.add(resetButton);
        resetButton.addActionListener(e -> model.reset());

        finishButton = new JButton("Finish");
        buttonsPanel.add(finishButton);
        finishButton.addActionListener(e -> model.finishSelection());

        // TODO A6.0a: Add the following option to your control panel's model selector:
        //  * Intelligent scissors: gray (`ScissorsSelectionModel` with a "CrossGradMono" weight
        //    name).  You will need to `import scissors.ScissorsSelectionModel` to use this class. DONE

        // Adding the combo box
        JComboBox<String> selecModules = new JComboBox<>(new String[] { "Point-to-point", "Spline",
                "Circle","Intelligent scissors"});
        selecModules.addActionListener(e -> {
            if (selecModules.getSelectedIndex() == 0) {
                setSelectionModel(new PointToPointSelectionModel(model));
            }
            else if (selecModules.getSelectedIndex() == 1){
                setSelectionModel(new SplineSelectionModel(model));
            }
            else if (selecModules.getSelectedIndex() == 2){
                setSelectionModel(new CircleSelectionModel(model));
            }
            else if (selecModules.getSelectedIndex() == 3){
                setSelectionModel(new ScissorsSelectionModel("CrossGradMono", true));
            }
        });

        JLabel selectText = new JLabel("Selection mode:");
        selectText.setPreferredSize(new Dimension(50, 50));
        buttonsPanel.add(selectText);
        buttonsPanel.add(selecModules);
        return buttonsPanel;
    }

    /**
     * Start the application by showing its window.
     */
    public void start() {
        // Compute ideal window size
        frame.pack();

        frame.setVisible(true);
    }

    /**
     * React to property changes in an observed model.  Supported properties include:
     * * "state": Update components to reflect the new selection state.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // TODO A6.0b: Update the progress bar [1] as follows:
        //  * When the model transitions into a state that is "processing", set the progress bar to
        //    "indeterminate" mode.  That way, the user sees that something is happening even before
        //    the model has an estimate of its progress.
        //  * When the model transitions to any other state, ensure that the progress bar's value is
        //    0 and that it is not in "indeterminate" mode.  The progress bar should look inert if
        //    the model isn't currently processing.
        //  * Upon receiving a "progress" property change, set the progress bar's value to the new
        //    progress value (which will be an integer in [0..100]) and ensure it is not in
        //    "indeterminate" mode.  You need to use the event object to get the new value.
        //  [1] https://docs.oracle.com/javase/tutorial/uiswing/components/progress.html DONE

        if ("state".equals(evt.getPropertyName())) {
                reflectSelectionState(model.state());
            if("processing".equals(evt.getPropertyName())){
                processingProgress.setIndeterminate(true);
            } else{
                processingProgress.setIndeterminate(false);
                processingProgress.setValue(0);
            }
        } else if ("progress".equals(evt.getPropertyName())) {
            processingProgress.setIndeterminate(false);
            processingProgress.setValue((int)evt.getNewValue());
        }
    }

    /**
     * Update components to reflect a selection state of `state`.  Disable buttons and menu items
     * whose actions are invalid in that state, and update the status bar.
     */
    private void reflectSelectionState(SelectionState state) {
        // Update status bar to show current state
        statusLabel.setText(state.toString());

        cancelButton.setEnabled(state.isProcessing());
        undoButton.setEnabled(state.canUndo());
        finishButton.setEnabled(state.canFinish());
        resetButton.setEnabled(!state.isEmpty());
        saveItem.setEnabled(state.isFinished() && !state.isEmpty());
    }

    /**
     * Return the model of the selection tool currently in use.
     */
    public SelectionModel getSelectionModel() {
        return model;
    }

    /**
     * Use `newModel` as the selection tool and update our view to reflect its state.  This
     * application will no longer respond to changes made to its previous selection model and will
     * instead respond to property changes from `newModel`.
     */
    public void setSelectionModel(SelectionModel newModel) {
        // Stop listening to old model
        if (model != null) {
            model.removePropertyChangeListener(this);
        }

        imgPanel.setSelectionModel(newModel);
        model = imgPanel.selection();
        model.addPropertyChangeListener("state", this);

        // New in A6: Listen for "progress" events
        model.addPropertyChangeListener("progress", this);

        // Since the new model's initial state may be different from the old model's state, manually
        //  trigger an update to our state-dependent view.
        reflectSelectionState(model.state());
    }

    /**
     * Start displaying and selecting from `img` instead of any previous image.  Argument may be
     * null, in which case no image is displayed and the current selection is reset.
     */
    public void setImage(BufferedImage img) {
        imgPanel.setImage(img);
    }

    /**
     * Allow the user to choose a new image from an "open" dialog.  If they do, start displaying and
     * selecting from that image.  Show an error message dialog (and retain any previous image) if
     * the chosen image could not be opened.
     */
    private void openImage() {
        JFileChooser chooser = new JFileChooser();
        // Start browsing in current directory
        chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        // Filter for file extensions supported by Java's ImageIO readers
        chooser.setFileFilter(new FileNameExtensionFilter("Image files",
                ImageIO.getReaderFileSuffixes()));

        int returnVal = chooser.showOpenDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            BufferedImage img = null;

            try {
                img = ImageIO.read(file);
                if (img ==null) {
                    throw new IOException("Failed to open the file. The file could not be read as an image.");
                }
                this.setImage(img);

            } catch (IOException e) {
                String filePath = file.getPath();
                String errorMessage = "Could not read the image at "+filePath;
                JOptionPane.showMessageDialog(null,
                        errorMessage,
                        "Unsupported image format",
                        JOptionPane.ERROR_MESSAGE);
                openImage();
            }
        }
    }

    /**
     * Save the selected region of the current image to a file selected from a "save" dialog.
     * Show an error message dialog if the image could not be saved.
     */
    private void saveSelection() {
        JFileChooser chooser = new JFileChooser();
        // Start browsing in current directory
        chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        // We always save in PNG format, so only show existing PNG files
        chooser.setFileFilter(new FileNameExtensionFilter("PNG images", "png"));

        boolean saveStatus = false;

        while (!saveStatus){
            int returnVal = chooser.showSaveDialog(null);

            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();

                if (!file.getName().toLowerCase().endsWith(".png")) {
                    file = new File(file.getAbsolutePath() + ".png");
                }

                if (file.exists()) {
                    int overwriteChoice = JOptionPane.showConfirmDialog(
                            null,
                            "File already exists, Do you want to replace it with your current file?",
                            "Confirm Overwrite",
                            JOptionPane.YES_NO_CANCEL_OPTION
                    );

                    if (overwriteChoice != JOptionPane.YES_OPTION) {
                        if (overwriteChoice == JOptionPane.CANCEL_OPTION) {
                            saveStatus = true;
                            break;
                        }
                        continue;
                    }
                }

                try (OutputStream out = new FileOutputStream(file)) {

                    model.saveSelection(out);
                    JOptionPane.showMessageDialog(null, "Image saved successfully!");
                    saveStatus = true;

                } catch (IOException e) {
                    JOptionPane.showMessageDialog(
                            null,
                            e.getMessage(),
                            e.getClass().getSimpleName(),
                            JOptionPane.ERROR_MESSAGE
                    );
                } catch (IllegalStateException e) {
                    JOptionPane.showMessageDialog(
                            null,
                            e.getMessage(),
                            "Selection Not Completed",
                            JOptionPane.WARNING_MESSAGE
                    );
                }

            }
            else{
                saveStatus = true;
            }
        }
    }

    /**
     * Run an instance of SelectorApp.  No program arguments are expected.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Set Swing theme to look the same (and less old) on all operating systems.
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception ignored) {
                /* If the Nimbus theme isn't available, just use the platform default. */
            }

            // Create and start the app
            SelectorApp app = new SelectorApp();
            app.start();
        });
    }
}
