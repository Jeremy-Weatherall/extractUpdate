package tableau.update.server.extract;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.CharacterIterator;
import java.text.MessageFormat;
import java.text.StringCharacterIterator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.core.MediaType;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.multipart.BodyPart;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.MultiPart;
import com.sun.jersey.multipart.MultiPartMediaTypes;
import com.sun.jersey.multipart.file.FileDataBodyPart;

/**
 * @author jweatherall
 *
 */
public class TableauRest {

	
	/*
	 * Contains the REST URL construction, values enclosed in {} are replaced at run time
	 * API version to use, and Server URL are defined in the config.properties file
	 */
	public enum APIURL {
		api_signin("auth/signin"), 
		api_signinBody("'{\"credentials\": {\"name\": \"'{0}\",\"password\": \"{1}'\", " + " \"site\": {\r\n"
				+ "\"contentUrl\": \"\'{2}\"'}}}'"),
		api_signin_PAT_Body("'{\"credentials\": {\"personalAccessTokenName\": \"'{0}\",\"personalAccessTokenSecret\": \"{1}'\", " + " \"site\": {\r\n"
				+ "\"contentUrl\": \"\'{2}\"'}}}'"),
		
		api_hyperUpdateDataForSingleHyper("sites/{0}/datasources/{1}/data?uploadSessionId={2}"),
		api_HyperDatasourceReplaceBODY(
				"{~actions~: [{~action~: ~{0}~, ~source-table~: ~{1}~,  ~target-table~: ~{2}~, ~source-schema~: ~{3}~, ~target-schema~: ~{4}~ }]}"),
		
		api_FileUploadInitiate("sites/{0}/fileUploads"), 
		api_FileUploadAppendTo("sites/{0}/fileUploads/{1}"),
		api_getDatasourceID("sites/{0}/datasources?filter=name:eq:{1}") 
		
		;

		private String url;

		APIURL(String envUrl) {
			this.url = envUrl;
		}

		public String getUrl() {
			return url;
		}
	}

	private final String TABLEAU_AUTH_HEADER = "X-Tableau-Auth";
	private final String TABLEAU_PAYLOAD_NAME = "request_payload";

	private ExtractUpdate parent;

