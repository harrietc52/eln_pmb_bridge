package uk.ac.sanger.eln_pmb_bridge;

/**
 * A printer service interface, implemented currently by PMBClient.
 */
public interface PrintService {

    void print(PrintRequest request) throws Exception;

}
