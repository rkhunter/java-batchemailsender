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
import com.google.api.services.gmail.model.Message;
import models.Utils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
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

    static void setCampaign(String campaignName) {
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

    private static CompletableFuture<Credential> authorize() {
        CompletableFuture<Credential> result = new CompletableFuture<>();
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

    private static CompletableFuture<Gmail> initializeGmailService() {
        CompletableFuture<Gmail> result = new CompletableFuture<>();

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
        } catch (InterruptedException | ExecutionException e) {
            result.cancel(true);
            e.printStackTrace();
        }

        return result;
    }

    static CompletableFuture<Boolean> setupAuth() {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
            initializeGmailService().thenApply(_service -> {
                service = _service;
                result.complete(null);
                return null;
            });

        return result;
    }

    private static CompletableFuture<DB> setupDB() {
        CompletableFuture<DB> result = new CompletableFuture<>();
        campaigns = DBMaker.fileDB("campaigns.db").fileMmapEnableIfSupported().make();
        result.complete(campaigns);
        return result;
    }

    private static CompletableFuture closeDB() {
        CompletableFuture<DB> result = new CompletableFuture<>();
        campaigns.close();
        result.complete(campaigns);
        return result;
    }

    static CompletableFuture<java.lang.Iterable<String>> getCampaigns() {
        CompletableFuture<java.lang.Iterable<String>> result = new CompletableFuture<>();
        setupDB()
                .thenRun(() -> {
                    if (!campaigns.isClosed()) result.complete(campaigns.getAllNames());
                    else result.cancel(true);
                })
                .thenRun(BatchEmailSender::closeDB);

        return result;
    }

    static CompletableFuture<Boolean> campaignExists(String campaignName) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (campaigns.isClosed()) {
            setupDB()
                    .thenRun(() -> result.complete(campaigns.exists(campaignName)))
                    .thenRun(BatchEmailSender::closeDB);
        }
        else result.complete(campaigns.exists(campaignName));

        return result;
    }

    static CompletableFuture<Boolean> createNewCampaign(String campaignName) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        setupDB().thenRun(() -> {
            if (!campaigns.isClosed()){
                //ConcurrentMap<String, ConcurrentMap> newCampaign = campaigns.hashMap(campaignName, Serializer.STRING, Serializer.ELSA).create();
                ConcurrentMap<String, ConcurrentMap> newCampaign = campaigns.hashMap(campaignName, String.class, ConcurrentMap.class).create();
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
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
            else {
                System.out.println("DB is closed!");
                result.cancel(true);
            }
        }).thenRun(BatchEmailSender::closeDB);

        return result;
    }

    static CompletableFuture<Boolean> deleteCampaign(String campaignName) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        setupDB().thenRun(() -> {
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
                        } catch (InterruptedException | ExecutionException e) {
                            e.printStackTrace();
                        }
                    }
                    else {
                        System.out.println("DB is closed!");
                        result.cancel(true);
                    }
        }).thenRun(BatchEmailSender::closeDB);

        return result;
    }

    static CompletableFuture<List<String>> getLabelsList() {
        CompletableFuture<List<String>> result = new CompletableFuture<>();

        try {
            result.complete(
                    service.users().labels().list(USER_ME).execute().getLabels().stream().map(Label::getId).collect(Collectors.toList())
            );
        } catch (IOException e) {
            result.cancel(true);
            e.printStackTrace();
        }

        return result;
    }

    static CompletableFuture<? extends Boolean> updateLastSentEmailsDateInLocalDB(List<String> emailAddresses) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        setupDB().thenRun(() -> {
            ConcurrentMap <String, ConcurrentMap> campaign = campaigns.hashMap(CAMPAIGN_NAME, String.class, ConcurrentMap.class).createOrOpen();
            ConcurrentMap <String, Long> lastSendEmailsInDB = campaign.get("Last Sent");
            long time = System.currentTimeMillis();
            emailAddresses.forEach(emailAddress -> {
                lastSendEmailsInDB.put(emailAddress, time);
            });
            campaign.put("Last Sent", lastSendEmailsInDB);
            result.complete(true);
        }).thenRun(BatchEmailSender::closeDB);

        return result;
    }

    static CompletableFuture<ConcurrentMap<String, Long>> getLastSentEmailsFromLocalDB() {
        CompletableFuture<ConcurrentMap<String, Long>> result = new CompletableFuture<>();

        setupDB().thenRun(() -> {
            ConcurrentMap <String, ConcurrentMap> campaign = campaigns.hashMap(CAMPAIGN_NAME, String.class, ConcurrentMap.class).createOrOpen();
            result.complete(campaign.get("Last Sent"));
        }).thenRun(BatchEmailSender::closeDB);

        return result;
    }

    static CompletableFuture<Boolean> setSettings(String lastQuery, String lastRecentlyUsedFolder, String subject, String textFallBack, String htmlFileTemplatePath, String attachments) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        setupDB().thenRun(() -> {
            if (!campaigns.isClosed()){
                ConcurrentMap<String, ConcurrentMap> campaign = campaigns.hashMap(CAMPAIGN_NAME, String.class, ConcurrentMap.class).createOrOpen();
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
        }).thenRun(BatchEmailSender::closeDB);

        return result;
    }

    static CompletableFuture<ConcurrentMap<String, String>> getSettings() {
        CompletableFuture<ConcurrentMap<String, String>> result = new CompletableFuture<>();

        setupDB().thenRun(() -> {
            ConcurrentMap <String, ConcurrentMap> campaign = campaigns.hashMap(CAMPAIGN_NAME, String.class, ConcurrentMap.class).createOrOpen();
            result.complete(campaign.get("Settings"));
        }).thenRun(BatchEmailSender::closeDB);

        return result;
    }

    static CompletableFuture<List<String>> getEmailsByQueryInRemote(String query, String folder) {
        CompletableFuture<List<String>> result = new CompletableFuture<>();
        try {
            List<String> labels = new ArrayList<>();
            labels.add(folder);
            List<String> requiredHeaders = new ArrayList<>();
            requiredHeaders.add("To");
            requiredHeaders.add("Cc");
            requiredHeaders.add("Bcc");
            ListMessagesResponse response = service.users().messages().list(USER_ME).setLabelIds(labels).setQ(query).execute();
            List<Message> messages = new ArrayList<>();
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
                    header = service.users().messages().get(USER_ME, message.getId()).setFormat("metadata").setMetadataHeaders(requiredHeaders).execute().getPayload().getHeaders();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return header;
            }).flatMap(Collection::stream).
                    map(MessagePartHeader::getValue)
                    .distinct()
                    .map(notExtractedAddress -> {
                        Matcher matcher = emailPattern.matcher(notExtractedAddress);
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

    private static Message createMessageWithEmail(MimeMessage emailContent)
            throws MessagingException, IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = org.apache.commons.codec.binary.Base64.encodeBase64URLSafeString(bytes);
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    static MimeMessage createEmailWithAttachment(String to, String subject, File htmlFilePath, String bodyText, File[] files)
            throws MessagingException, IOException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress("sergiykat@gmail.com"));
        email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject);

        /* Everything below is done in two rounds:
         *  Round 1:
         *      Multipart/Alternative is created. Text/Plain and Text/HTMl are placed in it.
         *  Round 2:
         *      Multipart/Mixed is created. Round 1 is packed and is added to Multipart/Mixed. Then, all attachments are added to Multipart/Mixed.
         * Schema:
         * [multipart/mixed] ──┬──> [multipart/alternative] ──┬──> [text/plain]
         *                     ├──> [attachment 1]            └──> [text/html]
         *                     ├──> [...]
         *                     └──> [attachment n]
         *
         */

        Multipart prettyHtmlChild = new MimeMultipart("alternative");

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setContent(bodyText, "text/plain; charset=utf-8");
        prettyHtmlChild.addBodyPart(textPart);

        if (htmlFilePath != null && htmlFilePath.exists()) {
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(new String(Files.readAllBytes(Paths.get(htmlFilePath.getAbsolutePath()))), "text/html; charset=utf-8");
            prettyHtmlChild.addBodyPart(htmlPart);
        }

        MimeBodyPart packed = new MimeBodyPart();
        packed.setContent(prettyHtmlChild);

        Multipart parent = new MimeMultipart();
        parent.addBodyPart(packed);



        // Attachments
        Arrays.stream(files).forEach(file -> {
            final MimeBodyPart _mimeBodyPart = new MimeBodyPart();
            DataSource source = new FileDataSource(file);

            try {
                _mimeBodyPart.setDataHandler(new DataHandler(source));
                _mimeBodyPart.setFileName(file.getName());

                parent.addBodyPart(_mimeBodyPart);
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        });

        email.setContent(parent);

        return email;
    }

    static Message sendMessage(MimeMessage emailContent)
            throws MessagingException, IOException {
        Message message = createMessageWithEmail(emailContent);
        message = service.users().messages().send(USER_ME, message).execute();

        System.out.println("Message id: " + message.getId());
        System.out.println(message.toPrettyString());
        return message;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            CampaignSelectOrCreateWindow campaignSelectOrCreateWindow = new CampaignSelectOrCreateWindow();
            campaignSelectOrCreateWindow.setVisible(true);
        });
    }
}
