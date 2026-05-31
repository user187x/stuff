using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Windows.Forms;
using UglyToad.PdfPig;
using UglyToad.PdfPig.Content;

namespace PDF_Renamer
{
    public partial class Form1 : Form
    {
        // UI Components
        private TableLayoutPanel tableLayoutPanel1;
        private Label pdfDirectoryLabel;
        private TextBox pdfDirectoryPathField;
        private Button browsePdfDirectoryButton;
        private Label outputDirectoryLabel;
        private TextBox outputPathField;
        private Button browseOutputButton;
        private ProgressBar progressBar;
        private Button processPdfsButton;
        private SplitContainer splitContainer1;
        private GroupBox statusGroupBox;
        private TextBox statusArea;
        private GroupBox pdfListGroupBox;
        private ListBox pdfList;
        private ContextMenuStrip contextMenu;
        private ToolStripMenuItem openMenuItem;
        private ToolStripMenuItem convertMenuItem;
        private ToolStripMenuItem showPathMenuItem;

        public Form1()
        {
            Init(); // This method is managed by the Windows Forms designer
            InitializeCustomComponents(); // Our manual setup
        }

        /// <summary>
        /// A custom class to hold PDF file information for the ListBox.
        /// </summary>
        private class PdfEntry
        {
            public string FilePath { get; }
            public string StudentName { get; set; }

            public PdfEntry(string filePath, string studentName)
            {
                FilePath = filePath;
                StudentName = studentName;
            }

            // The ToString() is a fallback, but we use custom drawing in the ListBox.
            public override string ToString()
            {
                return $"{Path.GetFileName(FilePath)} | {StudentName ?? "N/A"}";
            }
        }

        #region Core Logic

        /// <summary>
        /// Handles the click event for the 'Browse' button to select the PDF source directory.
        /// </summary>
        private async void BrowsePdfDirectoryButton_Click(object sender, EventArgs e)
        {
            using (var dialog = new FolderBrowserDialog())
            {
                dialog.Description = "Select Directory Containing PDF Files";
                dialog.SelectedPath = Environment.GetFolderPath(Environment.SpecialFolder.Desktop);
                if (dialog.ShowDialog() == DialogResult.OK)
                {
                    pdfDirectoryPathField.Text = dialog.SelectedPath;
                    LogStatus($"ⓘ Directory Selected: {dialog.SelectedPath}");
                    statusArea.Clear();
                    pdfList.Items.Clear();

                    await ScanDirectoryForPdfsAsync(dialog.SelectedPath);
                }
            }
        }

        /// <summary>
        /// Handles the click event for the 'Browse' button to select the output directory.
        /// </summary>
        private void BrowseOutputButton_Click(object sender, EventArgs e)
        {
            using (var dialog = new FolderBrowserDialog())
            {
                dialog.Description = "Select Output Directory";
                dialog.SelectedPath = Environment.GetFolderPath(Environment.SpecialFolder.Desktop);
                if (dialog.ShowDialog() == DialogResult.OK)
                {
                    outputPathField.Text = dialog.SelectedPath;
                    LogStatus($"Output directory selected: {dialog.SelectedPath}");
                }
            }
        }

        /// <summary>
        /// Initiates the processing of all PDF files currently listed in the ListBox.
        /// </summary>
        private async void ProcessPdfsButton_Click(object sender, EventArgs e)
        {
            var allListedPdfs = pdfList.Items.Cast<PdfEntry>().ToList();
            if (!allListedPdfs.Any())
            {
                LogStatus("No PDF files listed to process. Please select a PDF directory first.");
                return;
            }

            await ConvertPdfsAsync(allListedPdfs);
        }

