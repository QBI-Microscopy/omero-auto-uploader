package omerouploader;

import loci.formats.in.DefaultMetadataOptions;
import loci.formats.in.MetadataLevel;
import loci.formats.meta.MetadataStore;
import ome.formats.OMEROMetadataStoreClient;
import ome.formats.importer.ImportCandidates;
import ome.formats.importer.ImportConfig;
import ome.formats.importer.ImportEvent;
import ome.formats.importer.ImportLibrary;
import ome.formats.importer.OMEROWrapper;
import ome.formats.importer.cli.ErrorHandler;
import ome.formats.importer.cli.LoggingImportMonitor;
import omero.ServerError;

public class UploadToOmero {
    /** Configuration used by all components */
    public final ImportConfig config;

    /** ErrorHandler which is also responsible for uploading files */
    public final ErrorHandler handler;

    /** Bio-Formats {@link MetadataStore} implementation for OMERO. */
    private final OMEROMetadataStoreClient store;

    
    public UploadToOmero(final ImportConfig config) throws Exception {
        this.config = config;
        config.loadAll();
        config.isUpgradeNeeded();
        store = config.createStore();
        store.logVersionInfo(config.getIniVersionNumber());
        this.handler = new ErrorHandler(config);

//        Runtime.getRuntime().addShutdownHook(new Thread() {
//            public void run() {
//                cleanup();
//            }
//        });
    }

    public int start(String[] paths) throws ServerError {
    	boolean success = true;
		try {
			
			store.logVersionInfo(config.getIniVersionNumber());
 
			// Create a OMERO reader wrapper which encompasses the related
			// Bio-Formats functionality required for an OMERO import, an
			// import library which is responsible for actually performing an
			// OMERO import life cycle and an error handler which encompasses
			// all error reporting while an import is taking place.
			OMEROWrapper reader = new OMEROWrapper(config);
			ImportLibrary library = new ImportLibrary(store, reader);
 
			// Add a logging observer to the import library which will print
			// to the log file (defaulting to STDOUT/STDERR) the status of the
			// import process.
			library.addObserver(new LoggingImportMonitor());
 
			// Calculate the candidates that are required for the import, which
			// is done with a metadata level of MINIMUM to avoid parsing and
			// populating all metadata during this process.
			ImportCandidates candidates =
				new ImportCandidates(reader, paths, handler);
			
	        if (candidates.size() > 1) {
	            if (handler.errorCount() > 0) {
	                System.err.println("No imports due to errors!");
	                //report();
	            } else {
	                System.err.println("No imports found");
	                cleanup(); // #5426 Preventing close exceptions.
	            }
	        }

	        else { 
	        	// Up the metadata level to ALL so that the actual import itself
	        	// will be complete.
	        	reader.setMetadataOptions(
	        			new DefaultMetadataOptions(MetadataLevel.ALL));
	        	success = library.importCandidates(config, candidates);
	        	//report();
	        }
		} finally {
			store.logout();
		}
        return success? 0 : 2;
    }
    
    void report() {
        boolean report = config.sendReport.get();
        boolean files = config.sendFiles.get();
        boolean logs = config.sendLogFile.get();
        if (report) {
           handler.update(null, new ImportEvent.DEBUG_SEND(files, logs));
        }
    }

    /**
* Cleans up after a successful or unsuccessful image import. This method
* only does the minimum required cleanup, so that it can be called
* during shutdown.
*/
    public void cleanup() {
        if (store != null) {
            store.logout();
        }
    }

}