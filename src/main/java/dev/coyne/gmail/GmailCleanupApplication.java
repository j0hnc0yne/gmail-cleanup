package dev.coyne.gmail;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.yaml.snakeyaml.Yaml;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;

/* class to demonstrate use of Gmail list labels API */
public class GmailCleanupApplication {
	private static Logger logger = Logger.getLogger("logger");
	
	private static boolean batchDelete = true;
	private static int daysToKeep = 90;
  /**
   * Application name.
   */
  private static final String APPLICATION_NAME = "gmail-admin";
  /**
   * Global instance of the JSON factory.
   */
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  /**
   * Directory to store authorization tokens for this application.
   */
  private static final String TOKENS_DIRECTORY_PATH = "tokens";

  /**
   * Global instance of the scopes required by this app
   * If modifying these scopes, delete your previously saved tokens/ folder.
   */
  private static final List<String> SCOPES = Collections.singletonList("https://mail.google.com/");
  private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

  private static GmailService gmailService;
  
  /**
   * Creates an authorized Credential object.
   *
   * @param HTTP_TRANSPORT The network HTTP Transport.
   * @return An authorized Credential object.
   * @throws IOException If the credentials.json file cannot be found.
   */
  private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT)
      throws IOException {
    // Load client secrets.
    InputStream in = GmailCleanupApplication.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
    if (in == null) {
      throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
    }
    GoogleClientSecrets clientSecrets =
        GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

    // Build flow and trigger user authorization request.
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
        .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
        .setAccessType("offline")
        .build();
    LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
    Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    //returns an authorized Credential object.
    return credential;
  }

  public static void main(String... args) throws IOException, GeneralSecurityException {
	initialize();
	  
    Yaml yaml = new Yaml();
    InputStream inputStream = GmailCleanupApplication.class
      .getClassLoader()
      .getResourceAsStream("filter-config.yaml");
    
    Map<String, Object> filterConfig = yaml.load(inputStream);
    filterConfig.forEach(GmailCleanupApplication::cleanup);
    
  }
  
  private static void initialize() throws IOException, GeneralSecurityException {
    // Build a new authorized API client service.
    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    Gmail gmail = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
        .setApplicationName(APPLICATION_NAME)
        .build();
    gmailService = new GmailService(gmail);
  }
  
  
  private static void cleanup(String filterType, Object filters) {
	  logger.info("Processing filterType: " + filterType);
	  List<String> filterList = (List<String>)filters;
	  filterList.forEach(filterText -> cleanupForFilter(filterType + ":" + filterText));
  }
  
  private static void cleanupForFilter(String filter) {
	try {
		if (daysToKeep > 0) {
			filter = filter + " Older_than:"+daysToKeep+"d";
		}
	    List<Message> messages = gmailService.getMessagesByFilter(filter);
	    if (messages != null && messages.size() > 0) {
		    logger.info(filter + "->  Messages to delete: " + messages.size());
		    if (batchDelete) {
		    	gmailService.batchDelete(messages);
		    	logger.info(filter + "->  Batch delete complete");
		    } else { 
			    long deleted = gmailService.trashMessages(messages);
			    logger.info(filter + "-> Messages successfully deleted: " + deleted);
		    }
	    } else {
	    	logger.info(filter + "->  NO MESSAGES");
	    }

	} catch (IOException e) {
		e.printStackTrace();
	}
  }
}