        /// <summary>
        /// Scans the selected directory for PDF files in a background task.
        /// </summary>
        private async Task ScanDirectoryForPdfsAsync(string directoryPath)
        {
            SetGuiEnabledState(false);
            progressBar.Visible = true;
            progressBar.Style = ProgressBarStyle.Blocks;
            LogStatus("» Searching for applicable PDF files...");

            try
            {
                var allPdfs = Directory.EnumerateFiles(directoryPath, "*.pdf", SearchOption.AllDirectories).ToList();

                if (!allPdfs.Any())
                {
                    LogStatus("No PDF files found in the selected directory.");
                    return;
                }

                progressBar.Maximum = allPdfs.Count;
                progressBar.Value = 0;

                int foundCount = 0;

                await Task.Run(() =>
                {
                    for (int i = 0; i < allPdfs.Count; i++)
                    {
                        var pdfFile = allPdfs[i];
                        string studentName = null;
                        try
                        {
                            using (var document = PdfDocument.Open(pdfFile))
                            {
                                var fullText = string.Join("\n", document.GetPages().Select(p => p.Text));
                                if (fullText.IndexOf("Student's Name:", StringComparison.OrdinalIgnoreCase) >= 0)
                                {
                                    studentName = ExtractStudentName(fullText);
                                    var entry = new PdfEntry(pdfFile, studentName);

                                    // Update UI on the UI thread
                                    this.Invoke((Action)(() => {
                                        pdfList.Items.Add(entry);
                                        foundCount++;
                                    }));
                                }
                            }
                        }
                        catch (Exception ex)
                        {
                            LogStatus($"Error reading PDF file '{Path.GetFileName(pdfFile)}': {ex.Message}");
                        }

                        // Update progress bar on the UI thread
                        this.Invoke((Action)(() => {
                            progressBar.Value = i + 1;
                            progressBar.CreateGraphics().DrawString(
                                $"Scanning {i + 1}/{allPdfs.Count} PDFs...",
                                new Font("Arial", (float)8.25, FontStyle.Regular),
                                Brushes.Black,
                                new PointF(progressBar.Width / 2 - 40, progressBar.Height / 2 - 7));
                        }));
                    }
                });

                LogStatus($"→ Total Applicable PDF Files Found: {foundCount}");
            }
            catch (Exception ex)
            {
                LogStatus($"An error occurred during PDF search: {ex.Message}");
                MessageBox.Show($"An error occurred: {ex.Message}", "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
            finally
            {
                progressBar.Visible = false;
                SetGuiEnabledState(true);
            }
        }

        /// <summary>
        /// Converts a list of PDFs (renames and saves them) in a background task.
        /// </summary>
        private async Task ConvertPdfsAsync(List<PdfEntry> pdfsToConvert)
        {
            if (string.IsNullOrWhiteSpace(outputPathField.Text))
            {
                MessageBox.Show("Please select an output directory first.", "Output Directory Required", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                BrowseOutputButton_Click(this, EventArgs.Empty); // Prompt user to select
                if (string.IsNullOrWhiteSpace(outputPathField.Text)) return; // User cancelled
            }

            SetGuiEnabledState(false);
            progressBar.Visible = true;
            progressBar.Maximum = pdfsToConvert.Count;
            progressBar.Value = 0;

            var outputBaseDirectory = outputPathField.Text;
            var finalOutputDirectory = Path.Combine(outputBaseDirectory, "UpdatedPDFs");

            try
            {
                Directory.CreateDirectory(finalOutputDirectory);
                LogStatus($"» Starting PDF Conversion to: {finalOutputDirectory}");

                await Task.Run(() =>
                {
                    for (int i = 0; i < pdfsToConvert.Count; i++)
                    {
                        var pdfEntry = pdfsToConvert[i];
                        try
                        {
                            // Ensure student name is extracted if it was missed
                            if (string.IsNullOrEmpty(pdfEntry.StudentName))
                            {
                                using (var document = PdfDocument.Open(pdfEntry.FilePath))
                                {
                                    var text = string.Join("\n", document.GetPages().Select(p => p.Text));
                                    pdfEntry.StudentName = ExtractStudentName(text);
                                }
                            }

                            if (!string.IsNullOrEmpty(pdfEntry.StudentName))
                            {
                                var sanitizedName = FormatAndSanitizeFilename(pdfEntry.StudentName);
                                var newFileName = $"{sanitizedName}.pdf";
                                var newFilePath = Path.Combine(finalOutputDirectory, newFileName);

                                File.Copy(pdfEntry.FilePath, newFilePath, true); // Overwrite if exists
                                LogStatus($"Successfully saved '{Path.GetFileName(pdfEntry.FilePath)}' as '{newFileName}'");
                            }
                            else
                            {
                                LogStatus($"Could not find student's name in '{Path.GetFileName(pdfEntry.FilePath)}'. Skipped.");
                            }
                        }
                        catch (Exception ex)
                        {
                            LogStatus($"Error processing '{Path.GetFileName(pdfEntry.FilePath)}': {ex.Message}");
                        }

                        this.Invoke((Action)(() => {
                            progressBar.Value = i + 1;
                            progressBar.CreateGraphics().DrawString(
                                $"Converting {i + 1}/{pdfsToConvert.Count} PDFs...",
                                new Font("Arial", (float)8.25, FontStyle.Regular),
                                Brushes.Black,
                                new PointF(progressBar.Width / 2 - 40, progressBar.Height / 2 - 7));
                        }));
                    }
                });

                LogStatus("\n» PDF Conversion Finished");
                // Create a custom dialog form to show the completion message.
                using (var customDialog = new Form())
                {
                    customDialog.Text = "Conversion Complete";
                    customDialog.StartPosition = FormStartPosition.CenterParent;
                    customDialog.ClientSize = new Size(340, 120);
                    customDialog.FormBorderStyle = FormBorderStyle.FixedDialog;
                    customDialog.MaximizeBox = false;
                    customDialog.MinimizeBox = false;

                    var messageLabel = new Label()
                    {
                        Text = $"PDF conversion completed successfully!\n{pdfsToConvert.Count} files processed.",
                        Location = new Point(15, 15),
                        AutoSize = true,
                        Font = new Font("Segoe UI", 9F)
                    };

                    var viewButton = new Button()
                    {
                        Text = "View",
                        Location = new Point(150, 80),
                        Size = new Size(80, 25),
                        DialogResult = DialogResult.Yes // This result will mean "View" was clicked
                    };

                    var dismissButton = new Button()
                    {
                        Text = "Dismiss",
                        Location = new Point(240, 80),
                        Size = new Size(80, 25),
                        DialogResult = DialogResult.No // This result will mean "Dismiss" was clicked
                    };

                    // Set default button behaviors
                    customDialog.AcceptButton = viewButton;
                    customDialog.CancelButton = dismissButton;

                    customDialog.Controls.Add(messageLabel);
                    customDialog.Controls.Add(viewButton);
                    customDialog.Controls.Add(dismissButton);

                    // Show the custom dialog and wait for a result
                    var dialogResult = customDialog.ShowDialog(this);

                    if (dialogResult == DialogResult.Yes) // Check if the "View" button was clicked
                    {
                        Process.Start("explorer.exe", finalOutputDirectory);
                    }
                }
            }
            catch (Exception ex)
            {
                LogStatus($"An unexpected error occurred during conversion: {ex.Message}");
                MessageBox.Show($"An error occurred: {ex.Message}", "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
            finally
            {
                progressBar.Visible = false;
                SetGuiEnabledState(true);
            }
        }

        #endregion

        #region Context Menu Handlers

        private void PdfList_MouseDown(object sender, MouseEventArgs e)
        {
            if (e.Button == MouseButtons.Right)
            {
                int index = pdfList.IndexFromPoint(e.Location);
                if (index != -1 && pdfList.SelectedIndices.Count <= 1)
                {
                    pdfList.SelectedIndex = index;
                }

                if (pdfList.SelectedIndex != -1)
                {
                    contextMenu.Show(Cursor.Position);
                }
            }
        }

        private void OpenMenuItem_Click(object sender, EventArgs e)
        {
            var selectedPdfs = pdfList.SelectedItems.Cast<PdfEntry>();
            if (!selectedPdfs.Any())
            {
                LogStatus("No PDF files selected to open.");
                return;
            }

            foreach (var entry in selectedPdfs)
            {
                try
                {
                    LogStatus($"Attempting to open: {entry.FilePath}");
                    Process.Start(entry.FilePath);
                }
                catch (Exception ex)
                {
                    LogStatus($"Error opening '{Path.GetFileName(entry.FilePath)}': {ex.Message}");
                }
            }
        }

        private async void ConvertMenuItem_Click(object sender, EventArgs e)
        {
            var selectedPdfs = pdfList.SelectedItems.Cast<PdfEntry>().ToList();
            if (!selectedPdfs.Any())
            {
                LogStatus("No PDF files selected for conversion.");
                return;
            }
            await ConvertPdfsAsync(selectedPdfs);
        }

        private void ShowPathMenuItem_Click(object sender, EventArgs e)
        {
            if (pdfList.SelectedItem is PdfEntry selectedEntry)
            {
                string message = $"Full path of the selected file:\n\n{selectedEntry.FilePath}\n\nWould you like to open the containing folder?";
                var result = MessageBox.Show(message, "File Path Information", MessageBoxButtons.YesNo, MessageBoxIcon.Information);

                if (result == DialogResult.Yes)
                {
                    try
                    {
                        Process.Start("explorer.exe", $"/select,\"{selectedEntry.FilePath}\"");
                    }
                    catch (Exception ex)
                    {
                        MessageBox.Show($"Could not open the directory: {ex.Message}", "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                    }
                }
            }
        }
        #endregion

        #region UI Helpers

        /// <summary>
        /// Sets up event handlers and properties for components not easily configured in the designer.
        /// </summary>
        private void InitializeCustomComponents()
        {
            // Event Handlers for Buttons
            this.browsePdfDirectoryButton.Click += new System.EventHandler(this.BrowsePdfDirectoryButton_Click);
            this.browseOutputButton.Click += new System.EventHandler(this.BrowseOutputButton_Click);
            this.processPdfsButton.Click += new System.EventHandler(this.ProcessPdfsButton_Click);

            // Context Menu Setup
            this.contextMenu = new ContextMenuStrip();
            this.openMenuItem = new ToolStripMenuItem("Open");
            this.convertMenuItem = new ToolStripMenuItem("Convert Selected");
            this.showPathMenuItem = new ToolStripMenuItem("Show Path");

            this.contextMenu.Items.AddRange(new ToolStripItem[] {
                this.openMenuItem,
                this.convertMenuItem,
                new ToolStripSeparator(),
                this.showPathMenuItem
            });

            this.openMenuItem.Click += new System.EventHandler(this.OpenMenuItem_Click);
            this.convertMenuItem.Click += new System.EventHandler(this.ConvertMenuItem_Click);
            this.showPathMenuItem.Click += new System.EventHandler(this.ShowPathMenuItem_Click);

            // ListBox Customization
            this.pdfList.DrawMode = DrawMode.OwnerDrawFixed;
            this.pdfList.ItemHeight = 28; // Set a fixed height for each item
            this.pdfList.DrawItem += new DrawItemEventHandler(this.PdfList_DrawItem);
            this.pdfList.MouseDown += new MouseEventHandler(this.PdfList_MouseDown);
        }

        /// <summary>
        /// Custom drawing logic for the ListBox to display file path and student name.
        /// </summary>
        private void PdfList_DrawItem(object sender, DrawItemEventArgs e)
        {
            if (e.Index < 0) return;

            // Draw background
            e.DrawBackground();

            // Get the PdfEntry for the current item
            var entry = (PdfEntry)pdfList.Items[e.Index];

            // Use different colors for text based on selection
            Brush textBrush = (e.State & DrawItemState.Selected) == DrawItemState.Selected
                ? SystemBrushes.HighlightText
                : SystemBrushes.ControlText;

            // Define fonts
            using (var pathFont = new Font("Consolas", 8.5f, FontStyle.Regular))
            using (var nameFont = new Font("Segoe UI", 9f, FontStyle.Bold))
            {
                // Prepare path text (truncate if too long)
                string fullPath = entry.FilePath;
                string displayPath = fullPath;
                int maxLen = 80;
                if (fullPath.Length > maxLen)
                {
                    displayPath = "..." + fullPath.Substring(fullPath.Length - maxLen);
                }

                // Draw path text
                e.Graphics.DrawString(displayPath, pathFont, textBrush,
                    new Rectangle(e.Bounds.Left + 5, e.Bounds.Top + 2, e.Bounds.Width - 10, 14));

                // Prepare and draw student name text
                string studentName = FormatAndSanitizeFilename(entry.StudentName) ?? "N/A";
                e.Graphics.DrawString(studentName, nameFont, textBrush,
                    new Rectangle(e.Bounds.Left + 5, e.Bounds.Top + 14, e.Bounds.Width - 10, 14));
            }

            // Draw focus rectangle
            e.DrawFocusRectangle();
        }

        /// <summary>
        /// Logs a message to the status text area on the UI thread.
        /// </summary>
        private void LogStatus(string message)
        {
            if (statusArea.InvokeRequired)
            {
                statusArea.Invoke((Action)(() => LogStatus(message)));
            }
            else
            {
                statusArea.AppendText(message + Environment.NewLine);
            }
        }

        /// <summary>
        /// Toggles the enabled state of the main UI controls.
        /// </summary>
        private void SetGuiEnabledState(bool enabled)
        {
            browsePdfDirectoryButton.Enabled = enabled;
            browseOutputButton.Enabled = enabled;
            processPdfsButton.Enabled = enabled;
            pdfList.Enabled = enabled;
        }

        #endregion

        #region Text & Filename Helpers

        /// <summary>
        /// Extracts the student's name from the full text of a PDF.
        /// </summary>
        private string ExtractStudentName(string text)
        {
            var match = Regex.Match(text, @"Student's Name:\s*(.*?)(?=\s{2,}|Student ID|\r?\n|$)", RegexOptions.IgnoreCase);
            return match.Success ? match.Groups[1].Value.Trim() : null;
        }

        /// <summary>
        /// Sanitizes a string to be a valid filename and formats it as "Last, First".
        /// </summary>
        private string FormatAndSanitizeFilename(string name)
        {
            if (string.IsNullOrWhiteSpace(name)) return "UnnamedStudent";

            // Sanitize
            var invalidChars = new string(Path.GetInvalidFileNameChars());
            var sanitized = Regex.Replace(name.Trim(), $"[{Regex.Escape(invalidChars)}]", "_");

            // Format as "Last, First"
            var nameParts = sanitized.Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries);
            if (nameParts.Length >= 2)
            {
                var lastName = nameParts.Last();
                var firstName = string.Join(" ", nameParts.Take(nameParts.Length - 1));
                return $"{lastName}, {firstName}";
            }

            return sanitized; // Return as is if format is not "First Last"
        }

        #endregion

        #region Windows Form Designer generated code
        // NOTE: The rest of the `Form1.Designer.cs` content is managed by Visual Studio.
        // This is a manual representation of what the designer would generate.
        // You can copy/paste the method calls from InitializeCustomComponents() into
        // the real InitializeComponent() method in Form1.Designer.cs or let the designer
        // create the events for you.
        private void Init()
        {
            this.tableLayoutPanel1 = new System.Windows.Forms.TableLayoutPanel();
            this.pdfDirectoryLabel = new System.Windows.Forms.Label();
            this.pdfDirectoryPathField = new System.Windows.Forms.TextBox();
            this.browsePdfDirectoryButton = new System.Windows.Forms.Button();
            this.outputDirectoryLabel = new System.Windows.Forms.Label();
            this.outputPathField = new System.Windows.Forms.TextBox();
            this.browseOutputButton = new System.Windows.Forms.Button();
            this.progressBar = new System.Windows.Forms.ProgressBar();
            this.processPdfsButton = new System.Windows.Forms.Button();
            this.splitContainer1 = new System.Windows.Forms.SplitContainer();
            this.statusGroupBox = new System.Windows.Forms.GroupBox();
            this.statusArea = new System.Windows.Forms.TextBox();
            this.pdfListGroupBox = new System.Windows.Forms.GroupBox();
            this.pdfList = new System.Windows.Forms.ListBox();
            this.tableLayoutPanel1.SuspendLayout();
            ((System.ComponentModel.ISupportInitialize)(this.splitContainer1)).BeginInit();
            this.splitContainer1.Panel1.SuspendLayout();
            this.splitContainer1.Panel2.SuspendLayout();
            this.splitContainer1.SuspendLayout();
            this.statusGroupBox.SuspendLayout();
            this.pdfListGroupBox.SuspendLayout();
            this.SuspendLayout();
            // 
            // tableLayoutPanel1
            // 
            this.tableLayoutPanel1.ColumnCount = 3;
            this.tableLayoutPanel1.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle());
            this.tableLayoutPanel1.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle(System.Windows.Forms.SizeType.Percent, 100F));
            this.tableLayoutPanel1.ColumnStyles.Add(new System.Windows.Forms.ColumnStyle());
            this.tableLayoutPanel1.Controls.Add(this.pdfDirectoryLabel, 0, 0);
            this.tableLayoutPanel1.Controls.Add(this.pdfDirectoryPathField, 1, 0);
            this.tableLayoutPanel1.Controls.Add(this.browsePdfDirectoryButton, 2, 0);
            this.tableLayoutPanel1.Controls.Add(this.outputDirectoryLabel, 0, 1);
            this.tableLayoutPanel1.Controls.Add(this.outputPathField, 1, 1);
            this.tableLayoutPanel1.Controls.Add(this.browseOutputButton, 2, 1);
            this.tableLayoutPanel1.Controls.Add(this.progressBar, 0, 2);
            this.tableLayoutPanel1.Controls.Add(this.processPdfsButton, 0, 3);
            this.tableLayoutPanel1.Controls.Add(this.splitContainer1, 0, 4);
            this.tableLayoutPanel1.Dock = System.Windows.Forms.DockStyle.Fill;
            this.tableLayoutPanel1.Location = new System.Drawing.Point(0, 0);
            this.tableLayoutPanel1.Name = "tableLayoutPanel1";
            this.tableLayoutPanel1.Padding = new System.Windows.Forms.Padding(10);
            this.tableLayoutPanel1.RowCount = 5;
            this.tableLayoutPanel1.RowStyles.Add(new System.Windows.Forms.RowStyle());
            this.tableLayoutPanel1.RowStyles.Add(new System.Windows.Forms.RowStyle());
            this.tableLayoutPanel1.RowStyles.Add(new System.Windows.Forms.RowStyle());
            this.tableLayoutPanel1.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Absolute, 50F));
            this.tableLayoutPanel1.RowStyles.Add(new System.Windows.Forms.RowStyle(System.Windows.Forms.SizeType.Percent, 100F));
            this.tableLayoutPanel1.Size = new System.Drawing.Size(984, 561);
            this.tableLayoutPanel1.TabIndex = 0;
            // 
            // pdfDirectoryLabel
            // 
            this.pdfDirectoryLabel.Anchor = System.Windows.Forms.AnchorStyles.Left;
            this.pdfDirectoryLabel.AutoSize = true;
            this.pdfDirectoryLabel.Location = new System.Drawing.Point(13, 18);
            this.pdfDirectoryLabel.Name = "pdfDirectoryLabel";
            this.pdfDirectoryLabel.Size = new System.Drawing.Size(79, 13);
            this.pdfDirectoryLabel.TabIndex = 0;
            this.pdfDirectoryLabel.Text = "PDF Directory:";
            // 
            // pdfDirectoryPathField
            // 
            this.pdfDirectoryPathField.Anchor = ((System.Windows.Forms.AnchorStyles)((System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right)));
            this.pdfDirectoryPathField.Location = new System.Drawing.Point(98, 14);
            this.pdfDirectoryPathField.Name = "pdfDirectoryPathField";
            this.pdfDirectoryPathField.ReadOnly = true;
            this.pdfDirectoryPathField.Size = new System.Drawing.Size(778, 20);
            this.pdfDirectoryPathField.TabIndex = 1;
            // 
            // browsePdfDirectoryButton
            // 
            this.browsePdfDirectoryButton.Anchor = System.Windows.Forms.AnchorStyles.Left;
            this.browsePdfDirectoryButton.Location = new System.Drawing.Point(882, 13);
            this.browsePdfDirectoryButton.Name = "browsePdfDirectoryButton";
            this.browsePdfDirectoryButton.Size = new System.Drawing.Size(75, 23);
            this.browsePdfDirectoryButton.TabIndex = 2;
            this.browsePdfDirectoryButton.Text = "Browse...";
            this.browsePdfDirectoryButton.UseVisualStyleBackColor = true;
            // 
            // outputDirectoryLabel
            // 
            this.outputDirectoryLabel.Anchor = System.Windows.Forms.AnchorStyles.Left;
            this.outputDirectoryLabel.AutoSize = true;
            this.outputDirectoryLabel.Location = new System.Drawing.Point(13, 47);
            this.outputDirectoryLabel.Name = "outputDirectoryLabel";
            this.outputDirectoryLabel.Size = new System.Drawing.Size(74, 13);
            this.outputDirectoryLabel.TabIndex = 3;
            this.outputDirectoryLabel.Text = "Output Directory:";
            // 
            // outputPathField
            // 
            this.outputPathField.Anchor = ((System.Windows.Forms.AnchorStyles)((System.Windows.Forms.AnchorStyles.Left | System.Windows.Forms.AnchorStyles.Right)));
            this.outputPathField.Location = new System.Drawing.Point(98, 43);
            this.outputPathField.Name = "outputPathField";
            this.outputPathField.ReadOnly = true;
            this.outputPathField.Size = new System.Drawing.Size(778, 20);
            this.outputPathField.TabIndex = 4;
            // 
            // browseOutputButton
            // 
            this.browseOutputButton.Anchor = System.Windows.Forms.AnchorStyles.Left;
            this.browseOutputButton.Location = new System.Drawing.Point(882, 42);
            this.browseOutputButton.Name = "browseOutputButton";
            this.browseOutputButton.Size = new System.Drawing.Size(75, 23);
            this.browseOutputButton.TabIndex = 5;
            this.browseOutputButton.Text = "Browse...";
            this.browseOutputButton.UseVisualStyleBackColor = true;
            // 
            // progressBar
            // 
            this.tableLayoutPanel1.SetColumnSpan(this.progressBar, 3);
            this.progressBar.Dock = System.Windows.Forms.DockStyle.Fill;
            this.progressBar.Location = new System.Drawing.Point(13, 71);
            this.progressBar.Name = "progressBar";
            this.progressBar.Size = new System.Drawing.Size(958, 20);
            this.progressBar.TabIndex = 6;
            this.progressBar.Visible = false;
            // 
            // processPdfsButton
            // 
            this.processPdfsButton.Anchor = System.Windows.Forms.AnchorStyles.None;
            this.tableLayoutPanel1.SetColumnSpan(this.processPdfsButton, 3);
            this.processPdfsButton.Font = new System.Drawing.Font("Microsoft Sans Serif", 9.75F, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, ((byte)(0)));
            this.processPdfsButton.Location = new System.Drawing.Point(422, 102);
            this.processPdfsButton.Name = "processPdfsButton";
            this.processPdfsButton.Size = new System.Drawing.Size(139, 34);
            this.processPdfsButton.TabIndex = 7;
            this.processPdfsButton.Text = "Process Files";
            this.processPdfsButton.UseVisualStyleBackColor = true;
            // 
            // splitContainer1
            // 
            this.tableLayoutPanel1.SetColumnSpan(this.splitContainer1, 3);
            this.splitContainer1.Dock = System.Windows.Forms.DockStyle.Fill;
            this.splitContainer1.Location = new System.Drawing.Point(13, 147);
            this.splitContainer1.Name = "splitContainer1";
            this.splitContainer1.Orientation = System.Windows.Forms.Orientation.Horizontal;
            // 
            // splitContainer1.Panel1
            // 
            this.splitContainer1.Panel1.Controls.Add(this.statusGroupBox);
            // 
            // splitContainer1.Panel2
            // 
            this.splitContainer1.Panel2.Controls.Add(this.pdfListGroupBox);
            this.splitContainer1.Size = new System.Drawing.Size(958, 401);
            this.splitContainer1.SplitterDistance = 110;
            this.splitContainer1.TabIndex = 8;
            // 
            // statusGroupBox
            // 
            this.statusGroupBox.Controls.Add(this.statusArea);
            this.statusGroupBox.Dock = System.Windows.Forms.DockStyle.Fill;
            this.statusGroupBox.Location = new System.Drawing.Point(0, 0);
            this.statusGroupBox.Name = "statusGroupBox";
            this.statusGroupBox.Padding = new System.Windows.Forms.Padding(5);
            this.statusGroupBox.Size = new System.Drawing.Size(958, 110);
            this.statusGroupBox.TabIndex = 0;
            this.statusGroupBox.TabStop = false;
            this.statusGroupBox.Text = "Processing Status";
            // 
            // statusArea
            // 
            this.statusArea.Dock = System.Windows.Forms.DockStyle.Fill;
            this.statusArea.Location = new System.Drawing.Point(5, 18);
            this.statusArea.Multiline = true;
            this.statusArea.Name = "statusArea";
            this.statusArea.ReadOnly = true;
            this.statusArea.ScrollBars = System.Windows.Forms.ScrollBars.Vertical;
            this.statusArea.Size = new System.Drawing.Size(948, 87);
            this.statusArea.TabIndex = 0;
            // 
            // pdfListGroupBox
            // 
            this.pdfListGroupBox.Controls.Add(this.pdfList);
            this.pdfListGroupBox.Dock = System.Windows.Forms.DockStyle.Fill;
            this.pdfListGroupBox.Location = new System.Drawing.Point(0, 0);
            this.pdfListGroupBox.Name = "pdfListGroupBox";
            this.pdfListGroupBox.Padding = new System.Windows.Forms.Padding(5);
            this.pdfListGroupBox.Size = new System.Drawing.Size(958, 287);
            this.pdfListGroupBox.TabIndex = 0;
            this.pdfListGroupBox.TabStop = false;
            this.pdfListGroupBox.Text = "PDF Files (File Path | Name)";
            // 
            // pdfList
            // 
            this.pdfList.Dock = System.Windows.Forms.DockStyle.Fill;
            this.pdfList.FormattingEnabled = true;
            this.pdfList.Location = new System.Drawing.Point(5, 18);
            this.pdfList.Name = "pdfList";
            this.pdfList.SelectionMode = System.Windows.Forms.SelectionMode.MultiExtended;
            this.pdfList.Size = new System.Drawing.Size(948, 264);
            this.pdfList.TabIndex = 0;
            // 
            // Form1
            // 
            this.AutoScaleDimensions = new System.Drawing.SizeF(6F, 13F);
            this.AutoScaleMode = System.Windows.Forms.AutoScaleMode.Font;
            this.ClientSize = new System.Drawing.Size(984, 561);
            this.Controls.Add(this.tableLayoutPanel1);
            this.MinimumSize = new System.Drawing.Size(600, 400);
            this.Name = "Form1";
            this.Text = "PDF Renamer";
            this.Icon = ((System.Drawing.Icon)(System.Drawing.SystemIcons.Application));
            this.tableLayoutPanel1.ResumeLayout(false);
            this.tableLayoutPanel1.PerformLayout();
            this.splitContainer1.Panel1.ResumeLayout(false);
            this.splitContainer1.Panel2.ResumeLayout(false);
            ((System.ComponentModel.ISupportInitialize)(this.splitContainer1)).EndInit();
            this.splitContainer1.ResumeLayout(false);
            this.statusGroupBox.ResumeLayout(false);
            this.statusGroupBox.PerformLayout();
            this.pdfListGroupBox.ResumeLayout(false);
            this.ResumeLayout(false);
        }
        #endregion
    }
}