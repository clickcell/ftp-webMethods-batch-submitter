import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Vector;

import org.apache.commons.net.util.Base64;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class SubmitPayloads {

	private static String PORTAL = "http://xx.xxx.xxx.xxx:62230/invoke/xx.xxx.xxxx.xxx.Services/DocInjector"; //webMethods server
	private static String PORTAL_USERNAME = "xxxxxxxxx";
	private static String PORTAL_PASSWORD = "xxxxxxxxx";
	// https://en.wikipedia.org/wiki/Basic_access_authentication
	private static final byte[] BASIC_ACCESS_AUTHENTICATION= Base64.encodeBase64((PORTAL_USERNAME+":"+PORTAL_PASSWORD).getBytes());
	private static String FTP_SERVER = "xx.xxx.xxx.xxx";
	private static int FTP_PORT = 22;
	private static String FTP_USERNAME = "xxxxxxxxx";
	private static String FTP_PASSWORD = "xxxxxxxxx";
	private static final String FTP_ROOT_FOLDER = "/home/xxxxxxxxx/xxx/";
	private static final String DOCUMENT_TYPE_NAME = "xx.xxx.xxxx.xxx.xxxx.xxx:xxxxxxxSubmission";
	private static final String NUM_TO_PUBLISH = "1";
	private static final String FILETYPE = ".xml";
	private static ArrayList<String> filesToSubmit = new ArrayList<String>();
	private static Session session = null;
	private static ChannelSftp sftpChannel = null;
	private static Scanner reader = new Scanner(System.in);  


	public static void main(String[] args) throws Exception {
		// remove this for release, only for testing
		//System.setProperty("http.proxyHost", "localhost");
		//System.setProperty("http.proxyPort", "8888");

		while (true) {
			try {
				filesToSubmit.clear();
				openFtpSession();
				getFiles(promptUserForDirectory());
				closeFtpSession();
				
				System.out.println(String.format("The following %d files were discovered: ", filesToSubmit.size()));
				
				for (String file : filesToSubmit){
					System.out.println(file);
				}
				
				System.out.println(String.format("%d files were discovered, enter 'y' and hit return to submit to RTI: ", filesToSubmit.size()));
				
				if (reader.nextLine().toLowerCase().contains("y")){
					for (String file : filesToSubmit){
						sendGet(file);
					}
				}
				else{
					System.out.println("Nothing has been submitted, Goodbye");
					System.exit(0);
				}
				
				break;
			} 
			catch (Exception e) {
				closeFtpSession();
				//e.printStackTrace();
				System.err.println(e.getMessage());
			}
		}
	}
	
	public static String promptUserForDirectory(){
		String path = "";

		while (!path.contains(FTP_ROOT_FOLDER)){
			System.out.println(String.format("Please paste in the directory path from WinSCP, including %s: ", FTP_ROOT_FOLDER));
			
			while (true) {
				if (reader.hasNext()){
					path = reader.nextLine();
					break;
				}
			}
		}
		if (path.endsWith("/")){
			path = path.substring(0, path.length()-1);
		}
		return path;
	}
	
	private static void openFtpSession() throws Exception{
		JSch jsch = new JSch();
		
		session = jsch.getSession(FTP_USERNAME, FTP_SERVER, FTP_PORT);
		session.setConfig("StrictHostKeyChecking", "no");
		session.setPassword(FTP_PASSWORD);
		session.connect();

		Channel channel = session.openChannel("sftp");
		channel.connect();
		sftpChannel = (ChannelSftp) channel;
	}
	
	private static void closeFtpSession() throws Exception{
		sftpChannel.exit();
		session.disconnect();
	}

	@SuppressWarnings("unchecked")
	private static void getFiles(String path) throws Exception{
		
		sftpChannel.cd(path);
		
		ArrayList<String> subDiretoryList = new ArrayList<String>();
		Vector<ChannelSftp.LsEntry> directoryList = sftpChannel.ls("*");
		
		for(int index=0; index < directoryList.size(); index++){
			
			// need to crawl for subdirectories
			if (directoryList.get(index).getAttrs().isDir()){
				subDiretoryList.add(path + "/" + directoryList.get(index).getFilename());
			}
			else
			{
				if (directoryList.get(index).getFilename().toLowerCase().endsWith(FILETYPE)){
					filesToSubmit.add(path + "/" +  directoryList.get(index).getFilename());
				}
			}
		}
		for (String subDirectory : subDiretoryList){
			getFiles(subDirectory);
		}
	}

	private static void sendGet(String filepath) throws Exception {
		//BUILD REQUEST STRING
		String request = String.format("%s?DocumentTypeName=%s&DocumentContentFilePath=%s&NumToPublish=%s", 
				PORTAL,
				URLEncoder.encode(DOCUMENT_TYPE_NAME, "UTF-8"),
				URLEncoder.encode(filepath, "UTF-8"),
				URLEncoder.encode(NUM_TO_PUBLISH, "UTF-8"));
		
		URL obj = new URL(request);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();

		con.setRequestMethod("GET");
		con.setRequestProperty("Authorization", "Basic " + new String(BASIC_ACCESS_AUTHENTICATION));
		
		if (con.getResponseCode() == 200){
			System.out.println(String.format("Successfully submitted %s", filepath));
		}
		else{
			System.out.println(String.format("Failure to submit %s, HTTP code %d", filepath, con.getResponseCode()));
		}
	}
}