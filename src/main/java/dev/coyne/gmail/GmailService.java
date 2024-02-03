package dev.coyne.gmail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.BatchDeleteMessagesRequest;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;

public class GmailService {
    
	private static final Long MAX_MESSAGES = 500L;
	
    private final Gmail service;

    public GmailService(Gmail service) {
        this.service = service;
    }

    public List<Message> getMessagesByFilter(String filter) throws IOException {
    	List<Message> messages = new ArrayList<>();
    	String token = "";
        do {
            ListMessagesResponse output = service.users().messages()
                    .list("me")
                    .setMaxResults(MAX_MESSAGES)
                    .setQ(filter)
                    .setPageToken(token)
                    .execute();
            token = output.getNextPageToken();
            if (output !=null && output.getMessages() != null) {
                messages.addAll(output.getMessages());
            }
        } while (token != null);
        return messages;
    }
    
    public void batchDelete(List<Message> messages) {
    	try {
    		// Max size is 1000 per batch
    		for (int i = 0; i <= (int) messages.size() / 1000; i++ ) {
    			int startIdx = i * 1000;
    			int length = Math.min(1000, messages.size() - startIdx);
        		List<Message> messageSub = messages.subList(startIdx, startIdx + length);
    			BatchDeleteMessagesRequest messagesBatch = new BatchDeleteMessagesRequest();
    			messagesBatch.setIds(messageSub.stream().map(Message::getId).collect(Collectors.toList()));
    			service.users().messages().batchDelete("me", messagesBatch).execute();
    		}
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    public long trashMessages(List<Message> messages) {
    	return messages.stream().map(this::trashMessage).filter(deleted -> deleted).count();
    }
    
    public boolean trashMessage(Message message) {
		try {
	    	service.users().messages().trash("me", message.getId()).execute();
	    	return true;
		} catch (IOException e) {
			System.err.println("Unable to delete message: " +  message.getId() + " - " + e.getMessage());
			return false;
		}
    }
}
