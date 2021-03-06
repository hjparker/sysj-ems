package com.systemjx.ems;

import static com.systemjx.ems.InputSignalSerial.buildID;
import static com.systemjx.ems.SharedResource.PACKET_TYPE_THL;
import static com.systemjx.ems.SharedResource.SENSOR_HUMIDITY;
import static com.systemjx.ems.SharedResource.SENSOR_LIGHT;
import static com.systemjx.ems.SharedResource.SENSOR_TEMPERATURE;
import static com.systemjx.ems.SharedResource.logger;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.systemj.Signal;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

public class SerialEventListener implements SerialPortEventListener {
	
	private Map<String, List<Signal>> isMap;
	private final SerialPortConnector spc = new SerialPortConnector();
	private static final String IP_GUI = System.getProperty("ems.hostname", "127.0.0.1");
	private static final int PORT_GUI  = Integer.parseInt(System.getProperty("ems.port", "7072"));
	private Socket socket = new Socket();
	
	public SerialEventListener(Map<String, List<Signal>> isMap) {
		this.isMap = isMap;
	}
	
	public boolean isLastEventLongerThan(long min) {
		return System.currentTimeMillis() - lastEvent > TimeUnit.MINUTES.toMillis(min);
	}
	
	private long lastEvent = System.currentTimeMillis();
	
	public void updateLastEvent() {
		lastEvent = System.currentTimeMillis();
	}
	
	@Override
	public void serialEvent(SerialPortEvent ev) {
		lastEvent = System.currentTimeMillis();
		final SerialPort sp = spc.getSerialPort();
		if (ev.isRXCHAR() && ev.getEventValue() > 0 && sp != null) {
			try {
				byte[] b = sp.readBytes(ev.getEventValue());
				if ((b[0] & 0xFF) == 0xAA) {
					final int type = getPacketType(b);
					switch (type) {
					case PACKET_TYPE_THL:
						int sourceGroupId = getSourceGroupID(b);
						int sourceNodeId = getSourceNodeID(b);
						String idTemp = buildID(sourceGroupId, sourceNodeId, SENSOR_TEMPERATURE);
						String idHumidity = buildID(sourceGroupId, sourceNodeId, SENSOR_HUMIDITY);
						String idLight = buildID(sourceGroupId, sourceNodeId, SENSOR_LIGHT);
						float t = getTemperature(b);
						float h = getHumidity(b);
						float l = getLight(b);
						
						List<Signal> os = isMap.getOrDefault(idTemp, Collections.emptyList());
						if (!os.isEmpty()) {
							os.forEach(s -> s.getServer().setBuffer(new Object[] { true, t }));
							os = isMap.getOrDefault(idHumidity, Collections.emptyList());
							os.forEach(s -> s.getServer().setBuffer(new Object[] { true, h }));
							os = isMap.getOrDefault(idLight, Collections.emptyList());
							os.forEach(s -> s.getServer().setBuffer(new Object[] { true, l }));
						} else {
							try {
								if (socket.isClosed() || !socket.isConnected()) {
									socket.close();
									socket = new Socket();
									socket.connect(new InetSocketAddress(IP_GUI, PORT_GUI), 50);
								}
								BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream());
								final String sd = "{\"cd\": \"_env_fallback_\", \"name\": \"env\", \"status\": true, \"temperature\": "+t+", \"humidity\": "+h+", \"light\": "+l
												  + ", \"node\": " + sourceNodeId + "," + "\"group\": " + sourceGroupId + "}";
								bos.write(sd.getBytes());
								bos.flush();
							} catch (IOException e) {
								try {
									SharedResource.logger.fine(e.getMessage());
									socket.close();
								} catch (IOException e1) {
									SharedResource.logException(e1);
								}
							}
						}
						logger.fine("Received THL: " + t + ", " + h + ", " + l+" for node id "+getSourceNodeID(b));
						break;
					default:
						logger.info("Unrecognized packet format " + String.format("%02X", b[0] & 0xFF));
						break;
					}
				}
			} catch (SerialPortException e) {
				logger.warning(e.getMessage()+" at "+e.getMethodName()+", closing the port "+e.getPortName());
				spc.closeSerialPort();
			}
		}
	}
	
	private int getDestGroupID(byte[] b) {
		return b[7] & 0xFF;
	}
	
	private int getDestNodeID(byte[] b) {
		return b[8] & 0xFF;
	}
	
	private int getSourceGroupID(byte[] b) {
		return b[9] & 0xFF;
	}

	private int getSourceNodeID(byte[] b) {
		return b[10] & 0xFF;
	}
	
	private int getPacketType(byte[] b) {
		return b[11] & 0xFF;
	}
	
	private float getTemperature(byte[] b) {
		return (b[12] & 0xFF) + (b[13] & 0xFF) / 100;
	}
	
	private float getHumidity(byte[] b) {
		return (b[14] & 0xFF) + (b[15] & 0xFF) / 100;
	}
	
	private float getLight(byte[] b) {
		return (((b[16] & 0xFF) << 8) + (b[17] & 0xFF)) * 16;
	}
	
}
