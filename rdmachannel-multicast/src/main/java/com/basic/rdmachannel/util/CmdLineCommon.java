package com.basic.rdmachannel.util;

import org.apache.commons.cli.*;

/**
 * locate com.basic.rdma
 * Created by master on 2019/8/25.
 */
public class CmdLineCommon {

	private static final String IP_KEY = "a";
	private String ip;
	private static final String DEFAULT_IP = "locahost";

	private static final String PORT_KEY = "p";
	private int port;
	private static final int DEFAULT_PORT = 1919;

	private static final String SIZE_KEY = "s";
	private int size;
	private static final int DEFAULT_SIZE = 1024*1024;

	private static final String INTERFACE_KEY = "i";
	private String iface;
	private static final String DEFAULT_INTERFACE = "ib0";

	private static final String LOOP_KEY = "k";
	private int loop;
	private static final int LOOP_DEFAULT = 500;

	private static final String NUMBER_KEY = "m";
	private int num;
	private static final int NUMBER_DEFAULT = 2;

	private final String appName;

	private final Options options;

	public CmdLineCommon(String appName) {
		this.appName = appName;

		this.options = new Options();
		Option address = Option.builder(IP_KEY).desc("ip address").hasArg().build();
		Option iface = Option.builder(INTERFACE_KEY).desc("iface").hasArg().type(String.class).build();
		Option port = Option.builder(PORT_KEY).desc("port").hasArg().type(Number.class).build();
		Option size = Option.builder(SIZE_KEY).desc("size").hasArg().type(Number.class).build();
		Option iterations = Option.builder(LOOP_KEY).desc("number of iterations").hasArg().type(Number.class).build();
		Option numbers = Option.builder(NUMBER_KEY).desc("number of multicast Set").hasArg().type(Number.class).build();

		options.addOption(address);
		options.addOption(port);
		options.addOption(size);
		options.addOption(iface);
		options.addOption(iterations);
		options.addOption(numbers);
	}

	protected Options addOption(Option option) {
		return options.addOption(option);
	}

	public void printHelp() {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(appName, options);
	}

	protected void getOptionsValue(CommandLine line) throws ParseException {
		ip = line.getOptionValue(IP_KEY);
		if (line.hasOption(PORT_KEY)) {
			port = ((Number) line.getParsedOptionValue(PORT_KEY)).intValue();
		} else {
			port = DEFAULT_PORT;
		}
		if (line.hasOption(SIZE_KEY)) {
			size = ((Number) line.getParsedOptionValue(SIZE_KEY)).intValue();
		} else {
			size = DEFAULT_SIZE;
		}
		if (line.hasOption(IP_KEY)) {
			ip = ((String) line.getParsedOptionValue(IP_KEY));
		} else {
			ip = DEFAULT_IP;
		}
		if (line.hasOption(INTERFACE_KEY)) {
			iface = ((String) line.getParsedOptionValue(INTERFACE_KEY));
		} else {
			iface = DEFAULT_INTERFACE;
		}
		if (line.hasOption(LOOP_KEY)) {
			loop = ((Number)line.getParsedOptionValue(LOOP_KEY)).intValue();
		} else {
			loop = LOOP_DEFAULT;
		}
		if (line.hasOption(NUMBER_KEY)) {
			num= ((Number)line.getParsedOptionValue(NUMBER_KEY)).intValue();
		} else {
			num = NUMBER_DEFAULT;
		}
	}

	public void parse(String[] args) throws ParseException {
		CommandLineParser parser = new DefaultParser();
		CommandLine line = parser.parse(options, args);
		getOptionsValue(line);
	}

	public String getIp() {
		return ip;
	}

	public int getPort() {
		return port;
	}

	public int getSize() {
		return size;
	}

	public String getIface() {
		return iface;
	}

	public int getLoop() {
		return loop;
	}

	public int getNum() {
		return num;
	}
}