	private static LogWriter lw = new LogWriter("Tableau.log");

	
	public TableauRest(ExtractUpdate parent) {
		super();
		this.parent = parent;

	}

	
	public void updateDatasource_ExtractChunked( String pathToExtract, String dataSourceName, String sqlAction,
			 String source_table, String source_schema, String target_table, String target_schema,long chunkSize)
			throws Exception, Error {
		
		
		login();
		String dataSourceID=getDatasourceID(dataSourceName);
		
			
		parent.setFileUploadID(invokeInitiateFileUpload());
		File workbookFile = new File(pathToExtract);

		// Creates a buffer to read chunks 
		byte[] buffer = new byte[(int) chunkSize];
		int numReadBytes = 0;

		// Reads the specified workbook and appends each chunk to the file upload
		int chunk = 1;
		try (FileInputStream inputStream = new FileInputStream(workbookFile.getAbsolutePath())) {
			while ((numReadBytes = inputStream.read(buffer)) != -1) {
				invokeAppendFileUpload(buffer, numReadBytes, chunk++);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read the hyper file.");
		}
		
		

		//now do final call telling Server to Update Hyper file in DS
		String localURL = parent.getUrlAndAPI() + APIURL.api_hyperUpdateDataForSingleHyper.getUrl();
		localURL = MessageFormat.format(localURL, parent.getSiteID(), dataSourceID,   parent.getFileUploadID());
		
		String body=APIURL.api_HyperDatasourceReplaceBODY.getUrl();
		//
		List<String> tableValues = new ArrayList<String>();
	
		tableValues.add(sqlAction);
		tableValues.add(source_table);
		tableValues.add(target_table);
		tableValues.add(source_schema);
		tableValues.add(target_schema);
		
		int i=0;
		for (Iterator<String> iterator = tableValues.iterator(); iterator.hasNext();) {
			String string = iterator.next();
			body=body.replace("{" + i++ + "}", string);
		}
		body=body.replaceAll("~", "\"");
		
		//now call the REST API PATCH method
		responseErrorCheck(
				useNewHTTP_PATCH(localURL,  parent.getToken(), body)
		);
		
		lw.WriteToLog("dataSource size: " + humanReadableByteCountBin(workbookFile.length()));
	
	}
	



	/**
	 * Invokes an HTTP request to append to target file upload on target site.
	 * 
	 * @param chunk         the chunk of data to append to target file upload
	 * @param numChunkBytes the number of bytes in the chunk of data
	 * @throws Error
	 * @throws Exception
	 */
	private void invokeAppendFileUpload(byte[] chunk, int numChunkBytes, int uploadCount) throws Exception, Error {

		lw.WriteToLog(String.format("Appending to file upload '%s'.", parent.getFileUploadID()));

		String url = parent.getUrlAndAPI() + APIURL.api_FileUploadAppendTo.getUrl();
		url = MessageFormat.format(url, parent.getSiteID(), parent.getFileUploadID());

		// Writes the chunk of data to a temporary file
		try (FileOutputStream outputStream = new FileOutputStream("appendFileUpload.temp")) {
			outputStream.write(chunk, 0, numChunkBytes);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to create temporary file to append to file upload");
		}

		// Makes a multipart PUT request with specified credential's
		// authenticity token and payload
		BodyPart filePart = new FileDataBodyPart("tableau_file", new File("appendFileUpload.temp"),
				MediaType.APPLICATION_OCTET_STREAM_TYPE);

		/*
		 * <tsResponse version-and-namespace-settings > <fileUpload
		 * uploadSessionId="13253:6744F321974F4E8B8EC1424A3D56E0EA-0:0" fileSize="1"/>
		 * </tsResponse>
		 */

		String json = postOrPutMultipartLazy(url, parent.getToken(), "", filePart, false);

		JSONParser jsonParser = new JSONParser();
		JSONObject jsonObject = (JSONObject) jsonParser.parse(json);

		lw.WriteToLog(uploadCount + ">Uploaded extract MG: "
				+ (String) ((JSONObject) jsonObject.get("fileUpload")).get("fileSize"));

	}

	


	private String getDatasourceID(String datasourceName) throws UnsupportedEncodingException {

		// get project ID
		String localURL = parent.getUrlAndAPI() + APIURL.api_getDatasourceID.getUrl();
		//catch spaces and non URL standard characters in project name
		localURL = MessageFormat.format(localURL, parent.getSiteID(), URLEncoder.encode(datasourceName,StandardCharsets.UTF_8.toString()));

		// {"pagination":{"pageNumber":"1","pageSize":"100","totalAvailable":"1"},"projects":{"project":[{"owner":{"id":"cbb9dcd9-e93d-4827-9561-c1f7ad6b891f"},"id":"809f94b1-0e26-4707-b574-b21f26fd8b85","name":"default","description":"The
		// default project that was automatically created by
		// Tableau.","createdAt":"2017-06-05T12:47:53Z","updatedAt":"2017-12-14T14:11:24Z","contentPermissions":"LockedToProject"}]}}

		try {

			JSONObject jsonObject = getServerContentID(localURL, parent.getToken());

			if (((JSONObject) jsonObject.get("pagination")).get("totalAvailable").equals("0"))
				throw new Error("Datasource <" + datasourceName + "> was not found");

			JSONObject child = (JSONObject) jsonObject.get("datasources");
			JSONArray childArray = (JSONArray) child.get("datasource");
			 return ((String) ((JSONObject) childArray.get(0)).get("id"));
		} catch (Exception | Error e) {

			throw new Error("Error in getting Project ID: " + e.getMessage());

		}

	}
	
	
	private String humanReadableByteCountBin(long bytes) {
		if (-1000 < bytes && bytes < 1000) {
			return bytes + " B";
		}
		CharacterIterator ci = new StringCharacterIterator("kMGTPE");
		while (bytes <= -999_950 || bytes >= 999_950) {
			bytes /= 1000;
			ci.next();
		}
		return String.format("%.1f %cB", bytes / 1000.0, ci.current());
	}

	

	private String postOrPutMultipartLazy(String url, String authToken, String requestPayload, BodyPart filePart,
			boolean bPost) throws Exception, Error {

		if (requestPayload != null && !requestPayload.equals(""))
			lw.WriteToLog("Input payload: \n" + requestPayload);

		// Creates the XML request payload portion of the multipart request
		BodyPart payloadPart = new FormDataBodyPart(TABLEAU_PAYLOAD_NAME, requestPayload);

		// Creates the multipart object and adds the file portion of the
		// multipart request to it
		MultiPart multipart = new MultiPart();
		multipart.bodyPart(payloadPart);

		if (filePart != null) {
			multipart.bodyPart(filePart);
		}

		// Creates the HTTP client object and makes the HTTP request to the
		// specified URL
		Client client = Client.create();
		WebResource webResource = client.resource(url);

		ClientResponse clientResponse;

		if (bPost)
			clientResponse = webResource.header(TABLEAU_AUTH_HEADER, authToken).header("Accept", "application/json")
					.type(MultiPartMediaTypes.createMixed()).post(ClientResponse.class, multipart);
		else
			clientResponse = webResource.header(TABLEAU_AUTH_HEADER, authToken).header("Accept", "application/json")
					.type(MultiPartMediaTypes.createMixed()).put(ClientResponse.class, multipart);

		return responseErrorCheck(clientResponse);

	}

	private JSONObject getServerContentID(String l_url, String token) throws Exception, Error {

		HttpResponse<String> clientResponse = genericGetorPost(l_url, null, token, true,"");
		

		// checks for errors and if OK, returns payload in JSON format
		String json = responseErrorCheck(clientResponse);

		JSONParser jsonParser = new JSONParser();
		Object object = jsonParser.parse(json);
		return (JSONObject) object;

	}

	private HttpResponse<String> genericGetorPost(String l_url, String payload, String token, boolean outputToPrintln, String mediaType) throws IOException, InterruptedException {
		
		if (mediaType.equals(""))
			mediaType=MediaType.APPLICATION_XML;
	
		
		if (outputToPrintln && payload != null)
			lw.WriteToLog("Input payload: \n" + payload);

	
	  
		if (payload != null )
			//clientResponse = webResource.header(TABLEAU_AUTH_HEADER, token).header("Accept", "application/json")
				//	.header("Content-type", "application/json").type(mediaType).post(ClientResponse.class, payload);
		
			return useNewHTTP_POST(l_url,payload, token); 
		
		else
			//clientResponse = webResource.header(TABLEAU_AUTH_HEADER, token).header("Accept", "application/json")
			//		.header("Content-type", "application/json").get(ClientResponse.class);
		
			return useNewHTTP_GET(l_url, token);
		

	}
	
	
	

	private String responseErrorCheck(ClientResponse clientResponse) throws Exception, Error {

		try {

			String json = clientResponse.getEntity(String.class);
			lw.WriteToLog(json, true);

			JSONParser jsonParser = new JSONParser();
			JSONObject jsonObject = (JSONObject) jsonParser.parse(json);

			if (clientResponse.getStatus() != 200) {
				jsonObject = (JSONObject) jsonObject.get("error");
				// confirm not null
				if (jsonObject != null) {
					lw.WriteToLog((String) jsonObject.get("detail") + ". Code:" + (String) jsonObject.get("code"));
					throw new Error((String) jsonObject.get("detail") + ". Code:" + (String) jsonObject.get("code"));
				}
			}
			return json;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		return null;
	}
	
	
	private String responseErrorCheck(HttpResponse<String> clientResponse) throws Exception, Error {

		try {

			String json = clientResponse.body();
			lw.WriteToLog(json, true);

			JSONParser jsonParser = new JSONParser();
			JSONObject jsonObject = (JSONObject) jsonParser.parse(json);

			if (clientResponse.statusCode() != 200) {
				jsonObject = (JSONObject) jsonObject.get("error");
				// confirm not null
				if (jsonObject != null) {
					lw.WriteToLog((String) jsonObject.get("detail") + ". Code:" + (String) jsonObject.get("code"));
					throw new Error((String) jsonObject.get("detail") + ". Code:" + (String) jsonObject.get("code"));
				}
			}
			return json;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		return null;
	}

	private void login() throws Exception, Error {

		if (!parent.getToken().isEmpty())
			return;
		lw.WriteToLog("logging in");

		/*
		 * { "credentials": { "name": "admin", "password": "p@ssword", "site": {
		 * "contentUrl": "MarketingTeam" } }
		 */
		
		//see if using Personal Access Tokens(PAT) or regular signin
		String payload="";
		if (parent.isPATuse())
		{
			payload = APIURL.api_signin_PAT_Body.getUrl();
			payload = MessageFormat.format(payload, parent.getPATName(), parent.getPATSecret(), parent.getSiteName());
		}
		else 
		{
			payload = APIURL.api_signinBody.getUrl();
			payload = MessageFormat.format(payload, parent.getUserName(), parent.getUserPassword(), parent.getSiteName());
		}
		
		HttpResponse<String> clientResponse=   genericGetorPost(parent.getUrlAndAPI() + APIURL.api_signin.getUrl(),payload,"",false, "application/json");
	
		// checks for errors and if OK, returns payload in JSON format
		String json = responseErrorCheck(clientResponse);

		try {

			JSONParser jsonParser = new JSONParser();
			JSONObject jsonObject = (JSONObject) jsonParser.parse(json);

			jsonObject = (JSONObject) jsonObject.get("credentials");
			parent.setToken((String) jsonObject.get("token"));
			parent.setUserID((String) ((JSONObject) jsonObject.get("user")).get("id"));
			parent.setSiteID((String) ((JSONObject) jsonObject.get("site")).get("id"));

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	

	private String invokeInitiateFileUpload() throws Exception, Error {

		String url = APIURL.api_FileUploadInitiate.getUrl();
		url = MessageFormat.format(url, parent.getSiteID());
		url = parent.getUrlAndAPI() + url;


		HttpResponse<String> cr = genericGetorPost(url, "", parent.getToken(), false,"");
		String json = responseErrorCheck(cr);

		/*
		 * <tsResponse> <fileUpload uploadSessionId=new-upload-session-id fileSize=0 />
		 * </tsResponse>
		 */

		JSONParser jsonParser = new JSONParser();
		JSONObject jsonObject = (JSONObject) jsonParser.parse(json);

		return (String) ((JSONObject) jsonObject.get("fileUpload")).get("uploadSessionId");

	}

	private HttpResponse<String> useNewHTTP_POST(String URL,String payload,String token) throws IOException, InterruptedException {
		//see https://mkyong.com/java/java-11-httpclient-examples/
		
		//get uses HttpClient.Version.HTTP_1_1
		//POST uses HttpClient.Version.HTTP_2
		
		    HttpClient httpClient = HttpClient.newBuilder()
		            .version(HttpClient.Version.HTTP_2)
		            .connectTimeout(Duration.ofSeconds(10))
		            .build();

	        HttpRequest request = HttpRequest.newBuilder()
	                .POST(HttpRequest.BodyPublishers.ofString(payload))
	                .uri(URI.create(URL))
	                .setHeader(TABLEAU_AUTH_HEADER, token).header("Accept", "application/json").header("Content-type", "application/json").build();

	        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	        
	        return response;
	
	}
	
private HttpResponse<String> useNewHTTP_GET(String URL,String token) throws IOException, InterruptedException {
		
		
		//get uses HttpClient.Version.HTTP_1_1
		//POST uses HttpClient.Version.HTTP_2
		
		    HttpClient httpClient = HttpClient.newBuilder()
		            .version(HttpClient.Version.HTTP_1_1)
		            .connectTimeout(Duration.ofSeconds(10))
		            .build();

	        HttpRequest request = HttpRequest.newBuilder()
	                .GET()
	                .uri(URI.create(URL))
	                .setHeader(TABLEAU_AUTH_HEADER, token).header("Accept", "application/json").header("Content-type", "application/json").build();

	        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	        
	        return response;
	 
	}

private HttpResponse<String> useNewHTTP_PATCH(String URL,String token,String payload) throws IOException, InterruptedException {
	
	
	//get uses HttpClient.Version.HTTP_1_1
	//POST uses HttpClient.Version.HTTP_2
	
	
	/*
	 * RequestID: A user-generated identifier that uniquely identifies a request. 
	 * If the server receives more than one request with the same ID within 24 hours, 
	 * all subsequent requests will be treated as duplicates and ignored by the server. 
	 */
	UUID uuid = UUID. randomUUID();
	String uuidAsString = uuid. toString();

	
	  HttpClient httpClient = HttpClient.newBuilder()
	            .version(HttpClient.Version.HTTP_2)
	            .connectTimeout(Duration.ofSeconds(10))
	            .build();

      HttpRequest request = HttpRequest.newBuilder()
              .method("PATCH",HttpRequest.BodyPublishers.ofString(payload))
              .uri(URI.create(URL))
              .setHeader(TABLEAU_AUTH_HEADER, token).header("Accept", "application/json").header("Content-type", "application/json").header("RequestID", uuidAsString).build();
      
      
    lw.WriteToLog("URL: " + URL);
	lw.WriteToLog("PATCH payload: " + payload);


      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      
      return response;
       
}


	
}
