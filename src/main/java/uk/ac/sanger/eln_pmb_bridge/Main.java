package uk.ac.sanger.eln_pmb_bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ELN PMB Bridge is an application that polls files from (current: web-cgap-idbstest-01)
 * Builds a print request from the file
 * Sends a print job request to PrintMyBarcode to print requested labels
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(PrintConfig.class);
    private static final PropertiesFileReader properties = new PropertiesFileReader();

    public static void main(String[] args) throws Exception {
        /**
         * When a host has both IPv4 and IPv6 addresses, change preference to use IPv6 addresses over IPv4
         */
        System.setProperty("java.net.preferIPv6Addresses", "true");

        try {
            properties.loadProperties();
            sendStartUpEmail();
            startService();
        } catch (Exception e) {
            log.error("Fatal error", e);
            sendErrorEmail("ELN PMB Bridge - fatal error", e);
        }
    }

    private static void startService() throws Exception {
        PrintRequestHelper printRequestHelper = new PrintRequestHelper();
        Path pollPath = properties.getPollFolderPath();
        PrintConfig printConfig = PrintConfig.loadConfig(properties);
        /**
         * A new WatchService monitors the polling folder specified in eln_pmb.properties
         * The polling directory is registered to watch for entry create events
         * When any create event is detected, a key is added to the watch service que
         * The take method returns the watch key from the que
         * The event is processed and the newly created filename returned
         */
        WatchService service = pollPath.getFileSystem().newWatchService();
        pollPath.register(service, StandardWatchEventKinds.ENTRY_CREATE);

        while (true) {
            WatchKey watchKey = service.take();
            List<WatchEvent<?>> watchEvents = watchKey.pollEvents();

            for (WatchEvent event : watchEvents) {
                String newFileName = event.context().toString();

                try {
                    File file = properties.findFile(newFileName);
                    printRequestHelper.loadPrinters(properties.getPrinters());
                    PrintRequest request = printRequestHelper.makeRequestFromFile(file);
                    PMBClient pmbClient = new PMBClient(printConfig);
                    pmbClient.print(request);
                    properties.moveFileToFolder(newFileName, properties.getArchiveFolder());
                } catch (Exception e) {
                    log.error("Recoverable error", e);
                    sendErrorEmail("ELN PMB Bridge - recoverable error", e);
                    properties.moveFileToFolder(newFileName, properties.getErrorFolder());
                }
            }
            watchKey.reset();
        }
    }

    private static void sendStartUpEmail() {
        String currentTime = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
        String message = String.format("Starting up ELN PMB Bridge Service at %s", currentTime);
        sendEmail("Starting up ELN PMB Bridge Service", message);
    }

    private static void sendErrorEmail(String subject, Exception e) {
        String message = String.format("Error when trying to print labels via PrintMyBarcode from an polled ELN file. " +
                "Moving file to error folder. %s", e);
        sendEmail(subject, message);
    }

    private static void sendEmail(String subject, String message) {
        try {
            EmailService emailManager = new EmailService(properties.getMailProperties());
            emailManager.sendEmail(subject, message);
        } catch (Exception e){
            log.error(String.format("Failed to send email with subject %s", subject));
        }
    }

}