package tableau.update.server.extract;

public class ServerContent {

	protected String token = "";
	protected String userID;
	protected String siteID;
	protected String projectID;
	protected String projectName;
	protected String fileUploadID;
	protected String userName;
	protected String userPassword;
	protected String serverURL = "";
	protected String apiVersion = "";
	protected String siteName;
	protected String urlAndAPI;

	protected String PATName = "";
	protected String PATSecret = "";
	protected boolean PATuse = false;
	
	protected boolean getViewsForWorkbooks;

	public String getPATName() {
		return PATName;
	}

	public void setPATName(String pATName) {
		PATName = pATName;
	}

	public String getPATSecret() {
		return PATSecret;
	}

	public void setPATSecret(String pATSecret) {
		PATSecret = pATSecret;
	}

	public boolean isPATuse() {
		return PATuse;
	}

	public void setPATuse(boolean pATuse) {
		PATuse = pATuse;
	}

	

	public String getUrlAndAPI() {
		return urlAndAPI;
	}

	public void setUrlAndAPI(String urlAndAPI) {
		this.urlAndAPI = urlAndAPI;
	}

	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public String getSiteName() {
		return siteName;
	}

	public void setSiteName(String siteName) {
		this.siteName = siteName;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getUserID() {
		return userID;
	}

	public void setUserID(String userID) {
		this.userID = userID;
	}

	public String getSiteID() {
		return siteID;
	}

	public void setSiteID(String siteID) {
		this.siteID = siteID;
	}

	public String getProjectID() {
		return projectID;
	}

	public void setProjectID(String projectID) {
		this.projectID = projectID;
	}

	public String getFileUploadID() {
		return fileUploadID;
	}

	public void setFileUploadID(String fileUploadID) {
		this.fileUploadID = fileUploadID;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getUserPassword() {
		return userPassword;
	}

	public void setUserPassword(String userPassword) {
		this.userPassword = userPassword;
	}

	public String getServerURL() {
		return serverURL;
	}

	public void setServerURL(String serverURL) {
		this.serverURL = serverURL;
		createUrlAndAPI();
	}

	public String getApiVersion() {
		return apiVersion;
	}

	public void setApiVersion(String apiVersion) {
		this.apiVersion = apiVersion;
		createUrlAndAPI();
	}

	private void createUrlAndAPI() {

		if (serverURL.length() > 0 && apiVersion.length() > 0)
			urlAndAPI = serverURL + apiVersion;

	}

	public boolean isGetViewsForWorkbooks() {
		return getViewsForWorkbooks;
	}

	public void setGetViewsForWorkbooks(boolean getViewsForWorkbooks) {
		this.getViewsForWorkbooks = getViewsForWorkbooks;
	}

}
