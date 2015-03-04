/* 

	This collection of classes implements a swing gui for making an omero connection,
	tracking a specfied directory using the Java7 watchservice and the omero import 
	library as a way of ingesting images to a user specified omero server
	
	Written by Daniel Matthews, QBI Microscopy Officer
	Last updated 150513
 
*/ 
package omero-auto-uploader;

import java.awt.*; 
import java.awt.event.*; 
import javax.swing.*; 

import java.io.*;
import java.nio.file.*;
import ome.formats.importer.ImportConfig;
import omero.model.Dataset;

public class OmeroConnectGUI extends JPanel implements ActionListener { 
  /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	static private final String newline = "\n";
	private ConnectionTask task;
	JTextArea log; 
	JTextField jtfHost;
	JTextField jtfPort;
	JTextField jtfUserName;
	JPasswordField jtfPassword;
	JTextField jtfTrackFolder;  
	JTextField jtfDataSetID;
	//JTextArea jtaLog;
	
	JButton jbtnConnect; // button to compare the files 
	JButton jbtnSelectDir;
	JButton jbtnStopConnection;
	JFileChooser fc;

	JLabel jlabHost, jlabPort, jlabUserName, jlabPassword, jlabTrackFolder, jLabDataSetID; // displays prompts  
	JLabel jlabResult; // displays results and error messages 

	OmeroConnectGUI() { 

		// Create a new JFrame container. 
		JFrame jfrm = new JFrame("Connect to Omero"); 

		// Specify FlowLayout for the layout manager. 
		jfrm.setLayout(new FlowLayout()); 

		// Give the frame an initial size. 
		jfrm.setSize(200, 500); 
		jfrm.setLocationRelativeTo(null);

		// Terminate the program when the user closes the application. 
		jfrm.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// Create the text fields for the file names.. 
		jtfHost = new JTextField(14); 
		jtfHost.setText("localhost");
		jtfPort = new JTextField(14); 
		jtfPort.setText("4064");
		jtfUserName = new JTextField(14); 
		//jtfUserName.setText("root");
		jtfPassword = new JPasswordField(14);
		//jtfPassword.setText("omero");
		jtfTrackFolder = new JTextField(14);
		jtfDataSetID = new JTextField(5);
		//jtfDataSetID.setText("2");
		//jtaLog = new JTextArea(5, 15);
		//JScrollPane scrollPane = new JScrollPane(jtaLog); 
		//jtaLog.setEditable(false);
		
		// Set the action commands for the text fields. 
		jtfHost.setActionCommand("Host"); 
		jtfPort.setActionCommand("Port"); 
		jtfUserName.setActionCommand("Username"); 
		jtfPassword.setActionCommand("Password"); 
		jtfTrackFolder.setActionCommand("TrackFolder");
		jtfTrackFolder.setText("/Users/uqdmatt2/Documents/Programming/Java/Omero/another_test");
		jtfDataSetID.setActionCommand("DataSetID");
		
		// Create the buttons. 
		JButton jbtnConnect = new JButton("Connect");
		jbtnConnect.setActionCommand("connect"); 
		JButton jbtnSelectDir = new JButton("Monitor Directory");
		jbtnSelectDir.setActionCommand("select");
		JButton jbtnStopConnection = new JButton("Stop Connection");
		jbtnStopConnection.setActionCommand("stopconnection");
		
		// Create the file chooser
		fc = new JFileChooser();
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

		// Add action listener for the buttons. 
		jbtnConnect.addActionListener(this); 
		jbtnSelectDir.addActionListener(this); 
		jbtnStopConnection.addActionListener(this); 
		
		// Create the labels. 
		jlabHost = new JLabel("HOST: "); 
		jlabPort = new JLabel("PORT: ");
		jlabUserName = new JLabel("Username: "); 
		jlabPassword = new JLabel("Password: ");
		jlabTrackFolder = new JLabel("Folder to monitor: ");
		jLabDataSetID = new JLabel("Import images to dataset ID: ");
		jlabResult = new JLabel(""); 

		// Add the components to the content pane. 
		jfrm.add(jlabHost); 
		jfrm.add(jtfHost);  
		jfrm.add(jlabPort); 
		jfrm.add(jtfPort);	
		jfrm.add(jlabUserName); 
		jfrm.add(jtfUserName);  
		jfrm.add(jlabPassword); 
		jfrm.add(jtfPassword);
		jfrm.add(jlabTrackFolder); 
		jfrm.add(jtfTrackFolder);
		jfrm.add(jLabDataSetID);
		jfrm.add(jtfDataSetID);
		jfrm.add(jbtnSelectDir);    
		jfrm.add(jbtnConnect);
		jfrm.add(jbtnStopConnection);
		jfrm.add(jlabResult); 
		//jfrm.add(jtaLog);

		// Display the frame. 
		jfrm.setVisible(true); 
	} 

	
	private class ConnectionTask extends SwingWorker<Void,Void> { 
		private Path trkPath;
		private boolean recursive;
		private ImportConfig config;
		private WatchDir watcher;
		
