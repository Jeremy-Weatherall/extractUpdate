package tableau.update.server.extract;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;



public class ExtractUpdate extends ServerContent {

	static private Properties s_properties = new Properties();
	static TableauRest tr = null;;

	static {

		// Loads the values from configuration file into the Properties instance
		try {
			
			// Relative path
			s_properties.load(new FileInputStream("config/config.properties"));
		} catch (IOException e) {

		}
	}

	private TableauRest getTableauRest() {

		if (tr == null)
			tr = new TableauRest(this);
		return tr;

	}

	public static void main(String[] args) {

		// if passed in an arg for the property file, then use this
		try {
			if (args.length > 0) {
				try {
					s_properties.load(new FileInputStream(args[0]));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					System.out.println(
							"Error finding property file pass in on cmd line <" + args[0] + ">: " + e.getMessage());
					return;
				}
			}

			ExtractUpdate me = new ExtractUpdate();

			me.setUserName(s_properties.getProperty("user.admin.name"));
			me.setServerURL(s_properties.getProperty("server.host"));
			me.setApiVersion(s_properties.getProperty("server.api.version"));
			me.setUserPassword(s_properties.getProperty("user.admin.password"));
			me.setSiteName(s_properties.getProperty("site.default.contentUrl"));
			me.setProjectName(s_properties.getProperty("hyper.datasource.project"));
			
			me.setPATName(s_properties.getProperty("user.tokenName"));
			me.setPATSecret(s_properties.getProperty("user.tokenSecret"));
			me.setPATuse(s_properties.getProperty("user.usePAT").toLowerCase().equals("true"));


			me.getTableauRest();

			
			System.out.println(
						"Uploading <" + s_properties.getProperty("datasource.sample.path") + "> to Tableau Server....");
			me.publishExtractToServer();
			

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Error e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}


	private void publishExtractToServer() throws Exception, Error {
		
		
		//set default size to 3mg
		long chunkSize=3000000;
		try {
			chunkSize =   Long.parseLong(   s_properties.getProperty("server.chunk.upload.byte.size"));
			//max is 64 mg
			if (chunkSize>64000000)
				chunkSize=64000000;
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		/*
		 * Hard coded updating an existing published data source that contains a hyper file with only one Table
		 * Also hard coded that we are going to upload this in chunks, even if the file fits in the first chunk!
		 * 
		 */
			tr.updateDatasource_ExtractChunked(s_properties.getProperty("datasource.sample.path"), s_properties.getProperty("datasource.tableauserver.urlname"),
					 s_properties.getProperty("datasource.tableauserver.action").toLowerCase()
					,s_properties.getProperty("datasource.LOCAL.hyper.table.name"),
					 s_properties.getProperty("datasource.LOCAL.hyper.schema.name")
					,s_properties.getProperty("datasource.server.hyper.table.name"),
					 s_properties.getProperty("datasource.server.hyper.schema.name"),chunkSize);
	
			System.out.println("Completed upload.............");
	}

}