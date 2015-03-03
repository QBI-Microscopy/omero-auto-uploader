package omerouploader;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.lang.Object;
import java.util.Collections;
import org.joda.time.DateTime;

import ome.formats.importer.ImportConfig;

public class WatchDir {
		 
	    private final WatchService watcher;
	    private final Map<WatchKey,Path> keys;
	    private final boolean recursive;
	    private boolean trace = false;
	    ImportConfig config;
		private UploadToOmero connection;
		private Path dir;
	    @SuppressWarnings("unchecked")
	    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
	        return (WatchEvent<T>)event;
	    }

	    @SuppressWarnings("unchecked")
		private final Map<Path, Long> expirationTimes = new HashMap();
	    private Long newFileWait = 10000L;  
	    	
	    /**
	     * Register the given directory with the WatchService
	     */
	    void register(Path dir) throws IOException {
	        WatchKey key = dir.register(watcher, ENTRY_CREATE);
	        if (trace) {
	            Path prev = keys.get(key);
	            if (prev == null) {
	                System.out.format("register: %s\n", dir);
	            } else {
	                if (!dir.equals(prev)) {
	                    System.out.format("update: %s -> %s\n", prev, dir);
	                }
	            }
	        }
	        keys.put(key, dir);
	    }
	 
	    /**
	     * Register the given directory, and all its sub-directories, with the
	     * WatchService.
	     */
	    void registerAll(final Path start) throws IOException {
	        // register directory and sub-directories
	        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
	            @Override
	            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
	                throws IOException
	            {
	                register(dir);
	                return FileVisitResult.CONTINUE;
	            }
	        });
	    }
	 
	    /**
	     * Creates a WatchService and registers the given directory
	     */
	    WatchDir(Path dir, boolean recursive, ImportConfig config) throws IOException {
			try {
				UploadToOmero connection = new UploadToOmero(config);
				this.connection = connection;
			} catch (Exception e) {
				e.printStackTrace();
			}
			
	        this.watcher = FileSystems.getDefault().newWatchService();
	        this.keys = new HashMap<WatchKey,Path>();
	        this.recursive = recursive;
	        
	        if (recursive) {
	            System.out.format("Scanning %s ...\n", dir);
	            registerAll(dir);
	            System.out.println("Done.");
	        } else {
	            register(dir);
	        }
	 
	        // enable trace after initial registration
	        this.trace = true;
	    }
	    
	    public void run() throws InterruptedException, IOException {
	        for(;;) {
	            //Retrieves and removes next watch key, waiting if none are present.
	            WatchKey k = watcher.take();

	            for(;;) {
	                long currentTime = new DateTime().getMillis();

	                if(k!=null)
	                    handleWatchEvents(k);
	                Path dir = this.dir;
	                System.out.println(dir);
	                handleExpiredWaitTimes(currentTime,dir);

	                // If there are no files left stop polling and block on .take()
	                if(expirationTimes.isEmpty())
	                    break;

	                long minExpiration = Collections.min(expirationTimes.values());
	                long timeout = minExpiration-currentTime;
	                System.out.println("timeout: "+timeout);
	                try {
						k = watcher.poll(timeout, TimeUnit.MILLISECONDS);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	            }
	        }
	    }

	    private void handleExpiredWaitTimes(Long currentTime, Path dir) {
	        // Start import for files for which the expirationtime has passed
	    	
	        for(Entry<Path, Long> entry : expirationTimes.entrySet()) {
	            if(entry.getValue()<=currentTime) {
	                Path child = entry.getKey();
					String name = child.toString();
					String fname = new File(name).getName();
					String sep = System.getProperty("file.separator");
					String[] paths = new String[] { dir+sep+fname };
					String extension = fname.substring(fname.lastIndexOf(".") + 1, fname.length());
					System.out.println(extension);
					String tif = "tif";
					String TIF = "TIF";
					String tiff = "tiff";
					String txt = "txt";				
					if (extension.equals(txt)){
					    System.out.printf("New file '%s' a plain text file.%n", fname);
						continue;
					}	
					if (extension.equals(tif) || extension.equals(tiff) || extension.equals(TIF)){
						//upload this to omero
					    System.out.printf("New file '%s' a tif file.%n", fname);
						try {
							//connection.start(paths); // Be sure to disconnect
						} catch (Exception e) {
							e.printStackTrace();
						}
					}				
					else {
						System.out.printf("New file '%s' is not a recognised file.%n", fname);
					}
	            	System.out.println("expired "+entry);
	                // do something with the file
	                expirationTimes.remove(entry.getKey());
	            }
	        }
	    }

	    private void handleWatchEvents(WatchKey k) throws IOException {
	        List<WatchEvent<?>> events = k.pollEvents();
	        for (WatchEvent<?> event : events) {
	            handleWatchEvent(event, keys.get(k));
	        }
	        // reset watch key to allow the key to be reported again by the watch service
	        k.reset();
	    }

	    private void handleWatchEvent(WatchEvent<?> event, Path dir) throws IOException {
	        Kind<?> kind = event.kind();

	        WatchEvent<Path> ev = cast(event);
	            Path name = ev.context();
	            Path child = dir.resolve(name);

	        if (kind == ENTRY_MODIFY || kind == ENTRY_CREATE) {
	            // Update modified time
	        	BasicFileAttributes attrs = Files.readAttributes(child, BasicFileAttributes.class);
	            FileTime lastModified = attrs.lastModifiedTime();
	            expirationTimes.put(name, lastModified.toMillis()+newFileWait);
	        }

	        if (kind == ENTRY_DELETE) {
	            expirationTimes.remove(child);
	        }
	    }	    
	 
	    /**
	     * Process all events for keys queued to the watcher
	     */
	    void processEvents() {
	        for (;;) {
	 
	            // wait for key to be signalled
	            WatchKey key;
	            try {
	                key = watcher.take();
	            } catch (InterruptedException x) {
	                return;
	            }
	 
	            Path dir = keys.get(key);
	            if (dir == null) {
	                System.err.println("WatchKey not recognized!!");
	                continue;
	            }
	 
	            for (WatchEvent<?> event: key.pollEvents()) {
	                Kind<?> kind = event.kind();
	 
	                // TBD - provide example of how OVERFLOW event is handled
	                if (kind == OVERFLOW) {
	                    continue;
	                }
	 
	                // Context for directory entry event is the file name of entry
	                WatchEvent<Path> ev = cast(event);
	                Path filename = ev.context();

	                //Verify that the new file is a text file.
	                Path child = dir.resolve(filename);
					String name = child.toString();
					String fname = new File(name).getName();
					String sep = System.getProperty("file.separator");
					String[] paths = new String[] { dir+sep+fname };
					String extension = fname.substring(fname.lastIndexOf(".") + 1, fname.length());
					System.out.println(extension);
					String tif = "tif";
					String TIF = "TIF";
					String tiff = "tiff";
					String txt = "txt";
					String zvi = "zvi";
					String ZVI = "ZVI";	
					if (extension.equals(txt)){
					    System.out.printf("New file '%s' a plain text file.%n", filename);
						continue;
					}	
					if (extension.equals(tif) || extension.equals(tiff) || extension.equals(TIF)|| extension.equals(zvi)|| extension.equals(ZVI)){
						//upload this to omero
					    System.out.printf("New file '%s' detected.%n", filename);
						try {
							connection.start(paths); // Be sure to disconnect
							
						} catch (Exception e) {
							e.printStackTrace();
						}
						continue;
					}				
					else {
						System.out.printf("New file '%s' is not a plain text file.%n", filename);
					}
	 
	            }
	 
	            // reset key and remove from set if directory no longer accessible
	            boolean valid = key.reset();
	            if (!valid) {
	                keys.remove(key);
	 
	                // all directories are inaccessible
	                if (keys.isEmpty()) {
	                    break;
	                }
	            }
	        }
	    }
	 
	    static void usage() {
	        System.err.println("usage: java WatchDir [-r] dir");
	        System.exit(-1);
	    }


		public void closeService() throws IOException {
			// TODO Auto-generated method stub
			watcher.close();
			connection.cleanup();
			System.out.println("file closed");
		}

}