		public ConnectionTask(Path trkPath, boolean recursive, ImportConfig config) {
			this.trkPath = trkPath;
			this.recursive = recursive;
			this.config = config;
		}
		@Override
		protected Void doInBackground() {
			try {
				WatchDir watcher = new WatchDir(trkPath, recursive, config);
				this.watcher = watcher;
				watcher.processEvents();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return null;
		}
		public void stop() {
			try {
				watcher.closeService();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
 
  // Compare the files when the Compare button is pressed. 
	public void actionPerformed(ActionEvent ae) {    
		boolean recursive = false;

		if ("select".equals(ae.getActionCommand())) {
			System.out.println("Selection pressed");
			//Handle select directory button action.
			System.out.println("Trigger file chooser");
			int returnVal = fc.showOpenDialog(OmeroConnectGUI.this);

			if (returnVal == JFileChooser.APPROVE_OPTION) {
				String trkFolder = fc.getSelectedFile().getAbsolutePath();
				jtfTrackFolder.setText(trkFolder);
			} else {
				log.append("Open command cancelled by user." + newline);
			} 	 
		}

		if ("connect".equals(ae.getActionCommand())) {
			// First, confirm that both file names have 
			// been entered. 
			System.out.println("Connect pressed");
			if(jtfHost.getText().equals("")) { 
				jlabResult.setText("Host name missing."); 
				return; 
			} 
			if(jtfPort.getText().equals("")) {  //put in something to check that a number was entered
				jlabResult.setText("Port number missing."); 
				return; 
			} 
			if(jtfUserName.getText().equals("")) { 
				jlabResult.setText("Username missing."); 
				return; 
			}
			if(jtfPassword.getPassword().equals("")) { 
				jlabResult.setText("Password missing."); 
				return; 
			}
			if(jtfTrackFolder.getText().equals("")) { 
				jlabResult.setText("Folder missing."); 
				return; 
			}
			if(jtfDataSetID.getText().equals("")) { 
				jlabResult.setText("DataSet ID missing."); 
				return; 
			}
			else {
				recursive = true; //should probably have some kind of check here to make sure the folder exists			
			}		 
			// get host name    
			String host = jtfHost.getText();    

			// get port
			int port = Integer.parseInt(jtfPort.getText());     

			// get username
			String uname = jtfUserName.getText(); 

			// get password
			String pwrd = new String(jtfPassword.getPassword());

			// get folder to track
			String trkFolder = jtfTrackFolder.getText();
			Path trkPath = Paths.get(trkFolder);
			
			//get the ID of the dataset being imported to
			int dataSetID = Integer.parseInt(jtfDataSetID.getText());
			
			// Set up configuration parameters
			ImportConfig config = new ImportConfig();
			config.email.set("");
			config.sendFiles.set(true);
			config.sendReport.set(false);
			config.contOnError.set(false);
			config.debug.set(false);
			config.hostname.set(host);
			config.port.set(port);
			config.username.set(uname);
			config.password.set(pwrd);
			config.targetClass.set(Dataset.class.getName());
			config.targetId.set((long) dataSetID);

			// Attempt the connection here.
			//ConnectToOMERO connection = new ConnectToOMERO(host, port, uname, pwrd);
			(task = new ConnectionTask(trkPath, recursive, config)).execute();
		}

		if ("stopconnection".equals(ae.getActionCommand())) {
			System.out.println("Stop pressed");
			task.stop();
		}

  } 
 
  public static void main(String args[]) { 
    // Create the frame on the event dispatching thread. 
    SwingUtilities.invokeLater(new Runnable() { 
      public void run() { 
        new OmeroConnectGUI(); 
      } 
    }); 
  } 
}