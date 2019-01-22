import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.DataOutputStream ;
import java.io.File ;
import java.io.FileOutputStream ;

public class SimpleApp {

	public static WawaServer wserver;
	public static ClientServer cserver;
	public static ConfigServer conf_server;
	public static ConfigClientServer conf_clientserver;

	boolean app_should_stop = false;

	public void Start() {
		System.out.println("app start");
		
		long aa = System.currentTimeMillis()/1000;
		int bb = (int)aa;
		int c = bb;

		wserver = new WawaServer();// A class that handles doll machine application messages. You should do this in
									// this section: handle the doll machine heartbeat keeps alive, timeout, and
									// maintain the state of the doll machine (idle, available, current player,
									// current player in this room, etc.)
		wserver.Start(7770);// This is the application server port to which the Android board is connected.

		cserver = new ClientServer();// Class that handles the player app。
		cserver.Start(7771);// The port to which the player app is connected

		conf_server = new ConfigServer();// This is the configuration server. This server is responsible for the doll
											// machine list and forwards the doll machine parameters.
		conf_server.Start(7776);// Configuration server port connected to the Android board

		conf_clientserver = new ConfigClientServer();// External network configuration tool processing class
		conf_clientserver.Start(7778);

		while (app_should_stop == false) {// Infinite loop listener whether to enter exit. If you enter exit, the normal exit.
			try {
				InputStreamReader is_reader = new InputStreamReader(System.in);
				String str = new BufferedReader(is_reader).readLine();
				if (str.equals("exit")) {
				
					if (wserver != null) {
						wserver.Stop();
						wserver = null;
					}

					if (cserver != null) {
						cserver.Stop();
						cserver = null;
					}

					if (conf_server != null) {
						conf_server.Stop();
						conf_server = null;
					}

					if (conf_clientserver != null) {
						conf_clientserver.Stop();
						conf_clientserver = null;
					}

					app_should_stop = true;
				} else
					continue;

			} catch (IOException e) {
				e.printStackTrace();
				app_should_stop = true;
			}
		}

		System.out.println("app exit.");
	}

	public static void main(String[] args) {
		SimpleApp app = new SimpleApp();
		app.Start();
	}
}
