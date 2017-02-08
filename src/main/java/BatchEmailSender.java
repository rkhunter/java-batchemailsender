import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;

import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.*;
import com.google.api.services.gmail.Gmail;
import com.sun.xml.internal.messaging.saaj.packaging.mime.MessagingException;
import models.Utils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.*;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BatchEmailSender {
    private static String USER_ME = "me";

    private static Pattern emailPattern = Pattern.compile(Utils.EMAIL_REGEX);

    /* Global instance of Database */
    private static DB campaigns;

    /** Global instance of Gmail service */
    private static Gmail service;

    // Application name
    private static String CAMPAIGN_NAME;


    /** Directory to store user credentials for this application. */
    private static java.io.File DATA_STORE_DIR;

    public static void setCampaign(String campaignName) {
        CAMPAIGN_NAME = campaignName;
        DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"), String.valueOf(".credentials/" + campaignName));
    }

    /** Global instance of the {@link FileDataStoreFactory}. */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /** Global instance of the HTTP transport. */
    private static HttpTransport HTTP_TRANSPORT;

    /** Required permissions */
    private static final List<String> SCOPES = Arrays.asList(GmailScopes.GMAIL_LABELS, GmailScopes.GMAIL_READONLY, GmailScopes.GMAIL_SEND);

    public static CompletableFuture<Credential> authorize() {
        CompletableFuture result = new CompletableFuture<Credential>();
        try {
            // Load client secrets.
            InputStream in = BatchEmailSender.class.getResourceAsStream("/client_secret.json");
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            // Build flow and trigger user authorization request.
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                                                                                .setDataStoreFactory(DATA_STORE_FACTORY)
                                                                                .setAccessType("offline")
                                                                                .build();

            Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
            result.complete(credential);
            System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
        } catch (IOException e) {
            result.cancel(true);
            e.printStackTrace();
        }

        return result;
    }

    public static CompletableFuture<Gmail> initializeGmailService() {
        CompletableFuture result = new CompletableFuture<Gmail>();

        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }

        try {
            Credential credential = authorize().get();
            Gmail _service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                    .setApplicationName(CAMPAIGN_NAME)
                    .build();
            result.complete(_service);
        } catch (InterruptedException e) {
            result.cancel(true);
            e.printStackTrace();
        } catch (ExecutionException e) {
            result.cancel(true);
            e.printStackTrace();
        }

        return result;
    }

    public static CompletableFuture setupAuth() {
        CompletableFuture result = new CompletableFuture();
            initializeGmailService().thenApply(_service -> {
                service = _service;
                result.complete(null);
                return null;
            });

        return result;
    }

    public static CompletableFuture setupDB() {
        CompletableFuture result = new CompletableFuture();
        campaigns = DBMaker.fileDB("campaigns.db").fileMmapEnableIfSupported().make();
        result.complete(campaigns);
        return result;
    }

    public static CompletableFuture closeDB() {
        CompletableFuture result = new CompletableFuture();
        campaigns.close();
        result.complete(campaigns);
        return result;
    }

    public static CompletableFuture<java.lang.Iterable<String>> getCampaigns() {
        CompletableFuture<java.lang.Iterable<String>> result = new CompletableFuture<>();
        setupDB().thenRun(new Runnable() {
            @Override
            public void run() {
                if (!campaigns.isClosed()) result.complete(campaigns.getAllNames());
                else result.cancel(true);
            }
        }).thenRun(new Runnable() {
            @Override
            public void run() {
                closeDB();
            }
        });

        return result;
    }

    public static CompletableFuture<Boolean> campaignExists(String campaignName) {
        CompletableFuture<Boolean> result = new CompletableFuture<Boolean>();
        if (campaigns.isClosed()) {
            setupDB().thenRun(new Runnable() {
                @Override
                public void run() {
                    result.complete(campaigns.exists(campaignName));
                }
            }).thenRun(new Runnable() {
                @Override
                public void run() {
                    closeDB();
                }
            });
        }
        else result.complete(campaigns.exists(campaignName));

        return result;
    }

    public static CompletableFuture createNewCampaign(String campaignName) {
        CompletableFuture<Boolean> result = new CompletableFuture<Boolean>();

        setupDB().thenRun(() -> {
            if (!campaigns.isClosed()){
                ConcurrentMap<String, ConcurrentMap> newCampaign = campaigns.hashMap(campaignName, Serializer.STRING, Serializer.ELSA).create();
                ConcurrentMap<String, String> initialSettings = new ConcurrentHashMap<>();
                initialSettings.put("Last Query", "Hello World");
                initialSettings.put("LRU Folder", "SENT");
                initialSettings.put("Subject", "Hello");
                initialSettings.put("Text Fallback", "World");
                initialSettings.put("HTML File Template Path", "");
                initialSettings.put("Attachments", "");
                newCampaign.put("Settings", initialSettings);
                ConcurrentMap<String, Long> initialLastSent = new ConcurrentHashMap<>();
                initialLastSent.put("vlado@gmail.com", 1486156785000L);
                newCampaign.put("Last Sent", initialLastSent);

                try {
                    if (campaignExists(campaignName).get()) {
                        setCampaign(campaignName);
                        result.complete(true);
                    }
                    else {
                        result.cancel(true);
                        System.out.println("Could not create campaign");
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
            else {
                System.out.println("DB is closed!");
                result.cancel(true);
            }
        }).thenRun(() -> closeDB());

        return result;
    }

    public static CompletableFuture deleteCampaign(String campaignName) {
        CompletableFuture<Boolean> result = new CompletableFuture<Boolean>();

        setupDB().thenRun(new Runnable() {
            @Override
            public void run() {
                if (!campaigns.isClosed()){
                    campaigns.delete(campaignName);
                    try {
                        if (!campaignExists(campaignName).get()) {
                            try {
                                FileUtils.deleteDirectory(new java.io.File(System.getProperty("user.home"), String.valueOf(".credentials/" + campaignName)));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            setCampaign(null);
                            result.complete(true);
                        }
                        else {
                            result.cancel(true);
                            System.out.println("Could not delete campaign");
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    System.out.println("DB is closed!");
                    result.cancel(true);
                }
            }
        }).thenRun(new Runnable() {
            @Override
            public void run() {
                closeDB();
            }
        });

        return result;
    }

    public static CompletableFuture<List> getLabelsList() {
        CompletableFuture<List> result = new CompletableFuture<List>();

        try {
            result.complete(
                    service.users().labels().list(USER_ME).execute().getLabels().stream().map(label -> label.getId()).collect(Collectors.toList())
            );
        } catch (IOException e) {
            result.cancel(true);
            e.printStackTrace();
        }

        return result;
    }

    public static CompletableFuture updateLastSentEmailsDateInLocalDB(List<String> emailAddresses) {
        CompletableFuture<ConcurrentMap<String, Long>> result = new CompletableFuture<>();

        setupDB().thenRun(() -> {
            ConcurrentMap <String, ConcurrentMap> campaign = campaigns.hashMap(CAMPAIGN_NAME, Serializer.STRING, Serializer.ELSA).createOrOpen();
            ConcurrentMap <String, Long> lastSendEmailsInDB = campaign.get("Last Sent");
            long time = System.currentTimeMillis();
            emailAddresses.forEach(emailAddress -> {
                lastSendEmailsInDB.put(emailAddress, time);
            });
            result.complete(campaign.put("Last Sent", lastSendEmailsInDB));
        }).thenRun(() -> closeDB());

        return result;
    }

    public static CompletableFuture<ConcurrentMap<String, Long>> getLastSentEmailsFromLocalDB() {
        CompletableFuture<ConcurrentMap<String, Long>> result = new CompletableFuture<>();

        setupDB().thenRun(() -> {
            ConcurrentMap <String, ConcurrentMap> campaign = campaigns.hashMap(CAMPAIGN_NAME, Serializer.STRING, Serializer.ELSA).createOrOpen();
            result.complete(campaign.get("Last Sent"));
        }).thenRun(() -> closeDB());

        return result;
    }

    public static CompletableFuture setSettings(String lastQuery, String lastRecentlyUsedFolder, String subject, String textFallBack, String htmlFileTemplatePath, String attachments) {
        CompletableFuture<Boolean> result = new CompletableFuture<Boolean>();

        setupDB().thenRun(() -> {
            if (!campaigns.isClosed()){
                ConcurrentMap<String, ConcurrentMap> campaign = campaigns.hashMap(CAMPAIGN_NAME, Serializer.STRING, Serializer.ELSA).createOrOpen();
                ConcurrentMap<String, String> oldSettings = campaign.get("Settings");
                ConcurrentMap<String, String> newSettings = new ConcurrentHashMap<>();

                if(lastQuery != null) newSettings.put("Last Query", lastQuery);
                else newSettings.put("Last Query", oldSettings.getOrDefault("Last Query", ""));

                if (lastRecentlyUsedFolder != null) newSettings.put("LRU Folder", lastRecentlyUsedFolder);
                else newSettings.put("LRU Folder", oldSettings.getOrDefault("LRU Folder", "SENT"));

                if (subject != null) newSettings.put("Subject", subject);
                else newSettings.put("Subject", oldSettings.getOrDefault("Subject", ""));

                if (textFallBack != null) newSettings.put("Text Fallback", textFallBack);
                else newSettings.put("Text Fallback", oldSettings.getOrDefault("Text Fallback", ""));


                if (htmlFileTemplatePath != null) newSettings.put("HTML File Template Path", htmlFileTemplatePath);
                else newSettings.put("HTML File Template Path", oldSettings.getOrDefault("HTML File Template Path", ""));

                if (attachments != null) newSettings.put("Attachments", attachments);
                else newSettings.put("Attachments", oldSettings.getOrDefault("Attachments", ""));

                campaign.put("Settings", newSettings);
                result.complete(true);
            }
            else {
                System.out.println("DB is closed!");
                result.cancel(true);
            }
        }).thenRun(() -> closeDB());

        return result;
    }

    public static CompletableFuture<ConcurrentMap<String, String>> getSettings() {
        CompletableFuture<ConcurrentMap<String, String>> result = new CompletableFuture<>();

        setupDB().thenRun(() -> {
            ConcurrentMap <String, ConcurrentMap> campaign = campaigns.hashMap(CAMPAIGN_NAME, Serializer.STRING, Serializer.ELSA).createOrOpen();
            result.complete(campaign.get("Settings"));
        }).thenRun(() -> closeDB());

        return result;
    }

    public static CompletableFuture<List<String>> getEmailsByQueryInRemote(String query, String folder) {
        CompletableFuture<List<String>> result = new CompletableFuture<>();
        try {
            List<String> labels = new ArrayList<>();
            labels.add(folder);
            List<String> requiredHeaders = new ArrayList<>();
            requiredHeaders.add("To");
            requiredHeaders.add("Cc");
            requiredHeaders.add("Bcc");
            ListMessagesResponse response = service.users().messages().list(USER_ME).setLabelIds(labels).setQ(query).execute();
            List<Message> messages = new ArrayList<Message>();
            while (response.getMessages() != null) {
                messages.addAll(response.getMessages());
                if (response.getNextPageToken() != null) {
                    String pageToken = response.getNextPageToken();
                    response = service.users().messages().list(USER_ME).setLabelIds(labels).setQ(query).setPageToken(pageToken).execute();
                } else {
                    break;
                }
            }
            List<String> extractedEmails = messages.stream().map(message -> {
                List<MessagePartHeader> header = new ArrayList<>();
                try {
                    /*
                        [{"name":"Bcc","value":"Volodymyr Katkalov <valut.dev@gmail.com>"}, {"name":"To","value":"Vladimir Katkalov <vladimir@katkalov.me>"}, {"name":"Cc","value":"vladimirkatkalov@icloud.com"}]
                        [{"name":"To","value":"Vladimir Katkalov <vladimir@katkalov.me>"}]
                     */
                    header = service.users().messages().get(USER_ME, message.getId()).setFormat("metadata").setMetadataHeaders(requiredHeaders).execute().getPayload().getHeaders();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return header;
            }).flatMap(header -> header.stream()).
                    map(object -> object.getValue())
                    .distinct()
                    .map(unextractedAddress -> {
                        Matcher matcher = emailPattern.matcher(unextractedAddress);
                        String email = matcher.find() ? matcher.group(0) : "";
                        if (email.equals("")) return "";
                        if (EmailValidator.getInstance().isValid(email)) return email;
                        else return "";
                    })
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .collect(Collectors.toList());
            result.complete(extractedEmails);
        } catch (GoogleJsonResponseException e) {
            System.out.println("Insufficient permissions: " + e);
            result.cancel(true);
        } catch (IOException e) {
            e.printStackTrace();
            result.cancel(true);
        }

        return result;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                 CampaignSelectOrCreateWindow campaignSelectOrCreateWindow = new CampaignSelectOrCreateWindow();
                 campaignSelectOrCreateWindow.setVisible(true);
            }
        });
    }

    /**
     * Create a MimeMessage using the parameters provided.
     *
     * @param to Email address of the receiver.
     * @param from Email address of the sender, the mailbox account.
     * @param subject Subject of the email.
     * @param bodyText Body text of the email.
     * @param file Path to the file to be attached.
     * @return MimeMessage to be used to send email.
     * @throws MessagingException
     */
    public static MimeMessage createEmailWithAttachment(String to,
                                                        String from,
                                                        String subject,
                                                        String bodyText,
                                                        File file)
            throws MessagingException, IOException, javax.mail.MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress(from));
        email.addRecipient(javax.mail.Message.RecipientType.TO,
                new InternetAddress(to));
        email.setSubject(subject);

        MimeBodyPart mimeBodyPart = new MimeBodyPart();
        mimeBodyPart.setContent(bodyText, "text/plain");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(mimeBodyPart);

        mimeBodyPart = new MimeBodyPart();
        DataSource source = new FileDataSource(file);

        mimeBodyPart.setDataHandler(new DataHandler(source));
        mimeBodyPart.setFileName(file.getName());

        multipart.addBodyPart(mimeBodyPart);
        email.setContent(multipart);

        return email;
    }
}
