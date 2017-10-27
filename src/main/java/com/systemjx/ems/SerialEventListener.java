package com.systemjx.ems;

import static com.systemj.Utils.log;
import static com.systemjx.ems.InputSignalSerial.buildID;
import static com.systemjx.ems.SharedResource.PACKET_TYPE_THL;
import static com.systemjx.ems.SharedResource.SENSOR_HUMIDITY;
import static com.systemjx.ems.SharedResource.SENSOR_LIGHT;
import static com.systemjx.ems.SharedResource.SENSOR_TEMPERATURE;
import static com.systemjx.ems.SharedResource.logException;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import com.systemj.Signal;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

public class SerialEventListener implements SerialPortEventListener {
	
	private Map<String, Signal> isMap;

	public SerialEventListener(Map<String, Signal> isMap) {
		this.isMap = isMap;
	}
	
	@Override
	public void serialEvent(SerialPortEvent ev) {
		final SerialPort sp = SharedResource.getSerialPort();
		if (ev.isRXCHAR() && ev.getEventValue() > 0) {
			try {
				byte[] b = sp.readBytes(ev.getEventValue());
				if ((b[0] & 0xFF) == 0xAA) {
					final int type = getPacketType(b);
					switch (type) {
					case PACKET_TYPE_THL:
						String idTemp = buildID(getSourceGroupID(b), getSourceNodeID(b), SENSOR_TEMPERATURE);
						String idHumidity = buildID(getSourceGroupID(b), getSourceNodeID(b), SENSOR_HUMIDITY);
						String idLight = buildID(getSourceGroupID(b), getSourceNodeID(b), SENSOR_LIGHT);
						float t = getTemperature(b);
						float h = getHumidity(b);
						float l = getLight(b);
						
						Optional<Signal> os = Optional.ofNullable(isMap.getOrDefault(idTemp, null));
						os.ifPresent(s -> s.getServer().setBuffer(new Object[] { true, t }));
						os = Optional.ofNullable(isMap.getOrDefault(idHumidity, null));
						os.ifPresent(s -> s.getServer().setBuffer(new Object[] { true, h }));
						os = Optional.ofNullable(isMap.getOrDefault(idLight, null));
						os.ifPresent(s -> s.getServer().setBuffer(new Object[] { true, l }));
						System.out.println("Received THL: " + t + ", " + h + ", " + l);
						log.info("Received THL: " + t + ", " + h + ", " + l+" for node id "+getSourceNodeID(b));
						break;
					default:
						log.info("Unrecognized packet format " + String.format("%02X", b[0] & 0xFF));
						break;
					}
				}
			} catch (SerialPortException e) {
				logException(e);
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