package pt.utl.ist.tlo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * A thread-escaped-fields (tef) file.
 * <p>
 * Contains a list of fields that have been classified as being thread-escaped
 * by the TLO analysis.
 */
public final class TefFile {

  /** Tef file name. */
  private String filename;

  /** Tef file handle. */
  private File file;

  /** Tef file stream. */
  private BufferedWriter stream;

  /**
   * Constructor.
   * @param  filename tef file name
   */
  public TefFile(String filename) {
    this.filename = filename;
  }

  /**
   * Initializes the tef file for writing.
   * @throws TefFileException if the file cannot be opened or an I/O error
   *                          occurs
   */
  public void init() throws TefFileException {
    try {
      file = new File(filename);
      stream = new BufferedWriter(new FileWriter(file));
    } catch (FileNotFoundException fnfe) {
      throw new TefFileException("Unable to open tef file.", fnfe);
    } catch (IOException ioe) {
      throw new TefFileException("Failed to write to tef file.", ioe);
    }
  }

  /**
   * Fills the tef file with the thread-escaped-fields in a given list.
   * @param  fields           list of thread-escaped-fields
   * @throws TefFileException if an I/O error occurs
   */
  public void fillWith(List<ThreadEscapedField> fields)
                throws TefFileException {
    try {
      for (ThreadEscapedField f : fields) {
        stream.write(f.toString());
        stream.newLine();
      }
    } catch (IOException ioe) {
      throw new TefFileException("Failed to write to tef file.", ioe);
    }
  }

  /**
   * Closes the tef file.
   */
  public void terminate() {
    try {
      stream.close();
    } catch (IOException ioe) { }
  }
}
