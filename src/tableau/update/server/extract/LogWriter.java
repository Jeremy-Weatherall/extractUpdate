package tableau.update.server.extract;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogWriter {

	private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	BufferedWriter bw = null;
	FileWriter fw = null;

	public LogWriter(String logFile) {
		super();
		logWriter(logFile,  false);
	}
	
	public LogWriter(String logFile, boolean append) {
		super();
		logWriter(logFile,  append);

	}

	

  private void logWriter(String logFile, boolean append) {
		try {
			File _logFile = new File(logFile);
			if (!_logFile.exists()) {
				_logFile.createNewFile();
			}
			this.fw = new FileWriter(_logFile.getAbsoluteFile(), append);
			this.bw = new BufferedWriter(fw);

		} catch (IOException e) {

			e.printStackTrace();

		}
	}



	public void WriteToLog(String text) {
		WriteToLog(text, false);
	}

	public void WriteToLog(String text, boolean sysOutOnly) {

		try {
			String _local = dateFormat.format(new Date()) + ": " + text;
			if (!sysOutOnly) {
				bw.write(_local + "\n");//
				bw.flush();
			}
			System.out.println(_local);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	
	public void writeToCSVFile(StringBuffer sb) {
		
		try {
			bw.write(sb.toString() );
			bw.flush();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}//
		
			
		
		
		
	}

}
